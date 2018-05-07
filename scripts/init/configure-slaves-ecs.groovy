import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.util.EC2MetadataUtils
import com.amazonaws.services.elasticloadbalancing.*
import com.amazonaws.services.elasticloadbalancing.model.*
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.MountPointEntry
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.EnvironmentEntry
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.LogDriverOption
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.PortMappingEntry
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.ExtraHostEntry

import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud
import hudson.model.*
import hudson.plugins.gradle.*
import hudson.tools.*
import jenkins.model.*
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectInputStream;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ListObjectsRequest;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

import groovy.json.JsonSlurper

import java.util.logging.Logger;

def env = System.getenv()

if ( env.JENKINSENV_SLAVEPROVIDER == "ecs" ) { 
    
    Logger.global.info("[Running] Configuring ECS as slave provider")
    configureCloud()
    Jenkins.instance.save()
    Logger.global.info("[Done] ECS Slave Provider configuraton finished ")

}

private String getRegion() {
    EC2MetadataUtils.instanceInfo.region
}

private getClientConfiguration() {
    new ClientConfiguration()
}

private String getJenkinsURL() {
    JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
    return globalConfig.getUrl()
}

private String getClusterArn() {
    def env = System.getenv()
    return env.ECS_ARN
}

private String getVolumeCodeOnTapPath() {
    def env = System.getenv()
    return env.CODEONTAPVOLUME
}

private String queryJenkinsClusterArn(String regionName, String clusterArn) {
    AmazonECSClient client = new AmazonECSClient(clientConfiguration)
    client.setRegion(RegionUtils.getRegion(regionName))
    client.listClusters().getClusterArns().find { it.contains(clusterArn) }
}

private void configureCloud( ) {
    try {
        Logger.global.info("Building ECS Task Definition Templates")

        // Explicit Task Definitions 
        def ecsTemplates = templates = Arrays.asList(
            //createECSTaskTemplate('codeontap-something', 'codeontap/gen3:jenkinsslave-stable', 512, 1024),
            //createECSTaskTemplate('codeontap-latest', 'codeontap/gen3:jenkinsslave-latest', 512, 1024),
        )

        // S3 Configuration file based Templates 
        ecsTemplates += getS3ECSTaskTemplates()

        Logger.global.info( "Found ${ecsTemplates.Size()} Task Definitions" )

        String envClusterArn = getClusterArn()
        String clusterArn = queryJenkinsClusterArn(region, envClusterArn)

        Logger.global.info("Creating ECS Cloud for $clusterArn")
        def ecsCloud = new ECSCloud(
                name = "jenkins_cluster",
                templates = ecsTemplates,
                credentialsId = '',
                cluster = clusterArn,
                regionName = region,
                jenkinsUrl = null,
                slaveTimoutInSeconds = 60
        )

        Jenkins.instance.clouds.clear()
        Jenkins.instance.clouds.add(ecsCloud)
    } catch (com.amazonaws.SdkClientException e) {
        Logger.global.severe({ e.message })
        Logger.global.severe("ERROR: Could not create ECS config, are you running this container in AWS?")
    }
}

private ECSTaskTemplate createECSTaskTemplate(String label, String image, int softMemory, int cpu) {
    Logger.global.info("Creating ECS Template '$label' for image '$image' (memory: softMemory, cpu: $cpu)")

    new ECSTaskTemplate(
            templateName = label,
            label = label,
            image = image,
            remoteFSRoot = "/home/jenkins",
            //memory reserved
            memory = 0,
            //soft memory
            memoryReservation = softMemory,
            cpu = cpu,
            privileged = false,
            containerUser = null,
            logDriverOptions = null,
            environments = null,
            extraHosts = null,
            mountPoints = null,
            portMappings = null
    )
}

private ArrayList<ECSTaskTemplate> getS3ECSTaskTemplates() {

    def env = System.getenv()
    def templates = env.findResults {  k, v -> k.startsWith("SLAVE") == true ? [ (k.split("_").reverse()[1..-1]).join("-"), k.split("_").reverse()[0],v ] : null }
    templates = templates.groupBy( { template -> template[0] })

    def taskTemplates = []

    for ( String key in templates.keySet() ) {
        def String s3Bucket = ""
        def String definitionFile = ""

        def String localFile = "/tmp/taskDefinition-${key}.config"

        properties = templates.get(key)
        for ( property in properties ) { 
            switch (property[1]) { 
                case "DEFINITIONBUCKET":
                    s3Bucket = property[2]
                    break
                case "DEFINITIONFILE":
                    definitionFile = property[2]
                    break
            }
        }

        AmazonS3 s3 = 
        AmazonS3ClientBuilder.standard()
                             .withRegion("ap-southeast-2") // The first region to try your request against
                             .withForceGlobalBucketAccessEnabled(true) // If a bucket is in a different region, try again in the correct region
                             .build();
        try {
            S3Object o = s3.getObject(s3Bucket, definitionFile);
            S3ObjectInputStream s3is = o.getObjectContent();
            FileOutputStream fos = new FileOutputStream(new File(localFile));
            byte[] read_buf = new byte[1024];
            int read_len = 0;
            while ((read_len = s3is.read(read_buf)) > 0) {
                fos.write(read_buf, 0, read_len);
            }
            s3is.close();
            fos.close();
        } catch (AmazonServiceException e) {
            Logger.global.info(e.getErrorMessage());
        } catch (FileNotFoundException e) {
            Logger.global.info(e.getErrorMessage());
        } catch (IOException e) {
            Logger.global.info(e.getErrorMessage());
        }

        def configFile = new File(localFile)
        def configJson = new JsonSlurper().parseText(configFile.text)

        Logger.global.info(configJson.toMapString())
        
        for ( containerDefinition in configJson.ContainerDefinitions ) {
            Logger.global.info("Building S3 container definition for ${containerDefinition.Name}")
            
            def mountPoints = []
            for ( mountPoint in containerDefinition.MountPoints) {
                mountPoints.push(new MountPointEntry(
                    name = mountPoint.ContainerPath.Join("-"),
                    sourcePath = mountPoint.SourceVolume,
                    containerPath = mountPoint.ContainerPath,
                    readOnly = mountPoint.ReadOnly
                ))
            }

            def environmentVariables = []
            for ( environmentVariable in containerDefinition.Environment ) {
                environmentVariables.push( new EnvironmentEntry(
                    name = environmentVariable.Name,
                    value = environmentVariable.Value
                ))
            }

            def logDriverOptions = []
            containerDefinition.LogConfiguration.Options.each {  k, v -> logDriverOptions.push( new LogDriverOption( name=k, value=v))}
            
            def portMappings = []
            for ( portMapping in containerDefinition.PortMappings ) {
                portMappings.push( new PortMappingEntry( 
                    containerPort = portMapping.ContainerPort,
                    hostPort = portMapping.HostPort, 
                    protocol = "tcp"
                ))
            }

            def extraHosts = []
            for ( host in containerDefinition.ExtraHosts ) {
                extraHosts.push( new ExtraHostEntry(
                    ipAddress = host.IpAddress
                    hostname = host.Hostname
                ))
            }

            taskTemplate = new ECSTaskTemplate(
                    templateName = containerDefinition.Name,
                    label = containerDefinition.Name,
                    image = containerDefinition.Image,
                    remoteFSRoot = "/home/jenkins",
                    //memory reserved
                    memory = containerDefinition.Memory,
                    //soft memory
                    memoryReservation = containerDefinition.MemoryReservation,
                    cpu = containerDefinition.Cpu,
                    privileged = false,
                    containerUser = null,
                    logDriverOptions = logDriverOptions ?: null,
                    environments = environmentVariables ?: null,
                    extraHosts = extraHosts ?: null,
                    mountPoints = mountPoints ?: null,
                    portMappings = portMappings ?: null 
            
                )
            if (configJson.TaskRoleArn )  {
                taskTemplate.setTaskrole(configJson.TaskRoleArn)
            }

            if (containerDefinition.LogConfiguration.LogDriver ) { 
                taskTemplate.setLogDriver(containerDefinition.LogConfiguration.LogDriver )
            }

            taskTemplates.push(taskTemplate)
            
        }
    }
    return taskTemplates
}
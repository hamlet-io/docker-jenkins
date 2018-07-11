import com.amazonaws.ClientConfiguration
import com.amazonaws.AmazonServiceException
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

    def env =  System.getenv()
    def region = EC2MetadataUtils.getEC2InstanceRegion() ?: env.DEFAULT_AWS_REGION
}

private String getClusterArn() {
    def env = System.getenv()
    return env.ECS_ARN
}

private getClientConfiguration() {
    new ClientConfiguration()
}

private String getJenkinsURL() {
    JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
    return globalConfig.getUrl()
}

private String queryJenkinsClusterArn(String regionName, String clusterArn) {

    if ( clusterArn.startsWith("arn:") ) {
        return clusterArn 
    } 
    else { 
        AmazonECSClient client = new AmazonECSClient(clientConfiguration)
        client.setRegion(RegionUtils.getRegion(regionName))
        return client.listClusters().getClusterArns().find { it.contains(clusterArn) }
    }
}

private void configureCloud( ) {
    try {
        Logger.global.info("Building ECS Task Definition Templates")

        // Explicit Task Definitions 
        def ecsTemplates = templates = Arrays.asList(
            //createECSTaskTemplate('codeontap-something', 'codeontap/gen3:jenkinsslave-stable', 512, 1024),
            //createECSTaskTemplate('codeontap-latest', 'codeontap/gen3:jenkinsslave-latest', 512, 1024),
        )

        // Environment Variable based tasks using existing defintions 
        ecsTemplates += getEnvTaskTemplates()

        Logger.global.info( "Found ${ecsTemplates.size()} Task Definitions" )
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

private ECSTaskTemplate createECSTaskTemplate(String label, String image, String launchType, int softMemory, int cpu) {
    Logger.global.info("Creating ECS Template '$label' for image '$image' (memory: softMemory, cpu: $cpu)")

    new ECSTaskTemplate(
            templateName = label,
            label = label,
            image = image,
            remoteFSRoot = "/home/jenkins",
            //memory reserved
            memory = 0,
            //soft memory
            launchType=launchType,
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

private ArrayList<ECSTaskTemplate> getEnvTaskTemplates() {

    def env = System.getenv()
    def remoteFS = env.AGENT_REMOTE_FS ?: "/home/jenkins"
    def templates = env.findResults {  k, v -> k.startsWith("SLAVE") == true ? [ k.split("_").reverse()[0..-1].join("-"), k.split("_").reverse()[0],v ] : null }
    templates = templates.groupBy( { template -> template[0] })

    def taskTemplates = []

    for ( String key in templates.keySet() ) {

        properties = templates.get(key)
        for ( property in properties ) { 
            switch (property[1]) { 
                case "ECSHOST":
                    ecsHost = property[2]
                    break
                case "DEFINITION":
                    definitionName = property[2]
                    break
            }
        }        

        taskTemplate = new ECSTaskTemplate(
                templateName = key.toLowerCase(),
                label = key.toLowerCase(),
                taskDefinitionOverride=definitionName,
                image="jenkinsci/jnlp-slave",
                launchType="EC2",
                remoteFSRoot = remoteFS,
                //memory reserved
                memory = 1,
                //soft memory
                memoryReservation = 0,
                cpu = 1,
                subnets = null,
                securityGroups = null,
                assignPublicIp = false,
                privileged = false,
                containerUser = null,
                logDriverOptions = null,
                environments = null,
                extraHosts = null,
                mountPoints = null,
                portMappings = null
            )

        taskTemplates.push(taskTemplate)
            
    }
    return taskTemplates
}
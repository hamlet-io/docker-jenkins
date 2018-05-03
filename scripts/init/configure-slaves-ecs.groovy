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
import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud
import com.cloudbees.jenkins
import hudson.model.*
import hudson.plugins.gradle.*
import hudson.tools.*
import jenkins.model.*
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration

import java.util.logging.Logger

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

private void configureCloud() {
    try {
        Logger.global.info("Creating ECS Template")
        def ecsTemplates = templates = Arrays.asList(
                createECSTaskTemplate('codeontap', 'codeontap/gen3:jenkinsslave-stable', 512, 1024),
                createECSTaskTemplate('codeontap-latest', 'codeontap/gen3:jenkinsslave-latest', 512, 1024)
        )
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

    def volumeCodeOnTapSource = getVolumeCodeOnTapPath()
    
    def mountPoints = []

    if ( volumeCodeOnTapSource ) {
        volumeCodeOnTap = new MountPointEntry(
            name = siteProperties
            sourcePath = volumeCodeOnTapSource
            containerPath = "/var/opt/codeontap/"
            readOnly = true
        )

        mountPoints.push(volumeCodeOnTap)
    }

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
            mountPoints = mountPoints,
            portMappings = null
    )
}
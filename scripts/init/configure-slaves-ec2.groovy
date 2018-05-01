import com.amazonaws.ClientConfiguration
import com.amazonaws.regions.RegionUtils
import com.amazonaws.services.ecs.AmazonECSClient
import com.amazonaws.util.EC2MetadataUtils
import com.amazonaws.services.elasticloadbalancing.*
import com.amazonaws.services.elasticloadbalancing.model.*
import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate
import hudson.model.*
import hudson.plugins.gradle.*
import hudson.tools.*
import jenkins.model.*
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration

import java.util.logging.Logger

Logger.global.info("[Running] startup script")

configureCloud()

Jenkins.instance.save()

Logger.global.info("[Done] startup script")


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

private String queryJenkinsClusterArn(String regionName, String clusterArn) {
    AmazonECSClient client = new AmazonECSClient(clientConfiguration)
    client.setRegion(RegionUtils.getRegion(regionName))
    client.listClusters().getClusterArns().find { it.contains(clusterArn) }
}

private void configureCloud() {
    try {
        Logger.global.info("Creating ECS Template")
        def ecsTemplates = templates = Arrays.asList(
                createECSTaskTemplate('codeontap', 'codeontap/gen3', 2048, 2048)
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

//cloudbees/jnlp-slave-with-java-build-tools
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

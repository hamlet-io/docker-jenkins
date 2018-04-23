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

def env = System.getenv()
def jenkins = Jenkins.getInstance()

Logger.global.info("Configuring ECS Slaves")

if ( env.JENKINS_SLAVEPROVIDER == "ecs" ) {

    def clusterName = env.ECS_ARN

    configureCloud(clusterName)

    Jenkins.instance.save()

    Logger.global.info("[Done] startup script")

}
else { 
    Logger.global.info("[Done] ECS not required, moving on..")
}

private getClientConfiguration() {
    new ClientConfiguration()
}

private String getRegion() {
    EC2MetadataUtils.instanceInfo.region
}

private String queryJenkinsClusterArn(String regionName String arn) {
    AmazonECSClient client = new AmazonECSClient(clientConfiguration)
    client.setRegion(RegionUtils.getRegion(regionName))
    client.listClusters().getClusterArns().find { it.contains(arn) }
}

private void configureCloud( String ecsCluster ) {
    try {
        Logger.global.info("Creating ECS Template")
        def ecsTemplates = templates = Arrays.asList(
                //a t2.micro has 992 memory units & 1024 CPU units
                createECSTaskTemplate('ecs-java', 'cloudbees/jnlp-slave-with-java-build-tools', 992, 1024),
        )
        String clusterArn = queryJenkinsClusterArn(region)

        Logger.global.info("Creating ECS Cloud for $clusterArn")
        def ecsCloud = new ECSCloud(
                name = "jenkins_cluster",
                templates = ecsTemplates,
                credentialsId = '',
                cluster = clusterArn,
                regionName = region,
                jenkinsUrl = instanceUrl,
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
            logDriverOptions = null,
            environments = null,
            extraHosts = null,
            mountPoints = null
    )
}


import com.amazonaws.arn.Arn

import com.cloudbees.jenkins.plugins.amazonecs.ECSCloud
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.MountPointEntry
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.EnvironmentEntry
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.LogDriverOption
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.PortMappingEntry
import com.cloudbees.jenkins.plugins.amazonecs.ECSTaskTemplate.ExtraHostEntry

import hudson.model.*
import hudson.plugins.gradle.*
import hudson.tools.*
import jenkins.model.*
import jenkins.model.Jenkins
import jenkins.model.JenkinsLocationConfiguration

import java.util.List;

import java.util.logging.Logger;

def env = System.getenv()

Logger.global.info("[Running] Creating agent definition for ECS Agents" )

ecsAgentEnvPrefix = env.ECS_AGENT_PREFIX ?: 'AGENT'

if ( (env.findResults {  k, v -> k.startsWith(ecsAgentEnvPrefix) == true }).contains(true)  ) {
    Logger.global.info("[Running] Configuring ECS as agent provider")

    try {
        // Environment Variable based tasks using existing defintions
        def ecsTemplates = getEnvTaskTemplates(ecsAgentEnvPrefix)

        Logger.global.info( "Found ${ecsTemplates.size()} Task Definitions" )
        Arn envClusterArn = Arn.fromString(env.ECS_ARN)
        String envClusterRegion = envClusterArn.getRegion()
        String envClusterCredentialId = env.ECS_CRED_ID ?: null

        String jnlpTunnel = env.AGENT_JNLP_TUNNEL ?: ''
        String jenkinsInternalUrl = env.AGENT_JENKINS_URL ?: ''

        Logger.global.info("Creating ECS cloud for " + envClusterArn.toString())
        def ecsCloud = new ECSCloud(
                name = "jenkins_cluster",
                credentialsId = envClusterCredentialId,
                cluster = envClusterArn.toString()
        )

        ecsCloud.setRegionName(envClusterRegion)
        ecsCloud.setSlaveTimeoutInSeconds(300)
        ecsCloud.setJenkinsUrl(jenkinsInternalUrl)
        ecsCloud.setTunnel(jnlpTunnel)

        if ( ecsTemplates ) {
            ecsCloud.setTemplates(ecsTemplates)
        }

        Jenkins.instance.clouds.clear()
        Jenkins.instance.clouds.add(ecsCloud)

    } catch (com.amazonaws.SdkClientException e) {
        Logger.global.severe({ e.message })
        Logger.global.severe("ERROR: Could not create ECS config, are you running this container in AWS?")
    }


    Jenkins.instance.save()
    Logger.global.info("[Done] ECS agent provider configuraton finished ")
}


private String getJenkinsURL() {
    JenkinsLocationConfiguration globalConfig = new JenkinsLocationConfiguration();
    return globalConfig.getUrl()
}

private ArrayList<ECSTaskTemplate> getEnvTaskTemplates(String ecsAgentEnvPrefix) {

    def env = System.getenv()
    def remoteFS = env.AGENT_REMOTE_FS ?: "/home/jenkins"

    def ecsAgentPrefixLength = ecsAgentEnvPrefix.split('_').size()

    def templates = env.findResults {  k, v -> k.startsWith(ecsAgentEnvPrefix) == true ? [ (k.split("_")[-2..ecsAgentPrefixLength].reverse()).join("-"), k.split("_").reverse()[0], v ] : null }
    templates = templates.groupBy( { template -> template[0] })

    Logger.global.info(" '$templates' ")

    def taskTemplates = []

    for ( String key in templates.keySet() ) {

        def definitionName = ""

        properties = templates.get(key)
        for ( property in properties ) {
            switch (property[1]) {
                case "DEFINITION":
                    definitionName = property[2]
                    break
            }
        }

        if ( definitionName ) {

            taskTemplate = new ECSTaskTemplate(
                templateName = key.toLowerCase(),
                label = key.toLowerCase(),
                taskDefinitionOverride = definitionName,
                dynamicTaskDefinitionOverride = null,
                image = "jenkins/jnlp-slave",
                repositoryCredentials = null,
                launchType = "EC2",
                networkMode = "default",
                remoteFSRoot = null,
                uniqueRemoteFSRoot = false,
                platformVersion = 'LATEST',
                memory = 0 ,
                memoryReservation = 128,
                cpu = 128,
                subnets = null,
                securityGroups = null,
                assignPublicIp = false,
                privileged = false,
                containerUser = null,
                logDriverOptions = null,
                environments = null,
                extraHosts = null,
                mountPoints = null,
                portMappings = null,
                executionRole = null,
                placementStrategies = null,
                taskrole = null,
                inheritFrom= null,
                sharedMemorySize = 64,
            )
            taskTemplates.push(taskTemplate)
        }
    }
    return taskTemplates
}

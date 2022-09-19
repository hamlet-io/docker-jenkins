FROM jenkins/jenkins:lts-jdk11

USER jenkins

# install plugins specified in https://github.com/kohsuke/jenkins/blob/master/core/src/main/resources/jenkins/install/platform-plugins.json
COPY scripts/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli -f /usr/share/jenkins/ref/plugins.txt

RUN echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state

#Copy Jenkins init files - make sure they override the old files
COPY scripts/init/ /usr/share/jenkins/ref/init.groovy.d/
RUN find /usr/share/jenkins/ref/init.groovy.d/ -type f -exec mv '{}' '{}'.override \;
COPY scripts/casc/ /usr/share/jenkins/ref/casc_configs

# Container Configuration
USER root

## Used to store properties files which are shared between agents
ENV PROPERTIES_DIR="/var/opt/properties/"

RUN mkdir -p "${PROPERTIES_DIR}" && \
    chown -R jenkins:jenkins "${PROPERTIES_DIR}" && \
    mkdir -p "/var/opt/codeontap" && \
    chown -R jenkins:jenkins "/var/opt/codeontap" && \
    mkdir -p "/var/opt/hamlet" && \
    chown -R jenkins:jenkins "/var/opt/hamlet"

# Change back to jenkins user to run jenkins
USER jenkins

ENV AGENT_REMOTE_FS="/home/jenkins"
ENV CASC_JENKINS_CONFIG="/usr/share/jenkins/ref/casc_configs"

# Defaults for Jenkins Configuration
ENV JENKINS_URL="http://localhost:8080"
ENV JENKINS_ADMIN="root@local.host"
ENV JENKINS_MASTER_EXECUTORS="0"
ENV JENKINS_QUIET_PERIOD="0"
ENV JENKINS_SCM_RETRY_COUNT="2"

ENV SLACK_BOT_USER="false"

# Jenkins Authentication
ENV JENKINSENV_SECURITYREALM="local"

ENV JENKINSENV_USER=""
ENV JENKINSENV_PASS=""

ENV GITHUBAUTH_CLIENTID=""
ENV GITHUBAUTH_ADMIN=""
ENV GITHUBAUTH_SECRET=""

ENV TIMEZONE="Australia/Sydney"
ENV JAVA_OPTS="-Dhudson.DNSMultiCast.disabled=true \
                -Djenkins.install.runSetupWizard=false \
                -Dorg.apache.commons.jelly.tags.fmt.timeZone=${TIMEZONE} \
                -Dhudson.slaves.ChannelPinger.pingIntervalSeconds=300 \
                -Dhudson.slaves.ChannelPinger.pingTimeoutSeconds=30 \
                -Dhudson.slaves.NodeProvisioner.initialDelay=0"

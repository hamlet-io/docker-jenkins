FROM jenkins/jenkins:lts

LABEL MAINTAINER="codeontap"

# Install OS Packages
USER root
RUN apt-get update && apt-get install -y \
    curl \
    apt-utils \
    dos2unix \
    tar zip unzip \
    less vim tree \
    git \
    jq \
    ca-certificates \
 && rm -rf /var/lib/apt/lists/*

 USER jenkins

# install plugins specified in https://github.com/kohsuke/jenkins/blob/master/core/src/main/resources/jenkins/install/platform-plugins.json
COPY scripts/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN /usr/local/bin/install-plugins.sh < /usr/share/jenkins/ref/plugins.txt
RUN echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state

#Copy Jenkins init files - make sure they override the old files
COPY scripts/init/ /usr/share/jenkins/ref/init.groovy.d/
RUN find /usr/share/jenkins/ref/init.groovy.d/ -type f -exec mv '{}' '{}'.override \;
COPY scripts/casc/ /usr/share/jenkins/ref/casc_configs

# Container Configuration 
USER root

# Create extra volumes for logging and WAR cache location to allow for updates as part of master docker image 
RUN mkdir -p /var/log/jenkins && \
    chown -R jenkins:jenkins /var/log/jenkins && \
    mkdir -p /var/opt/codeontap/ && \
    chown -R jenkins:jenkins /var/opt/codeontap/ 

# Change back to jenkins user to run jenkins
USER jenkins

ENV AGENT_REMOTE_FS="/home/jenkins"
ENV CASC_JENKINS_CONFIG="/usr/share/jenkins/ref/casc_configs"

ENV JENKINS_URL="http://localhost:8080"
ENV JENKINS_ADMIN="root@local.host"
ENV JENKINS_MASTER_EXECUTORS="0"
ENV JENKINS_QUIET_PERIOD="0"
ENV JENKINS_SCM_RETRY_COUNT="2"
ENV JENKINSENV_SLAVEPROVIDER="ecs"

# Jenkins Authentication
ENV JENKINSENV_SECURITYREALM="local"

ENV JENKINSENV_USER=""
ENV JENKINSENV_PASS=""

ENV GITHUBAUTH_CLIENTID=""
ENV GITHUBAUTH_ADMIN=""
ENV GITHUBAUTH_SECRET=""

ENV TIMEZONE="Australia/Sydney"
ENV MAXMEMORY="4096m"
ENV JAVA_OPTS="-Dhudson.DNSMultiCast.disabled=true \
                -Djenkins.install.runSetupWizard=false \
                -Dorg.apache.commons.jelly.tags.fmt.timeZone=${TIMEZONE} \
                -Duser.timezone=${TIMEZONE} \
                -XX:+UnlockExperimentalVMOptions \
                -XX:+UseCGroupMemoryLimitForHeap \
                -XX:MaxRAMFraction=2 \
                -XshowSettings:vm \
                -Dhudson.slaves.ChannelPinger.pingIntervalSeconds=300 \
                -Dhudson.slaves.ChannelPinger.pingTimeoutSeconds=30 \
                -Dhudson.slaves.NodeProvisioner.initialDelay=0 \
                -Dhudson.slaves.NodeProvisioner.MARGIN=50 \
                -Dhudson.slaves.NodeProvisioner.MARGIN0=0.85"


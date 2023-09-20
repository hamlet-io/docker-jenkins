FROM jenkins/jenkins:lts-jdk17

USER jenkins

# install plugins specified in https://github.com/kohsuke/jenkins/blob/master/core/src/main/resources/jenkins/install/platform-plugins.json
COPY scripts/plugins.txt /usr/share/jenkins/ref/plugins.txt
RUN jenkins-plugin-cli -f /usr/share/jenkins/ref/plugins.txt
RUN echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state

#Copy Jenkins init files - make sure they override the old files
COPY scripts/init.groovy.d/ /usr/share/jenkins/ref/init.groovy.d/
RUN find /usr/share/jenkins/ref/init.groovy.d/ -type f -exec mv '{}' '{}'.override \;

ENV TIMEZONE="Australia/Sydney"
ENV JAVA_OPTS="-Dhudson.DNSMultiCast.disabled=true \
                -Djenkins.install.runSetupWizard=false \
                -Dorg.apache.commons.jelly.tags.fmt.timeZone=${TIMEZONE} \
                -Dhudson.slaves.ChannelPinger.pingIntervalSeconds=300 \
                -Dhudson.slaves.ChannelPinger.pingTimeoutSeconds=30 \
                -Dhudson.slaves.NodeProvisioner.initialDelay=0"

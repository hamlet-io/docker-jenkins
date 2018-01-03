FROM jenkins/jenkins:lts

LABEL MAINTAINER="codeontap"

# install plugins specified in https://github.com/kohsuke/jenkins/blob/master/core/src/main/resources/jenkins/install/platform-plugins.json

# install Organisation and Administration plugins
RUN /usr/local/bin/install-plugins.sh cloudbees-folder
RUN /usr/local/bin/install-plugins.sh antisamy-markup-formatter

# install Build Features plugins
RUN /usr/local/bin/install-plugins.sh build-timeout
RUN /usr/local/bin/install-plugins.sh credentials-binding
RUN /usr/local/bin/install-plugins.sh timestamper
RUN /usr/local/bin/install-plugins.sh ws-cleanup

# install Build Tools plugins
RUN /usr/local/bin/install-plugins.sh ant
RUN /usr/local/bin/install-plugins.sh gradle

# install Pipelines and Continuous Delivery plugins
# These are pretty big plugins so they have specific versions set to reduce potential issues
RUN /usr/local/bin/install-plugins.sh workflow-aggregator:2.5
RUN /usr/local/bin/install-plugins.sh github-organization-folder:1.6
RUN /usr/local/bin/install-plugins.sh pipeline-stage-view:2.9

# install Source Code Management plugins
RUN /usr/local/bin/install-plugins.sh git
RUN /usr/local/bin/install-plugins.sh subversion

# install Distributed Builds plugins
RUN /usr/local/bin/install-plugins.sh ssh-slaves

# install User Management and Security plugins
RUN /usr/local/bin/install-plugins.sh matrix-auth
RUN /usr/local/bin/install-plugins.sh pam-auth
RUN /usr/local/bin/install-plugins.sh ldap

# install Notifications and Publishing plugins
RUN /usr/local/bin/install-plugins.sh email-ext
RUN /usr/local/bin/install-plugins.sh mailer

# Install CodeOnTap Specific Plugins
RUN /usr/local/bin/install-plugins.sh github-oauth
RUN /usr/local/bin/install-plugins.sh slack
RUN /usr/local/bin/install-plugins.sh extended-choice-parameter
RUN /usr/local/bin/install-plugins.sh build-user-vars-plugin
RUN /usr/local/bin/install-plugins.sh envinject
RUN /usr/local/bin/install-plugins.sh parameterized-trigger

RUN echo 2.0 > /usr/share/jenkins/ref/jenkins.install.UpgradeWizard.state

# Container Configuration 
USER root

# Create Jenkins Volumes 
RUN mkdir /var/log/jenkins
RUN mkdir /var/cache/jenkins
RUN chown -R jenkins:jenkins /var/log/jenkins
RUN chown -R jenkins:jenkins /var/cache/jenkins

# Install OS Packages
RUN apt-get update && apt-get install -y \
    build-essential \
    docker \
    dos2unix \
    git \
    jq \
    && rm -rf /var/lib/apt/lists/*

# Install Node/NPM 
RUN ["/bin/bash", "-c", "set -o pipefail && curl -sL https://deb.nodesource.com/setup_8.x | bash -" ]
RUN apt-get update && apt-get install -y \
    nodejs\
     && rm -rf /var/lib/apt/lists/*

# Install NPM Packages
RUN npm install --only=production -g \
    yamljs \
    ajv \
    swagger \
    swagger-tools

# Change back to jenkins user to run jenkins
USER jenkins

# Set environmental configuration for Jenkins
ENV JAVA_OPTS="-Xmx4096m -Dhudson.DNSMultiCast.disabled=true -Dorg.apache.commons.jelly.tags.fmt.timeZone=Australia/Sydney" 
ENV JENKINS_OPTS="--logfile=/var/log/jenkins/jenkins.log --webroot=/var/cache/jenkins/war"

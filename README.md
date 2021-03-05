# Jenkins Master for Hamlet Deploy

This container provides a standard Jenkins install which we recommended for [Hamlet Deploy](https://docs.hamlet.io) deployments.

The container also supports deployment using Hamlet Deploy along with using this Jenkins server to deploy other products.

## Environment Variables

### Jenkins Base Level Configuration

- JENKINS_URL
- TIMEZONE
- MAXMEMORY
- JAVA_OPTS
- JAVA_EXTRA_OPTS

### Agent Configuration

This Jenkins instance is designed to work with container based on-demand agents and include init scripts to provision clouds based on the [amazon-ecs](https://github.com/jenkinsci/amazon-ecs-plugin) plugin

- ECS_ARN - The ARN for the ECS cluster to run the task on
- AGENT_JNLP_TUNNEL - An alternate hostname:port combination which can be used to provide a new network path to the JNLP endpoint
- AGENT_JENKINS_URL - An alternate Url which can be used to provide a new network path to the JNLP endpoint
- AGENT_REMOTE_FS - The path on the agent which will be used as the agent users home

Multiple agent labels with their own task definitions are supported

- ECS_AGENT_PREFIX - Sets the prefix used to search in environment variables for the agents

Agents can then be defined using the following syntax

- <ECS_AGENT_PREFIX>_<Agent_Label>_DEFINITION - ECS Task Definition Id or Arn

### Security Realm

The Security realm defines the authentication service provider you would like to use for your Jenkins Instance

- JENKINSENV_SECURITYREALM

### Security Realm - Local

- JENKINSENV_USER
- JENKINSENV_PASS

### Security Realm - Github Auth

- GITHUBAUTH_CLIENTID
- GITHUBAUTH_ADMIN
- GITHUBAUTH_SECRET

### Security Realm - SAML

- SAMLAUTH_META_XML
- SAMLAUTH_META_URL
- SAMLAUTH_META_UPDATE
- SAMLAUTH_ATTR_DISPLAYNAME
- SAMLAUTH_ATTR_USERNAME
- SAMLAUTH_ATTR_GROUP
- SAMLAUTH_ATTR_EMAIL
- SAMLAUTH_LOGOUT_URL
- SAMLAUTH_LIFETIME_MAX
- SAMLAUTH_BINDING
- SAMLAUTH_USERCASE

### Github Webhook Secret

- GITHUB_WEBHOOK_CRED_ID - the id of the plain text credential for the github webhook secret

### Slack Default Configuration

- SLACK_WORKSPACE
- SLACK_DEFAULT_CHANNEL
- SLACK_CRED_ID - the id of the plain text credential for the slack token

### Jenkins Credentials

Allows you to provide credentials for the global Jenkins namespace to add on startup
The type allows for other credentials to be loaded and they each have their own properties

#### Generic Properties

- JENKINSENV_CREDENTIALS_<NAME>_NAME
- JENKINSENV_CREDENTIALS_<NAME>_DESCRIPTION
- JENKINSENV_CREDENTIALS_<NAME>_TYPE

#### Type: userpw

Creates a username and password credential based on the [credentials plugin](https://github.com/jenkinsci/credentials-plugin)

- JENKINSENV_CREDENTIALS_<NAME>_USER
- JENKINSENV_CREDENTIALS_<NAME>_PASSWORD

#### Type: secret

Creates a secret text credential based on the [plaintext credential plugin](https://github.com/jenkinsci/plain-credentials-plugin)

- JENKINSENV_CREDENTIALS_<NAME>_SECRET

#### Type: githubapp

Creates a github app credential based on the [github branch source](https://github.com/jenkinsci/github-branch-source-plugin) GitHub app

- JENKINSENV_CREDENTIALS_<NAME>_GHAPPID
- JENKINSENV_CREDENTIALS_<NAME>_GHAPPOWNER
- JENKINSENV_CREDENTIALS_<NAME>_PRIVATEKEY

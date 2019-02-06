# Jenkins Master for Codeontap

This container provides a standard Jenkins install which we recommend for CodeOnTap ( https://codeontap.io ) deployments.
The container also supports deployment using the CodeOnTap framework along with using this Jenkins server to deploy other products.

## Environment Variables

### Jenkins Base Level Configuration

- JENKINS_URL
- TIMEZONE
- MAXMEMORY
- JAVA_OPTS
- JAVA_EXTRA_OPTS

### Agent Configuration

This Jenkins instance is designed to work with container based on-demand agents

- JENKINSENV_SLAVEPROVIDER

- SLAVE_<NAME>_ECSHOST
- SLAVE_<NAME>_DEFINITION

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

### Jenkins Credentails

- JENKINSENV_CREDENTIALS_<NAME>_USER
- JENKINSENV_CREDENTIALS_<NAME>_PASSWORD
- JENKINSENV_CREDENTIALS_<NAME>_DESCRIPTION
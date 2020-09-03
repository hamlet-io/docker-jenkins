import jenkins.model.*;
import hudson.security.*;
import java.util.logging.Logger;

import org.jenkinsci.plugins.saml.*;

import org.jenkinsci.plugins.GithubSecurityRealm;
import net.sf.json.JSONObject;
import com.cloudbees.plugins.credentials.*;

import java.nio.ByteBuffer;
import com.amazonaws.util.Base64;
import com.amazonaws.services.kms.*;
import com.amazonaws.services.kms.model.*;

def env = System.getenv()
def jenkins = Jenkins.getInstance()

def String securityRealm = env.JENKINSENV_SECURITYREALM

def String jenkinsUser = env.JENKINSENV_USER
def String jenkinsPassword = env.JENKINSENV_PASS

def String githubClientId = env.GITHUBAUTH_CLIENTID
def String githubSecret = env.GITHUBAUTH_SECRET
def String githubAdmin =  env.GITHUBAUTH_ADMIN

def String samlMetadataXml = env.SAMLAUTH_META_XML
def String samlMetadataUrl = env.SAMLAUTH_META_URL
def String samlMetadataPeriod = env.SAMLAUTH_META_UPDATE
def String samlAttrDisplayName = env.SAMLAUTH_ATTR_DISPLAYNAME ?: SamlSecurityRealm.DEFAULT_DISPLAY_NAME_ATTRIBUTE_NAME
def String samlAttrUser = env.SAMLAUTH_ATTR_USERNAME
def String samlAttrGroup = env.SAMLAUTH_ATTR_GROUP ?: SamlSecurityRealm.DEFAULT_GROUPS_ATTRIBUTE_NAME
def String samlAttrEmail = env.SAMLAUTH_ATTR_EMAIL
def String samlLogoutUrl = env.SAMLAUTH_LOGOUT_URL
def String samlLifeTimeMax = env.SAMLAUTH_LIFETIME_MAX ?: SamlSecurityRealm.DEFAULT_MAXIMUM_AUTHENTICATION_LIFETIME
def String samlBinding = env.SAMLAUTH_BINDING
def String samlUserCase = env.SAMLAUTH_USERCASE ?: SamlSecurityRealm.DEFAULT_USERNAME_CASE_CONVERSION

if (jenkinsPassword) {
    Logger.global.info("Attempting Decryption of jenkinsPassword")
    jenkinsPassword = getKMSDecryptedString(env.JENKINSENV_PASS)
}

if (githubSecret) {
    Logger.global.info("Attempting Decryption of githubSecret")
    githubSecret = getKMSDecryptedString(env.GITHUBAUTH_SECRET)
}

if (!jenkinsPassword) {
    jenkinsPassword = env.JENKINSENV_PASS
}

if (!githubSecret) {
    githubSecret = env.GITHUBAUTH_SECRET
}

// Set Auth Strategy
if ( !(jenkins.getAuthorizationStrategy() instanceof ProjectMatrixAuthorizationStrategy) ) {
    jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy())
}

// -- Add Authentication Sources
switch ( securityRealm ) {

    // Inbuilt local Realm
    case "local":
        SecurityRealm local_realm = new HudsonPrivateSecurityRealm(false)
        if(!(jenkins.getSecurityRealm() instanceof HudsonPrivateSecurityRealm )) {
            jenkins.setSecurityRealm(local_realm)

            // Create a single local admin user
            def user = jenkins.getSecurityRealm().createAccount(jenkinsUser, jenkinsPassword)
            user.save()
        }

        jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, jenkinsUser)
        break

    // GitHub oAuth
    case "github":
        if(!binding.hasVariable('github_realm')) {
            github_realm = [:]
        }

        if(!(github_realm instanceof Map)) {
            throw new Exception('github_realm must be a Map.')
        }

        github_realm = github_realm as JSONObject

        String githubWebUri = github_realm.optString('web_uri', GithubSecurityRealm.DEFAULT_WEB_URI)
        String githubApiUri = github_realm.optString('api_uri', GithubSecurityRealm.DEFAULT_API_URI)
        String oauthScopes  = github_realm.optString('oauth_scopes', GithubSecurityRealm.DEFAULT_OAUTH_SCOPES)
        String clientID     = github_realm.optString('client_id', githubClientId)
        String clientSecret = github_realm.optString('client_secret', githubSecret)

        if(clientID && clientSecret) {
            SecurityRealm github_realm = new GithubSecurityRealm(
                githubWebUri,
                githubApiUri,
                clientID,
                clientSecret,
                oauthScopes)
            //check for equality, no need to modify the runtime if no settings changed
            if(!(jenkins.getSecurityRealm() instanceof GithubSecurityRealm)) {
                jenkins.setSecurityRealm(github_realm)
            }
        }
        jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, githubAdmin)
        break

    // SAML Authentication Realm
    case "saml":

        // https://github.com/jenkinsci/saml-plugin/blob/master/src/main/java/org/jenkinsci/plugins/saml/IdpMetadataConfiguration.java
       /**
        * @param xml Idp Metadata XML. if xml is null, url and period should not.
        * @param url Url to download the IdP Metadata.
        * @param period Period in minutes between updates of the IdP Metadata.
        */
        if ( samlMetadataXml ) {
            def samlIdpMetadata = new IdpMetadataConfiguration(
                new String( samlMetadataXml.decodeBase64() ),
                null,
                null
            )
        } else if ( samlMetadataUrl ) {
            def samlIdpMetadata = new IdpMetadataConfiguration(
                null,
                samlMetadataUrl,
                samlMetadataPeriod
            )
        } else {
            throw new Exception("ERROR: Could not determine SAML metadata endpoint", E);
        }


        // https://github.com/jenkinsci/saml-plugin/blob/master/src/main/java/org/jenkinsci/plugins/saml/SamlSecurityRealm.java
        /**
        * @param idpMetadata Identity provider Metadata.
        * @param displayNameAttributeName attribute that has the displayname.
        * @param groupsAttributeName attribute that has the groups.
        * @param maximumAuthenticationLifetime maximum time that an identification it is valid.
        * @param usernameAttributeName attribute that has the username.
        * @param emailAttributeName attribute that has the email.
        * @param logoutUrl optional URL to redirect on logout.
        * @param advancedConfiguration advanced configuration settings.
        * @param encryptionData encryption configuration settings.
        * @param usernameCaseConversion username case sensitive settings.
        * @param binding SAML binding method.
        */
        def saml_realm = new SamlSecurityRealm(
                samlIdpMetadata,
                samlAttrDisplayName,
                samlAttrGroup,
                samlLifeTimeMax.toInteger(),
                samlAttrUser,
                samlAttrEmail ?: null ,
                samlLogoutUrl ?: null,
                null,
                null,
                samlUserCase,
                samlBinding ?: null
        )

        if(!(jenkins.getSecurityRealm() instanceof SamlSecurityRealm)) {
            jenkins.setSecurityRealm(saml_realm)
        }

        break
}

jenkins.save()

private String getKMSDecryptedString( String encryptedString ) {

    // Support prefixed KMS secrets
    def env = System.getenv()
    def kms_prefix = env["KMS_PREFIX"] ?: 'kms+base64:'
    encryptedString = encryptedString.minus(kms_prefix)

    try {

        AWSKMS kmsClient = AWSKMSClientBuilder.defaultClient();

		ByteBuffer cipherText = ByteBuffer.wrap(Base64.decode(encryptedString));
        DecryptRequest decryptRequest = new DecryptRequest().withCiphertextBlob(cipherText);
        ByteBuffer plainText = kmsClient.decrypt(decryptRequest).getPlaintext();

        byte[] byteArray = new byte[plainText.remaining()];
		plainText.get(byteArray);
		return new String(byteArray);
    }
    catch (all) {
        Logger.global.info("Couldn't decrypt string - using as plaintext")
        Logger.global.info("$all.message")
    }
}

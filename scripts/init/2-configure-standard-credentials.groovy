import jenkins.model.*;

import hudson.util.Secret;
import com.cloudbees.plugins.credentials.*;
import com.cloudbees.plugins.credentials.common.*;
import com.cloudbees.plugins.credentials.domains.*;
import com.cloudbees.plugins.credentials.impl.*;

import org.jenkinsci.plugins.github_branch_source.GitHubAppCredentials ;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;

import java.util.logging.Logger;

import java.nio.ByteBuffer;
import com.amazonaws.util.Base64;
import com.amazonaws.services.kms.*;
import com.amazonaws.services.kms.model.*;

def jenkins = Jenkins.getInstance()

def env = System.getenv()
def credentials = env.findResults {  k, v -> k.contains("JENKINSENV_CREDENTIALS_") == true ? [ k.minus("JENKINSENV_CREDENTIALS_").split("_")[0],k.minus("JENKINSENV_CREDENTIALS_").split("_")[1],v ] : null }
credentials = credentials.groupBy( { credential -> credential[0] })

credentialObjects = []

domain = Domain.global()
store = jenkins.getExtensionList('com.cloudbees.plugins.credentials.SystemCredentialsProvider')[0].getStore()

currentCreds = store.getCredentials(Domain.global())

for ( String key in credentials.keySet() ) {

    def name = ""
    def description = ""
    def type = "userpw"

    // UserPW specifc credentials
    def username = ""
    def password = ""

    // Github App Credential
    def ghAppId = ""
    def ghPrivateKey = ""
    def ghOwner = ""

    // Secret Text
    def secret = ""

    properties = credentials.get(key)
    for ( property in properties ) {
        switch (property[1]) {
            case "NAME":
                name = property[2]
                break
            case "DESCRIPTION":
                description = property[2]
                break
            case "TYPE":
                type = property[2]
                break

            // userpw type
            case "USER":
                username = property[2]
                break
            case "PASSWORD":
                password = property[2]
                break

            // github app
            case "GHAPPID":
                ghAppId = property[2]
                break
            case "GHAPPKEY":
                ghPrivateKey = property[2]
                break
            case "GHAPPOWNER":
                ghOwner = property[2]
                break

            // secret string
            case "SECRET":
                secret = property[2]
                break
        }
    }

    Logger.global.info("Creating Credential: " + name + " Type: " + type )

    // Remove existing credentials
    currentCreds.find{ it.id == name }.each{
        store.removeCredentials(Domain.global(), it)
    }

    if (type == "userpw") {
        password = getKMSDecryptedString(password)

        usernameAndPassword = new UsernamePasswordCredentialsImpl(
            CredentialsScope.GLOBAL,
            name,
            description,
            username,
            password
        )

        store.addCredentials(domain, usernameAndPassword)
    }

    if ( type == "githubapp" ) {
        ghPrivateKey = getKMSDecryptedString(ghPrivateKey)

        githubAppCredentials = new GitHubAppCredentials(
            CredentialsScope.GLOBAL,
            name,
            description,
            ghAppId,
            Secret.fromString(ghPrivateKey)
        )

        store.addCredentials(domain, githubAppCredentials)

        if ( ghOwner ) {
            githubAppCredentials.setOwner(ghOwner)
        }
    }

    if ( type == "secret" ) {
        secret = getKMSDecryptedString(secret)

        secretText = new StringCredentialsImpl(
            CredentialsScope.GLOBAL,
            name,
            description,
            Secret.fromString(secret)
        )

        store.addCredentials(domain, secretText)
    }
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
        return new String(encryptedString)
    }
}

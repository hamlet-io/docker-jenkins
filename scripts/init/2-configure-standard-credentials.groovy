import jenkins.model.*
import com.cloudbees.plugins.credentials.*
import com.cloudbees.plugins.credentials.common.*
import com.cloudbees.plugins.credentials.domains.*
import com.cloudbees.plugins.credentials.impl.*

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

for ( String key in credentials.keySet() ) {

    def name = ""
    def username = ""
    def password = ""
    def description = ""

    properties = credentials.get(key)
    for ( property in properties ) { 
        switch (property[1]) { 
            case "NAME":
                name = property[2]
                break
            case "USER":
                username = property[2]
                break
            case "PASSWORD":
                password = property[2]
                break
            case "DESCRIPTION":
                description = property[2]
                break
        }
    }

    Logger.global.info("Creating Credential:" + name ) 

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

jenkins.save()

private String getKMSDecryptedString( String encryptedString ) {
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
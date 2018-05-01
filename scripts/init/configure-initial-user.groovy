import jenkins.model.*;
import hudson.security.*;
import java.util.logging.Logger;

import org.jenkinsci.plugins.GithubSecurityRealm;
import net.sf.json.JSONObject;
import com.cloudbees.plugins.credentials.*;

import com.amazonaws.util.Base64;
import com.amazonaws.services.kms.*;
import com.amazonaws.services.kms.model.*;

import java.nio.ByteBuffer;

def env = System.getenv()
def jenkins = Jenkins.getInstance()

def String securityRealm = env.JENKINSENV_SECURITYREALM
def String jenkinsUser = env.JENKINSENV_USER
def String jenkinsPassword = env.JENKINSENV_PASS
def String githubClientId = env.GITHUBAUTH_CLIENTID
def String githubSecret = env.GITHUBAUTH_SECRET
def String githubAdmin =  env.GITHUBAUTH_ADMIN

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
// Inbuilt local user
if(securityRealm == "local") {
    
    SecurityRealm local_realm = new HudsonPrivateSecurityRealm(false) 
    if(!(jenkins.getSecurityRealm() instanceof HudsonPrivateSecurityRealm )) {
        jenkins.setSecurityRealm(local_realm)

        // Create a single local admin user
        def user = jenkins.getSecurityRealm().createAccount(jenkinsUser, jenkinsPassword)
        user.save()
    }

    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, jenkinsUser)
    
}

// GitHub oAuth 
if(securityRealm == "github") {
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
        SecurityRealm github_realm = new GithubSecurityRealm(githubWebUri, githubApiUri, clientID, clientSecret, oauthScopes)
        //check for equality, no need to modify the runtime if no settings changed
        if(!(jenkins.getSecurityRealm() instanceof GithubSecurityRealm)) {
            jenkins.setSecurityRealm(github_realm)
        } 
    }
    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, githubAdmin)
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
    }
}
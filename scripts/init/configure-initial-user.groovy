import jenkins.model.*
import hudson.security.*
import org.jenkinsci.plugins.GithubSecurityRealm
import net.sf.json.JSONObject
import com.cloudbees.plugins.credentials.*

import com.amazonaws.util.Base64
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.ClasspathPropertiesFileCredentialsProvider;
import com.amazonaws.services.kms.AWSKMSClient;
import com.amazonaws.services.kms.model.DecryptRequest;

def env = System.getenv()
def jenkins = Jenkins.getInstance()
def jenkinsPassword = ""
def githubSecret = ""

if( env.SENSITIVE_JENKINS_PASS ) {
    
    String decryptedPass = getKMSDecryptedString(env.SENSITIVE_JENKINS_PASS)
    jenkinsPassword = decryptedPass

} else { 
    jenkinsPassword = env.JENKINS_PASS    
}

if( env.SENSITVE_GITHUB_SECRET ) { 
    
    String decryptedGithubSecret = getKMSDecryptedString(env.SENSITVE_GITHUB_SECRET)
    githubSecret = decryptedGithubSecret
    
}
else { 
    githubSecret = env.GITHUB_SECRET
}

Logger.global.info("Secret: " + githubSecret)
Logger.global.info("Password: " + decryptedPass)

// Set Auth Strategy 
if ( !(jenkins.getAuthorizationStrategy() instanceof ProjectMatrixAuthorizationStrategy) ) {
    jenkins.setAuthorizationStrategy(new ProjectMatrixAuthorizationStrategy())
}

// -- Add Authentication Sources
// Inbuilt local user
if(env.JENKINS_SECURITYREALM == "local") {
    
    SecurityRealm local_realm = new HudsonPrivateSecurityRealm(false) 
    if(!(jenkins.getSecurityRealm() instanceof HudsonPrivateSecurityRealm )) {
        jenkins.setSecurityRealm(local_realm)

        // Create a single local admin user
        def user = jenkins.getSecurityRealm().createAccount(env.JENKINS_USER, env.JENKINS_PASS)
        user.save()
    }

    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, env.JENKINS_USER)
    
}

// GitHub oAuth 
if(env.JENKINS_SECURITYREALM == "github") {
    if(!binding.hasVariable('github_realm')) {
        github_realm = [:]
    }

    if(!(github_realm instanceof Map)) {
        throw new Exception('github_realm must be a Map.')
    }

    github_realm = github_realm as JSONObject

    String githubWebUri = github_realm.optString('web_uri', GithubSecurityRealm.DEFAULT_WEB_URI)
    String githubApiUri = github_realm.optString('api_uri', GithubSecurityRealm.DEFAULT_API_URI)
    String oauthScopes = github_realm.optString('oauth_scopes', GithubSecurityRealm.DEFAULT_OAUTH_SCOPES)
    String clientID = github_realm.optString('client_id', env.GITHUB_CLIENTID)
    String clientSecret = github_realm.optString('client_secret', env.GITHUB_SECRET)

    if(clientID && clientSecret) {
        SecurityRealm github_realm = new GithubSecurityRealm(githubWebUri, githubApiUri, clientID, clientSecret, oauthScopes)
        //check for equality, no need to modify the runtime if no settings changed
        if(!(jenkins.getSecurityRealm() instanceof GithubSecurityRealm)) {
            jenkins.setSecurityRealm(github_realm)
        } 
        
    }
    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, env.GITHUB_ADMIN)
}

jenkins.save()

private String getKMSDecryptedString( String encryptedString ) {
    try {
		byte[] cipherText = Base64.getDecoder().decode(encryptedString);

		AWSKMS kmsClient = AWSKMSClientBuilder.defaultClient();

		// decrypt data
		DecryptRequest decryptRequest = new DecryptRequest()
				.withCiphertextBlob(cipherText);
		String plainText = kmsClient.decrypt(decryptRequest).getPlaintext().toString();

        return plainText
    }
    catch (com.amazonaws.AmazonServiceException e) { 
        Logger.global.severe({ e.message })
        Logger.global.severe("Couldn't decrypt string")
    }
}
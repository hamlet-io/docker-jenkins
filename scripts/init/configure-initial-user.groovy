import jenkins.model.*
import hudson.security.*
import org.jenkinsci.plugins.GithubSecurityRealm
import net.sf.json.JSONObject
import com.cloudbees.plugins.credentials.*

def env = System.getenv()
def jenkins = Jenkins.getInstance()

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
    String clientID = github_realm.optString('client_id', env.GITHUBAUTH_CLIENTID)
    String clientSecret = github_realm.optString('client_secret', env.GITHUBAUTH_SECRET)

    if(clientID && clientSecret) {
        SecurityRealm github_realm = new GithubSecurityRealm(githubWebUri, githubApiUri, clientID, clientSecret, oauthScopes)
        //check for equality, no need to modify the runtime if no settings changed
        if(!(jenkins.getSecurityRealm() instanceof GithubSecurityRealm)) {
            jenkins.setSecurityRealm(github_realm)
        } 
        
    }
    jenkins.getAuthorizationStrategy().add(Jenkins.ADMINISTER, env.GITHUBAUTH_ADMIN)
}

jenkins.save()
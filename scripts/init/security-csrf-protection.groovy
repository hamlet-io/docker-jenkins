/*
   Configures CSRF protection in global security settings.
 */

import jenkins.model.*
import hudson.security.csrf.DefaultCrumbIssuer

if(!Jenkins.instance.isQuietingDown()) {
    def jenkins = Jenkins.instance
    if(jenkins.getCrumbIssuer() == null) {
        jenkins.setCrumbIssuer(new DefaultCrumbIssuer(true))
        jenkins.save()
        println 'CSRF Protection configuration has changed.  Enabled CSRF Protection.'
    }
    else {
        println 'Nothing changed.  CSRF Protection already configured.'
    }
}
else {
    println "Shutdown mode enabled.  Configure CSRF protection SKIPPED."
}

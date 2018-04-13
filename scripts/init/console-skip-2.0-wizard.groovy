/*
   Skip the Jenkins 2.0 wizard.  We already configure Jenkins to our liking during
   bootstrap.
 */
import jenkins.model.*
import hudson.util.PluginServletFilter

def jenkins=Jenkins.instance

legacySetupWizard = ('getSetupWizard' in jenkins.metaClass.methods*.name)
newSetupWizard = (('getInstallState' in jenkins.metaClass.methods*.name) && ('isSetupComplete' in jenkins.installState.metaClass.methods*.name))


if((!newSetupWizard && legacySetupWizard) || (newSetupWizard && !jenkins.installState.isSetupComplete())) {
    def w=jenkins.setupWizard
    if(w != null) {
        try {
          //pre Jenkins 2.6
          w.completeSetup(j)
          PluginServletFilter.removeFilter(w.FORCE_SETUP_WIZARD_FILTER)
        }
        catch(Exception e) {
          w.completeSetup()
        }
        jenkins.save()
        println 'Jenkins 2.0 wizard skipped.'
    }
}

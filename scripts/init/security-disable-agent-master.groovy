// Disable Agenet Master Security

import jenkins.model.*
import hudson.security.*

def rule = Jenkins.instance.getExtensionList(jenkins.security.s2m.MasterKillSwitchConfiguration.class)[0].rule
if(!rule.getMasterKillSwitch()) {
    rule.setMasterKillSwitch(true)
    //dismiss the warning because we don't care (cobertura reporting is broken otherwise)
    Jenkins.instance.getExtensionList(jenkins.security.s2m.MasterKillSwitchWarning.class)[0].disable(true)
    Jenkins.instance.save()
    println 'Disabled agent -> master security for cobertura.'
}
else {
    println 'Nothing changed.  Agent -> master security already disabled.'
}
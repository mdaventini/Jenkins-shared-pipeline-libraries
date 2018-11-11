def call() {
	log.info("getBuildCause Datos de la ejecución ${currentBuild.rawBuild.getCauses()}")
	//La causa default de la ejecucion es Integracion continua IC
	buildCause = 'IC'
	if ( currentBuild.rawBuild.getCause(hudson.triggers.TimerTrigger.TimerTriggerCause) ) {
		buildCause = 'AC'
	}
	if ( currentBuild.rawBuild.getCause(jenkins.branch.BranchIndexingCause) ) {
		buildCause = 'SCAN'
	}
	currentBuild.description = "${(currentBuild.rawBuild.getCause(hudson.model.Cause).properties).shortDescription}"
	vReplaybuildCause = ''
	if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
		vReplaybuildCause = "${(currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause).getOriginal()).getDisplayName()}"
		//Si la anterior era RD va a dar error is out of date
		if ( vReplaybuildCause.split('#')[0] == 'RD' ) {
			log.error("No se puede ejecutar Replay de un Release a Demanda. Causa out of date en el release:prepare")
		} else {
			if ( vReplaybuildCause.split('#')[0] ) {
				buildCause = vReplaybuildCause.split('#')[0]
			} 
		}
		vReplaybuildCause = " (Replay ${vReplaybuildCause})"
	} else {					
		if ( (currentBuild.rawBuild.getCause(hudson.model.Cause.UserIdCause)) ) {
			//Si la causa es UserIdCause hudson.model.Cause.UserIdCause comienza con BD
			buildCause = 'BD'
		}
	}
	currentBuild.displayName = "${buildCause}#${BUILD_NUMBER}${vReplaybuildCause}"
	return buildCause
}

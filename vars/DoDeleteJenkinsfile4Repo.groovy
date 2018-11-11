def call(String vsvnUrl, String vincludedRegions, String vJob, Boolean debug = false) {
	log.info("DoDeleteJenkinsfile4Repo vsvnUrl:${vsvnUrl} vincludedRegions:${vincludedRegions} vJob:${vJob}")
	if ( vincludedRegions ) {
		vincludedRegions = vincludedRegions + "/"
	}
	datosTo = ""
	//Arma la lista con los branches
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		vSvnList = "svn list ${vsvnUrl}branches --username=${svnUsername} --password=${svnPassword} | awk '{print \"${vsvnUrl}branches/\" \$1}' > log.txt"
		log.debug("DoDeleteJenkinsfile4Repo.vSvnList ${vSvnList}", debug)
		status = sh returnStatus: true, script: vSvnList
	}
	log.debug("DoDeleteJenkinsfile4Repo.status ${status}", debug)
	if ( status == 0 ) {
		datosTo = readFile file: 'log.txt'
		sh "rm -Rf log.txt"
		log.debug("DoDeleteJenkinsfile4Repo.datosTo tiene ${datosTo}", debug)
	}
	datosTo = "${vsvnUrl}trunk/ \n" + datosTo
	datosTo.split("\\\n").each { vBranch ->
		vsvnUrlPath = vBranch.trim() + vincludedRegions
		log.debug("DoDeleteJenkinsfile4Repo revisa vsvnUrlPath:${vsvnUrlPath}", debug)
		//Elimina el file
		svnUtils.DoDeleteCommitSvn("${vsvnUrlPath}${vJob}", "Para eliminar automaticamente el Jenkinsfile del antiguo multibranchPipelineJob", debug)
	}
	log.info("DoDeleteJenkinsfile4Repo.end")
}
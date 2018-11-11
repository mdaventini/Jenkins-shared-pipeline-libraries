def call(String vsvnUrl, String vincludedRegions, String vJob, Boolean debug = false) {
	log.info("DoImportJenkinsfile2Repo vsvnUrl:${vsvnUrl} vincludedRegions:${vincludedRegions} vJob:${vJob}")
	if ( vincludedRegions ) {
		vincludedRegions = vincludedRegions + "/"
	}
	datosTo = ""
	//Arma la lista con trunk + branches
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		vSvnList = "svn list ${vsvnUrl}branches --username=${svnUsername} --password=${svnPassword} | awk '{print \"${vsvnUrl}branches/\" \$1}' > log.txt"
		//log.debug("DoImportJenkinsfile2Repo.vSvnList ${vSvnList}", debug)
		status = sh returnStatus: true, script: vSvnList
	}
	log.debug("DoImportJenkinsfile2Repo.status ${status}", debug)
	if ( status == 0 ) {
		datosTo = readFile file: 'log.txt'
		sh "rm -Rf log.txt"
		//log.debug("DoImportJenkinsfile2Repo.datosTo tiene ${datosTo}", debug)
	}
	datosTo = "${vsvnUrl}trunk/ \n" + datosTo
	datosTo.split("\\\n").each { vBranch ->
		vsvnUrlPath = vBranch.trim() + vincludedRegions
		log.debug("DoImportJenkinsfile2Repo revisa vsvnUrlPath:${vsvnUrlPath}", debug)
		//Si tiene pom.xml
		if ( svnUtils.getExiste("${vsvnUrlPath}pom.xml", debug) ) {
			//Si tiene pom.xml siempre agrega el trunk
			//Revisa que no sea anterior a ndias
			vLastCommit = svnUtils.getLastCommit(vsvnUrlPath, debug)
			//log.debug("DoImportJenkinsfile2Repo vsvnUrlPath:${vsvnUrlPath} Edad:${vLastCommit.edad} ndias:${EDAD.toInteger()}", debug)
			if ( (vLastCommit.edad > 0 && vLastCommit.edad <= EDAD.toInteger()) || vBranch.trim() == "${vsvnUrl}trunk/" ) {
				//Si no tuvo el archivo anteriormente!!!!!
				if ( !svnUtils.getBorrado(vsvnUrl, vsvnUrlPath.minus(vsvnUrl), vJob, debug) ) {
					log.info("DoImportJenkinsfile2Repo Crea el file ${vJob} para hacer copy en ${vsvnUrlPath}${vJob} Edad:${vLastCommit.edad}")
					writeFile file: vJob, text: "// ${vsvnUrlPath}${vJob} \nPipelineBuild()"
					svnUtils.DoImportCommitSvn(vJob, "${vsvnUrlPath}${vJob}", "Para detectar automaticamente en multibranchPipelineJob", debug)
					sh "rm -Rf ${vJob}"
				}
			}
		}
	}
	log.info("DoImportJenkinsfile2Repo.end")
}
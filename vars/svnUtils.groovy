def customCheckout(String vsvnUrl, String vincludedRegions, Boolean debug = false) {
	vremote = vsvnUrl + env.BRANCH_NAME+'/' + vincludedRegions
	log.debug("svnUtils.customCheckout vsvnUrl:${vsvnUrl} vincludedRegions:${vincludedRegions} vremote:${vremote}", debug)
	//As in https://issues.jenkins-ci.org/browse/JENKINS-40337
	try {
		checkout([
			$class: 'SubversionSCM', 
			additionalCredentials: [], 
			excludedCommitMessages: '', excludedRegions: '', excludedRevprop: '', excludedUsers: 'jenkins', 
			filterChangelog: false, ignoreDirPropChanges: false, includedRegions: vincludedRegions, 
			locations: [[credentialsId: 'svnjenkins', depthOption: 'infinity', ignoreExternalsOption: true, local: '.', remote: vremote]], 
			workspaceUpdater: [$class: 'UpdateUpdater']])
	} catch (err) {
		log.error("SubversionSCM ${vsvnUrl}${vincludedRegions}: ${err}")
    }
	log.debug("svnUtils.customCheckout.End", debug)
}

def getLastCommit(String vsvnUrl, Boolean debug = false) {
	log.debug("svnUtils.getLastCommit.vsvnUrl ${vsvnUrl}", debug)
	datosLastCommit = [fecha: 'Desconocido', edad: -1, revision: 0]
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		vSvnInfo = "svn info ${vsvnUrl} --username=${svnUsername} --password=${svnPassword} | grep -E 'Fecha|Rev' | grep 'cambio' | awk -F' ' '{print \$1\":\"\$5\" \"\$6}' > log.txt"
		log.debug("svnUtils.getLastCommit.vSvnInfo ${vSvnInfo}", debug)
		status = sh returnStatus: true, script: vSvnInfo
	}
	log.debug("svnUtils.getLastCommit.status ${status} svnUtils.getLastCommit.datosLastCommit ${datosLastCommit}", debug)
	if ( status == 0 ) {
		datosTo = readFile file: 'log.txt'
		sh "rm -Rf log.txt"
		log.debug("svnUtils.getLastCommit.datosTo tiene ${datosTo}", debug)
		//Revision del ultimo cambio: 1081
		//Fecha de ultimo cambio: 2018-03-15 14:46:25 -0300 (jue 15 de mar de 2018)
		if ( datosTo ) {
			if ( datosTo.contains("Fecha") ) {
				datosLastCommit.fecha = datosTo.split("Fecha:")[1].split("\\n")[0]
			}
			if ( datosTo.contains("Revi") ) {
				datosLastCommit.revision = datosTo.split("Revi")[1].split(":")[1].split("\\n")[0]
			}
			log.debug("svnUtils.getLastCommit.datosLastCommit ${datosLastCommit}", debug)
			if ( datosLastCommit.fecha ) {
				datosLastCommit.edad = (new Date()) - Date.parse("yyyy-MM-dd HH:mm:ss", datosLastCommit.fecha)
			}
		}
	} else {
		log.error("SubversionSCM Last Commit Desconocido")
	}
	log.debug("svnUtils.getLastCommit.datosLastCommit ${datosLastCommit}", debug)
	return datosLastCommit
}

def DoAddCommitSvn(String vDir, String vFile, String vMessage, Boolean nuevo, Boolean debug = false) {
	log.debug("svnUtils.DoAddCommitSvn vDir:${vDir} vFile:${vFile} vMessage:${vMessage}", debug)
	dir(vDir) {
		withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
			if ( nuevo ) {
				vSvnAdd = "svn add ${vFile} --username=${svnUsername} --password=${svnPassword} --force"
				log.debug("svnUtils.DoAddCommitSvn.vSvnAdd ${vSvnAdd}", debug)
				status = sh returnStatus: true, script: vSvnAdd
				if ( status != 0 ) {
					log.error("En svn add")
				}
			}
			vSvnCommit = "svn commit --username=${svnUsername} --password='${svnPassword}' --message='${vMessage}'"
			log.debug("svnUtils.DoAddCommitSvn.vSvnCommit ${vSvnCommit}", debug)
			status = sh returnStatus: true, script: vSvnCommit
			if ( status != 0 ) {
				log.error("En svn commit")
			}
		}
	}
	log.debug("svnUtils.DoAddCommitSvn.End", debug)
}

def getExiste(String vUrlPathFile, Boolean debug = false) {
	log.debug("svnUtils.getExiste vUrlPathFile:${vUrlPathFile}", debug)
	//Si svn info ... retorna error o no retorna el Nombre = no existe
	vExiste = false
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		vSvnInfo = "svn info ${vUrlPathFile} --username=${svnUsername} --password=${svnPassword} | grep 'Nombre' > log.txt"
		log.debug("svnUtils.getExiste.vSvnInfo ${vSvnInfo}", debug)
		status = sh returnStatus: true, script: vSvnInfo
	}
	log.debug("svnUtils.getExiste.status ${status}", debug)
	if ( status == 0 ) {
		datosTo = readFile file: 'log.txt'
		sh "rm -Rf log.txt"
		log.debug("svnUtils.getExiste.datosTo tiene ${datosTo}", debug)
		if ( datosTo.contains("Nombre") ) {
			vExiste = true
		}
	}
	log.debug("svnUtils.getExiste.vExiste ${vExiste}", debug)
	return vExiste
}

def DoImportCommitSvn(String vFile, String vUrlPath, String vMessage, Boolean debug = false) {
	log.debug("svnUtils.DoImportCommitSvn vFile:${vFile} vUrlPath:${vUrlPath} vMessage:${vMessage}", debug)
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		//svn import near.txt file:///var/svn/repos/test/far-away.txt -m "Remote import."
		vSvnImport = "svn import ${vFile} ${vUrlPath} --username=${svnUsername} --password='${svnPassword}' --message='${vMessage}'"
		log.debug("svnUtils.DoImportCommitSvn.vSvnImport ${vSvnImport}", debug)
		//output = sh returnStdout: true, script: vSvnImport
		status = sh returnStatus: true, script: vSvnImport
	}
	if ( status != 0 ) {
		log.error("En svnUtils.DoImportCommitSvn", debug)
	} 
}

def getBorrado(String vsvnUrl, String vPath, String vFile, Boolean debug = false) {
	log.debug("svnUtils.getBorrado vsvnUrl:${vsvnUrl} vPath:${vPath} vFile:${vFile}", debug)
	//Si svn log --verbose ... retorna error o no retorna nada = no fue borrado
	vBorrado = false
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		/branches/VersionesJenkins20-sinFileJenkins/VersionesJenkins20'
		vSvnLog = "svn log --verbose ${vsvnUrl} ${vPath} --username=${svnUsername} --password=${svnPassword} | grep ' D ' | grep '${vPath}${vFile}' > log.txt"
		log.debug("svnUtils.getBorrado.vSvnLog ${vSvnLog}", debug)
		status = sh returnStatus: true, script: vSvnLog
	}
	log.debug("svnUtils.getBorrado.status ${status}", debug)
	if ( status == 0 ) {
		datosTo = readFile file: 'log.txt'
		sh "rm -Rf log.txt"
		log.debug("svnUtils.getBorrado.datosTo tiene ${datosTo}", debug)
		if ( datosTo ) {
			vBorrado = true
		}
	}
	log.debug("svnUtils.getBorrado.vBorrado ${vBorrado}", debug)
	return vBorrado
}

def DoDeleteCommitSvn(String vUrlPath, String vMessage, Boolean debug = false) {
	log.debug("svnUtils.DoDeleteCommitSvn vUrlPath:${vUrlPath} vMessage:${vMessage}", debug)
	withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
		//Verifica que exista
		if ( getExiste(vUrlPath, debug) ) {
			//svn delete file:///var/svn/repos/test/far-away.txt -m "Remove file far-away.txt"
			vSvnDelete = "svn delete ${vUrlPath} --username=${svnUsername} --password='${svnPassword}' --message='${vMessage}'"
			log.debug("svnUtils.DoDeleteCommitSvn.vSvnDelete ${vSvnDelete}", debug)
			status = sh returnStatus: true, script: vSvnDelete
			if ( status != 0 ) {
				log.error("En svn delete ${vUrlPath}")
			}
		}
	}
	log.debug("svnUtils.DoDeleteCommitSvn.End", debug)
}

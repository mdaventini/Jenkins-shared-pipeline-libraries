def call() {
pipeline {
	agent none
	options {
		skipDefaultCheckout() 
		disableConcurrentBuilds()
		timeout(time: 90, unit: 'MINUTES')
		//No se puede aplicar buildDiscarder se deben eliminar los que no son BD o RD y que la fecha es menor a hoy -1 
		//buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30', daysToKeepStr: '7'))
	}
	triggers {
		pollSCM("${CRON_PIPELINE}")
		cron("${CRON_SONARQUBE}")
    }
	stages {
		stage('Comienzo') {
			agent { label 'master' }
			steps {
				script {
					log.info("Jenkinsfile para proyectos")
					cleanWs()
					buildCause = getBuildCause()
					//En caso que sea replay se habilita el debug (se muestran los log.debug, effective-pom y -X en release)
					debug = (currentBuild.displayName.contains("Replay"))
					maven_Debug = ""
					plugin_effe = ""
					if ( debug ) {
						maven_Debug = "-X"
						plugin_effe = "org.apache.maven.plugins:maven-help-plugin:2.2:effective-pom"
					}
					vPipelineProgress = ""
					log.info("Obtener configuracion del Proyecto")
					datosConf = getJobConfigs((JOB_NAME).minus("/"+JOB_BASE_NAME), debug)
					log.debug("datosConf.branchAC != env.BRANCH_NAME ${datosConf.branchAC.trim()} != ${env.BRANCH_NAME}", debug)
					if ( buildCause == "AC" && datosConf.branchAC.trim() != env.BRANCH_NAME ) {
						vPipelineProgress = "SKIPPED on Branch ${env.BRANCH_NAME}"
						buildCause = "SKIPPED"
					} else {
						currentBuild.description = "${currentBuild.description}\n Repositorio: ${datosConf.svnUrl + env.BRANCH_NAME+'/' + datosConf.includedRegions}"
						vLastCommit = svnUtils.getLastCommit("${datosConf.svnUrl + env.BRANCH_NAME+'/' + datosConf.includedRegions}", debug)
						currentBuild.description = "${currentBuild.description}\n LastRevision:${vLastCommit.revision}\n Last Commit:${vLastCommit.fecha}\n"
						int ndias = EDAD.toInteger()
						log.debug("Edad: ${vLastCommit.edad} ndias ${ndias}", debug)
						if ( vLastCommit.edad > ndias || vLastCommit.edad == -1 ) {
							if ( buildCause == "SCAN" || buildCause == "AC" ) {
								//Si es muy viejo y esta haciendo SCAN o AC le pone SKIPPED y no hace el checkout
								vPipelineProgress = "SKIPPED por antiguedad\n"
								buildCause = "SKIPPED"
							} else {
								//Si es muy viejo y hace otra cosa le pone el warning
								log.warning("Last Commit muy antiguo!!")
							}
						}
					}
				}
			}
		}
		stage('Obtener Proyecto') {
			agent { label 'java' }
			when { // Solo se ejecuta si no es SKIPPED
				beforeAgent true
				//buidCause ["SCAN","IC","AC","SKIPPED","BD","RD"]
				expression { buildCause != "SKIPPED" }
			}
			steps {
				script {
					log.info("Prepara Workspace")
					// Si existe el archivo release.properties en el workspace es porque el release o update-versions dio error, si se hace replay se debe limpiar
					if ( buildCause != 'IC' && buildCause != 'AC' ) {
						log.debug("Limpia el workspace", debug)
						cleanWs()
					}
					if ( datosConf.includedRegions ) {
						vPipelineProgress = vPipelineProgress + "Checkout con configuraciones adicionales ${datosConf.svnUrl + env.BRANCH_NAME+'/' + datosConf.includedRegions} \n Se recomienda crear un repositorio con estructura standard para proyectos de desarrollo \n"
					} else {
						vPipelineProgress = vPipelineProgress + "Checkout simple ${datosConf.svnUrl}${env.BRANCH_NAME} \n"
					}
					svnUtils.customCheckout(datosConf.svnUrl, datosConf.includedRegions, debug)
					if ( datosConf.maven ) {
						datosMvn = mvnUtils.getDatosMvn(datosConf, debug)
						currentBuild.description = "${currentBuild.description}\n ${datosConf.rootPOM} ${datosMvn.pom}"
					} else {
						//En caso que no sea maven da error, sale del pipeline
						log.error("No es maven! Este tipo de proyecto no está soportado por el orquestador. Verificar con el administrador")
					}
				}
			}
		}
		stage('Obtener Changes para SNAPSHOT y RELEASE') {
			agent { label 'java' }
			when {
				beforeAgent true
				//buidCause ["SCAN","IC","AC","SKIPPED","BD","RD"]
				expression { buildCause in ["BD","RD"] }
			}
			steps {
				script {
					datosLastToKeep = getRecExecution.LastToKeep((currentBuild.getRawBuild().getParent()), debug)
					if ( datosLastToKeep.RRevision == 0 ) {
						log.info("Crea lastChanges desde LAST_SUCCESSFUL_BUILD")
						lastChanges format: 'LINE', matchWordsThreshold: '0.25', matching: 'NONE', matchingMaxComparisons: '1000', showFiles: true, since: 'LAST_SUCCESSFUL_BUILD', specificBuild: '', specificRevision: '', synchronisedScroll: true, vcsDir: ''
					} else {
						log.info("Crea lastChanges desde specificRevision: *${datosLastToKeep.RRevision.toString()}*")
						lastChanges format: 'LINE', matchWordsThreshold: '0.25', matching: 'NONE', matchingMaxComparisons: '1000', showFiles: true, since: 'LAST_SUCCESSFUL_BUILD', specificBuild: '', specificRevision: datosLastToKeep.RRevision.toString(), synchronisedScroll: true, vcsDir: ''
					}
				}
			}
		}
		stage('Compilacion') {
			agent { label 'java' }
			when {
				beforeAgent true
				expression { buildCause in ["SCAN","IC","AC"] }
			}
			steps {
				script {
					currentBuild.description = "${currentBuild.description}\n IC:" 
					vPipelineProgress = "${vPipelineProgress} IC:${datosMvn.maven} ${datosMvn.jdk}\n  Compilacion:\n    Validacion:"
					mvnUtils.validate(datosMvn, "mvn ${maven_Debug} ${plugin_effe} validate", debug)
					vPipelineProgress = "${vPipelineProgress}OK\n    Compilacion sin Tests:"
					datosMvn.mapeo = mvnUtils.installNoTests(datosMvn, "mvn ${maven_Debug} clean install -Dmaven.test.skip=true", debug)
				}
			}
		}
		stage('Datos Compilacion') {
			agent { label 'master' }
			when {
				beforeAgent true
				expression { buildCause in ["SCAN","IC","AC"] && datosConf.mapeo != datosMvn.mapeo }
			}
			steps {
				script {
					//Se debe actualizar el .config
					NdatosConf = getJobConfigs((JOB_NAME).minus("/"+JOB_BASE_NAME), debug, false)
					NdatosConf.mapeo = datosMvn.mapeo
					setJobConfigs(NdatosConf, "Modificacion de mapeo", false, debug)
				}
			}
		}
		stage('Tests') {
			agent { label 'java' }
			when {
				beforeAgent true
				expression { buildCause in ["SCAN","IC","AC"] }
			}
			steps {
				script {
					vPipelineProgress = "${vPipelineProgress}OK\n  Tests:"
					mvnUtils.test(datosMvn, "mvn ${maven_Debug} test ${datosMvn.mvnTest}", debug)
					junit allowEmptyResults: true, testResults: '**/test-reports/*.xml'
				}
			}
		}	
		stage('Analisis de codigo preview') {
			agent { label 'java' }
			when {
				beforeAgent true
				expression { buildCause == "IC" }
			}
			steps {
				script {
					vPipelineProgress = "${vPipelineProgress}OK\n  Analisis de codigo preview:"
					vPipelineProgress = "${vPipelineProgress}\n    SonarQube Preview:"
					vPipelineProgress = "${vPipelineProgress} " + mvnUtils.sonarqubePreview(datosMvn, "mvn ${maven_Debug} ${datosMvn.sonarqubePlugin} -Dsonar.buildbreaker.skip=false -Dsonar.analysis.mode=preview -Dsonar.issuesReport.console.enable=true -Dsonar.issuesReport.lightModeOnly -Dsonar.issuesReport.html.enable=true", debug)
					publishHTML([allowMissing: false, alwaysLinkToLastBuild: true, keepAll: true, reportDir: 'target/sonar/issues-report/', reportFiles: 'issues-report-light.html', reportName: 'Preview SonarQube', reportTitles: "Preview SonarQube ${JOB_BASE_NAME}"])
				}
			}
		}
		stage('Analisis de codigo') {
			agent { label 'java' }
			//Solo se ejecuta si el datosConf.branchAC es == al branch que se está ejecutando
			when { // Solo se ejecuta si es AC
				beforeAgent true
				expression { buildCause == 'AC' }
			}
			steps {
				script {
					currentBuild.description = "${currentBuild.description}OK\n AC:"
					vPipelineProgress = "${vPipelineProgress}OK\n AC:\n  Analisis de codigo:\n    SonarQube:"
					withCredentials([
						usernamePassword(credentialsId: 'sonar', usernameVariable: 'login', passwordVariable: 'password'),
						usernamePassword(credentialsId: 'jdbc', usernameVariable: 'jdbc_username', passwordVariable: 'jdbc_password')]) {
						mvnUtils.sonarqube(datosMvn, "mvn ${maven_Debug} ${datosMvn.sonarqubePlugin} -Dsonar.login=${login} -Dsonar.password=${password} -Dsonar.jdbc.username=${jdbc_username} -Dsonar.jdbc.url='jdbc:jtds:sqlserver://SQLSERVER-URL:PORT/DBSonarQube;SelectMethod=Cursor'", debug)
					}
					vPipelineProgress = "${vPipelineProgress}OK\n"
					//Ejecuta el report owasp:dependency-check-maven
					//Este reporte es impracticable porque se usa toda la memoria y se genera una cola muy larga haciendo que se venzan los jobs por timeout
					//vPipelineProgress = "${vPipelineProgress}\n    Dependencies:"
					// From https://jeremylong.github.io/DependencyCheck/data/proxy.html
					//mvnUtils.dependencycheck(datosMvn, "mvn ${maven_Debug} org.owasp:dependency-check-maven:3.0.2:check -Dformat=XML", debug)
					//dependencyCheckPublisher canComputeNew: false, defaultEncoding: '', healthy: '', pattern: '', unHealthy: ''
					//vPipelineProgress = "${vPipelineProgress}OK\n"
				}
			}
		}
		stage('Selección de usuario: SNAPSHOT') {
			agent { label 'master' }
			when { // Solo se ejecuta si hay un usuario atendiendo
				beforeAgent true
				expression { buildCause == 'BD' }
			}
			steps {
				script { 
					vPipelineProgress = "${vPipelineProgress}OK\nBuild SNAPSHOT?:"
					currentBuild.description = "${currentBuild.description}OK\n BD SNAPSHOT:"
					//A partir de la version 2.5.3 de maven-release-plugin, se puede acualizar una version que no es SNAPSHOT https://issues.apache.org/jira/browse/MRELEASE-611
					timeout(time: 5, unit: 'MINUTES') {
						buildSnapshot = input message: "Indicar la versión SNAPSHOT para DESA", ok: 'Continuar', id: 'buildSnapshot', submitterParameter: 'submitter',
							parameters: [
								booleanParam(defaultValue: false, description: 'Saltear la generación de Snapshot', name: 'skipSnapshot'),
								booleanParam(defaultValue: true, description: '+ Deploy a DESA', name: 'deployDesa'),
								booleanParam(defaultValue: true, description: '+ Restart DESA (solo se ejecuta si se hace deploy a desa)', name: 'restartDesa'),
								string(defaultValue: "${datosMvn.majorV}", description: '“versión major”: DEBE ser incrementada si cualquier cambio no compatible con la versión anterior.', name: 'majorV'),
								string(defaultValue: "${datosMvn.minorV}", description: '“versión minor”: DEBE ser incrementada si se introduce nueva funcionalidad compatible con la versión anterior.', name: 'minorV'),
								string(defaultValue: "${datosMvn.bugfixV}", description: '“versión patch”: DEBE incrementarse cuando se introducen sólo arreglos compatibles con la versión anterior.', name: 'bugfixV'),
								string(defaultValue: "${datosMvn.preReleaseV}", description: '“pre-release”: OPCIONAL para representar una iteración interna', name: 'preReleaseV')
							]
					}
				}
			}
		}
		stage('SNAPSHOT') {
			agent { label 'java' }
			when { // Solo se ejecuta si hay un usuario atendiendo
				beforeAgent true
				expression { buildCause == 'BD' && !buildSnapshot['skipSnapshot'] }
			}	
			steps {
				script { 
					vPipelineProgress = "${vPipelineProgress}\n  SNAPSHOT\n    Iniciado por ${buildSnapshot['submitter']}"
					if ( buildSnapshot['preReleaseV'] == '' ) {
						datosMvn.nuevaVersion = buildSnapshot['majorV']+"."+buildSnapshot['minorV']+"."+buildSnapshot['bugfixV']+"-SNAPSHOT"
					} else {
						datosMvn.nuevaVersion = buildSnapshot['majorV']+"."+buildSnapshot['minorV']+"."+buildSnapshot['bugfixV']+"-"+buildSnapshot['preReleaseV']+"-SNAPSHOT"
					}
					if ( !buildSnapshot['skipSnapshot'] ) {
						if (datosMvn.version != datosMvn.nuevaVersion ) {
							datosMvn.commitMessage = "[maven-release-plugin] "+buildSnapshot['submitter']+" modifica snapshot de ${datosMvn.version} a ${datosMvn.nuevaVersion}"
							vPipelineProgress = "${vPipelineProgress}\n    ${datosMvn.commitMessage}"
							withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
								mvnUtils.updateversions(datosMvn, "mvn ${maven_Debug} org.apache.maven.plugins:maven-release-plugin:2.5.3:update-versions -DautoVersionSubmodules=true -DdevelopmentVersion=${datosMvn.nuevaVersion} scm:checkin -Dusername=${svnUsername} -Dpassword=${svnPassword} -Dmessage='${datosMvn.commitMessage}'", debug)
							}
							datosMvn.version = datosMvn.nuevaVersion
						}
						vPipelineProgress = "${vPipelineProgress}\n    Genera SNAPSHOT version ${datosMvn.version}"
						dataFromMaven = mvnUtils.deploySNAPSHOT(datosMvn, "mvn ${maven_Debug} deploy -Dmaven.test.skip=true -DaltDeploymentRepository=aplicaciones-snapshot-jenkins::default::https://NEXUS_SERVER:PORT/content/repositories/aplicaciones-snapshot-jenkins/", debug)
						vPipelineProgress = "${vPipelineProgress}:OK\n"
					}
				}
			}
		}
		stage('Deploy de SNAPSHOT en DESA') { 
			agent { label 'master' }
			when { // Solo se ejecuta si hay un usuario atendiendo y si no se hizo skip del snapshot y existen datos a hacer deploy
				beforeAgent true
				expression { buildCause == 'BD' && ! buildSnapshot['skipSnapshot'] && buildSnapshot['deployDesa'] && datosConf.toDeploy }
			}
			steps {
				script {
					mrestart = ""
					if ( buildSnapshot['restartDesa'] ) {
						mrestart = " y Restart"
					}
					if ( datosConf.desa != "" ) {
						vPipelineProgress = "${vPipelineProgress}    Deploy${mrestart} DESA aprobado por ${buildSnapshot['submitter']}:"
						log.debug("Deploy a DESA, Ambientes destino: ${datosConf.desa}, URLS: ${dataFromMaven}, Restart: ${buildSnapshot['restartDesa']}\nVa a ejecutar : ${datosConf.deployJob} con parametros: 'desa', ${dataFromMaven}, ${buildSnapshot['restartDesa']}", debug)
						build job : datosConf.deployJob, parameters: [
							string(name: 'Ambiente', value: 'desa'),
							string(name: 'Version', value: "${dataFromMaven}"),
							string(name: 'Restart', value: "${buildSnapshot['restartDesa']}")],
							propagate: false, wait: false
					} else {
						log.debug("No hace Deploy a DESA porque no tiene configurado el ambiente", debug)
						vPipelineProgress = "${vPipelineProgress}    Deploy: WARNING! No tiene configurado el ambiente desa datosConf.desa = ${datosConf.desa}"
					}
				}
			}
		}
		stage('Selección de usuario: RELEASE') {
			agent { label 'master' }
			when { // Solo se ejecuta si hay un usuario atendiendo
				beforeAgent true
				expression { buildCause == 'BD' }
			}
			steps{
				script {
					vPipelineProgress = "${vPipelineProgress}OK\nBuild RELEASE?:"
					currentBuild.description = "${currentBuild.description}OK\n RD RELEASE:"
					timeout(time: 5, unit: 'MINUTES') {
						createRelease = input message: '¿Genera Release?', ok: 'Ok!', id: 'Release', submitterParameter: 'submitter',
							parameters: [
								booleanParam(defaultValue: false, description: 'Saltear la generación de Release', name: 'skipRelease'),
								booleanParam(defaultValue: false, description: '+ Deploy a DESA', name: 'deployDesa'),
								booleanParam(defaultValue: false, description: '+ Restart DESA (solo se ejecuta si se hace deploy a desa)', name: 'restartDesa'),
								booleanParam(defaultValue: true, description: '+ Deploy a TEST', name: 'deployTest'),
								booleanParam(defaultValue: true, description: '+ Restart TEST (solo se ejecuta si se hace deploy a Test)', name: 'restartTest'),
								string(defaultValue: "${buildSnapshot['majorV']}", description: '“versión major”: DEBE ser incrementada si cualquier cambio no compatible con la versión anterior.', name: 'majorV'),
								string(defaultValue: "${buildSnapshot['minorV']}", description: '“versión minor”: DEBE ser incrementada si se introduce nueva funcionalidad compatible con la versión anterior.', name: 'minorV'),
								string(defaultValue: "${buildSnapshot['bugfixV']}", description: '“versión patch”: DEBE incrementarse cuando se introducen sólo arreglos compatibles con la versión anterior.', name: 'bugfixV'),
								string(defaultValue: "${buildSnapshot['preReleaseV']}", description: '“pre-release”: OPCIONAL para representar una iteración interna', name: 'preReleaseV')
							]
					}
				}
			}
		}
		stage('Release') {
			agent { label 'java' }
			when { // Solo se ejecuta si hay un usuario atendiendo
				beforeAgent true
				expression { buildCause == 'BD' && !createRelease['skipRelease'] }
			}
			steps{
				script {
					buildCause = 'RD'
					currentBuild.displayName = "${buildCause}#${BUILD_NUMBER}"
					if ( createRelease['preReleaseV'] == '' ) {
						datosMvn.nuevaVersion = createRelease['majorV']+"."+createRelease['minorV']+"."+createRelease['bugfixV']
					} else {
						datosMvn.nuevaVersion = createRelease['majorV']+"."+createRelease['minorV']+"."+createRelease['bugfixV']+"-"+createRelease['preReleaseV']
					}
					log.info("Se va a armar la version candidata ${datosMvn.nuevaVersion}")
					vPipelineProgress = "${vPipelineProgress}\n  Genera Release\n    Iniciado por ${createRelease['submitter']}"
					vPipelineProgress = "${vPipelineProgress}\n    Genera RELEASE version ${datosMvn.nuevaVersion}"
					withCredentials([usernamePassword(credentialsId: 'svnjenkins', usernameVariable: 'svnUsername', passwordVariable: 'svnPassword')]) {
						dataFromMaven = mvnUtils.release(datosMvn, "mvn ${maven_Debug} clean org.apache.maven.plugins:maven-release-plugin:2.5.3:prepare -DautoVersionSubmodules=true -DreleaseVersion=${datosMvn.nuevaVersion} -Dusername=${svnUsername} -Dpassword=${svnPassword} ", "mvn ${maven_Debug} -B org.apache.maven.plugins:maven-release-plugin:2.5.3:perform -Darguments='-Dmaven.javadoc.skip=true -DaltDeploymentRepository=aplicaciones-releases-jenkins::default::https://NEXUS_SERVER:PORT/content/repositories/aplicaciones-releases-jenkins/'", true)
					}
					vPipelineProgress = "${vPipelineProgress}:OK\n"
				}
			}
		}
		stage('Deploy de RELEASE') { 
			agent { label 'master' }
			when { // Solo se ejecuta si hay un usuario atendiendo y si no se hizo skip del Release y existen datos a hacer deploy
				beforeAgent true
				expression { buildCause == 'RD' && !createRelease['skipRelease'] && ( createRelease['deployDesa'] || createRelease['deployTest'] ) && datosConf.toDeploy  }
			}
			steps {
				script {
					if ( createRelease['deployTest'] ) {
						mrestart = ""
						if ( createRelease['restartTest'] ) {
							mrestart = " y Restart"
						}
						if ( datosConf.test != "" ) {
							vPipelineProgress = "${vPipelineProgress}\n    Deploy${mrestart} TEST aprobado por ${createRelease['submitter']}:"
							log.debug("Deploy de Release a TEST, Ambientes destino: ${datosConf.test}, URLS: ${dataFromMaven}, Restart: ${createRelease['restartTest']}\nVa a ejecutar : ${datosConf.deployJob} con parametros: 'test', ${dataFromMaven}, ${createRelease['restartTest']}", debug)
							build job : datosConf.deployJob, parameters: [
								string(name: 'Ambiente', value: 'test'),
								string(name: 'Version', value: "${dataFromMaven}"),
								string(name: 'Restart', value: "${createRelease['restartTest']}")],
								propagate: false, wait: false
						} else {
							log.debug("No hace Deploy a TEST porque no tiene configurado el ambiente", debug)
							vPipelineProgress = "${vPipelineProgress}\n    Deploy: WARNING! No tiene configurado el ambiente test datosConf.test = ${datosConf.test}"
						}
					}
					if ( createRelease['deployDesa'] ) {
						mrestart = ""
						if ( createRelease['restartDesa'] ) {
							mrestart = "+Restart"
						}
						if ( datosConf.desa != "" ) {
							vPipelineProgress = "${vPipelineProgress}\n    Deploy${mrestart} DESA aprobado por ${createRelease['submitter']}"
							log.debug("Deploy de Release a DESA, Ambientes destino: ${datosConf.desa}, URLS: ${dataFromMaven}, Restart: ${createRelease['restartDesa']}\nVa a ejecutar : ${datosConf.deployJob} con parametros: 'desa', ${dataFromMaven}, ${createRelease['restartDesa']}", debug)
							build job : datosConf.deployJob,  parameters: [
								string(name: 'Ambiente', value: 'desa'),
								string(name: 'Version', value: "${dataFromMaven}"),
								string(name: 'Restart', value: "${createRelease['restartDesa']}")],
								propagate: false, wait: false
						} else {
							log.debug("No hace Deploy a DESA porque no tiene configurado el ambiente", debug)
							vPipelineProgress = "${vPipelineProgress}\n    Deploy: WARNING! No tiene configurado el ambiente desa datosConf.desa = ${datosConf.desa}"
						}
					}
				}
			}
		}
	}
	post { 
		always { 
			script {
				vPipelineProgress = "${vPipelineProgress} ${currentBuild.currentResult}"
				currentBuild.description = "${currentBuild.description} ${currentBuild.currentResult}\n Pipeline:\n ${vPipelineProgress}"
				emailextSubject = "${currentBuild.displayName} ${JOB_NAME} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				log.info("${currentBuild.currentResult}")
			}
		}
		success {
			script {
				//En la primer ejecucion el anterior no existe
				if ( currentBuild.previousBuild ) {
					if ( currentBuild.previousBuild.result.toString().equals('FAILURE') && buildCause != 'SKIPPED' ) {
						log.info("Success! Avisa a DevelopersRecipientProvider, ADMINMAIL y mailLeader")
						//Si el anterior daba error, avisa que se arreglo a DevelopersRecipientProvider, ADMINMAIL y mailLeader
						emailext subject: emailextSubject, 
							body: emailextBody, 
							recipientProviders: [[$class: 'DevelopersRecipientProvider']], 
							to: "${ADMINMAIL} ${datosConf.mailLeader}"
					}
				}
			}
		}
		aborted {
			script {
				//Avisa aborted a RequesterRecipientProvider y ADMINMAIL
				log.info("Aborted! Avisa a RequesterRecipientProvider y ADMINMAIL")
				emailext subject: emailextSubject, 
					body: emailextBody, 
					recipientProviders: [[$class: 'RequesterRecipientProvider']], 
					to: "${ADMINMAIL}"
			}
		}
		failure {
			script {
				//Avisa error, pasa por aca si lo manda un message error o si hay error en algun stage anterior
				log.info("Failure! Avisa a RequesterRecipientProvider, ADMINMAIL y mailLeader")
				emailext subject: emailextSubject, 
					body: emailextBody, 
					recipientProviders: [[$class: 'RequesterRecipientProvider']], 
					to: "${ADMINMAIL} ${datosConf.mailLeader}"
			}
		}
	}
}

}
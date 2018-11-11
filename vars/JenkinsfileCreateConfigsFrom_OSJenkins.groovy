def call() {
pipeline {
	agent { label 'master' }
	options {
		timeout(time: 30, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para crear .configs de forma masiva desde /Appweb/jenkins/Extraer_BuildConfigs_OSJenkinsDESA.csv y /Appweb/jenkins/Extraer_deploys_OSJenkins*.csv")
					//Solo se activa debug si es REPLAY
					debug = false
					if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
						debug = true
					}
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					currentBuild.description = "${JOB_NAME}#${BUILD_NUMBER}"
					vCambios = ""
					vToAdd = ""
					log.debug("Limpia el workspace", debug)
					cleanWs()
				}
			}
		}
		stage('Prepara Workspace') {
			steps {
				script {
					//Copia /Appweb/jenkins/Extraer_BuildConfigs_OSJenkinsDESA.csv y /Appweb/jenkins/Extraer_deploys_OSJenkins*.csv
					status = sh returnStatus: true, script: "cp /Appweb/jenkins/Extraer_BuildConfigs_OSJenkinsDESA.csv /Appweb/jenkins/Extraer_deploys_OSJenkins*.csv ."
					if ( status != 0 ) {
						log.error("No se pudo copiar /Appweb/jenkins/Extraer_BuildConfigs_OSJenkinsDESA.csv /Appweb/jenkins/Extraer_deploys_OSJenkins*.csv")
					}
					archiveArtifacts artifacts: 'Extraer*.csv', onlyIfSuccessful: true
				}
			}
		}
		stage('Jobs Configs') {
			steps {
				script {
					//Lee Extraer_BuildConfigs_OSJenkinsDESA.csv
					log.debug("Lee Extraer_BuildConfigs_OSJenkinsDESA.csv", debug)
					configOSJenkinsDESA = readFile file: 'Extraer_BuildConfigs_OSJenkinsDESA.csv'
					configOSJenkinsDESA.split("\\\n").each { linea ->
						if ( linea.split(",")[0] == "Cambiar" ) { //Keys
							propertiesKeys = linea.split(",")
							log.debug("propertiesKeys:${propertiesKeys}", debug)
						} else { //Values
							//Este replace es para los data que estan vacios
							linea = linea.replaceAll(",",", ")
							propertiesValues = linea.split(",")
							log.debug("propertiesValues:${propertiesValues}", debug)
							datosConfig = [:]
							for (i = 1; i <propertiesKeys.size(); i++) {
								if ( propertiesKeys[i].trim() == "svnIncludes" ) {
									propertiesValues[i] = propertiesValues[i].trim().replaceAll(" ",", ")
								}
								datosConfig.put(propertiesKeys[i],propertiesValues[i].trim())
							}
							log.debug("datosConfig: ${datosConfig}", debug)
							vFileName = "${datosConfig.Job}.config"
							//Agrega los datos de Targets
							datosConfig = getTargetsOSJenkins.extraerOSJenkins(datosConfig, debug)
							//Buscar si existe el config
							log.debug("Busca ${vFileName}", debug)
							if ( fileExists ("../${JOB_NAME}@libs/pipeline-library/resources/configs/${vFileName}") ) {
								log.info("YA EXISTE ${vFileName}")
								OlddatosConf = getJobConfigs(datosConfig.Job, debug, false)
								if ( OlddatosConf.desa != datosConfig.desa || OlddatosConf.test != datosConfig.test || OlddatosConf.prepro != datosConfig.prepro || OlddatosConf.prod != datosConfig.prod ) {
									log.debug("No son iguales",debug)
									setJobConfigs(datosConfig, "Modificacion de targets ${currentBuild.displayName}", false, debug)
									currentBuild.description = "${currentBuild.description} \n Modificacion: ${vFileName}"
									vCambios = "CON CAMBIOS"
								}
							} else {
								vCambios = "CON CAMBIOS"
								setJobConfigs(datosConfig, "Nuevo config ${currentBuild.displayName}", true, debug)
								currentBuild.description = "${currentBuild.description} \n Nuevo: ${vFileName}"
							}
						}
					}
					archiveArtifacts allowEmptyArchive: true, artifacts: '*.config*', onlyIfSuccessful: true
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description} \n${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult} ${vCambios}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				emailext subject: emailextSubject, 
						body: emailextBody, 
						to: "${ADMINMAIL}"
			}
		}
		success {
			script {
				log.debug("Success", debug)
			}
		}
		aborted {
			script {
				log.debug("Aborted!", debug)
			}
		}
		failure {
			script {
				log.debug("Failure!", debug)
			}
		}
	}
}
}
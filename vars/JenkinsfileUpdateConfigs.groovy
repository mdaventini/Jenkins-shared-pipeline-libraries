def call(Boolean debug = false) {
pipeline {
	agent { label 'master' }
	options {
		timeout(time: 120, unit: 'MINUTES')
		buildDiscarder(logRotator(daysToKeepStr: '10'))
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para modificar/agregar .configs de forma masiva a partir de bulk/Configs.csv")
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					currentBuild.description = "${JOB_NAME}#${BUILD_NUMBER}"
					vCambios = ""
					log.debug("Limpia el workspace", debug)
					cleanWs()
				}
			}
		}
		stage('Jobs Configs') {
			steps {
				script {
					//Lee bulk/Configs.csv
					log.debug("Lee resource bulk/Configs.csv", debug)
					libResource = libraryResource "bulk/Configs.csv"
					writeFile file: 'Configs.csv', text: "${libResource}"
					archiveArtifacts artifacts: 'Configs.csv', onlyIfSuccessful: true
					libResource.split("\n").each { linea ->
						linea = linea.replace("\n","").replace("\r", "")
						if ( linea.split(",")[0] == "Cambiar" ) { //Keys
							propertiesKeys = linea.split(",")
							log.debug("propertiesKeys:${propertiesKeys}", debug)
						} else { //Values
							//Este replace es para los data que estan vacios
							linea = linea.replaceAll(",",", ")
							propertiesValues = linea.split(",")
							log.debug("propertiesValues:${propertiesValues}", debug)
							//Solamente crea .config para los que estan marcados para CAMBIAR o RENOMBRAR 
							if ( propertiesValues[0].trim() ) {
								vCambios = "CON CAMBIOS"
								datosConfig = [:]
								for (i = 1; i <propertiesKeys.size(); i++) {
									if ( propertiesKeys[i].trim() == "svnIncludes" ) {
										propertiesValues[i] = propertiesValues[i].trim().replaceAll(" ",", ")
									}
									datosConfig.put(propertiesKeys[i],propertiesValues[i].trim())
								}
								log.debug("datosConfig: ${datosConfig}", debug)
								//Si cambia el nombre del Job debe crear el nuevo .config
								if ( datosConfig.Job != datosConfig.NewJob && propertiesValues[0].trim() != "CAMBIAR" ) {
									//El nombre del job cambio o es uno nuevo, se debe crear un nuevo config
									datosConfig.OldJob = datosConfig.Job
									datosConfig.Job = datosConfig.NewJob
									vFileName = "${datosConfig.Job}.config"
									setJobConfigs(datosConfig, "Nuevo config x renombrar ${datosConfig.OldJob} a ${datosConfig.NewJob} ${currentBuild.displayName}", true, debug)
									//Programa update Jobs
									build job : 'GASCreateJobsfromConfigs', 
										parameters: [string(name: 'FromConfigs', value: "${vFileName}")], propagate: true, wait: true
									currentBuild.description = "${currentBuild.description}\n Nuevos Jobs para: ${datosConfig.Job}"
									//El nombre del job cambio, se debe modificar el config indicando datosConfig.svnIncludes = "disabled"
									if ( datosConfig.Job != datosConfig.OldJob && datosConfig.OldJob ) {
										datosConfig.Job = datosConfig.OldJob
										datosConfig.svnIncludes = "disabled"
										vFileName = "${datosConfig.Job}.config"
										setJobConfigs(datosConfig, "Modifica config x renombrar ${datosConfig.OldJob} a ${datosConfig.NewJob} ${currentBuild.displayName}", false, debug)
										//Programa update Jobs
										build job : 'GASCreateJobsfromConfigs', 
											parameters: [string(name: 'FromConfigs', value: "${vFileName}")], propagate: true, wait: true
										currentBuild.description = "${currentBuild.description}\n Modifica Jobs x cambio de nombre: ${datosConfig.Job}"
									}
								}
								if ( propertiesValues[0].trim() == "CAMBIAR" ) {
									vFileName = "${datosConfig.Job}.config"
									setJobConfigs(datosConfig, "Modifica config ${datosConfig.Job} ${currentBuild.displayName}", false, debug)
									//Programa update Jobs
									build job : 'GASCreateJobsfromConfigs', 
									parameters: [string(name: 'FromConfigs', value: "${vFileName}")], propagate: true, wait: true
									currentBuild.description = "${currentBuild.description}\n Modifica Jobs de: ${datosConfig.Job}"
								}
							}
						}
					}
					log.debug("currentBuild.currentResult antes de archiveArtifacts = ${currentBuild.currentResult}", debug)
					archiveArtifacts artifacts: '*.config', onlyIfSuccessful: true
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
				

def call() {
pipeline {
	agent { label 'master' }
	options {
		skipDefaultCheckout() 
		disableConcurrentBuilds()
		timeout(time: 30, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '30', artifactNumToKeepStr: '30', daysToKeepStr: '7'))
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para restart")
					//Solo se activa debug si es REPLAY
					debug = false
					//La causa default de la ejecucion es MANUAL
					buildCause = "MANUAL"
					vReplaybuildCause = ""
					//Evalua buildCause
					if ( (currentBuild.rawBuild.getCause(hudson.model.Cause.UpstreamCause)) ) {
						//Si la causa es UpstreamCause hudson.model.Cause.UpstreamCause recupera los datos del que lo llama
						buildCause = "CALL"
					} else {
						if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
							vReplaybuildCause = " (Replay ${((currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause).getOriginal()).getDisplayName())})"
							buildCause = vReplaybuildCause.split("#")[1].split("#")[0]
							debug = true
						}
						//Si la causa es MANUAL debe ingresar el ticket
						if ( !params.Ticket && params.Ambiente in ["prepro","prod"]) {
							log.error("Debe indicar el ticket que ocasiona el restart en ${params.Ambiente}")
						}
					}
					currentBuild.description = "${(currentBuild.rawBuild.getCause(hudson.model.Cause).properties).shortDescription}"
					currentBuild.displayName = "${JOB_NAME} - ${params.Ambiente.toUpperCase()}#${buildCause}#${BUILD_NUMBER} ${vReplaybuildCause}"
					log.debug("Param data : ", debug)
					log.debug("   params.Ambiente = ${params.Ambiente}", debug)
					log.info("Obtener configuracion del Proyecto")
					//Este script contenmpla [GASJOB_NAME]-restart y GAS-[GASJOB_NAME]-restart
					GASJOB_NAME = (JOB_NAME).minus("GAS-").minus("-restart")
					datosConf = getJobConfigs(GASJOB_NAME, debug)
					//Si el Ambiente no existe en las configuraciones
					if ( ! datosConf[params.Ambiente] ) {
						//Si no existe en las configuraciones
						log.error("No existe el destino ${params.Ambiente} en el archivo de configuraciones ${GASJOB_NAME}.config \n Verificar con el administrador")
					}
					log.debug("Lee resource scripts/RestartInstance.sh", debug)
					lr = libraryResource "scripts/RestartInstance.sh"
					writeFile file: 'RestartInstance.sh', text: "${lr}" 
					sh " chmod +x *.sh" 
				}
			}
		}
		stage('Restart') {
			steps {
				script {
					currentBuild.description = "${currentBuild.description}\n Ambiente:${params.Ambiente}\n Ticket:${params.Ticket}"
					pipelineRestart = ""
					emailextSubject = "${currentBuild.displayName}"
					emailextBody = "Check progress output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
					emailext subject: "Inicio de Restart" + emailextSubject, 
						body: emailextBody, 
						to: "${ADMINMAIL} ${datosConf.mailLeader} ${datosConf.mailTest} ${datosConf.mailDesa}"
					splitAmbiente = "${datosConf[params.Ambiente]}".split("\\|")
					log.debug("Datos ${splitAmbiente}", debug)
					vInstanciasEjecutadas = ""
					splitAmbiente.each { datosAmbiente ->
						datosServer = getServerMap (datosAmbiente, debug)
						if ( !vInstanciasEjecutadas.contains(datosServer.instance) ) {
							vInstanciasEjecutadas = vInstanciasEjecutadas + datosServer.instance
							log.info("Se va a ejecutar RestartInstance.sh ${datosServer.instance} en el servidor ${datosServer.host}")
							pipelineRestart = "${pipelineRestart}\n${datosServer.host}:${datosServer.instance}"
							status = sh returnStatus: true, script: "ssh ${datosServer.host} 'bash -s' < RestartInstance.sh ${datosServer.instance}"
							if ( status != 0 ) {
								log.error("Error al reiniciar ${datosServer.host}:${datosServer.instance}")
							}
						}
					}
				}
			}
		}
		stage ('Ejecución de Tests Funcionales e integrales') {
			when { // Solo se ejecuta si el destino es desa o test
				expression { params.Ambiente == "desa" || params.Ambiente == "test" }
			}
			steps {
				script {
					parallel (
						"Tests de Integracion" : {
							log.info("Tests de Integracion")
						},
						"Tests Funcionales" : {
							log.info("Tests Funcionales")
						}
					)
				}
			}
		}
	}
	post { 
		always { 
			script {
				log.info("Fin del pipeline de restart!")
				currentBuild.description = "${currentBuild.description}\n${pipelineRestart}\n${currentBuild.currentResult}"
				vFile = "${datosConf.Job}-${params.Ambiente}-${currentBuild.currentResult}"
				writeFile file: vFile, text: pipelineRestart
				archiveArtifacts artifacts: vFile, onlyIfSuccessful: false
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				emailext subject: "Fin de Restart" + emailextSubject, 
					body: emailextBody, 
					to: "${ADMINMAIL} ${datosConf.mailLeader} ${datosConf.mailTest} ${datosConf.mailDesa}"
			}
		}
		success {
			script {
				log.debug("success", debug)
			}
		}
		aborted {
			script {
				log.info("Aborted!")
			}
		}
		failure {
			script {
				log.info("Failure! Avisa a admin")
			}
		}
	}
}
}
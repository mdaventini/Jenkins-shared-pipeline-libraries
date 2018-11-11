def call() {
pipeline {
	agent { label 'master' }
	options {
		skipDefaultCheckout() 
		disableConcurrentBuilds()
		timeout(time: 30, unit: 'MINUTES')
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para deploy")
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
					}
					log.debug("Param data : ", debug)
					log.debug("   params.Ambiente = ${params.Ambiente}", debug)
					log.debug("   params.Version = ${params.Version}", debug)
					log.debug("   params.Restart = ${params.Restart}", debug)
					//Este script contenmpla [GASJOB_NAME]-deploy y GAS-[GASJOB_NAME]-deploy
					GASJOB_NAME = (JOB_NAME).minus("GAS-").minus("-deploy")
					datosConf = getJobConfigs(GASJOB_NAME, debug)
					//Si el Ambiente no existe en las configuraciones
					if ( ! datosConf[params.Ambiente] ) {
						//Si no existe en las configuraciones
						log.error("No existe el destino ${params.Ambiente} en el archivo de configuraciones ${GASJOB_NAME}.config \n Verificar con el administrador")
					}
					if ( buildCause == "MANUAL" ) {
						//Si la causa es manual hay que convertir params.Version
						log.debug("params.Version = ${params.Version}", debug)
						log.debug("params.Version.split(':')[0] = ${params.Version.split(':')[0]}", debug)
						log.debug("params.Version.split(':')[1] = ${params.Version.split(':')[1]}", debug)
						log.debug("NEXUS_SERVER = ${NEXUS_SERVER}", debug)
						if ( params.Version.split(':')[0].contains("SNAPSHOT") ) {
							vRepo = "aplicaciones-snapshot-jenkins"
						} else {
							vRepo = "aplicaciones-releases-jenkins"
						}
						deployURLS = "${NEXUS_SERVER}/service/local/artifact/maven/content?r=${vRepo}&g=ar.com.company&a=${GASJOB_NAME}&p=txt&c=${params.Version.split(':')[1]}&v=${params.Version.split(':')[0]}"
						//Si la causa es MANUAL debe ingresar el ticket
						if ( !params.Ticket && params.Ambiente in ["prepro","prod"]) {
							log.error("Debe indicar el ticket que ocasiona el deploy en ${params.Ambiente}")
						}
					} else {
						deployURLS = params.Version.trim()
					}
					currentBuild.description = "${(currentBuild.rawBuild.getCause(hudson.model.Cause).properties).shortDescription}"
					currentBuild.displayName = "${JOB_NAME} - ${params.Ambiente.toUpperCase()}#${buildCause}#${BUILD_NUMBER} ${vReplaybuildCause}"
					cleanWs()
					log.debug("deployURLS = ${deployURLS}", debug)
					output = sh returnStdout: true, script: "wget -q --no-check-certificate '${deployURLS}' -O artefactos"
					if ( output ) {
						log.error("No se pudo obtener el archivo ${deployURLS}")
					}
					archiveArtifacts artifacts: 'artefactos', allowEmptyArchive: true
				}
			}
		}
		stage('Deploy') {
			steps {
				script {
					log.debug("Stage Deploy", debug)
					currentBuild.description = "${currentBuild.description}\n Ambiente:${params.Ambiente}\n Version:${deployURLS}\n Restart:${params.Restart}\n Ticket:${params.Ticket}"
					pipelineDeploy = ""
					//Para SNAPSHOT Verifica que no se haga deploy a ambiente <> desa o test
					if ( !(params.Ambiente in ["desa","test"] ) && deployURLS.contains("SNAPSHOT") ) {
						log.error("No se puede hacer deploy de SNAPSHOT en ${params.Ambiente}")
					}
					emailextSubject = "${currentBuild.displayName}"
					emailextBody = "Check progress output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
					emailext subject: "Inicio de Deploy" + emailextSubject, 
						body: emailextBody, 
						to: "${ADMINMAIL} ${datosConf.mailLeader} ${datosConf.mailTest} ${datosConf.mailDesa}"
					dataURLS = readFile file: 'artefactos'
					dataURLS.split("\\\n").each{ vUrl ->
						log.debug("vUrl ${vUrl}", debug)
						OrigenNexus = vUrl.split("=")[1]
						Artefacto = vUrl.split("=")[0]
						log.debug("OrigenNexus ${OrigenNexus} Artefacto ${Artefacto}", debug)
						vScript = "wget -q --no-check-certificate '${OrigenNexus}' -O ${Artefacto}"
						log.debug("Va a ejecutar: ${vScript}", debug)
						vwget = sh returnStdout: true, script: vScript
						if ( vwget ) {
							log.error("No se pudo obtener el archivo ${datosArtifact.artefacto} de ${datosArtifact.URL}")
						}
						datosConf[params.Ambiente].split("\\|").each{ vAmbiente ->
							datosServer = getServerMap(vAmbiente, debug)
							log.debug("datosServer ${datosServer}", debug)
							if ( datosServer.artefacto == Artefacto ) {
								pipelineDeploy = "${pipelineDeploy}\n${OrigenNexus} ${vAmbiente}"
								log.debug("pipelineDeploy ${pipelineDeploy}", debug)
								timestamp = new Date().format("yyyyMMdd_HHmmss")
								log.debug("timestamp ${timestamp}", debug)
								log.debug("backup = ssh ${datosServer.host} 'cp ${datosServer.folder}${datosServer.artefacto} ${datosServer.folder}../backup/${datosServer.artefacto}_${timestamp}'", debug)
								backup = sh returnStatus: true, script: "ssh ${datosServer.host} 'cp ${datosServer.folder}${datosServer.artefacto} ${datosServer.folder}../backup/${datosServer.artefacto}_${timestamp}'"
								if ( backup != 0 ) {
									log.warning("No se pudo hacer backup")
								}
								log.debug("deploy = scp ${datosServer.artefacto} ${datosServer.host}:${datosServer.folder}${datosServer.artefacto}", debug)
								deploy = sh returnStatus: true, script: "scp ${datosServer.artefacto} ${datosServer.host}:${datosServer.folder}${datosServer.artefacto}"
								if ( deploy != 0 ) {
									log.error("No se pudo hacer deploy ${OrigenNexus} ${vAmbiente}")
								}
							}
						}
					}
				}
			}
		}
		stage('Restart') {
			when { //Solo se ejecuta si se solicito params.Restart
				expression { params.Restart }
			}
			steps {
				script {
					log.info("Programa: ${datosConf.restartJob} con parametros: ${datosConf[params.Ambiente]}")
					build job : datosConf.restartJob, parameters: [
						string(name: 'Ambiente', value: "${params.Ambiente}")],
						propagate: false, wait: false
				}
			}
		}
		stage ('Ejecución de Tests Funcionales e integrales') {
			when { // Solo se ejecuta si el destino es desa o test y no se solicito restart - porque restart ya hace el test funcional e integral
				expression { (params.Ambiente == "desa" || params.Ambiente == "test") && params.Restart == 'false' }
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
				log.info("Fin del pipeline de deploy!")
				currentBuild.description = "${currentBuild.description} ${pipelineDeploy}\n${currentBuild.currentResult}"
				vFile = "${datosConf.Job}-${params.Ambiente}-${currentBuild.currentResult}"
				writeFile file: vFile, text: pipelineDeploy
				archiveArtifacts artifacts: vFile, onlyIfSuccessful: false
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				emailext subject: "Fin de Deploy" + emailextSubject, 
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
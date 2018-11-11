def call() {
pipeline {
	agent { label 'master' }
	options {
		disableConcurrentBuilds()
		timeout(time: 15, unit: 'MINUTES')
		buildDiscarder logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '')
		durabilityHint('PERFORMANCE_OPTIMIZED')
	}
	triggers {
		cron("${CRON_REPORTES}")
    }
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline en ReporteGAS para crear Reporte de Restarts de desarrollonew")
					//Solo se activa debug si es REPLAY
					debug = false
					if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
						debug = true
					}
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					currentBuild.description = "${JOB_NAME}#${BUILD_NUMBER}"
					warnings = ""
				}
			}
		}
		stage('Prepara Workspace') {
			steps {
				script {
					log.debug("Limpia el workspace", debug)
					cleanWs()
				}
			}
		}
		stage('Jobs') {
			steps {
				script {
					warnings = ""
					header = ['FechaReporte','JobRestart','NServidor','NInstancia','Environment','UrlJobRestart','FechaRestart','TKTRestart','StatusRestart','UsuarioRestart']
					//Crea el file Extraer_restarts_desarrollonew.csv
					writeFile file: 'Extraer_restarts_desarrollonew.csv', text: (header.join(','))
					File vFile = new File(WORKSPACE, 'Extraer_restarts_desarrollonew.csv') 
					vFile << "\n"
					Jenkins.getActiveInstance().getAllItems().each { vRestart ->
						if ( vRestart instanceof org.jenkinsci.plugins.workflow.job.WorkflowJob && !vRestart.isDisabled() && vRestart.getDisplayName().contains("-restart") ) {
							LEnvironments = ["desa","test"]
							if ( vRestart.getDisplayName().contains("GAS-") ) {
								LEnvironments = ["prepro","prod"]
							}
							datos = [:]
							datos.FechaReporte = new Date().format("dd/MM/YYYY HH:mm:ss")
							datos.JobRestart = vRestart.getDisplayName()
							datos.JobBuild = datos.JobRestart.minus("-restart").minus("GAS-")
							log.info("JobRestart ${datos.JobRestart}")
							datosConf = getJobConfigs(datos.JobBuild, debug, false)
							if ( !vRestart.getBuilds() ) {
								log.debug("JobRestart ${datos.JobRestart} sin builds" ,debug)
								datos.UrlJobRestart = vRestart.getAbsoluteUrl()
								LEnvironments.each{ vEnvironment ->
									if ( !datosConf[vEnvironment] ) {
										datos.Environment = vEnvironment
										DoWriteReportLineFromMap(datos, "", header, vFile, debug)
										warnings = "${warnings}\n El ambiente ${vEnvironment} no esta definido en el archivo ${datos.JobBuild}.config"
									} else {
										datos.AInstancias = []
										datosConf[vEnvironment].split("\\|").each{ IRestart ->
											if ( IRestart.contains(":") ) {
												if ( !datos.AInstancias.contains(IRestart) ) {
													datos.AInstancias.add(IRestart)
													datos.NServidor = IRestart.split(":")[0]
													datos.NInstancia = IRestart.split(":")[1].split("/deploy")[0]
													datos.Environment = vEnvironment
													DoWriteReportLineFromMap(datos, "", header, vFile, debug)
												}
											} else {
												warnings = "${warnings}\n El dato ${IRestart} de ${vEnvironment} no esta correctamente definido en el archivo ${datos.JobBuild}.config"
											}
										}
									}
								}
							}
							datos.EnvironmentOK = []
							vRestart.getBuilds().find{ vEjec ->
								//Cuando tiene todos deja de buscar
								if ( datos.EnvironmentOK.size() == LEnvironments.size() ) {
									log.debug("JobRestart ${datos.JobRestart} deja de buscar ${datos.EnvironmentOK}" ,debug)
									return true
								}
								vEnvironment = vEjec.getAction(hudson.model.ParametersAction).getParameter("Ambiente").getValue()
								if ( !datos.EnvironmentOK.contains(vEnvironment) ) {
									datos.EnvironmentOK.add(vEnvironment)
									datos.Environment = vEnvironment
									datos.UrlJobRestart = vEjec.getAbsoluteUrl()
									datos.FechaRestart = vEjec.getTime().format("dd/MM/YYYY HH:mm:ss")
									if ( vEjec.getAction(hudson.model.ParametersAction).getParameter("Ticket") ) { 
										datos.TKTRestart = vEjec.getAction(hudson.model.ParametersAction).getParameter("Ticket").getValue().replaceAll(","," ")
									}
									datos.StatusRestart = vEjec.getResult()
									vCause = (vEjec.getCause(hudson.model.Cause).properties)
									datos.UsuarioRestart = vCause.shortDescription
									if ( vEjec.getCause(hudson.model.Cause.UserIdCause) ) {
										datos.UsuarioRestart = datos.UsuarioRestart + "(${(vEjec.getCause(hudson.model.Cause.UserIdCause).properties).userId})"
									}
									vEjec.getArtifacts().each{ vFileArtifact ->
										if ( vFileArtifact.getFile().toString().split("/").last().contains(datos.JobBuild) ) {
											dataFromTxtArtifact = readFile file: vFileArtifact.getFile().toString()
											dataFromTxtArtifact.split("\\\n").each { vPipelineRestart ->
												if ( vPipelineRestart ) {
													log.debug("vPipelineRestart ${vPipelineRestart}" ,debug)
													datos.INST = vPipelineRestart
													datos.NServidor = datos.INST.split(":")[0]
													datos.NInstancia = datos.INST.split(":")[1]
												}
											}
										}
									}
									//Aunque no tenga artefactos lo informa igual falta DServidor y DInstancia
									DoWriteReportLineFromMap(datos, "", header, vFile, debug)
								}
								return false
							}
						}
					}
					archiveArtifacts artifacts: 'Extraer_restarts_desarrollonew.csv', onlyIfSuccessful: true
					status = sh returnStatus: true, script: "cp Extraer_restarts_desarrollonew.csv /Appweb/jenkins/."
					if ( status != 0 ) {
						log.error("No se pudo copiar Extraer_restarts_desarrollonew.csv a /Appweb/jenkins/.")
					}
					build job: 'Reporte_Siotecno_restarts', propagate: false, wait: false
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description}\n ${warnings}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}\n Ver adjunto ${BUILD_URL}artifact/Extraer_restarts_desarrollonew.csv/*view*/"
				emailext subject: emailextSubject, 
						body: emailextBody, 
						to: "${ADMINMAIL}"
				log.info(currentBuild.currentResult)
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

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
					log.info("Pipeline en ReporteGAS para crear Reporte de Deploys de desarrollonew")
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
					header = ['FechaReporte','JobDeploy','NServidor','NInstancia','Environment','UrlJobDeploy','FechaDeploy','TKTDeploy','StatusDeploy','UsuarioDeploy','Target','Source','OrigenNexus','Artefacto','Version']
					//Crea el file Extraer_deploys_desarrollonew.csv
					writeFile file: 'Extraer_deploys_desarrollonew.csv', text: (header.join(','))
					File vFile = new File(WORKSPACE, 'Extraer_deploys_desarrollonew.csv') 
					vFile << "\n"
					Jenkins.getActiveInstance().getAllItems().each { vDeploy ->
						if ( vDeploy instanceof org.jenkinsci.plugins.workflow.job.WorkflowJob && !vDeploy.isDisabled() && vDeploy.getDisplayName().contains("-deploy") ) {
							LEnvironments = ["desa","test"]
							if ( vDeploy.getDisplayName().contains("GAS-") ) {
								LEnvironments = ["prepro","prod"]
							}
							datos = [:]
							datos.FechaReporte = new Date().format("dd/MM/YYYY HH:mm:ss")
							datos.JobDeploy = vDeploy.getDisplayName()
							datos.JobBuild = datos.JobDeploy.minus("-deploy").minus("GAS-")
							log.info("JobDeploy ${datos.JobDeploy}")
							datosConf = getJobConfigs(datos.JobBuild, debug, false)
							if ( !vDeploy.getBuilds() ) {
								log.debug("JobDeploy ${datos.JobDeploy} sin builds" ,debug)
								LEnvironments.each{ vEnvironment ->
									if ( !datosConf[vEnvironment] ) {
										datos.Environment = vEnvironment
										DoWriteReportLineFromMap(datos, "", header, vFile, debug)
										warnings = "${warnings}\n El ambiente ${vEnvironment} no esta definido en el archivo ${datos.JobBuild}.config"
									} else {
										datosConf[vEnvironment].split("\\|").each{ IDeploy ->
											if ( IDeploy.contains(":") ) {
												datos.NServidor = IDeploy.split(":")[0]
												datos.Target = IDeploy
												datos.NInstancia = IDeploy.split("/")[-3]
												datos.Environment = vEnvironment
												DoWriteReportLineFromMap(datos, "", header, vFile, debug)
											} else {
												warnings = "${warnings}\n El dato ${IDeploy} de ${vEnvironment} no esta correctamente definido en el archivo ${datos.JobBuild}.config"
											}
										}
									}
								}
							}
							datos.EnvironmentOK = []
							vDeploy.getBuilds().find{ vEjec ->
								//Cuando tiene todos deja de buscar
								if ( datos.EnvironmentOK.size() == LEnvironments.size() ) {
									log.debug("JobDeploy ${datos.JobDeploy} deja de buscar ${datos.EnvironmentOK}" ,debug)
									return true
								}
								vEnvironment = vEjec.getAction(hudson.model.ParametersAction).getParameter("Ambiente").getValue()
								if ( !datos.EnvironmentOK.contains(vEnvironment) ) {
									datos.EnvironmentOK.add(vEnvironment)
									datos.Environment = vEnvironment
									datos.UrlJobDeploy = vEjec.getAbsoluteUrl()
									datos.FechaDeploy = vEjec.getTime().format("dd/MM/YYYY HH:mm:ss")
									if ( vEjec.getAction(hudson.model.ParametersAction).getParameter("Ticket") ) { 
										datos.TKTDeploy = vEjec.getAction(hudson.model.ParametersAction).getParameter("Ticket").getValue()
									}
									datos.StatusDeploy = vEjec.getResult()
									vCause = (vEjec.getCause(hudson.model.Cause).properties)
									datos.UsuarioDeploy = vCause.shortDescription
									if ( vEjec.getCause(hudson.model.Cause.UserIdCause) ) {
										datos.UsuarioDeploy = datos.UsuarioDeploy + "(${(vEjec.getCause(hudson.model.Cause.UserIdCause).properties).userId})"
									}
									//Si no tiene artefactos es una ejecucion vieja que no los guardo
									vEjec.getArtifacts().each{ vFileArtifact ->
										if ( vFileArtifact.getFile().toString().split("/").last().contains(datos.JobBuild) ) {
											dataFromTxtArtifact = readFile file: vFileArtifact.getFile().toString()
											dataFromTxtArtifact.split("\\\n").each { vPipelineDeploy ->
												if ( vPipelineDeploy ) {
													datos.OrigenNexus = vPipelineDeploy.split(" ")[0]
													datos.Artefacto = vPipelineDeploy.split(" ")[0].split("/").last() 
													datos.Version = vPipelineDeploy.split(" ")[0].split("/")[-2] 
													datos.Target = vPipelineDeploy.split(" ")[1]
													datos.NServidor = datos.Target.split(":")[0]
													datos.NInstancia = datos.Target.split("/")[-3]
													DoWriteReportLineFromMap(datos, "", header, vFile, debug)
												}
											}
										}
									}
								}
								return false
							}
						}
					}
					archiveArtifacts artifacts: 'Extraer_deploys_desarrollonew.csv', onlyIfSuccessful: true
					status = sh returnStatus: true, script: "cp Extraer_deploys_desarrollonew.csv /Appweb/jenkins/."
					if ( status != 0 ) {
						log.error("No se pudo copiar Extraer_deploys_desarrollonew.csv a /Appweb/jenkins/.")
					}
					build job: 'Reporte_Siotecno_deploys', propagate: false, wait: false
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description}\n ${warnings}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}\n Ver adjunto ${BUILD_URL}artifact/Extraer_deploys_desarrollonew.csv/*view*/"
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

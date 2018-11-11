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
					log.info("Pipeline en ReporteGAS para crear Reporte de Builds de desarrollonew")
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
					header = ['FechaReporte','JobBuild','UrlJobBuildBranch','Artefacto','UrlJobIC','FechaIC','StatusIC','UrlJobBD','FechaBD','StatusBD','UsuarioBD','VersionBD','ArtefactoBD','UrlJobRD','FechaRD','StatusRD','UsuarioRD','VersionRD','ArtefactoRD']
					//Crea el file Extraer_builds_desarrollonew.csv
					writeFile file: 'Extraer_builds_desarrollonew.csv', text: (header.join(','))
					File vFile = new File(WORKSPACE, 'Extraer_builds_desarrollonew.csv') 
					vFile << "\n"
					Jenkins.getActiveInstance().getAllItems().each { vBuild ->
						if ( vBuild instanceof org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject && !vBuild.isDisabled() ) {
							datos = [:]
							datos.FechaReporte = new Date().format("dd/MM/YYYY HH:mm:ss")
							datos.JobBuild = vBuild.getDisplayName()
							log.info("Job ${datos.JobBuild}")
							datosConf = getJobConfigs(datos.JobBuild, debug, false)
							datos.AArtefacto = []
							if ( datosConf.mapeo ) {
								//ccm-frontend:ccm-frontend.war|ccm-backend:ccm-backend.war
								datosConf.mapeo.split("\\|").each{ mapeo ->
									datos.AArtefacto.add(mapeo)
								}
							}
							if ( !vBuild.getAllJobs() ) {
								datos.UrlJobBuildBranch = "SIN BRANCHES"
								datos.AArtefacto.each{ vArtefacto ->
									datos.Artefacto = vArtefacto.split(":")[1]
									DoWriteReportLineFromMap(datos, "", header, vFile, debug)
								}
							}
							vBuild.getAllJobs().each { branch ->
								datos.UrlJobBuildBranch = branch.getAbsoluteUrl()
								log.info("Branch ${datos.UrlJobBuildBranch}")
								datosBranch = [:]
								branch.getBuilds().find{ vEjec ->
									prefijo = vEjec.getDisplayName().split("#")[0]
									if ( ["IC","BD","RD"].contains(prefijo) ) {
										//Cuando tiene los 3 deja de buscar
										if ( datosBranch["UrlJobIC"] && datosBranch["UrlJobBD"] && datosBranch["UrlJobRD"] ) {
											return true
										}
										if ( !datosBranch["UrlJob"+prefijo] ) {
											datosBranch["UrlJob"+prefijo] = vEjec.getAbsoluteUrl()
											datosBranch["Fecha"+prefijo] = vEjec.getTime().format("dd/MM/YYYY HH:mm:ss")
											datosBranch["Status"+prefijo] = vEjec.getResult()
											vCause = (vEjec.getCause(hudson.model.Cause).properties)
											datosBranch["Usuario"+prefijo] = vCause.shortDescription
											if ( vEjec.getCause(hudson.model.Cause.UserIdCause) ) {
												datosBranch["Usuario"+prefijo] = datosBranch["Usuario"+prefijo] + "(${(vEjec.getCause(hudson.model.Cause.UserIdCause).properties).userId})"
											}
											datosBranch["AArtefacto"+prefijo] = []
											vEjec.getArtifacts().each{ vFileArtifact ->
												vFileArtifact = vFileArtifact.getFile().toString()
												if ( vFileArtifact.split("/").last().contains(datos.JobBuild) ) {
													dataFromTxtArtifact = readFile file: vFileArtifact
													dataFromTxtArtifact.split("\\\n").each { vArtifact ->
														if ( !datosBranch["AArtefacto"+prefijo].contains(vArtifact) ) {
															datosBranch["AArtefacto"+prefijo].add(vArtifact)
														}
													}
												}
											}
										}
										return false
									}
								}
								//Agrega los datos
								datosBranch.putAll(datos)
								//En caso que no tenga definidos mapeos
								if ( datos.AArtefacto.size() == 0 ) {
									DoWriteReportLineFromMap(datosBranch, "", header, vFile, debug)
								}
								//Agrega los artefactos que coinciden con los de mapeo
								datosBranch.AArtefactoOK = []
								datos.AArtefacto.each{ mapeo ->
									datosArtefacto = [:]
									datosArtefacto.Artefacto = mapeo.split(":")[1]
									datosBranch.AArtefactoBD.find{ vSnapshot ->
										//cmm-frontend=https://NEXUS_SERVER:PORT/content/repositories/aplicaciones-snapshot-jenkins/prueba/ar/com/company/ccm-frontend/1.0.1-SNAPSHOT/ccm-frontend-1.0.1-20180423.155234-2.war
										if ( vSnapshot.contains("SNAPSHOT") && vSnapshot.contains(mapeo.split(":")[0]) ) {
											datosArtefacto.ArtefactoBD = vSnapshot.split("/").last()
											datosArtefacto.VersionBD = vSnapshot.split("/")[-2]
											return true
										}
									}
									datosBranch.AArtefactoRD.find{ vRelease ->
										//cmm-frontend=https://NEXUS_SERVER:PORT/content/repositories/aplicaciones-snapshot-jenkins/prueba/ar/com/company/ccm-frontend/1.0.1/ccm-frontend-1.0.1.war
										if ( !vRelease.contains("SNAPSHOT") && vRelease.contains(mapeo.split(":")[0]) ) {
											datosArtefacto.ArtefactoRD = vRelease.split("/").last()
											datosArtefacto.VersionRD = vRelease.split("/")[-2]
											return true
										}
									}
									datosArtefacto.putAll(datosBranch)
									datosBranch.AArtefactoOK.add(mapeo.split(":")[0])
									DoWriteReportLineFromMap(datosArtefacto, "", header, vFile, debug)
								}
								//Agrega los artefactos de BD que no estan en AArtefactoOK
								datosBranch.AArtefactoBD.each{ vSnapshot ->
									datosArtefacto = [:]
									//Si no esta en AArtefactoOK lo escribe
									if ( vSnapshot.contains("SNAPSHOT") && !datosBranch.AArtefactoOK.contains(vSnapshot.split("/")[-3]) ) {
										datosArtefacto.ArtefactoBD = vSnapshot.split("/").last()
										datosArtefacto.VersionBD = vSnapshot.split("/")[-2]
										datosBranch.AArtefactoRD.find{ vRelease ->
											//Si ya informo el modulo no lo escribe
											if ( !vRelease.contains("SNAPSHOT") && vRelease.contains(vSnapshot.split("/")[-3]) ) {
												datosArtefacto.ArtefactoRD = vRelease.split("/").last()
												datosArtefacto.VersionRD = vRelease.split("/")[-2]
												return true
											}
										}
										datosArtefacto.putAll(datosBranch)
										datosBranch.AArtefactoOK.add(vSnapshot.split("/")[-3])
										DoWriteReportLineFromMap(datosArtefacto, "", header, vFile, debug)
									}
								}
								//Agrega los artefactos de RD que no estan en mapeo ni en BD
								datosBranch.AArtefactoRD.each{ vRelease ->
									datosArtefacto = [:]
									//Si no esta en AArtefactoOK lo escribe
									if ( !vRelease.contains("SNAPSHOT") && !datosBranch.AArtefactoOK.contains(vRelease.split("/")[-3]) ) {
										datosArtefacto.ArtefactoRD = vRelease.split("/").last()
										datosArtefacto.VersionRD = vRelease.split("/")[-2]
										datosArtefacto.putAll(datosBranch)
										datosBranch.AArtefactoOK.add(vRelease.split("/")[-3])
										DoWriteReportLineFromMap(datosArtefacto, "", header, vFile, debug)
									}
								}
							}
						}
					}
					archiveArtifacts artifacts: 'Extraer_builds_desarrollonew.csv', onlyIfSuccessful: true
					status = sh returnStatus: true, script: "cp Extraer_builds_desarrollonew.csv /Appweb/jenkins/."
					if ( status != 0 ) {
						log.error("No se pudo copiar Extraer_builds_desarrollonew.csv a /Appweb/jenkins/.")
					}
					//Programa la ejecucion de Reporte_Siotecno_builds
					build job : 'Reporte_Siotecno_builds', propagate: false, wait: false
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description}\n ${warnings}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}\n Ver adjunto ${BUILD_URL}artifact/Extraer_builds_desarrollonew.csv/*view*/"
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

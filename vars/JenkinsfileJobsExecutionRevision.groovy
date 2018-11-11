def call(Boolean debug = false) {
pipeline {
	agent { label 'master' }
	options {
		disableConcurrentBuilds()
		timeout(time: 15, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para revision de jobs ejecutados")
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					currentBuild.description = "${JOB_NAME}#${BUILD_NUMBER}"
					log.debug("Limpia el workspace", debug)
					cleanWs()
				}
			}
		}
		stage('Jobs Revision') {
			steps {
				script {
					//JobsExecutionRevision
					//Dejar mapeo siempre al final! ver getLastMapeo.groovy
					header = ['jobUrl','NewJobUrl','Job','BranchName','LastCommit','LastSuccessfulBuild','LastFailedBuild','BuildDescription','LastSnapshotDate','LastSnapshotVersion','LastSnapshotUrl','LastReleaseDate','LastReleaseVersion','LastReleaseUrl','mapeo']
					writeFile file: 'Extraer_JobsExecutionRevision.csv', text: (header.join(','))
					File vFileJobsExecutionRevision = new File(WORKSPACE, 'Extraer_JobsExecutionRevision.csv') 
					vFileJobsExecutionRevision << "\n"
					Jenkins.getActiveInstance().getAllItems().each { vItemJob ->
						if ( vItemJob instanceof org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject ) {
						//if ( vItemJob instanceof org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject && vItemJob.getDisplayName() == "VersionesJenkins20" ) 
							log.info("WorkflowMultiBranchProject: ${vItemJob.getDisplayName()}")
							oldjobUrl = "(" + vItemJob.getDescription().split(/\(/)[1].split("\n")[0]
							if ( !vItemJob.getAllJobs() ) {
								data = [vjobUrl: oldjobUrl, vNewJobUrl: vItemJob.getAbsoluteUrl(), vJob: vItemJob.getDisplayName(), vBranchName: '', vLastCommit: '', vLastSuccessfulBuild: '', vLastFailedBuild: '', vBuildDescription: 'SIN BRANCHES', vOkBuildDescription: '', vFailedBuildDescription: '', vOkBuildNumber: 0, vFailedBuildNumber: 0, vLastSnapshotDate: '', vLastSnapshotVersion: 0, vLastSnapshotUrl: '', vLastReleaseDate: '', vLastReleaseVersion: 0, vLastReleaseUrl: '', vmapeo: '']
								DoWriteReportLineFromMap(data, "v", header, vFileJobsExecutionRevision, debug)
							}
							vItemJob.getAllJobs().each { branch ->
								data = [vjobUrl: oldjobUrl, vNewJobUrl: branch.getAbsoluteUrl(), vJob: vItemJob.getDisplayName(), vBranchName: branch.getDisplayName(), vLastCommit: '', vLastSuccessfulBuild: '', vLastFailedBuild: '', vBuildDescription: '', vOkBuildDescription: '', vFailedBuildDescription: '', vOkBuildNumber: 0, vFailedBuildNumber: 0, vLastSnapshotDate: '', vLastSnapshotVersion: 0, vLastSnapshotUrl: '', vLastReleaseDate: '', vLastReleaseVersion: 0, vLastReleaseUrl: '', vmapeo: '']
								if ( branch.isDisabled() ) {
									data.vLastSuccessfulBuild = "DISABLED"
								} else {
									vLastToKeep = getRecExecution.LastToKeep(branch, debug)
									data.vmapeo = vLastToKeep.mapeo
									data.vLastSnapshotDate = vLastToKeep.Sfecha
									data.vLastSnapshotVersion = vLastToKeep.Sversion
									data.vLastSnapshotUrl = vLastToKeep.Surl
									data.vLastReleaseDate = vLastToKeep.Rfecha
									data.vLastReleaseVersion = vLastToKeep.Rversion
									data.vLastReleaseUrl = vLastToKeep.Rurl
									if ( branch.getLastSuccessfulBuild() ) {
										data.vLastSuccessfulBuild = branch.getLastSuccessfulBuild().getTime().toLocaleString()
										data.vOkBuildDescription = branch.getLastSuccessfulBuild().getDescription()
										data.vOkBuildNumber = branch.getLastSuccessfulBuild().getNumber()
									}
									if ( branch.getLastFailedBuild() ) {
										data.vLastFailedBuild = branch.getLastFailedBuild().getTime().toLocaleString()
										data.vFailedBuildDescription = branch.getLastFailedBuild().getDescription()
										data.vFailedBuildNumber = branch.getLastFailedBuild().getNumber()
									}
									// Se queda con el mas nuevo
									if ( data.vOkBuildNumber > data.vFailedBuildNumber ) {
										data.vBuildDescription = data.vOkBuildDescription 
									} else {
										data.vBuildDescription = data.vFailedBuildDescription 
									}
									if ( data.vBuildDescription ) {
										if ( data.vBuildDescription.contains("Last Commit:") ) {
											data.vLastCommit = (data.vBuildDescription.split("Last Commit:")[1])
											data.vLastCommit = (data.vLastCommit.split("\\n")[0])
										}
										if ( data.vBuildDescription.contains("ERROR! ") ) {
											data.vBuildDescription = (data.vBuildDescription.split("ERROR")[1]).split("FAILURE")[0]
											//Si el failure es por el svn lo marca como disabled
											if ( data.vBuildDescription.contains("SubversionSCM") || data.vBuildDescription.contains("No se puede acceder al pom.xml") || data.vBuildDescription.contains("Tiene + de 120 días") ) {
												branch.disabled = true
												data.vLastSuccessfulBuild = "DISABLED NOW"
											}
										} else {
											if ( data.vBuildDescription.contains("SUCCESS") ) {
												data.vBuildDescription = "SUCCESS"
											} else {
												if ( data.vBuildDescription.split("FAILURE")[0] != data.vBuildDescription ) {
													data.vBuildDescription = "FAILURE"
												}
												if ( data.vBuildDescription.split("ABORTED")[0] != data.vBuildDescription ) {
													data.vBuildDescription = "ABORTED"
												}
											}
										}
									}
								}
								DoWriteReportLineFromMap(data, "v", header, vFileJobsExecutionRevision, debug)
							}							
						}
					}
					archiveArtifacts artifacts: 'Extraer_JobsExecutionRevision.csv', onlyIfSuccessful: true
					sh "cp Extraer_JobsExecutionRevision.csv /Appweb/jenkins/."
				}
			}
		}
		stage('Trigger ReporteConfigs') {
			steps {
				script {
					//Ejecuta ReporteConfigs
					build job : 'ReporteConfigs', wait: false
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description} ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				emailext subject: emailextSubject, 
					body: emailextBody, 
					to: "${ADMINMAIL}"
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
				log.info("Failure!")
			}
		}
	}
}
}
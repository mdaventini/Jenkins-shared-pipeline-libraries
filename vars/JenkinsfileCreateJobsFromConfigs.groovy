def call() {
pipeline {
	agent none
	options {
		skipDefaultCheckout() 
		timeout(time: 30, unit: 'MINUTES')
		//buildDiscarder(logRotator(daysToKeepStr: '10'))
	}
	stages {
		stage('Comienzo') {
			agent { label 'master' }
			steps {
				script {
					log.info("Declarative Jenkinsfile para crear Jobs con jobDsl a partir de archivos .config")
					//Solo se activa debug si es REPLAY
					debug = false
					if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
						debug = true
					}
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					log.debug("Param data : ", debug)
					log.debug("   params.FromConfigs = ${params.FromConfigs}", debug)
					datosConf = getJobConfigs((params.FromConfigs).minus(".config"), debug)
					currentBuild.description="Jobs para ${params.FromConfigs}"
				}
			}
		}
		stage('Crear Job de compilacion') {
			agent { label 'master' }
			steps {
				script {
					def vidJob = UUID.randomUUID().toString()
					vdsl = libraryResource 'dsl/multibranchPipelineJobBuild.dsl'
					vscriptText = vdsl.replaceAll("rNOMBREJOB","${datosConf.Job}")
					vscriptText = vscriptText.replaceAll("rJOBURL","${datosConf.jobUrl}")
					vscriptText = vscriptText.replaceAll("rOLDJOB","${datosConf.OldJob}")
					vscriptText = vscriptText.replaceAll("rUUID","${vidJob}")
					vscriptText = vscriptText.replaceAll("rSVNURL","${datosConf.svnUrl}")
					if ( !datosConf.svnIncludes ) {
						datosConf.svnIncludes = "trunk, branches/*"
					}
					if ( datosConf.svnIncludes == "disabled" ) {
						//Borra los archivos Jenkinsfile del repo
						DoDeleteJenkinsfile4Repo(datosConf.svnUrl, datosConf.includedRegions, datosConf.Job, debug)
					} else {
						//Crea los archivos Jenkinsfile en el repo
						DoImportJenkinsfile2Repo(datosConf.svnUrl, datosConf.includedRegions, datosConf.Job, debug)
					}
					vscriptText = vscriptText.replaceAll("rSVNINCLUDES","${datosConf.svnIncludes}")
					tempscriptPath = datosConf.Job
					if ( datosConf.includedRegions ) {
						tempscriptPath = datosConf.includedRegions + "/" + datosConf.Job
					}
					vscriptText = vscriptText.replaceAll("rSCRIPTPATH","${tempscriptPath}")
					//Crea el multibranchPipelineJob que detecta los archivos creados ateriormente
					log.info("Crea multibranchPipelineJob('${datosConf.Job}') con script parseado del multibranchPipelineJobIC.dsl \n${vscriptText}")
					jobDsl scriptText: vscriptText, removedJobAction: 'DISABLE', failOnMissingPlugin: true, removedConfigFilesAction: 'DELETE'
					log.info("Ejecuta el Scan del multibranchPipelineJob")
					build job: datosConf.Job, wait: false
				}
			}
		}
		stage('Crear Job de deploy') {
			agent { label 'master' }
			when { // Solo genera el job si existen artefactos toDeploy
				expression { datosConf.toDeploy }
			}
			steps {
				script {
					vdsl = libraryResource 'dsl/pipelineJobDeploy.dsl'
					vscriptTextDeploy = vdsl.replaceAll("rNOMBREJOB","${datosConf.Job}")
					vscriptTextDeploy = vscriptTextDeploy.replaceAll("rPROYNOMBREJOB","${datosConf.Job}")
					vscriptTextDeploy = vscriptTextDeploy.replaceAll("rNEXUS_SERVER","${NEXUS_SERVER}")
					vscriptTextDeploy = vscriptTextDeploy.replaceAll("rDESTINOS","desa\\\\ntest")
					log.info("Crea pipelineJob('${datosConf.Job}-deploy') con script parseado del pipelineJobDeploy.dsl \n${vscriptTextDeploy}")
					jobDsl scriptText: vscriptTextDeploy, removedJobAction: 'DISABLE', failOnMissingPlugin: true, removedConfigFilesAction: 'DELETE'
					
					//Crea los jobs GAS-[Job]-deploy para prepro y prod
					vscriptTextDeploy = vdsl.replaceAll("rNOMBREJOB","GAS-${datosConf.Job}")
					vscriptTextDeploy = vscriptTextDeploy.replaceAll("rPROYNOMBREJOB","${datosConf.Job}")
					vscriptTextDeploy = vscriptTextDeploy.replaceAll("rNEXUS_SERVER","${NEXUS_SERVER}")
					vscriptTextDeploy = vscriptTextDeploy.replaceAll("rDESTINOS","prepro\\\\nprod")
					log.info("Crea pipelineJob('GAS-${datosConf.Job}') con script parseado del pipelineJobDeploy.dsl \n${vscriptTextDeploy}")
					jobDsl scriptText: vscriptTextDeploy, removedJobAction: 'DISABLE', failOnMissingPlugin: true, removedConfigFilesAction: 'DELETE'
				}
			}
		}
		stage('Crear Job de restart') {
			agent { label 'master' }
			when { // Solo genera el job si existen artefactos toDeploy
				expression { datosConf.toDeploy }
			}
			steps {
				script {
					vdsl = libraryResource 'dsl/pipelineJobRestart.dsl'
					vscriptTextRestart = vdsl.replaceAll("rNOMBREJOB","${datosConf.Job}")
					vscriptTextRestart = vscriptTextRestart.replaceAll("rPROYNOMBREJOB","${datosConf.Job}")
					vscriptTextRestart = vscriptTextRestart.replaceAll("rDESTINOS","desa\\\\ntest")
					log.info("Crea pipelineJob('${datosConf.Job}-restart') con script parseado del pipelineJobRestart.dsl \n${vscriptTextRestart}")
					jobDsl scriptText: vscriptTextRestart, removedJobAction: 'DISABLE', failOnMissingPlugin: true, removedConfigFilesAction: 'DELETE'
					
					//Crea los jobs GAS-[Job]-restart 
					vscriptTextRestart = vdsl.replaceAll("rNOMBREJOB","GAS-${datosConf.Job}")
					vscriptTextRestart = vscriptTextRestart.replaceAll("rPROYNOMBREJOB","${datosConf.Job}")
					vscriptTextRestart = vscriptTextRestart.replaceAll("rDESTINOS","desa\\\\ntest\\\\nprepro\\\\nprod")
					log.info("Crea pipelineJob('GAS-${datosConf.Job}-restart') con script parseado del pipelineJobRestart.dsl \n${vscriptTextRestart}")
					jobDsl scriptText: vscriptTextRestart, removedJobAction: 'DISABLE', failOnMissingPlugin: true, removedConfigFilesAction: 'DELETE'
				}
			}
		}		
	}
	post { 
		always { 
			script {
				log.info("Fin del pipeline Create!")
				currentBuild.description = "${currentBuild.description}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
			}
		}
		aborted {
			script {
				log.info("Aborted!")
				emailext subject: emailextSubject, 
					body: emailextBody, 
					to: "${ADMINMAIL}"
			}
		}
		failure {
			script {
				//Avisa error, pasa por aca si lo manda un message error
				log.info("Failure! Avisa a admin")
				emailext subject: emailextSubject, 
					body: emailextBody, 
					to: "${ADMINMAIL}"
			}
		}
	}
}
}
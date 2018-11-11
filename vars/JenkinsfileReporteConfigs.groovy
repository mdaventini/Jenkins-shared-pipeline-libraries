def call() {
pipeline {
	agent { label 'master' }
	options {
		disableConcurrentBuilds()
		timeout(time: 15, unit: 'MINUTES')
		buildDiscarder logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '')
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para reporte de configs modificando datos: mapeo y ambientes (desa, test, prepro, prod)")
					//Solo se activa debug si es REPLAY
					debug = false
					if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
						debug = true
					}
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					currentBuild.description = "${JOB_NAME}#${BUILD_NUMBER}"
					vCambios = ""
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
		stage('Jobs Configs') {
			steps {
				script {
					//Configs
					header = ['Cambiar','NewJob','Job','OldJob','jobUrl','svnUrl','svnIncludes','includedRegions','rootPOM','jdk','maven','mvnTest','branchAC','toDeploy','mapeo','transferset','desa','test','prepro','prod','mailLeader','mailTest','mailDesa']					
					writeFile file: 'Configs.csv', text: (header.join(','))
					File vFileConfigs = new File(WORKSPACE, 'Configs.csv') 
					vFileConfigs << "\n"
					//Busca todos los configs 
					dir("../${JOB_NAME}@libs/pipeline-library/resources/configs/") {
						configs = findFiles glob: '*.config'
						configs.each { Config ->
							nameConfig = "${Config}"
							// ( !nameConfig.contains("ServPresta") )
							if ( 1 == 1 ) {
								log.debug("Viendo ${nameConfig}", debug)
								datosConf = getJobConfigs(nameConfig.minus(".config"), debug)
								datosConf.Cambiar = ""
								if ( !datosConf.OldJob ) {
									datosConf.OldJob = datosConf.Job 
								}
								//Asegura que datosConf.includedRegions no finalice con /
								if ( datosConf.includedRegions && datosConf.includedRegions.charAt(datosConf.includedRegions.length() - 1) == '/') {
									datosConf.includedRegions = datosConf.includedRegions.substring(0, datosConf.includedRegions.length() - 1)
								}
								//svnIncludes en el .config esta separado con ,
								datosConf.svnIncludes = datosConf.svnIncludes.trim().replaceAll(" ","").replaceAll(","," ")
								//mailLeader en el .config esta separado con ,
								datosConf.mailLeader = datosConf.mailLeader.trim().replaceAll(" ","").replaceAll(","," ")
								DoWriteReportLineFromMap(datosConf, "", header, vFileConfigs, debug)
								//Agrega datos a Cambiar
								if ( !vCambios && datosConf.Cambiar.trim() ) {
									vCambios = "CON CAMBIOS"
									log.debug("vCambios:*${vCambios}* datosConf.Cambiar:*${datosConf.Cambiar.trim()}*",debug)
								}
							}
						}
					}
					archiveArtifacts artifacts: 'Configs.csv', onlyIfSuccessful: true
				}
			}
		}
		stage('Actualiza configs') {
			steps {
				script {
					//Actualiza siempre aunque no existan cambios
					//Copia Configs.csv a ../${JOB_NAME}@libs/pipeline-library/resources/bulk/
					status = sh returnStatus: true, script: "cp Configs.csv ../${JOB_NAME}@libs/pipeline-library/resources/bulk/."
					if ( status != 0 ) {
						log.error("No se pudo actualizar el archivo Configs.csv")
					}
					//Add + Commit nuevos configs
					svnUtils.DoAddCommitSvn("../${JOB_NAME}@libs/pipeline-library/resources/bulk/", "Configs.csv", "Cambios Configs.csv - ${currentBuild.description}", false, debug)
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description} ${vCambios} ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult} ${vCambios}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}\n Debe revisar el adjunto ${BUILD_URL}artifact/Configs.csv/*view*/ y ejecutar ${JENKINS_URL}job/GASUpdateConfigs/"
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

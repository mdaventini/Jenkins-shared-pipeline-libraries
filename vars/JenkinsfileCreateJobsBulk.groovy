def call() {
pipeline {
	agent none
	options {
		skipDefaultCheckout() 
		disableConcurrentBuilds()
		timeout(time: 120, unit: 'MINUTES')
		buildDiscarder(logRotator(daysToKeepStr: '10'))
	}
	stages {
		stage('Comienzo') {
			agent { label 'master' }
			steps {
				script {
					log.info("Declarative Jenkinsfile para crear los Jobs, de los .configs seleccionados con filtro en ../${JOB_NAME}@libs/pipeline-library/resources/configs/")
					//Solo se activa debug si es REPLAY
					debug = false
					if ( currentBuild.rawBuild.getCause(org.jenkinsci.plugins.workflow.cps.replay.ReplayCause) ) {
						debug = true
					}
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					vglob = params.FilterConfigs + ".config"
					currentBuild.description = "Nuevos jobs generados para ${vglob}"
				}
			}
		}
		stage('TODOS') {
			agent { label 'master' }
			steps {
				script {
					//Busca todos los configs 
					dir("../${JOB_NAME}@libs/pipeline-library/resources/configs/") {
						configs = findFiles glob: vglob
						configs.each { Config ->
							currentBuild.description = "${currentBuild.description} \n ${Config}"
							log.debug(" Config = ${Config}", debug)
							build job : 'GASCreateJobsfromConfigs', 
								parameters: [string(name: 'FromConfigs', value: "${Config}")], propagate: true, wait: true
						}
					}
				}
			}
		}
	}
	post { 
		always { 
			script {
				log.info("Post del pipeline Create Bulk")
				currentBuild.description = "${currentBuild.description}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} ${JOB_NAME} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				emailext subject: emailextSubject, 
					body: emailextBody, 
					to: "${ADMINMAIL}"
			}
		}
		aborted {
			script {
				log.info("Aborted!")
			}
		}
		failure {
			script {
				//Avisa error, pasa por aca si lo manda un message error
				log.info("Failure! Avisa a admin")
			}
		}
	}
}
}
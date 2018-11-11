def call(String host, String report_folder, Boolean debug = false) {
pipeline {
	agent { label 'master' }
	options {
		timeout(time: 30, unit: 'MINUTES')
		buildDiscarder(logRotator(numToKeepStr: '10', artifactNumToKeepStr: '10'))
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline para ejecutar reportes Siotecno en ${host}")
					currentBuild.displayName = "${JOB_NAME}#${BUILD_NUMBER}"
					currentBuild.description = "${JOB_NAME}#${BUILD_NUMBER}\n Host: ${host}"
					log.debug("Limpia el workspace", debug)
					cleanWs()
				}
			}
		}
		stage('Exec Report') {
			steps {
				script {
					status = sh returnStatus: true, script: "ssh ${host} '~/jon-smreport/bin/jon-smreport.sh -f ${host} -p 7080 -l rhqadmin -P rhqadmin -O prod -r desa-reports.lst'"
					if ( status != 0 ) {
						log.error("No se pudo ejecutar el reporte ${status}")
					}
					status = sh returnStatus: true, script: "ssh ${host} '~/jon-smreport/bin/CopiaReportes.sh'"
					if ( status != 0 ) {
						log.error("No se pudo copiar el resultado de los reportes ${status}")
					}
					archiveArtifacts artifacts: '/www/siotecno_logs/${report_folder}/*.csv', onlyIfSuccessful: true
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description} ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} ${JOB_NAME} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}"
				emailext subject: emailextSubject, 
					body: emailextBody, 
					to: "${ADMINMAIL}, devops@company.com.ar"
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
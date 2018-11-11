def call() {
pipeline {
	agent { label 'master' }
	options {
		disableConcurrentBuilds()
		timeout(time: 15, unit: 'MINUTES')
		buildDiscarder logRotator(artifactDaysToKeepStr: '5', artifactNumToKeepStr: '', daysToKeepStr: '30', numToKeepStr: '')
		durabilityHint('PERFORMANCE_OPTIMIZED')
	}
	stages {
		stage('Comienzo') {
			steps {
				script {
					log.info("Pipeline en ReporteGAS para crear Reporte de Builds para Siotecno")
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
					header = ['JobBuild','UrlJobBuildBranch','Artefacto','UrlJobIC','FechaIC','StatusIC','UrlJobBD','FechaBD','StatusBD','UsuarioBD','VersionBD','ArtefactoBD','UrlJobRD','FechaRD','StatusRD','UsuarioRD','VersionRD','ArtefactoRD']
					//Crea el file Siotecno_builds.csv
					writeFile file: 'Siotecno_builds.csv', text: (header.join(','))
					File vFile = new File(WORKSPACE, 'Siotecno_builds.csv') 
					vFile << "\n"
					//Copia los datos de Origen al workspace (conservando y obteniendo la fecha de creacion), en caso que no exista da error
					vScript = "cp -p -u /Appweb/jenkins/Extraer_builds_*.csv ."
					log.debug(" vScript:${vScript}", debug)
					status = sh returnStatus: true, script: vScript
					log.debug("  status:${status}", debug)
					if ( status != 0 ) {
						log.error("No se pudieron obtener los reportes /Appweb/jenkins/Extraer_builds_*.csv")
					}
					DoUnificarReportes(vFile, "Extraer_builds_desarrollonew.csv", header, [:], debug)
					DoUnificarReportes(vFile, "Extraer_builds_OSJenkinsDESA.csv", header, [:], debug)
					archiveArtifacts artifacts: 'Siotecno_builds.csv'
					status = sh returnStatus: true, script: "cp Siotecno_builds.csv ${DIR_SIOTECNO}."
					if ( status != 0 ) {
						log.error("No se pudo copiar Siotecno_builds.csv a ${DIR_SIOTECNO}.")
					}
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}\n Ver adjunto ${BUILD_URL}artifact/Siotecno_builds.csv/*view*/"
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

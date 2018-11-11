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
					log.info("Pipeline en ReporteGAS para crear Reporte de Restarts para Siotecno")
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
		stage('Restarts') {
			steps {
				script {
					warnings = ""
					header = ['JobRestart','NServidor','NInstancia','Environment','UrlJobRestart','FechaRestart','TKTRestart','StatusRestart','UsuarioRestart','FechaUltimoInicio']
					//Crea el file Siotecno_restarts.csv
					writeFile file: 'Siotecno_restarts.csv', text: (header.join(','))
					File vFile = new File(WORKSPACE, 'Siotecno_restarts.csv') 
					vFile << "\n"
					//Copia los datos de Origen al workspace (conservando y obteniendo la fecha de creacion), en caso que no exista da error
					vScript = "cp -p -u /Appweb/jenkins/Extraer_restarts_*.csv ."
					status = sh returnStatus: true, script: vScript
					log.debug("  status:${status}", debug)
					if ( status != 0 ) {
						log.error("No se pudieron obtener los reportes /Appweb/jenkins/Extraer_restarts_*.csv")
					}
					//Arma el map mFechasUltimosInicios
					mFechasUltimosInicios = [:]
					vScript = "cat Extraer_restarts_*.csv  | grep -v FechaReporte | awk -F',' '{print \$3}' | sort -u > Servidores.txt"
					status = sh returnStatus: true, script: vScript
					log.debug("  status:${status}", debug)
					if ( status != 0 ) {
						log.error("No se pudieron obtener los datos para informar FechaUltimoInicio")
					}
					datosServidores = readFile file: 'Servidores.txt'
					if ( !datosServidores ) {
						log.error("No se pudieron obtener los datos para informar FechaUltimoInicio")
					} else {
						datosServidores.split("\\\n").each { linea ->
							if ( linea.trim() ) {
								vServidor = linea.split(" ")[0]
								vScript = "ssh -o ConnectTimeout=10 ${vServidor} 'ps  h -o lstart,cmd `pgrep -f \".*java.*/s.*\"`'"
								status = sh returnStatus: true, script: "${vScript} > FechasInstancias.txt"
								log.debug("  status:${status}", debug)
								if ( status != 0 ) {
									warnings = "${warnings}\n Error al ejecutar el comando ${vScript}"
									mFechasUltimosInicios[vServidor+"_"] = ""
								}
								datosFechasInstancias = readFile file: 'FechasInstancias.txt'
								if ( datosFechasInstancias ) {
									datosFechasInstancias.split("\\\n").each { lineainstancia ->
										if ( lineainstancia.trim() && lineainstancia.contains("/server/") && ( lineainstancia.split("/server/")[0] != lineainstancia.split("/server/") ) ) {
											vInstancia = lineainstancia.split("/server/")[1].split("/")[0]
											enUSLocale = new Locale("en", "US")
											Locale.setDefault(enUSLocale)
											vDate = lineainstancia.split("/")[0].trim()
											mFechasUltimosInicios[vServidor.split("@")[1]+"_"+vInstancia] = Date.parse("EEE MMM dd HH:mm:ss yyyy", vDate).format("dd/MM/YYYY HH:mm:ss")
										}
									}
								}
							}
						}
					}
					//Unifica Reportes
					DoUnificarReportes(vFile, "Extraer_restarts_OSJenkinsDESA.csv", header, mFechasUltimosInicios, debug)
					DoUnificarReportes(vFile, "Extraer_restarts_OSJenkinsPRO.csv", header, mFechasUltimosInicios, debug)
					DoUnificarReportes(vFile, "Extraer_restarts_desarrollonew.csv", header, mFechasUltimosInicios, debug)
					archiveArtifacts artifacts: 'Siotecno_restarts.csv'
					status = sh returnStatus: true, script: "cp Siotecno_restarts.csv ${DIR_SIOTECNO}."
					if ( status != 0 ) {
						log.error("No se pudo copiar Siotecno_restarts.csv a ${DIR_SIOTECNO}.")
					}
				}
			}
		}
	}
	post { 
		always { 
			script {
				currentBuild.description = "${currentBuild.description}\n ${warnings}\n ${currentBuild.currentResult}"
				emailextSubject = "${currentBuild.displayName} - ${currentBuild.currentResult}"
				emailextBody = "Check console output at ${BUILD_URL}console to view the results.\n ${currentBuild.description}\n Ver adjunto ${BUILD_URL}artifact/Siotecno_restarts.csv/*view*/"
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

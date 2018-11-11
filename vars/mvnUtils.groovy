//Genera el Map con los datos necesarios para ejecutar goals maven
def getDatosMvn(Map datosConf, Boolean debug = false) {
	log.info("Datos del Proyecto - maven")
	//Arma datosMvn con datos default
	datosMvn = [mavenSettingsConfig: 'maven-settings', mavenOpts: '-Xmx1536m']
	datosMvn.putAll(datosConf)
	if ( !fileExists (datosMvn.rootPOM) ) {
		log.error("No se puede acceder al ${datosMvn.rootPOM} - Verificar con el administrador")
	}
	pom = readMavenPom file: datosMvn.rootPOM 
	datosMvn.pom = pom
	datosMvn.version = pom.version
	//Agrega mapped
	datosMvn << getMappedVersion(pom.version, debug)
	//Agrega datos para los goals de maven
	datosMvn.artefactos = JOB_NAME.replaceAll("/","-").replaceAll("%2F","-")
	datosMvn.Nartefactos = "${datosMvn.artefactos}-Nexus"
	datosMvn.resultado = datosMvn.artefactos+".resultado"
	datosMvn.sonarqubePlugin = "org.codehaus.sonar:sonar-maven-plugin:4.5.4:sonar -Djava.io.tmpdir=${WORKSPACE} -Dsonar.sources=. -Dsonar.host.url=http://SONAR-HOST-URL/ -Dsonar.exclusions=target/**,src/test/**,**/jquery*.js,**/js/lib/**,**/jstree098/**,**/ui.*.js,**/supersubs.js,**/superfish.js,**min.js,**/bootstrap-*.js,**/jit*.js,**/tiny_mce/**,**/jquery-cometd-*/**,**/gears_init.js,**/geolocation.js,**/sarissa.js,**/uploadify/**,**/window.js"
	datosMvn.artifactId = (JOB_NAME).minus("/"+JOB_BASE_NAME)
	datosMvn.classifier = "${((BRANCH_NAME.minus("branches"))).minus("/")}-${BUILD_NUMBER}"
	datosMvn.teeResultado = "| tee >(grep 'BUILD SUCCESS' >${datosMvn.resultado})" 
	//Solamente se ejecuta el grep a datosMvn.artefactos si el config indica toDeploy
	if ( datosMvn.toDeploy != "" ) {
		//El formato es war|ear pero se debe transformar a '\.war|\.ear'
		vToDeploy = ""
		datosMvn.toDeploy.split("\\|").each { datos2Deploy ->
			if ( vToDeploy ) {
				vToDeploy = vToDeploy + "|\\." + datos2Deploy
			} else {
				vToDeploy = vToDeploy + "\\." + datos2Deploy
			}
		}
		//En caso que no sea multimodulo y se genere otro artefacto
		if ( datosMvn.toDeploy.contains(pom.packaging) ) {
			datosMvn.teeMapeos = " >(grep -e 'Installing' | grep -E '${vToDeploy}' | awk -F' to ' '{print \$1}' | awk -F'/' '{print \"${pom.artifactId}:\"\$NF}' > ${datosMvn.artefactos})"
		} else {
			datosMvn.teeMapeos = " >(grep -e 'Installing' | grep -E '${vToDeploy}' | awk -F' to ' '{print \$1}' | awk -F'/' '{print \$(NF-2)\":\"\$NF}' > ${datosMvn.artefactos})"
		}
		datosMvn.teeArtefactos = " >(grep Uploaded | grep -E '${vToDeploy}' | awk -F'(' '{print \$1}' | awk -F' ' '{print \$NF}' > ${datosMvn.Nartefactos})"
	}
	datosMvn.each { vdatosMvn ->
		log.debug("${vdatosMvn.key}: ${vdatosMvn.value}", debug)
	}
	return datosMvn
}

//Hace split de la version indicada en el pom, mapeando a [majorV: '1', minorV: '0', bugfixV: '0', preReleaseV: '', isSnapshotV: 'NO' ] from https://semver.org/lang/es/
def getMappedVersion(String vpomVersion, Boolean debug = false) {
	mappedVersion = [majorV: '1', minorV: '0', bugfixV: '0', preReleaseV: '', isSnapshotV: 'NO' ]
	ssplitVersion = "${vpomVersion}".split("-")
	if ( ssplitVersion.size() > 1 ) {
		//Si no es SNAPSHOT es preRelease (from https://semver.org/lang/es/)
		if ( ssplitVersion[1] != 'SNAPSHOT' ) {
			mappedVersion.preReleaseV = ssplitVersion[1]
			mappedVersion.isSnapshotV = ssplitVersion[2]
		} else {
			mappedVersion.isSnapshotV = ssplitVersion[1]
		}
	}
	splitVersion = "${ssplitVersion[0]}".split("\\.")
	mappedVersion.majorV = splitVersion[0]
	if ( splitVersion.size() >= 2 ) {
		mappedVersion.minorV = splitVersion[1]
	}
	if ( splitVersion.size() >= 3 ) {
		mappedVersion.bugfixV = splitVersion[2]
	}
	log.debug("getMappedVersion Version Parsed: ${mappedVersion}", debug)
	return mappedVersion
}

//Usa https://jenkins.io/doc/pipeline/steps/pipeline-maven/#withmaven-provide-maven-environment- Pipeline Maven Integration Plugin
//Atencion! Al ejecutar el sh con #!/bin/bash hace que el comando retorne ok aunque maven retorne error, entonces hay que controlar el contenido del archivo
def exec(String maven, String jdk, String mavenSettingsConfig, String  mavenOpts, String shcommand, Boolean debug = false) {
	withMaven( 
		maven: "${maven}",
		jdk: "${jdk}",
		mavenSettingsConfig: "${mavenSettingsConfig}",
		mavenOpts: "${mavenOpts}",
		publisherStrategy: 'EXPLICIT'
	) 
	{
		log.debug("exec ${shcommand}", debug)
		sh "${shcommand}"
	}
}

def validate(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado}"
	exec(datosMvn.maven, datosMvn.jdk, datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En validate")
	} 
}

def installNoTests(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado} ${datosMvn.teeMapeos}"
	exec(datosMvn.maven, datosMvn.jdk, datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En compilacion sin test")
	} else {
		log.debug("installNoTests Build ok! Hay datos toDeploy? ${datosMvn.toDeploy}", debug)
		if ( datosMvn.toDeploy ) {
			dataFromMaven = readFile file: datosMvn.artefactos
			if ( !dataFromMaven ) {
				log.error("En compilacion sin test: no se pudieron obtener los mapeos para ${datosMvn.toDeploy}")
			} else {
				return dataFromMaven.replaceAll("\n","|")
			}
		} else {
			return ""
		}
	}
}

def test(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado}"
	exec(datosMvn.maven, datosMvn.jdk, datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En tests")
	} 
}

def sonarqubePreview(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado} >(grep 'BUILD BREAKER' > ${datosMvn.artefactos})"
	exec("Maven-3.5.2", "Java 1.8.x", datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	sh "rm -Rf sonar-runner*"
	dataFromMaven = readFile file: datosMvn.resultado
	log.debug("sonarqubePreview datosMvn.resultado: ${dataFromMaven}", debug)
	if ( dataFromMaven ) {
		return "OK\n      No hay informacion de BUILD BREAKER:"
	} else {
		dataFromMaven = readFile file: datosMvn.artefactos
		log.debug("sonarqubePreview datosMvn.artefactos: ${dataFromMaven}", debug)
		if ( dataFromMaven ) {
			return "OK\n      ${dataFromMaven}\n WARNING! No cumple con los parámetros definidos de calidad"
		} else { 
			log.error("En SonarQube Preview")
		}
	}
}

def sonarqube(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado}"
	exec("Maven-3.5.2", "Java 1.8.x", datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	sh "rm -Rf sonar-runner*"
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En SonarQube")
	} 
}

// From https://jeremylong.github.io/DependencyCheck/data/proxy.html
def dependencycheck(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado}"
	exec("Maven-3.5.2", "Java 1.8.x", datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En DependencyCheck")
	} 
}

def updateversions(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado}"
	exec("Maven-3.5.2", "Java 1.8.x", datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En update-versions")
	} 
}

def deployArtefactos(Map datosMvn, String vTipo, String vVersion, Boolean debug = false) {
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("No se pudo generar el ${vTipo}")
	} 
	if ( datosMvn.toDeploy ) {
		dataNexus = readFile file: datosMvn.Nartefactos
		dataMapeo = readProperties file: datosMvn.artefactos
		dataFromMaven = ""
		if ( dataNexus && dataMapeo ) {
			dataMapeo.each { vdataMapeo ->
				dataNexus.split("\\\n").each { vUrl ->
					if ( vUrl.contains(vdataMapeo.key) ) {
						dataFromMaven = "${dataFromMaven}${vdataMapeo.value}=${vUrl}\n"
					}
				}
			}
			datosMvn.deployArtefactos = "${datosMvn.artefactos}-${vVersion}"
			writeFile file: datosMvn.deployArtefactos, text: dataFromMaven
		} else {
			log.error("Al leer artefactos ${vTipo}")
		}
		//deploy-file
		repo = "aplicaciones-releases-jenkins"
		if ( vTipo == "SNAPSHOT" ) {
			repo = "aplicaciones-snapshot-jenkins"
		}
		goal = "#!/bin/bash \n" + "mvn org.apache.maven.plugins:maven-deploy-plugin:2.8.2:deploy-file -Durl=https://NEXUS_SERVER:PORT/content/repositories/${repo}/ -DrepositoryId=${repo} -DartifactId=${datosMvn.artifactId} -DgroupId='ar.com.company' -Dpackaging=txt -Dfile=${datosMvn.deployArtefactos} -Dversion=${vVersion} -Dclassifier=${datosMvn.classifier} -DgeneratePom=false | tee >(grep 'Uploaded' | grep -E '${datosMvn.classifier}'| awk -F'(' '{print \$1}' | awk -F' ' '{print \$NF}' > ${datosMvn.resultado})"
		exec("Maven-3.5.2", "Java 1.8.x", datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, goal, debug)
		dataFromDeployFile = readFile file: datosMvn.resultado
		if ( !dataFromDeployFile ) {
			log.error("Al subir a Nexus artefactos ${vTipo}")
		} else {
			log.info("Artefactos generados leidos de ${dataFromDeployFile}: ${dataFromMaven}")
			archiveArtifacts artifacts: datosMvn.deployArtefactos, onlyIfSuccessful: false
			return dataFromDeployFile
		}
	}
	return ""
}

def deploySNAPSHOT(Map datosMvn, String goal, Boolean debug = false) {
	shgoal = "#!/bin/bash \n ${goal} ${datosMvn.teeResultado} ${datosMvn.teeMapeos} ${datosMvn.teeArtefactos}"
	exec(datosMvn.maven, datosMvn.jdk, datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	return deployArtefactos(datosMvn, "SNAPSHOT", datosMvn.version, debug)
}

def release(Map datosMvn, String prepare, String perform, Boolean debug = false) {
	shgoal = "#!/bin/bash \n" + "rm -Rf ${datosMvn.artefactos}* ; ${prepare} -DcheckModificationExcludeList=${datosMvn.resultado} ${datosMvn.teeResultado}"
	//Ejecuta prepare
	exec(datosMvn.maven, datosMvn.jdk, datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	dataFromMaven = readFile file: datosMvn.resultado
	if ( !dataFromMaven ) {
		log.error("En release:prepare")
	} 
	shgoal = "#!/bin/bash \n" + "rm -Rf ${datosMvn.artefactos}* ; ${perform} ${datosMvn.teeResultado} ${datosMvn.teeMapeos} ${datosMvn.teeArtefactos}"
	exec(datosMvn.maven, datosMvn.jdk, datosMvn.mavenSettingsConfig, datosMvn.mavenOpts, shgoal, debug)
	return deployArtefactos(datosMvn, "RELEASE", datosMvn.nuevaVersion, debug)
}
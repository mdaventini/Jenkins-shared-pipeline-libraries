def extraerOSJenkins(Map datosConf, Boolean debug = false) {
	log.debug("getTargetsOSJenkins.extraerOSJenkins datosConf ${datosConf}", debug)
	//Busca desa
	vAmbiente = datosConf.jobUrl.replace(datosConf.Job, "${datosConf.Job}-copiaDesa")
	datosConf.desa = buscar(vAmbiente, "", datosConf.desa, debug)
	//Busca test
	vAmbiente = datosConf.jobUrl.replace(datosConf.Job, "${datosConf.Job}-copiaTest")
	datosConf.test = buscar(vAmbiente, "", datosConf.test, debug)
	//Busca prepro
	vAmbiente =  "prepro"
	vSource = datosConf.desa + datosConf.test
	datosConf.prepro = buscar(vAmbiente, vSource, datosConf.prepro, debug)
	//Busca prod
	vAmbiente =  "prod"
	vSource = datosConf.desa + datosConf.test + datosConf.prepro
	datosConf.prod = buscar(vAmbiente, vSource, datosConf.prod, debug)
	log.debug("getTargetsOSJenkins.extraerOSJenkins datosConf ${datosConf}", debug)
	return datosConf
}

def buscar(String Ambiente, String Source, String Targets, Boolean debug = false) {
	log.debug("getTargetsOSJenkins.buscar Ambiente ${Ambiente} Targets ${Targets}", debug)
	vTargets = Targets
	//Lee el dato Target
	//header = ['FechaReporte','JobDeploy','NServidor','NInstancia','Environment','UrlJobDeploy','FechaDeploy','TKTDeploy','StatusDeploy','UsuarioDeploy','Target','Source','OrigenNexus','Artefacto','Version']
	vScript = "cat /Appweb/jenkins/Extraer_deploys_OSJenkins*.csv | grep ',${Ambiente},' | grep '${Source}' | awk -F',' '{print \$11}' > temp"
	log.debug("getTargetsOSJenkins.buscar vScript ${vScript}", debug)
	status = sh returnStatus: true, script: vScript
	if ( status == 0 ) {
		datosTo = readFile file: 'temp'
		log.debug("datosTo tiene ${datosTo}", debug)
		//if ( datosTo ) 
		datosTo.split("\\n").each { nuevoTarget ->
			//Agrega el nuevoTarget solo si no existe
			vTargets = utils.agregar(vTargets, nuevoTarget, debug)
		}
	}
	log.debug("getTargetsOSJenkins.buscar.end vTargets ${vTargets}", debug)
	return vTargets
}
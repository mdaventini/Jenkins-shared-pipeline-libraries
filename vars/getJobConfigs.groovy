def call(String nameConfig, Boolean debug = false, Boolean FromPipeline = true) {
	log.debug("getJobConfigs Busca configs/${nameConfig}.config", debug)
	try {
		libResource = libraryResource "configs/${nameConfig}.config"
	} catch (err) {
		log.error("Al leer libraryResource configs/${nameConfig}.config: ${err}")
    }
	log.debug("Datos del Proyecto - properties leidas de config configs/${nameConfig}.config\n libResource ${libResource}", debug)
	datosConf = readProperties text: libResource
	if ( FromPipeline ) {
		datosConf.dir = (datosConf.rootPOM).minus("pom.xml")
		datosConf.deployJob = "${nameConfig}-deploy"
		datosConf.restartJob = "${nameConfig}-restart"
	}
	log.debug("getJobConfigs.datosConf ${datosConf}", debug)
	return datosConf
}
def call(String datosAmbiente, Boolean debug = false) {
	mapServer = ""
	if ( datosAmbiente ) {
		mapServer = [host: '', folder: '', instance: '', artefacto: '']
		log.debug("datosAmbiente ${datosAmbiente}", debug)
		mapServer.host = datosAmbiente.split(':')[0]
		mapServer.artefacto = datosAmbiente.split(':')[1].split('/').last()
		mapServer.folder = datosAmbiente.split(':')[1].minus(mapServer.artefacto)
		mapServer.instance = mapServer.folder.split('/deploy')[0]
		log.debug("mapServer ${mapServer}", debug)
	}
	return mapServer
}
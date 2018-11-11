def call(Map datosConf, String vFileName, Boolean debug = false) {
	log.debug("DoWriteFileFromMap datosConf:${datosConf} vFileName:${vFileName}", debug)
	//Agrega los datos al archivo con enter
	lineas = []
	datosConf.each { vdatos ->
		lineas.add("${vdatos.key}: ${vdatos.value}")
	}
	log.debug("DoWriteFileFromMap Agrega ${lineas.join('\n')} Inicia el file ${vFileName}", debug)
	writeFile file: vFileName, text: (lineas.join('\n'))
	File vFile = new File(WORKSPACE, vFileName) 
	log.debug("DoWriteFileFromMap.end", debug)
}
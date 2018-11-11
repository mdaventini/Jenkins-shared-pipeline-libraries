def call(Map data, String prefijo, List header, File vFile, Boolean debug = false) {
	log.debug("DoWriteReportLineFromMap data:${data} header:${header} vFile:${vFile}", debug)
	columnas = []
	for (i = 0; i <header.size(); i++) {
		//Aca ver que tiene data[prefijo + header[i]] para que no ponga null
		if ( !data[prefijo + header[i]] || data[prefijo + header[i]] == "null" ) {
			data[prefijo + header[i]] = " "
		}
		columnas.add(data[prefijo + header[i]])
	}
	//Escribe en el file
	log.debug("DoWriteReportLineFromMap.columnas ${columnas.join(',')}", debug)
	vFile << columnas.join(',')
	vFile << "\n"
	log.debug("DoWriteReportLineFromMap.end", debug)
}
def call(File vFile, String vOrigen, List vHeader, Map mFechasUltimosInicios, Boolean debug = false) {
	log.info("DoUnificarReportes vFile:${vFile} vOrigen:${vOrigen} vHeader:${vHeader} mFechasUltimosInicios:${mFechasUltimosInicios}" )
	//Lee el file del workspace
	vDatosFile = readFile file: vOrigen
	if ( !vDatosFile ) {
		log.error("El archivo ${vOrigen} esta vacio")
	}
	vFechaReporte = ""
	vDatosFile.split("\\\n").each { linea ->
		if ( linea.split(",")[0] == "FechaReporte" ) { //Keys
			propertiesKeys = linea.split(",")
			log.debug("propertiesKeys:${propertiesKeys}", debug)
		} else { //Values
			//Arma el el map datos usando el header propio (por si tiene datos demas o de menos o en distinto orden)
			//Este replace es para los data que estan vacios
			linea = linea.replaceAll(",",", ")
			propertiesValues = linea.split(",")
			log.debug("propertiesValues:${propertiesValues}", debug)
			datos = [:]
			for (i = 0; i <propertiesKeys.size(); i++) {
				datos[propertiesKeys[i]] = propertiesValues[i].trim()
			}
			if ( datos.FechaReporte && !vFechaReporte ) {
				vFechaReporte = datos.FechaReporte
			}
			if ( datos.NServidor && datos.NInstancia ) {
				datos.NServidor = datos.NServidor.split("@")[1]
				datos.NInstancia = datos.NInstancia.split("/").last()
				//Si hay datos de Ultimos Inicios
				if ( mFechasUltimosInicios.size() != 0 ) {
					datos.FechaUltimoInicio = mFechasUltimosInicios[datos.NServidor+"_"+datos.NInstancia]
				}
			}
			DoWriteReportLineFromMap(datos, "", vHeader, vFile, debug)
		}
	}
	//Guarda vOrigen y FechaReporte en la descripcion del Job
	currentBuild.description = "${currentBuild.description}\n Archivo: ${vOrigen} Fecha del Reporte: ${vFechaReporte} "
}
def call(Map datosConf, String vMessage, Boolean nuevo, Boolean debug = false) {
	log.debug("setJobConfigs datosConf ${datosConf} vMessage ${vMessage}", debug)
	vFileName = "${datosConf.Job}.config"
	DoWriteFileFromMap(datosConf, vFileName, debug)
	//Copia *.config a vDir/
	status = sh returnStatus: true, script: "cp ${vFileName} ${WORKSPACE}@libs/pipeline-library/resources/configs/."
	if ( status != 0 ) {
		log.error("Al ejecutar cp ${vFileName} ${WORKSPACE}@libs/pipeline-library/resources/configs/.")
	}
	svnUtils.DoAddCommitSvn("${WORKSPACE}@libs/pipeline-library/resources/configs/", "${vFileName}", vMessage, nuevo, debug)
}
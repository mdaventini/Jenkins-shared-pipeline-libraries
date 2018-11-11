def ListaJobs(Boolean debug = false) {
	log.info("getRecExecution.ListaJobs")
	//List<Item> LgetAllItems = (Jenkins.getInstance().getAllItems())
	//https://issues.jenkins-ci.org/browse/JENKINS-48638
	List<Item> LgetAllItems = (Jenkins.getActiveInstance().getAllItems())
	LgetAllItems.each { vjob -> 
		if ( !( vjob instanceof org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject || ( vjob instanceof org.jenkinsci.plugins.workflow.job.WorkflowJob && ( vjob.fullName.contains("deploy") || vjob.fullName.contains("restart") ) ) ) ) {
			log.debug("getRecExecution.ListaJobs.remove ${vjob} ${vjob.getClass()}", debug)
			LgetAllItems.remove(vjob)
		}
	}
	log.debug("getRecExecution.ListaJobs ${LgetAllItems}", debug)
	return LgetAllItems
}

def ByName(List<Item> LgetAllItems, String jobName, Boolean debug = false) {
	jobName = jobName
	log.info("getRecExecution.byName ${jobName}")
	vJobByName = ""
	LgetAllItems.each { Ljob ->
		log.debug("getRecExecution.byName.Ljob ${Ljob} getRecExecution.byName.Ljob.fullName *${Ljob.fullName}* *${jobName}*", debug)
		if ( Ljob.fullName == "${jobName}" && !vJobByName ) {
			vJobByName = Ljob
			log.debug("getRecExecution.byName.found ${vJobByName}", debug)
		}
	}
	log.debug("getRecExecution.byName.return ${vJobByName}", debug)
	return vJobByName
}

def LastToKeep(Job vJob, Boolean debug = false) {
	log.info("getRecExecution.LastToKeep ${vJob}")
	datosLastToKeep = [mapeo: '', RRevision: 0, Rnumero: 0, Rfecha: '', Rversion: '', Rurl: '', SRevision: 0, Snumero: 0, Sfecha: '', Sversion: '', Surl: '']
	vJob.getBuilds().each { veRun ->
		if ( veRun ) {
			vDesc_eRun = veRun.getDescription()
			if ( vDesc_eRun ) {
				//Compilacion sin Tests:  mapeo:pipelinedemo-frontend:finalnamepipelinedemo-frontend.war|pipelinedemo-backend:pipelinedemo-backend.war|OK
				//Compilacion sin Tests:  mapeo:pipelinedemo-frontend:finalnamepipelinedemo-frontend.war|pipelinedemo-backend:pipelinedemo-backend.war| SUCCESS
				if ( ( vDesc_eRun.contains("SUCCESS") && vDesc_eRun.contains("Compilacion sin Tests:  mapeo:") ) && !datosLastToKeep.mapeo ) {
					datosLastToKeep.mapeo = ((vDesc_eRun.split("Compilacion sin Tests:  mapeo:")[1].split("\\n")[0]).replaceAll("\\|OK","\\| OK")).split("\\| ")[0]
					log.debug("getRecExecution.LastToKeep.datosLastToKeep.mapeo ${datosLastToKeep.mapeo}",debug)
				}
				if ( ( vDesc_eRun.contains("SUCCESS") && vDesc_eRun.contains("Genera RELEASE version") && vDesc_eRun.contains("LastRevision") ) && datosLastToKeep.Rnumero == 0 ) {
					//Muestra tooooda la ejecucion + log + properties
					//log.debug("getRecExecution.LastToKeep.RELEASE ${veRun.getProperties()}", debug)
					log.debug("getRecExecution.LastToKeep.RELEASE ${veRun}", debug)
					datosLastToKeep.RRevision = (vDesc_eRun.split("LastRevision:")[1].split("\\n")[0]).toInteger()
					datosLastToKeep.Rnumero = veRun.getNumber()
					datosLastToKeep.Rfecha = veRun.getTime().toLocaleString()
					datosLastToKeep.Rversion = vDesc_eRun.split("Genera RELEASE version")[1].split(":")[0]
					datosLastToKeep.Rurl = veRun.getAbsoluteUrl()
				}
				if ( ( vDesc_eRun.contains("SUCCESS") && vDesc_eRun.contains("Genera SNAPSHOT version") && vDesc_eRun.contains("LastRevision") ) && datosLastToKeep.Snumero == 0 ) {
					log.debug("getRecExecution.LastToKeep.SNAPSHOT ${veRun}", debug)
					//log.debug("getRecExecution.LastToKeep.vJob.veRun.vDesc_eRun ${vDesc_eRun}", debug)
					datosLastToKeep.SRevision = (vDesc_eRun.split("LastRevision:")[1].split("\\n")[0]).toInteger()
					datosLastToKeep.Snumero = veRun.getNumber()
					datosLastToKeep.Sfecha = veRun.getTime().toLocaleString()
					datosLastToKeep.Sversion = vDesc_eRun.split("Genera SNAPSHOT version")[1].split(":")[0]
					datosLastToKeep.Surl = veRun.getAbsoluteUrl()
				}
			}
		}
	}
	log.debug("getRecExecution.LastToKeep.datosLastToKeep ${datosLastToKeep} ", debug)
	return datosLastToKeep
}
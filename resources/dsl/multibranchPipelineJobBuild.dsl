 multibranchPipelineJob('rNOMBREJOB') {
	description ('Multibranch Pipeline para el proyecto rNOMBREJOB (migrado de rJOBURL) (anterior rOLDJOB) \n SCAN: Se ejecuta automaticamente por modificaciones en la configuracion\n IC: Se ejecuta automaticamente por trigger\n AC: Se ejecuta diariamente para el branch indicado en el config (ej: branchAC: trunk)\n BD: Permite al usuario generar version SNAPSHOT para desplegar en DESA y continuar a RD\n RD: Permite al usuario generar versionRELEASE para desplegar en DESA y TEST ')
	branchSources {
		branchSource {
			source {
				subversionSCMSource {
					id('rUUID')
					remoteBase('rSVNURL')
					credentialsId('svnjenkins')
					excludes('')
					includes('rSVNINCLUDES')
				}
			}
		}
	}
	factory {
		workflowBranchProjectFactory {
			scriptPath('rSCRIPTPATH')
		}
	}
}
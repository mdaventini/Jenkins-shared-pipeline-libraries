pipelineJob('rNOMBREJOB-deploy') {
	description ('Ejecuta deploy de un artefacto al ambiente seleccionado entre: desa, test, prepro y prod (Filtrado por permisos), para el proyecto rPROYNOMBREJOB \n Se ejecuta automaticamente desde el pipeline de Build para BD (SNAPSHOT) y RD (RELEASE) \n Permite ejecucion MANUAL a demanda del usuario\n  NOTA: Da error cuando el archivo de configuraciones no tiene la definición para el ambiente seleccionado ')
	properties {
		parameters {
			parameterDefinitions {
				extensibleChoiceParameterDefinition {
					name('Ambiente')
					editable(false)
					description('Ambiente destino. Valores posibles: desa, test, prepro, prod (Filtrado por permisos)')
					choiceListProvider {
						textareaChoiceListProvider {
							choiceListText('rDESTINOS')
							defaultChoice('desa')
							addEditedValue(false)
							whenToAdd('Triggered')
						}
					}
				}
				extensibleChoiceParameterDefinition {
					name('Version')
					editable(false)
					description('[VERSION]:[Ejecucion que genero los artefactos]')
					choiceListProvider {
						systemGroovyChoiceListProvider {
							groovyScript {
								script('''def root = new XmlParser().parseText("rNEXUS_SERVER/service/local/lucene/search?g=ar.com.company&a=${project.getDisplayName().minus("GAS-").minus("-deploy")}&p=txt".toURL().text)
versiones = []
root.data.artifact.each { it ->
	it.artifactHits.artifactHit.artifactLinks.artifactLink.classifier.each { cit ->
		versiones.add("${it.version.text()}:${cit.text()}")
	}
}
return versiones''')
								sandbox(false)
								classpath {}
							}
							defaultChoice('')
							usePredefinedVariables(true)
						}
					}
				}
				booleanParam {
					name('Restart')
					defaultValue(true)
					description('Restart AMBIENTE')
				}
				stringParam {
					name('Ticket')
					defaultValue('')
					description('Ticket que ocasiona el deploy (obligatorio para prepro y prod)')
					trim(true)
				}
			}
		}
	}
	definition { 
		cps {
			script("PipelineDeploy()")
			sandbox(true)
        }
	}
}
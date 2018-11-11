pipelineJob('rNOMBREJOB-restart') {
	description ('Ejecuta restart del ambiente seleccionado entre: desa, test, prepro y prod (Filtrado por permisos) para el proyecto rPROYNOMBREJOB \n Se ejecuta automaticamente desde el pipeline de deploy \n Permite ejecucion MANUAL a demanda del usuario ')
	properties {
		parameters {
			parameterDefinitions {
				extensibleChoiceParameterDefinition {
					name('Ambiente')
					editable(false)
					description('Valores posibles: desa, test, prepro, prod (Filtrado por permisos)')
					choiceListProvider {
						textareaChoiceListProvider {
							choiceListText('rDESTINOS')
							defaultChoice('desa')
							addEditedValue(false)
							whenToAdd('Triggered')
						}
					}
				}
				stringParam {
					name('Ticket')
					defaultValue('')
					description('Ticket que ocasiona el restart (obligatorio para prepro y prod)')
					trim(true)
				}
			}
		}
	}
	definition { 
		cps {
			script("PipelineRestart()")
			sandbox(true)
        }
	}
}
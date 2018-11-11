// Library utils
def agregar(String actual, String nuevo, Boolean debug = false) {
	//No pone los repetidos
	log.debug("utils.agregar actual ${actual} nuevo ${nuevo}", debug)
	if ( !actual.contains(nuevo) ) {
		actual = actual + nuevo + "|"
	}
	log.debug("utils.agregar.end actual ${actual}", debug)
	return actual
}
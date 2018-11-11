// Library log
def info(String message) {
	echo "[PIPELINEINFO] ${message}"
}

def warning(String message) {
	echo "[PIPELINEINFO][WARNING] ${message}"
}

def debug(String message, Boolean debug = false) {
	if ( debug ) {
		echo "[PIPELINEDEBUG] ${message}"
	}
}

def error(String message) {
	echo "[PIPELINEERROR] ${message}"
	currentBuild.description = "${currentBuild.description}\n ERROR! ${message}"
	error message: "${message}"
}
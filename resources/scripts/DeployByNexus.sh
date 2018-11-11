#!/bin/bash
#----------------------------------------------------------------------------
# Hace deploy de un artefacto bajado de nexus a un app server
# Este script se ejecuta directamente en el app server dado que es llamado con 
#	ssh ${usrHost_Desa} 'bash -s' < [path]/DeployByNexusu.sh ${URL} ${DESTINO} desde [path]/JenkinsDeploy.sh 
# Recibe 
#	URL: url del artefacto en Nexus
#   ARTEFACTO: nombre del artefacto en el servidor
#	DESTINO: path deploy 
#	BACKUP: si es <> "" hace backup del artefacto anterior
# Se basa en la estructura standard existente en los servidores jboss
# Deploy Path: /Appweb/jboss-[version]/server/[instancia]/[deployments o deploy]/[war]
# Backup de wars: /Appweb/jboss-[version]/server/[instancia]/[deployments o deploy]/../backup/[oldwar]
# Script start: /Appweb/jboss-[version]/bin/[???]/[instancia]-start.sh; 
# server.log: /Appweb/jboss-[version]/server/[instancia]/log/server.log
#----------------------------------------------------------------------------
#Parametros
URL="$1"
ARTEFACTO="$2"
DESTINO="$3"
BACKUP="$4"

#Esta funcion informa los valores posibles para URL
URL_info (){
	echo "Debe indicar una URL existente en el repositorio Nexus"
}

#Esta funcion informa los valores posibles para ARTEFACTO
ARTEFACTO_info (){
	echo "Debe indicar nombre del artefacto en el servidor"
}
	
#Esta funcion informa los valores posibles para DESTINO
DESTINO_info (){
	echo "Debe indicar un path existente en el app server destino"
}

#Esta funcion informa los valores posibles para BACKUP
BACKUP_info (){
	echo "Indicar un caracter distinto de vacio para hacer backup"
}

#main
echo off
echo "[INFO] Comienza DeployByNexus.sh"
#Valida parametros recibidos
if [ $# -lt 3 ]
then
    echo "[ERROR] Wrong arguments specified."
	echo "Se debe indicar URL ARTEFACTO DESTINO BACKUP"
	URL_info
	ARTEFACTO_info
	DESTINO_info
	BACKUP_info
	exit 1
fi

echo [INFO] Datos recibidos: URL=$URL - ARTEFACTO=$ARTEFACTO - DESTINO=$DESTINO - BACKUP=$BACKUP

echo [INFO] - $PWD

#Valida que se pueda hacer wget de la URL
wget --spider --no-check-certificate -q $URL 
if [ $? -ne 0 ]
then 
	echo "[ERROR] - [URL] $URL no existe o no se puede acceder desde este servidor."
	exit 1
fi

#Valida que exista el DESTINO
if [ -d "$DESTINO" ] ; then
	#/Appweb/jboss-4.3/server/OSIntegraBPM/deploy/
	SERVER_AT_HOST=$DESTINO/
	#/Appweb/jboss-4.3/server/OSIntegraBPM/deploy/
	INST_AT_HOST=`echo $DESTINO | cut -d/ -f5`
	#OSIntegraBPM
	if [ ! -f "$SERVER_AT_HOST$ARTEFACTO" ] ; then
		# Si no existe puede ser porque es un nuevo deploy y no existe el war en destino
		echo "[WARNING] - [DESTINO] $SERVER_AT_HOST$ARTEFACTO no existe, se asume que es un nuevo artefacto."
		#exit 3
	fi
else
	# Si no existe puede ser porque es un nuevo server de deploy
	echo "[ERROR] - [DESTINO] $DESTINO no existe."
	exit 2
fi

#Si se indica BACKUP <> "" copia el artefacto anterior a la carpeta ../backup/
if [ "$BACKUP" != "" ]; then
	#Verifica que exista la carpeta ../backup
	if [ -d "../backup" ] ; then
		#Si no existe la crea
		mkdir ../backup
	fi
	echo "### Copia el artefacto anterior a la carpeta $SERVER_AT_HOST../backup/"
	cp  $SERVER_AT_HOST$ARTEFACTO $SERVER_AT_HOST../backup/$ARTEFACTO"_"$(date +"%Y%m%d_%H%M%S")
fi

#Hace el wget al destino indicado
echo "### Obtiene el artefacto $URL en $SERVER_AT_HOST$ARTEFACTO"
wget -q --no-check-certificate $URL -O $SERVER_AT_HOST$ARTEFACTO

echo "[INFO] Final DeployByNexus.sh"
exit 
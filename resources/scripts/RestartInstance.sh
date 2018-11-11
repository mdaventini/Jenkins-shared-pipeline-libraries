#!/bin/bash
#----------------------------------------------------------------------------
# Hace restart en un app server
# Este script se ejecuta directamente en el app server dado que es llamado con 
#	ssh ${usrHost_Desa} 'bash -s' < [path]/RestartInstance.sh ${INSTANCE} [path]/JenkinsRestart.sh 
# Recibe 
#	INSTANCE: path de la instancia
# Se basa en la estructura standard existente en los servidores jboss
# Deploy Path: /Appweb/jboss-[version]/server/[instancia]/[deployments o deploy]/[war]
# Backup de wars: /Appweb/jboss-[version]/server/[instancia]/[deployments o deploy]/../backup/[oldwar]
# Script start: /Appweb/jboss-[version]/bin/[???]/[instancia]-start.sh; 
# server.log: /Appweb/jboss-[version]/server/[instancia]/log/server.log
#----------------------------------------------------------------------------
#Parametros
INSTANCE="$1"

#Esta funcion informa los valores posibles para INSTANCE
INSTANCE_info (){
	echo "[INFO] Debe indicar un path existente en el app server destino"
}

#main
echo off
echo "[INFO] Comienza RestartInstance.sh"
#Valida parametros recibidos
if [ $# -lt 1 ]
then
    echo "[ERROR] RestartInstance.sh-1=Wrong arguments specified."
	echo "[ERROR] Se debe indicar INSTANCE"
	INSTANCE_info
	exit 1
fi

echo [INFO] Datos recibidos: INSTANCE=$INSTANCE
#Valida que exista el path a INSTANCE
if [ ! -d "$INSTANCE/" ] ; then
	# Si no existe puede ser porque es un nuevo server 
	echo "[ERROR] RestartInstance.sh-2=[INSTANCE] $INSTANCE no existe."
	exit 2
fi

echo [INFO] Parsear $INSTANCE
#/Appweb/jboss-x/server/VRSDesa
NOMBRE_INSTANCIA=`basename $INSTANCE`
#VRSDesa
SERVER_INSTANCIA=`echo $INSTANCE | awk -F 'server' '{print $1}'`
#/Appweb/jboss-x/
START_INSTANCIA=`find $SERVER_INSTANCIA"bin" -name $NOMBRE_INSTANCIA"-start.sh"`
#/Appweb/jboss-x/bin/[???]/VRSDesa-start.sh
LOG_INSTANCIA=`echo $SERVER_INSTANCIA"server/"$NOMBRE_INSTANCIA"/log/server.log"`
#/Appweb/jboss-x/server/VRSDesa/log/server.log

#https://www.freebsd.org/cgi/man.cgi?query=pgrep&sektion=1
#INSTPID=`pgrep -a "$INSTANCE"`
#INSTDESC_PSGREP=`pgrep -af "$INSTANCE"`

#Sin la opcion -a porque no esta en todos los servidores
INSTPID=`pgrep -f ".*java.*$INSTANCE.*"`
INSTDESC_PSGREP=`pgrep -fl ".*java.*$INSTANCE.*"`

echo "[INFO] INSTDESC_PSGREP=$INSTDESC_PSGREP"

if [ -f $START_INSTANCIA ]; then
	echo "[INFO] Existe $START_INSTANCIA"
	echo "[INFO] INSTPID=$INSTPID"
	if [ ! -z $INSTPID ]; then
		echo "[INFO] Matando el proceso $INSTPID de la instancia $INSTANCE"
		kill -9 $INSTPID
		sleep 5
	else
		echo "[INFO] No se estaba ejecutando la instancia $INSTANCE"
	fi
    $START_INSTANCIA > /dev/null 2>&1
    sleep 5
    INSTPID=`pgrep -f ".*java.*$INSTANCE.*"`
    echo "[INFO] Iniciado el proceso $INSTPID para la instancia $INSTANCE"
	sleep 15s 
	tail -n 100 $LOG_INSTANCIA
else
	echo "[ERROR] RestartInstance.sh-3=NO EXISTE $START_INSTANCIA"
	exit 3
fi

echo "[INFO] Final RestartInstance.sh"
exit 
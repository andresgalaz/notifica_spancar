#!/bin/bash

# Se posiciona en la ruta donde está la SHELL
RUTA=`dirname "$0"`
if [ $RUTA != "." ] ; then
	cd $RUTA
fi

if [ "$1" ] ; then
	WSAPI_AMBIENTE="$1"
	export WSAPI_AMBIENTE
fi

echo Inicio $(date)
java -cp notifica.jar:lib/* snapCar.notif.Principal
echo Fin $(date)
echo ==============================================================================

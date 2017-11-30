#!/bin/bash

# Se posiciona en la ruta donde está la SHELL
RUTA=`dirname "$0"`
if [ $RUTA != "." ] ; then
    cd $RUTA
fi

echo Inicio $(date)
java -cp notifica.jar:lib/* snapCar.notif.Principal
echo Fin $(date)
echo ==============================================================================

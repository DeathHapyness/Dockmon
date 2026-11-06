#!/bin/sh
# Reativa todos os apps desativados no debloat. Uso: ./restaurar-apps.sh [caminho/do/adb]
ADB=${1:-adb}
DIR=$(dirname "$0")
while read -r p; do "$ADB" shell pm enable "$p" </dev/null; done < "$DIR/apps-desativados.txt"
"$ADB" shell 'settings put global window_animation_scale 1; settings put global transition_animation_scale 1; settings put global animator_duration_scale 1'

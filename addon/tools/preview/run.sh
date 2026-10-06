#!/usr/bin/env bash
# Renders the ClickGUI of a jar with Java2D (no Minecraft needed) and writes one PNG per theme.
# usage: run.sh <jar> <out dir> [theme]     (needs the stubs from patch-gui.sh: WORK=build/gui)
set -euo pipefail
cd "$(dirname "$0")"
JAR="$(realpath "$1")"
OUT="$(realpath -m "$2")"
THEME="${3:-}"
WORK="${WORK:-$PWD/../../build/gui}"
CACHE="${CACHE:-$PWD/../../.cache}"
LIBS="$(ls "$CACHE"/*.jar | tr '\n' ':')"
mkdir -p "$WORK/preview"
javac -proc:none -nowarn -d "$WORK/preview" -cp "$WORK/stubs:$JAR:$LIBS" $(find fake dev -name '*.java') 2>&1 | grep -v '^Picked\|^Note' || true
java -Djava.awt.headless=true -cp "$WORK/preview:$WORK/stubs:$JAR:$LIBS" dev.dihclient.preview.Preview "$OUT" $THEME 2>&1 | grep -v '^Picked'

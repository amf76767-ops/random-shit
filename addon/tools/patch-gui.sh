#!/usr/bin/env bash
# Rebuilds the GUI classes (themes, floating ClickGUI) and puts them into an existing DIHClient jar.
# usage: patch-gui.sh <input.jar> <output.jar> <version>
# Needs JDK 21, python3 and curl (libraries come from Maven Central, cached in $CACHE).
set -euo pipefail
cd "$(dirname "$0")/.."
IN="$(realpath "$1")"
OUT="$(realpath -m "$2")"
VERSION="$3"
CACHE="${CACHE:-$PWD/.cache}"
WORK="${WORK:-$PWD/build/gui}"
mkdir -p "$CACHE"
rm -rf "$WORK"
mkdir -p "$WORK/gen" "$WORK/stubs" "$WORK/classes"

mvn_get() {
  local f="$CACHE/$2-$3.jar"
  local i
  for i in 1 2 3 4 5; do
    [ -s "$f" ] && break
    curl -fsSL -o "$f" "https://repo1.maven.org/maven2/$(echo "$1" | tr . /)/$2/$3/$2-$3.jar" || sleep $((i * 3))
  done
  [ -s "$f" ] || { echo "cannot download $2-$3" >&2; exit 1; }
  echo "$f"
}
LIBS=""
for coord in com.google.code.gson:gson:2.11.0 org.slf4j:slf4j-api:2.0.13 org.joml:joml:1.10.8 it.unimi.dsi:fastutil:8.5.15 \
  com.google.guava:guava:33.3.1-jre org.apache.commons:commons-lang3:3.17.0 org.lwjgl:lwjgl:3.3.3 org.lwjgl:lwjgl-glfw:3.3.3 \
  org.lwjgl:lwjgl-opengl:3.3.3 org.lwjgl:lwjgl-stb:3.3.3 net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7 \
  org.apache.logging.log4j:log4j-api:2.24.1; do
  IFS=: read -r g a v <<<"$coord"
  LIBS="$LIBS:$(mvn_get "$g" "$a" "$v")"
done

python3 tools/GenStubs.py "$IN" tools/stubs-hints.txt "$WORK/gen" >/dev/null
python3 tools/stub-fixups.py "$WORK/gen/net/minecraft"
if ! javac -nowarn -cp "$CACHE/joml-1.10.8.jar" -d "$WORK/stubs" $(find stubs "$WORK/gen" -name '*.java') >"$WORK/stubs.log" 2>&1; then
  grep -v '^Picked' "$WORK/stubs.log" >&2
  exit 1
fi

SRC=src/override/java/dev/dihclient
if ! javac -proc:none -nowarn -Xlint:none -d "$WORK/classes" -cp "$WORK/stubs:$IN$LIBS" \
  $SRC/gui/theme/Skin.java $SRC/gui/theme/Theme.java $SRC/modules/client/ClickGui.java \
  $SRC/render/Gfx.java $SRC/gui/MeteorGuiScreen.java $SRC/hud/HudStyle.java >"$WORK/compile.log" 2>&1; then
  grep -v '^Picked' "$WORK/compile.log" >&2
  echo "compile failed" >&2
  exit 1
fi

python3 tools/inject-classes.py "$IN" "$OUT" "$WORK/classes" "$VERSION"

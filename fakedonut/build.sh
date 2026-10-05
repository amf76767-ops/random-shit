#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-0.1.0}"
MC_INT="${MC_INT:-/tmp/mc-int.jar}"
LIBS="${FD_LIBS:-/tmp/fd/libs}"
CACHE=../addon/.cache
OUT=../dist/fakedonut-${VERSION}.jar
BUILD=build
rm -rf "$BUILD"; mkdir -p "$BUILD/classes" ../dist
CP="$MC_INT:$(ls $LIBS/*.jar | tr '\n' ':')$(ls $CACHE/*.jar | tr '\n' ':')"
javac -proc:none -nowarn -Xlint:none -d "$BUILD/classes" -cp "$CP" $(find src/main/java -name '*.java')
sed "s/\${version}/$VERSION/" src/main/resources/fabric.mod.json > "$BUILD/classes/fabric.mod.json"
cp src/main/resources/fakedonut.mixins.json "$BUILD/classes/"
java -Dmixin.package=dev/fakedonut/mixin/ -cp "$CACHE/asm-9.7.jar:$CACHE/asm-tree-9.7.jar" ../addon/tools/MixinTargets.java "$BUILD/classes" "$MC_INT" "$LIBS/fabric-loader.jar"
(cd "$BUILD/classes" && jar --create --file "../../$OUT" .)
echo "built $OUT"

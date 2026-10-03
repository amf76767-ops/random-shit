#!/usr/bin/env bash
# Builds dist/dihclient-v<version>+mc1.21.11.jar from base/dihclient-v5.6.jar plus the code in addon/src/main.
# Needs: JDK 21, curl. Downloads gson, slf4j-api and ASM from Maven Central into addon/.cache on first run.
set -euo pipefail
cd "$(dirname "$0")"
VERSION="${1:-5.7.0}"
MC="1.21.11"
CACHE=.cache
BUILD=build
mkdir -p "$CACHE" "$BUILD"

mvn_get() { # group artifact version
  local f="$CACHE/$2-$3.jar"
  [ -s "$f" ] || curl -fsSL -o "$f" "https://repo1.maven.org/maven2/$(echo "$1" | tr . /)/$2/$3/$2-$3.jar"
  echo "$f"
}
GSON=$(mvn_get com.google.code.gson gson 2.11.0)
SLF4J=$(mvn_get org.slf4j slf4j-api 2.0.13)
MIXIN=$(mvn_get net.fabricmc sponge-mixin "0.17.3+mixin.0.8.7")
ASM=$(mvn_get org.ow2.asm asm 9.7):$(mvn_get org.ow2.asm asm-tree 9.7)
BASE=../base/dihclient-v5.6.jar

rm -rf "$BUILD/stubs" "$BUILD/gen" "$BUILD/classes" "$BUILD/override" "$BUILD/tools" "$BUILD/test"
mkdir -p "$BUILD/stubs" "$BUILD/gen" "$BUILD/classes" "$BUILD/override" "$BUILD/tools" "$BUILD/test"

# 1. stand-ins for the Minecraft classes the old jar uses (generated from its bytecode) and for Fabric (never shipped)
JOML=$(mvn_get org.joml joml 1.10.8)
python3 tools/GenStubs.py "$BASE" tools/stubs-hints.txt "$BUILD/gen"
javac -nowarn -cp "$JOML" -d "$BUILD/stubs" $(find stubs "$BUILD/gen" -name '*.java')
# 1b. rewritten modules: same class names as in the old jar, they replace the old classes
if [ -d src/override ]; then
  javac -proc:none -nowarn -Xlint:none -d "$BUILD/override" -cp "$BASE:$BUILD/stubs:$GSON:$SLF4J:$MIXIN:$ASM:$JOML" $(find src/override -name '*.java')
fi
# 2. the add-on itself, compiled against the old jar
javac -proc:none -nowarn -Xlint:none -d "$BUILD/classes" -cp "$BUILD/override:$BASE:$BUILD/stubs:$GSON:$SLF4J:$MIXIN:$ASM:$JOML" $(find src/main -name '*.java')
# 3. tests that need no Minecraft
javac -nowarn -d "$BUILD/test" -cp "$BUILD/classes:$GSON" $(find src/test -name '*.java')
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.UpdateTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.SupervisorTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.SoftKeysTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.LegalPlaceTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.Model3dTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.NavTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.MergeTests
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.EmoteTests
python3 ../resourcepack/build.py >/dev/null
java -cp "$BUILD/test:$BUILD/classes:$GSON" dev.dihclient.PackTests
# 4. patch + pack
javac -nowarn -d "$BUILD/tools" -cp "$ASM" tools/Patcher.java tools/MixinCheck.java
OUT="../dist/dihclient-v${VERSION}+mc${MC}.jar"
python3 ../resourcepack/build.py >/dev/null
java -cp "$BUILD/tools:$ASM" Patcher "$BASE" "$BUILD/classes" "$OUT" "${VERSION}+mc${MC}" ../dist/DIH-Visuals-1.21.11.zip "$BUILD/override" src/resources
# 5. the base jar must show the known problem, the new jar must not have any
if java -cp "$BUILD/tools:$ASM" MixinCheck "$BASE" >/dev/null; then echo "note: base jar has no mixin problem any more"; fi
java -cp "$BUILD/tools:$ASM" MixinCheck "$OUT"
echo "built $OUT"

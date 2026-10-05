#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
C=.cache
EX=""
for j in $C/netty-*.jar $C/guava-*.jar $C/fastutil-*.jar $C/commons-lang3-*.jar $C/log4j-api-*.jar $C/lwjgl*.jar; do EX="$EX:$j"; done
OUT=$(mktemp -d)
javac -proc:none -nowarn -Xlint:none -d "$OUT" \
  -cp "${MC_INT:-/tmp/mc-int.jar}:build/override:build/classes:../base/dihclient-v5.6.jar:build/stubs:$C/gson-2.11.0.jar:$C/slf4j-api-2.0.13.jar:$C/sponge-mixin-0.17.3+mixin.0.8.7.jar:$C/asm-9.7.jar:$C/asm-tree-9.7.jar:$C/joml-1.10.8.jar$EX" "$@"
echo "ok ($# files)"; rm -rf "$OUT"

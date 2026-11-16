#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/core-tests"
mkdir -p "$OUT"
# All dependencies are standard JDK classes; no SDK, Gradle, network or JUnit needed.
find "$ROOT/app/src/main/java/dev/graywake/core" "$ROOT/app/src/test/java/dev/graywake/core" \
  -name '*.java' -print > "$OUT/sources.txt"
javac --release 17 -Xlint:all -Werror -d "$OUT" @"$OUT/sources.txt"
java -ea -cp "$OUT" dev.graywake.core.CoreTests

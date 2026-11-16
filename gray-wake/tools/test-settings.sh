#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
OUT="$ROOT/build/settings-tests"
mkdir -p "$OUT"
find "$ROOT/tools/fakes" "$ROOT/app/src/main/java/dev/graywake/core" -name '*.java' -print > "$OUT/sources.txt"
printf '%s\n' "$ROOT/app/src/main/java/dev/graywake/GrayscaleSettings.java" >> "$OUT/sources.txt"
javac --release 17 -Xlint:all -Werror -d "$OUT" @"$OUT/sources.txt"
java -ea -cp "$OUT" dev.graywake.SettingsTests

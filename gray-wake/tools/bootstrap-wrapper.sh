#!/bin/sh
# Fetch only the official Gradle 8.13 wrapper and verify it before execution.
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
EXPECTED=81a82aaea5abcc8ff68b3dfcb58b3c3c429378efd98e7433460610fecd7ae45f
URL=https://raw.githubusercontent.com/gradle/gradle/v8.13.0/gradle/wrapper/gradle-wrapper.jar
hash_file() {
    if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
    elif command -v shasum >/dev/null 2>&1; then shasum -a 256 "$1" | awk '{print $1}'
    else echo 'Install sha256sum or shasum to verify the Gradle wrapper.' >&2; exit 1
    fi
}
if [ -f "$JAR" ]; then
    if [ "$(hash_file "$JAR")" = "$EXPECTED" ]; then exit 0; fi
    echo "Refusing an unverified wrapper: $JAR. Remove it and retry." >&2
    exit 1
fi
if [ "${GRAY_WAKE_NO_DOWNLOAD:-0}" = 1 ]; then
    echo 'Gradle wrapper is not cached, and downloads are disabled.' >&2
    exit 1
fi
mkdir -p "$(dirname "$JAR")"
TEMP=$(mktemp "$JAR.part.XXXXXX")
trap 'rm -f "$TEMP"' EXIT HUP INT TERM
printf '%s\n' 'Downloading the official Gradle wrapper (checksum pinned)…' >&2
if command -v curl >/dev/null 2>&1; then
    curl --fail --location --proto '=https' --tlsv1.2 --retry 2 --connect-timeout 15 \
        --max-time 120 --output "$TEMP" "$URL"
elif command -v wget >/dev/null 2>&1; then
    wget --https-only --timeout=60 --output-document="$TEMP" "$URL"
else
    echo 'Install curl or wget, then run ./gradlew again.' >&2
    exit 1
fi
if [ "$(hash_file "$TEMP")" != "$EXPECTED" ]; then
    echo 'Gradle wrapper checksum mismatch. Nothing was executed.' >&2
    exit 1
fi
mv "$TEMP" "$JAR"

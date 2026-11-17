#!/usr/bin/env bash
# Builds the release APK (R8, debug-signed) from scratch -> dist/cherrypicked.apk
set -euo pipefail
cd "$(dirname "$0")"
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
[ -n "${JAVA_HOME:-}" ] || { [ -d /usr/lib/jvm/java-25-openjdk ] && export JAVA_HOME=/usr/lib/jvm/java-25-openjdk; } || true # else gradle finds java on PATH
rm -rf app/build
./gradlew --no-daemon assembleRelease
mkdir -p dist
cp app/build/outputs/apk/release/app-release.apk dist/cherrypicked.apk
ls -l dist/cherrypicked.apk

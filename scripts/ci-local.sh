#!/usr/bin/env bash
# The whole check, the same on a dev machine and on CI: unit tests, lint, release build.
set -euo pipefail
cd "$(dirname "$0")/.."
export ANDROID_HOME="${ANDROID_HOME:-$HOME/Android/Sdk}"
./gradlew --no-daemon testDebugUnitTest lintDebug assembleRelease

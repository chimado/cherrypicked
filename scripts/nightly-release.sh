#!/usr/bin/env bash
# Publishes the checked-out commit as a GitHub pre-release, for the machine that watches the nightly branch.
# Needs the release key (see README, Build) and a signed-in gh. Keeps the five newest nightlies.
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f "$HOME/.config/cherrypicked-release/keystore.properties" ] || { echo "no release key here: not publishing"; exit 1; }
tag="v$(grep -oP '^cherrypicked.version=\K.*' gradle.properties)-nightly.$(git rev-list --count HEAD)"
if gh release view "$tag" >/dev/null 2>&1; then echo "$tag is already published"; exit 0; fi
./build.sh -Pnightly
gh release create "$tag" dist/cherrypicked.apk --prerelease --target "$(git rev-parse HEAD)" --title "Nightly ${tag##*.}" \
    --notes "Test build of the nightly branch at $(git rev-parse --short HEAD). It may be broken; the latest release is the one to install."
gh release list --limit 100 --json tagName,isPrerelease --jq '.[] | select(.isPrerelease and (.tagName | test("-nightly\\."))) | .tagName' |
    tail -n +6 | xargs -r -n1 gh release delete --yes --cleanup-tag

#!/bin/bash
# Builds the bundle to upload to Play, signed with the upload key from GCP Secret Manager (see
# scripts/make-upload-key.sh). The key and its password are fetched into a private temporary folder
# for the build and deleted after it, whatever happens.
#
# Run:  scripts/bundle-release.sh   → app/build/outputs/bundle/release/app-release.aab
# Raise versionCode in app/build.gradle.kts first: Play refuses a code it has already seen.
set -euo pipefail
cd "$(dirname "$0")/.."

PROJECT="${UPLOAD_KEY_PROJECT:-symbio-shared}"
KEY_SECRET="${UPLOAD_KEY_SECRET:-my-receipts-upload-key}"

work=$(mktemp -d)
chmod 700 "$work"
key="$work/upload.jks"
cleanup() {
    rm -f "$key"
    rmdir "$work" 2>/dev/null || true
}
trap cleanup EXIT

gcloud secrets versions access latest --secret "$KEY_SECRET" --project "$PROJECT" --out-file "$key"
RELEASE_STOREPASSWORD=$(gcloud secrets versions access latest --secret "$KEY_SECRET-password" --project "$PROJECT")
export RELEASE_STOREFILE="$key" RELEASE_STOREPASSWORD

./gradlew bundleRelease

bundle=app/build/outputs/bundle/release/app-release.aab
# jarsigner exits 0 for an unsigned file too: read what it says
if ! jarsigner -verify "$bundle" | grep -q "jar verified"; then echo "The bundle isn't signed." >&2; exit 1; fi
echo "Signed: $bundle (versionCode $(grep -oE 'versionCode = [0-9]+' app/build.gradle.kts | grep -oE '[0-9]+'))"

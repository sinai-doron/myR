#!/bin/bash
# Makes My Receipts' Play upload key, once, and keeps it in exactly two places: two secrets in GCP
# Secret Manager (the key file and its password), and the owner's Strongbox database (an entry with
# the password and the file attached, done by hand while this script waits). While it waits, the key
# file is in ~/Downloads, so Strongbox's attach dialog finds it; it is deleted from there once you
# press Enter (or if the script stops early). No copy stays on disk.
#
# The password is typed at a prompt and passed to keytool and gcloud through the environment and a
# pipe, never as an argument, so it is in neither the shell history nor the process list.
#
# Run it in a terminal (it asks questions):  scripts/make-upload-key.sh
# Needs: keytool (from a JDK), gcloud signed in as someone allowed to create secrets in the project.
set -euo pipefail

PROJECT="${UPLOAD_KEY_PROJECT:-symbio-shared}"
KEY_SECRET="${UPLOAD_KEY_SECRET:-my-receipts-upload-key}"
PASSWORD_SECRET="$KEY_SECRET-password"

for secret in "$KEY_SECRET" "$PASSWORD_SECRET"; do
    if gcloud secrets describe "$secret" --project "$PROJECT" >/dev/null 2>&1; then
        echo "The secret $secret already exists in $PROJECT: there is an upload key already. Stopping." >&2
        exit 1
    fi
done

work="$HOME/Downloads"
key="$work/my-receipts-upload.jks"
if [ -e "$key" ]; then echo "$key already exists. Move it away first. Nothing was made." >&2; exit 1; fi
cleanup() {
    rm -f "$key"
}
# Readable by you only, from the moment it is written.
umask 077
trap cleanup EXIT

read -r -s -p "Password for the new upload key (at least 12 characters): " UPLOAD_KEY_PASSWORD; echo
read -r -s -p "Again: " again; echo
if [ "$UPLOAD_KEY_PASSWORD" != "$again" ]; then echo "The two passwords differ. Nothing was made." >&2; exit 1; fi
if [ "${#UPLOAD_KEY_PASSWORD}" -lt 12 ]; then echo "Too short. Nothing was made." >&2; exit 1; fi
unset again
export UPLOAD_KEY_PASSWORD

keytool -genkeypair -keystore "$key" -storetype PKCS12 -alias upload \
    -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=neonsheep" \
    -storepass:env UPLOAD_KEY_PASSWORD -keypass:env UPLOAD_KEY_PASSWORD >/dev/null
keytool -list -keystore "$key" -storepass:env UPLOAD_KEY_PASSWORD >/dev/null
echo "Made the key. Its certificate fingerprint (Play shows the same one):"
keytool -list -v -keystore "$key" -storepass:env UPLOAD_KEY_PASSWORD | grep -E "SHA256:" | head -1

gcloud secrets create "$KEY_SECRET" --project "$PROJECT" --replication-policy automatic \
    --labels app=my-receipts,purpose=play-upload-key --data-file "$key" >/dev/null
printf '%s' "$UPLOAD_KEY_PASSWORD" | gcloud secrets create "$PASSWORD_SECRET" --project "$PROJECT" \
    --replication-policy automatic --labels app=my-receipts,purpose=play-upload-key --data-file - >/dev/null
echo "Saved to Secret Manager in $PROJECT: $KEY_SECRET and $PASSWORD_SECRET."

cat <<EOF

Now the second copy, in Strongbox:
  1. Add an entry titled "My Receipts – Play upload key".
  2. Put the password you just typed in its Password field.
  3. Attach this file to it, from your Downloads folder: $key
  4. Save the database.
EOF
read -r -p "Press Enter once it is saved in Strongbox (the file is then deleted from Downloads): " _
echo "Done: the key is in Secret Manager and in Strongbox, and nowhere else."

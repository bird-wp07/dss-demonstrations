#!/usr/bin/env sh

set -e

certdir="./custom-trusted-certificates"
outdir="./dss-demo-webapp/src/main/resources"

ksname="$(grep -E '^customKeystore\.filename' "$outdir/dss.properties" | cut -d' ' -f3-)"
kspath="$outdir/$ksname"
kstype="$(grep -E '^customKeystore\.type' "$outdir/dss.properties" | cut -d' ' -f3-)"
kspass="$(grep -E '^customKeystore\.password' "$outdir/dss.properties" | cut -d' ' -f3-)"

if [ -f "$kspath" ]; then
    rm "$kspath"
fi
find "$certdir" -type f -name "*.pem" | while read pempath; do
    if ! keytool -import -file "$pempath" -alias "$(basename "$pempath")" -keystore "$kspath" -storetype "$kstype" -storepass "$kspass" -noprompt; then
        printf "Failed adding '$pempath' to keystore '$kspath'. Abort.\n" >&2
        exit 1
    fi
done

mvn clean install -P quick

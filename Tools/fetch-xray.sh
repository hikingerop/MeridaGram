#!/bin/sh
# Downloads the Xray core (AndroidLibXrayLite) for MeridaGram's built-in Merida VPN,
# checks its checksum and keeps only what MeridaGram needs: arm64 native code, no geo databases.
# Run from the repository root before building.
set -eu

VERSION=v26.10.17  # the same core as v2rayNG 2.3.12, which Merida VPN is built on
SHA256=fd6cb61fed873e465c3064327dffbcb22dc3fd78260d162ec9e868db2582495f
LIBS=TMessagesProj/libs

if [ -f "$LIBS/libv2ray.aar" ] && [ -f "$LIBS/libv2ray-classes.jar" ]; then
    echo "Xray core is already in $LIBS"
    exit 0
fi

tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

curl -fsSL -o "$tmp/libv2ray.aar" \
    "https://github.com/2dust/AndroidLibXrayLite/releases/download/$VERSION/libv2ray.aar"
echo "$SHA256  $tmp/libv2ray.aar" | sha256sum -c -

zip -q -d "$tmp/libv2ray.aar" 'assets/*' 'jni/armeabi-v7a/*' 'jni/x86/*' 'jni/x86_64/*'
unzip -p "$tmp/libv2ray.aar" classes.jar > "$LIBS/libv2ray-classes.jar"
mv "$tmp/libv2ray.aar" "$LIBS/libv2ray.aar"
echo "Xray core $VERSION is in $LIBS"

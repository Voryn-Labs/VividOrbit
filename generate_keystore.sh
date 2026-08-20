#!/bin/bash
set -e

KEYSTORE_FILE="vividorbit-release.jks"
ALIAS="vividorbit"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "⚠️ Keystore $KEYSTORE_FILE already exists."
else
    echo "Generating new release keystore ($KEYSTORE_FILE)..."
    keytool -genkeypair -v \
        -keystore "$KEYSTORE_FILE" \
        -alias "$ALIAS" \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000
    echo "✅ Keystore created: $KEYSTORE_FILE"
fi

cat << EOF > keystore.properties
storeFile=$KEYSTORE_FILE
keyAlias=$ALIAS
EOF

echo "✅ Created keystore.properties"
echo ""
echo "To build a signed release bundle (.aab) for Google Play Store, run:"
echo "  export VIVIDORBIT_STORE_PASSWORD=\"<your-keystore-password>\""
echo "  ./gradlew bundleRelease"
echo ""
echo "Output bundle will be at: app/build/outputs/bundle/release/app-release.aab"

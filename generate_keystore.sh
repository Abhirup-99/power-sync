#!/bin/bash

KEYSTORE_FILE="release.keystore"
ALIAS="key0"
PASSWORD="password"

if [ -f "$KEYSTORE_FILE" ]; then
    echo "Keystore $KEYSTORE_FILE already exists."
else
    echo "Generating new keystore..."
    keytool -genkey -v -keystore $KEYSTORE_FILE -keyalg RSA -keysize 2048 -validity 10000 -alias $ALIAS -storepass $PASSWORD -keypass $PASSWORD -dname "CN=PowerSync Release,O=PowerSync,C=US"
    echo "Keystore generated: $KEYSTORE_FILE"
fi

echo ""
echo "Add the following to your local.properties file:"
echo "storeFile=$(pwd)/$KEYSTORE_FILE"
echo "storePassword=$PASSWORD"
echo "keyAlias=$ALIAS"
echo "keyPassword=$PASSWORD"

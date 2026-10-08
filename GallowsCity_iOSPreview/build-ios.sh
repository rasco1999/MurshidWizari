#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p dist package/Payload/GallowsCity.app
APP="$(pwd)/package/Payload/GallowsCity.app"
cp Info.plist "$APP/Info.plist"
SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
xcrun swiftc -O -target arm64-apple-ios15.0 -sdk "$SDK" -framework UIKit -framework SceneKit Game.swift -o "$APP/GallowsCity"
file "$APP/GallowsCity"
file "$APP/GallowsCity" | grep -q 'Mach-O 64-bit executable arm64'
(cd package && /usr/bin/zip -qr ../dist/GallowsCity_iOS_PREVIEW_UNSIGNED.ipa Payload)
unzip -l dist/GallowsCity_iOS_PREVIEW_UNSIGNED.ipa | grep 'Payload/GallowsCity.app/GallowsCity'

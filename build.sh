#!/bin/sh
# Builds Grove.app (menu-bar only, no Dock icon) into ./build
set -e
cd "$(dirname "$0")"
swift build -c release
APP=build/Grove.app
rm -rf "$APP"
mkdir -p "$APP/Contents/MacOS"
cp "$(swift build -c release --show-bin-path)/Grove" "$APP/Contents/MacOS/Grove"
cat > "$APP/Contents/Info.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0"><dict>
  <key>CFBundleName</key><string>Grove</string>
  <key>CFBundleIdentifier</key><string>local.grove.focus</string>
  <key>CFBundleExecutable</key><string>Grove</string>
  <key>CFBundlePackageType</key><string>APPL</string>
  <key>CFBundleShortVersionString</key><string>1.0</string>
  <key>LSMinimumSystemVersion</key><string>13.0</string>
  <key>LSUIElement</key><true/>
</dict></plist>
EOF
codesign --force -s - "$APP"
echo "Built $APP — run: open $APP"

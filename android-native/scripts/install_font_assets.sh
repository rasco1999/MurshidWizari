#!/usr/bin/env bash
set -euo pipefail

# Cairo is OFL-1.1, pinned to a repeatable Google Fonts source revision.
# Android uses the same embedded family for Arabic and Latin.
GOOGLE_FONTS_REV="66928797f271fed5ee428544e7229685e2d90127"
ROOT="https://raw.githubusercontent.com/google/fonts/${GOOGLE_FONTS_REV}/ofl/cairo"
mkdir -p app/src/main/res/font app/src/main/assets/licenses

curl -fLsS --retry 4 --connect-timeout 20 --max-time 120 \
  "${ROOT}/Cairo%5Bslnt%2Cwght%5D.ttf" -o app/src/main/res/font/cairo.ttf
curl -fLsS --retry 4 --connect-timeout 20 --max-time 120 \
  "${ROOT}/OFL.txt" -o app/src/main/assets/licenses/OFL_Cairo.txt

test "$(wc -c < app/src/main/res/font/cairo.ttf)" -ge 450000
grep -qi "SIL OPEN FONT LICENSE" app/src/main/assets/licenses/OFL_Cairo.txt
head -c 4 app/src/main/res/font/cairo.ttf | od -An -tx1 | grep -q "00 01 00 00"
echo "Cairo embedded (SIL OFL-1.1)."
sha256sum app/src/main/res/font/cairo.ttf

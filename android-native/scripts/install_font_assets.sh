#!/usr/bin/env bash
set -euo pipefail

# Reproducible Google Fonts snapshot (SIL OFL-1.1).
# Fonts are downloaded in CI and bundled inside the APK; not committed as binaries.
GOOGLE_FONTS_REV="66928797f271fed5ee428544e7229685e2d90127"
FONT_ROOT="https://raw.githubusercontent.com/google/fonts/${GOOGLE_FONTS_REV}/ofl"
mkdir -p app/src/main/res/font app/src/main/assets/licenses
download() {
  local source="$1"
  local target="$2"
  curl --fail --location --silent --show-error --retry 4 --retry-delay 2 \
    --connect-timeout 20 --max-time 100 "$source" --output "$target"
}
download "${FONT_ROOT}/notosansarabic/NotoSansArabic%5Bwdth%2Cwght%5D.ttf" "app/src/main/res/font/noto_sans_arabic.ttf"
download "${FONT_ROOT}/inter/Inter%5Bopsz%2Cwght%5D.ttf" "app/src/main/res/font/inter.ttf"
download "${FONT_ROOT}/notosansarabic/OFL.txt" "app/src/main/assets/licenses/OFL_NotoSansArabic.txt"
download "${FONT_ROOT}/inter/OFL.txt" "app/src/main/assets/licenses/OFL_Inter.txt"

test "$(wc -c < app/src/main/res/font/noto_sans_arabic.ttf)" -ge 500000
test "$(wc -c < app/src/main/res/font/inter.ttf)" -ge 500000
grep -qi "SIL OPEN FONT LICENSE" app/src/main/assets/licenses/OFL_NotoSansArabic.txt
grep -qi "SIL OPEN FONT LICENSE" app/src/main/assets/licenses/OFL_Inter.txt
head -c 4 app/src/main/res/font/noto_sans_arabic.ttf | od -An -tx1 | grep -q "00 01 00 00"
head -c 4 app/src/main/res/font/inter.ttf | od -An -tx1 | grep -q "00 01 00 00"
echo "Licensed Noto Sans Arabic and Inter embedded from pinned Google Fonts revision."
sha256sum app/src/main/res/font/*.ttf

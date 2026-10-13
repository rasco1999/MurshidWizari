#!/usr/bin/env python3
"""Read-only iPhone -> native Android guest screen parity guard."""
from pathlib import Path
import re
import sys

repo = Path(__file__).resolve().parents[2]
ios = (repo / "patches/AuthViews.swift").read_text(encoding="utf-8")
android = (repo / "android-from-ios/app/src/main/java/com/muriq/murshid/iosparity/MainActivity.kt").read_text(encoding="utf-8")
manifest = (repo / "android-from-ios/app/src/main/AndroidManifest.xml").read_text(encoding="utf-8")

def segment(source: str, start: str, end: str) -> str:
    return source.split(start, 1)[1].split(end, 1)[0]

ios_guest = segment(ios, "struct GuestLandingView: View {", "var body: some View")
android_guest = segment(android, "private fun GuestLandingScreen(", "@Composable\nprivate fun AuthHeading(")
ios_items = re.findall(r'\.init\((?:id:\s*\d+,\s*)?title:\s*"([^"]+)",\s*text:\s*"([^"]+)"',
                       ios_guest, flags=re.S)
android_items = re.findall(r'LandingItem\(\s*"([^"]+)",\s*"([^"]+)"\s*\)',
                           android_guest, flags=re.S)
if len(ios_items) != 15 or len(android_items) != 15:
    raise SystemExit(f"FAIL: feature+instruction count {len(ios_items)} iOS, {len(android_items)} Android; expected 15 each")
if ios_items != android_items:
    for i, (left, right) in enumerate(zip(ios_items, android_items), 1):
        if left != right:
            print(f"Mismatch at item {i}: iOS={left}, Android={right}", file=sys.stderr)
    raise SystemExit("FAIL: guest landing copy does not match iPhone")
features = segment(android_guest, "val featureIcons = listOf(", "val instructionIcons =")
instructions = segment(android_guest, "val instructionIcons = listOf(", "fun open(")
if features.count("Icons.Default.") != 8 or instructions.count("Icons.Default.") != 7:
    raise SystemExit("FAIL: 8 feature and 7 instruction icons are mandatory")
for label, value in (
    ("app icon", '@mipmap/ic_murshid_adaptive'),
    ("right-to-left layout", 'android:supportsRtl="true"'),
):
    if value not in manifest:
        raise SystemExit(f"FAIL: missing {label}")
if "(index + 1).toString().padStart(2, '0')" not in android_guest:
    raise SystemExit("FAIL: instruction numbering should match the iPhone reference")
print("PASS: 8 benefits, 7 instructions, 15 identical titles/descriptions, matched icons, RTL and official adaptive icon.")

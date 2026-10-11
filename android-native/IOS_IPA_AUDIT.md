# iOS IPA audit — reference for Android native port

Source: user-provided `Murshid_Wizari_iOS_Production_v4_8_18_BUILD498_unsigned(1).ipa` (do not commit the binary or any credentials).

Verified from Info.plist:
- CFBundleDisplayName: المرشد الوزاري
- CFBundleIdentifier: com.muriq.murshid
- CFBundleShortVersionString: 4.8.18
- CFBundleVersion: 498
- MinimumOSVersion: 16.0

Observed public URLs in executable:
- https://www.mur-iq.com
- https://mur-iq.com/privacy.php
- https://mur-iq.com/terms.php
- https://www.instagram.com/murshid.iq/
- https://www.facebook.com/Murshidiq/
- https://t.me/El_Murshed_iq

The IPA is a compiled iOS artifact, not Swift source. Native Kotlin reimplementation requires identifying and validating authenticated API endpoints and response schemas. No WebView. Do not assume compatibility based on these URLs alone.

2026-10-11 follow-up: The native Kotlin app **does compile into debug, QA and unsigned candidate APKs**; its QA build also passed a synthetic signed-in emulator matrix on Android API 26/29/33/35 (run `38133379229`). The original IPA icon was independently recovered byte-for-byte. These are reproducible build and controlled-device findings, **not** proof of pixel-perfect iOS parity, live signed-in backend compatibility, production signing or release approval. See `RELEASE_CHECKLIST.md` for outstanding gates.

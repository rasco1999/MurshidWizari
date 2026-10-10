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

The IPA is a compiled iOS artifact, not Swift source. Native Kotlin reimplementation requires identifying and validating authenticated API endpoints and response schemas. No WebView. Do not assume compatibility based on these URLs alone. The existing Kotlin scaffold is not feature-complete and has not been built/tested into an APK.

# Android ↔ iOS parity verification (source audit)

Reference: `patches/` in the original iOS branch `codex/build498-french-avatar-contest-20261010`.
Android: `android-from-ios/` in `android-from-ios-compose`.

## Verified in Android source

| Area | Android implementation | Status |
|---|---|---|
| Platform | Kotlin + Jetpack Compose; no WebView implementation | Source verified |
| Layout | Arabic RTL, scrollable screens, tablet content width cap | Source verified; device QA pending |
| Theme | Navy, blue and gold theme, dark/light/system selection | Source verified; visual comparison pending |
| Welcome and icon | Original iOS artwork carried into native app | Source verified |
| Authentication | Login, registration, grade choice, show/hide password | Source verified; server QA pending |
| Session | Android Keystore AES-GCM cookie persistence, weak-network retry | Source verified; server QA pending |
| Home | Four-slide banner, shortcuts, student metrics, social links | Source verified; pixel comparison pending |
| Learning | Subjects, topics, quiz, results, favorite, report, notes | Source verified; backend QA pending |
| Question copy | Editorial-prefix cleanup based on iOS presentation | Unit tested; edge-case review pending |
| Account | Profile, photo upload/delete, deletion request | Source verified; server QA pending |
| Payment | External HTTPS checkout, status check, subscription codes | Source verified; test gateway QA pending |
| CI | Android build, unit tests, APK signature and native-only checks | Automated |

## Not yet verified as exact iOS parity

- All original SwiftUI fonts, icon variants, image assets, spacing, animations and all screen states.
- Full iOS recovery and verification flows **inside the native UI** (Android still opens external official pages).
- All complex iOS tools, question search, contest/daily challenges, achievements and special question interactions.
- Android notification permission, push token registration and delivery.
- Biometric login/lock and any iOS-only security options.
- Pixel-by-pixel screenshots at small, medium, large, tablet and foldable Android screen sizes.
- Live production integration including email/phone verification, expired subscriptions, network interruptions, double submissions and payment callback.
- Accessibility (screen readers, 200% font scale, contrast), RTL mirroring and rotation.
- Production release keystore, app signing and distribution.

**A passing CI build proves the code compiles and unit tests pass. It does not prove complete feature parity or production readiness.**

## Release gates

1. Create an exhaustive screen/route list from iOS source and capture reference screenshots.
2. Implement missing native flows; do not silently substitute external websites for native functionality.
3. Compare screenshots on at least one phone and one tablet in light/dark themes.
4. Test with dedicated non-personal accounts and sandbox payments.
5. Run end-to-end server tests, including bad connectivity, retries and expired sessions.
6. Sign an Android release build, verify it on devices and document the final APK hash.

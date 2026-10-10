# Murshid Wizari — Native Android port of the original iOS source

This is a NEW independent Kotlin + Jetpack Compose codebase created from the SwiftUI sources on
`codex/build498-french-avatar-contest-20261010` (the iOS Build 498 source branch).

The previous Android APKs, WebView app and old `android`/`android-native` application code are
NOT used as implementation sources. The Android project is in `android-from-ios/`.

## Source parity

iOS references:
- `patches/ThemeV34.swift`: navy `#0B1F33`, blue `#2D6CDF`, gold `#F5B83D`, cards (22dp), buttons (17dp)
- `patches/AuthViews.swift`: onboarding, auth, five-tab navigation, exams, account
- `patches/V31Features.swift`: home, predictions, school ads, external checkout flow
- `patches/V45ContestWelcome.swift`: branded welcome treatment
- `patches/APIClient.swift`: HTTPS PHP backend contracts

Native Android: Compose UI, Arabic RTL, adaptive scrolling, dark/light/system appearance,
encrypted persisted cookies (Android Keystore AES-GCM), no WebView, and no hard-coded student data.

## Implemented initial port

Guest instructions, login/registration, grade selection, password show/hide, verification handoff,
student home and metrics, five native tabs, subjects/topics, staged quiz answers, server-verified
progress with automatic quota enforcement, result summary, favorites, reports, question notes,
predictions subscription lock, school ads, account/name/delete request, support, stories,
subscription code and server-created external payment redirect/status.

## Before production

This is an **initial native implementation, not a pixel-perfect or functionally complete iOS clone**.
The following require additional dedicated work and device verification:
- the complete iOS visual asset/font/icon library and identical home slider imagery
- all complex educational tool screens, daily challenge, contest, custom exam, question search
- Android avatar uploads, push notifications, biometric lock, recovery/OTP within native UI
- accessibility audits, Android tablet/foldable screenshot comparison and real backend integration QA
- production signing, release hardening and Play policies

Do not represent a successful GitHub build as evidence of full functional parity.
Before testing write operations, use a dedicated non-personal test account.

Build: `cd android-from-ios && gradle :app:assembleDebug` with JDK 17, Android SDK 35, Gradle 8.11.1.

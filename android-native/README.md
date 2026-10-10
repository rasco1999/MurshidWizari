# Murshid Wizari — Native Android

This is an **initial native Kotlin scaffold**, not an iOS feature-equivalent release and not an APK. No WebView is used.

## Build
Open `android-native/` in Android Studio (JDK 17, Android SDK 35) and run the Gradle `assembleDebug` task. The Gradle wrapper is not included.

## Next work
Review the actual iOS source and authenticated server API contract, implement student sign-in, session management, topics, questions, answer submission, subscriptions, profile and media, then run functional/device tests. Never embed server credentials or modify production DB for a client build.

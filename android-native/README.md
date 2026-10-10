# Android native port — work in progress

**This is not a feature-complete release.** Kotlin native UI (no WebView), session-cookie API client, login, student bootstrap, subjects, topics, read-only question preview and GitHub Actions debug APK build are implemented as initial integration.

Server contract reviewed from user-supplied PHP backup:
- POST `api/login.php`: JSON `identifier`, `password`, `remember_me`
- GET `mobile/bootstrap.php`: student, csrf, subjects, quota
- GET `mobile/topics.php?subject_id=...`
- GET `mobile/exam.php?chapter_id=...`

**Safety:** Answer submission is intentionally disabled pending verification of the exact endpoint and quota behavior. Do not use this preview as a replacement for production iOS. Registration, recovery, all question types, subscriptions, profiles, avatar uploads, and notifications are not yet implemented. Do not commit PHP source, database data, credentials, or private student data.

GitHub Actions workflow `Android Native Debug APK` builds a **debug APK** artifact on `android-native` branch. Build success and live compatibility must be verified in Actions and on a real device.

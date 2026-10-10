# المرشد الوزاري — Android Native

تطبيق Android أصلي بلغة Kotlin، دون WebView، يُبنى من فرع `android-native`.

## Implemented in the Android prototype
- Login, remembered sessions encrypted by Android Keystore, password visibility.
- Native home, subjects, topics, exams, registration form, account, notifications, favorites, review, stories, support, subscription code and predictions screens.
- RTL layout and manually selectable dark/light colors.
- Exam answers submitted to the PHP server with confirmation required before advancing; no automatic retry after an ambiguous timeout.
- Correct Android back navigation and both object/string multiple-choice formats.
- Debug APK build and artifact upload via GitHub Actions.

## Release blockers — do not distribute as production
1. **Server compatibility has not been verified on a real device.** The iOS source uses `mobile/login.php` with form encoding; the Android client currently calls `api/login.php` with JSON. Validate the actual server contracts, authentication cookies, CSRF and each response schema before claiming parity.
2. Confirm the server's answer grading/credit semantics with a **test account**; in particular whether `api/progress.php` acknowledges `grades` and prevents duplicate quota charges.
3. Verify registration, account recovery, subscription code, payment, avatar upload, notifications, question formats and errors with the deployed backend. Some Android screens are provisional and may require server changes.
4. Test on multiple physical Android devices, slow mobile data and offline transitions; verify accessibility, RTL, and API timeouts.
5. Compare all screens, assets and flows with the iOS 4.8.18 IPA. The current UI is **not pixel-perfect**.
6. Release APK requires production signing, a secure keystore and versioned release pipeline. Debug APK is only for internal testing.

**Safety:** Do not commit passwords, signing keys, private student data or server database exports. Do not modify production database or grant credits while testing.

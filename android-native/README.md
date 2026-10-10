# المرشد الوزاري — Android Native

تطبيق Android أصلي بلغة Kotlin، دون WebView، يُبنى من فرع `android-native`.

## Implemented in the Android prototype
- Login, remembered sessions encrypted by Android Keystore, password visibility.
- Native home, subjects, topics, exams, registration form, account, notifications, favorites, review, stories, support, subscription code and predictions screens.
- RTL layout and manually selectable dark/light colors.
- Exam answers submitted to the PHP server with confirmation required before advancing; no automatic retry after an ambiguous timeout.
- Native Daily Challenge GET/POST (`mobile/challenge.php`) with full-form validation, one-time submission, server-authoritative grading and an uncertain-request recovery gate.
- Million Contest and seasonal leaderboards (`mobile/contest.php`, `mobile/season.php`) with rank details and actions to earn XP, plus Achievements (`mobile/achievements.php`) with earned/locked filtering and paths back to studying. No client-side XP/badge awarding.
- Account: server-validated three-part name edit (including Unicode whitespace/direction normalization), refreshed profile/bootstrap after mutation, bounded multipart avatar upload, revision-aware uncached avatar display and direct removal; subscribed/remaining state never inferred from a local button.
- Subscription: interactive plan selection, externally opened HTTPS checkout, account-scoped persisted payment order ID, explicit and on-return payment status check. Unlocking subscribed features requires confirmed server bootstrap; requests with uncertain outcome are not auto-resubmitted.
- Brand tokens aligned with the SwiftUI navy `#0B1F33`, blue `#2D6CDF`, gold `#F5B83D`; 17dp gradient/ripple buttons with press animation, profile gradient card and constrained centered width plus a two-column tablet home menu.
- Android validated connectivity callbacks, differentiated timeout/offline/security messages, explicit retry/restore remembered session and stale-session callback suppression. A rotated exam keeps the active in-memory answers.
- Live unauthenticated read-only server checks: `mobile/bootstrap.php` and `mobile/account.php` returned HTTP 401 as expected; `api/grades.php` returned HTTP 200 with grade JSON. These are **not** authenticated integration tests.
- JVM regression tests for question quality, answer progression and daily challenge payload/completion rules. GitHub Actions runs them before building the APK.
- Correct Android back navigation and both object/string multiple-choice formats.
- Debug APK build and artifact upload via GitHub Actions.

## Release blockers — do not distribute as production
1. **Authenticated server compatibility has not been verified on a real device.** The latest iOS API client and Android both call `api/login.php` with JSON. Validate login cookies, CSRF, avatar revision, account name change, checkout callbacks, order status and all authenticated response schemas with a dedicated test account before claiming parity.
2. Confirm the server's answer grading/credit semantics with a **test account**; in particular whether `api/progress.php` acknowledges `grades` and prevents duplicate quota charges.
3. Verify registration, account recovery, subscription code, payment, avatar upload, notifications, question formats, daily challenge award/quota behavior, contest leaderboard response schemas and errors with a deployed **test account**. Some Android screens are provisional and may require server changes.
4. Test on multiple physical Android devices, slow mobile data and offline transitions; verify accessibility, RTL, and API timeouts.
5. Compare all screens, assets, fonts, icons, motion, RTL and flows against the iOS 4.8.18 IPA on phone/tablet screenshots. Brand colors and primary sizing are aligned, but the current native views are **not pixel-perfect** and have not had physical small/large-device layout audits.
6. Release APK requires production signing, a secure keystore and versioned release pipeline. Debug APK is only for internal testing.

**Safety:** Do not commit passwords, signing keys, private student data or server database exports. Do not modify production database or grant credits while testing.

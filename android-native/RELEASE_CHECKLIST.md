# Android Native — production release gates

Target: feature parity with iOS **4.8.18**. Branch: **`android-native`**. Do not distribute a provisional APK.

`[x]` means independently verified; all other checks must remain open until observed on the stated environment. Do not treat JVM/build success as device/server acceptance.

## Evidence currently available

- [x] Live anonymous `GET /mobile/bootstrap.php` returns HTTP 401 JSON: `ok=false` and an expired-session message (2026-10-10 UTC).
- [x] Live anonymous `GET /mobile/account.php` returns HTTP 401 JSON (2026-10-10 UTC).
- [x] Live anonymous `GET /api/grades.php` returns HTTP 200 JSON with three grade entries (2026-10-10 UTC).
- [x] JVM rules and debug compile passed on GitHub Actions (run 38094896178); a subsequent avatar/signing build must pass separately.

## Required authenticated integration tests

Use a dedicated **non-production student test account** and an administrator-approved test payment path. Never put credentials, CSRF, bearer tokens, photos, student identifiers or payment details in screenshots/logs/commits.

- [ ] Login, verification, encrypted remembered session and fresh app process re-open; logout and HTTP 401 reset.
- [ ] Edit a three-part name; confirm the returned account name and `bootstrap.user.name` agree immediately, and the one-time edit option disappears. Confirm normalisation of spaces/direction marks.
- [ ] Upload JPEG, PNG and device-supported HEIC photos; check compression, EXIF orientation, immediate preview, server `avatar_revision` and unchanged image URL cache invalidation; remove photo and verify across devices.
- [ ] Confirm free-remaining counts versus real server quotas, and subscribed access immediately after a voucher redemption.
- [ ] Select each available plan; confirm only one checkout is created per tap, HTTPS redirect is safe, persisted `order_id` belongs to the account, and returning to Android/pressing Status reads the actual server state.
- [ ] Verify a successful administrator-approved test payment updates `bootstrap.subscribed`, remaining days and prediction access. Pending/failed/timeout must never locally unlock content.
- [ ] Confirm all types of exams, correct-answer and explanation rendering, next/back navigation and prevention of duplicate deductions after a timed-out POST.
- [ ] Challenge once per day (score/XP/quota), contest and seasonal rank, earned/locked achievements, and actual server changes on subsequent GET.
- [ ] Exercise registration, recovery, notifications, favorites, support, privacy/deletion flow and every remaining native iOS action.

## Device, connection and visual tests

- [ ] Screenshot-compare fonts, icons, colors, sizes, spacing, RTL and motion against the iOS IPA with a defined acceptable tolerance. Current Android UI is **not** declared pixel-perfect.
- [ ] Check 360×640 and 393×852 phone layouts at 1.0×/1.3× font scales, light and dark mode, with keyboard visible.
- [ ] Check a 600dp-width small tablet, an 800dp-width tablet and landscape screens: centered content, two-column home actions, no clipping or text truncation.
- [ ] Rotate during an unanswered exam, daily challenge and an active form. Ensure active state is not silently discarded and system back works.
- [ ] Android API 26, 29, 33 and 35 on real devices/emulators; test accessibility labels, touch areas, edge insets and bottom navigation gesture zones.
- [ ] Simulate slow 2G/packet loss, DNS failure, SSL error, HTTP 401/429/500 and disconnect mid-GET and mid-POST. Check explicit retry and no automatic POST duplication.
- [ ] Reconnect in foreground/background and after process restart with “remember me”; verify session restoration, expiration and safe updates to profile/subscription.

## Signing and distribution gate

- [ ] All functional, server, device and visual checks above pass and are documented.
- [ ] Owner securely supplies production keystore and `MURSHID_RELEASE_KEYSTORE`, `MURSHID_RELEASE_STORE_PASSWORD`, `MURSHID_RELEASE_KEY_ALIAS`, `MURSHID_RELEASE_KEY_PASSWORD` to the protected build environment. Never use debug or temporary signing keys.
- [ ] Finalize version name/code, production application ID, launcher resources and shrinker settings.
- [ ] Run production `testDebugUnitTest`, lint, release build, and `apksigner verify --verbose --print-certs` on the signed output; verify expected certificate fingerprint.
- [ ] Install **only the final signed candidate** on approved test devices, smoke-test every critical flow and confirm no crashes in privacy-safe logs.
- [ ] Obtain owner release approval before sharing any signed APK or publishing to a store.

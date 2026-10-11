# Android Native — production release gates

Target: feature parity with iOS **4.8.18**. Branch: **`android-native`**. Do not distribute a provisional APK.

`[x]` means independently verified; all other checks must remain open until observed on the stated environment. Do not treat JVM/build success as device/server acceptance.

## Evidence currently available

- [x] Live anonymous `GET /mobile/bootstrap.php` returns HTTP 401 JSON: `ok=false` and an expired-session message (2026-10-10 UTC).
- [x] Live anonymous `GET /mobile/account.php` returns HTTP 401 JSON (2026-10-10 UTC).
- [x] Live anonymous `GET /api/grades.php` returns HTTP 200 JSON with three grade entries (2026-10-10 UTC).
- [x] Public `GET /forgot-password.php` includes a same-origin CSRF field and `identifier` form field (2026-10-10 UTC). No password-reset request was submitted.
- [x] All 35 JVM unit tests and debug compile passed on GitHub Actions for native registration/verification and custom practice (run 38096444401, 2026-10-10 UTC). These checks do **not** prove authenticated server behaviour or visual parity.
- [x] Secure browser sign-in to the owner-provided account succeeded on 2026-10-11 UTC. Authenticated `GET /mobile/bootstrap.php` returned `ok=true`, `subscribed=true`, 31 subscription days, user/subjects/stats and a CSRF field. No credentials or tokens were copied into repository files.
- [x] Authenticated **read-only** GET contract checks succeeded for account, notifications, favorites, study-plan, review, practice history, question submissions, support, contest, season, achievements, stories and subscribed predictions. The observed achievement payload has 5 badges (1 earned), contest has 2 leaderboard entries, review has 9 recorded mistakes, and practice history has 1 item. These observations do **not** validate any create/update/delete actions.
- [x] Eight representative exam-topic GETs (one from each subject) returned valid JSON and 75–150 questions each, covering text, true/false and calculation types. Previously answered questions supported previous/next navigation and correct-answer display in the live **website** without changing an answer. Additional matching/genetics types, explanation accuracy and Android device rendering still require testing.
- [x] Authenticated `GET /api/search.php?q=...` returned a `results` array with `type`, `title`, `meta`, `url` and **no `ok` field**. This revealed a native search-result acceptance bug and non-clickable results; patched the native screen to recognize the live envelope and open whitelisted exam links with a return path. Awaiting Android CI/device verification.
- [x] On 2026-10-11 UTC the owner confirmed the account is solely for testing and authorized state changes. A **website** one-time full-name edit succeeded using the synthetic three-word test name. Fresh authenticated `GET /mobile/account.php` and `GET /mobile/bootstrap.php` immediately agreed on the new name, the server returned `can_change_name=false`, and the subscription remained active with 31 days. The **Android native** edit POST and in-app refresh remain separately unverified.
- [x] On 2026-10-11 UTC, Android CI run **38099634397** passed Gradle JVM unit tests (including 43 rule tests) and `assembleDebug` after the contest-back-navigation and offline-retry fixes.
- [x] Anonymous Android **API-35 emulator** run **38099634444** passed install/launch/process and visible-login-title checks at **360×640, 1.0× font** and **800×1280, 1.3× font**, producing screenshots plus UI hierarchy XML. Earlier run **38099470479** missed the title; follow-up runs **38099748294** and **38099925764** passed, and the new UI script waits for the marker rather than relying on a fixed five-second startup delay. These runs do not prove visual parity or authenticated actions.
- [x] On 2026-10-11 UTC, emulator run **38100101736** passed **actual UI taps** on API 35 at both sizes: show/hide password checkbox checked state, opening password recovery and account registration, and Android hardware back returning to native login. It used no credentials or form submissions. Build run **38100101715** also passed. Visual side-by-side review and signed-in actions remain open.
- [ ] A synthetic, nonpersonal test avatar was prepared locally, but secure browser session restrictions prevented a file chooser/upload. No image update can be claimed; the Android JPEG/PNG/HEIC uploads, immediate avatar revision refresh, and delete/restore still require safe device testing.

## Required authenticated integration tests

Use a dedicated **non-production student test account** and an administrator-approved test payment path. Never put credentials, CSRF, bearer tokens, photos, student identifiers or payment details in screenshots/logs/commits.

- [ ] Login, verification, encrypted remembered session and fresh app process re-open; logout and HTTP 401 reset.
- [ ] Register via email and via Iraqi mobile, including both genders/grades, test duplicate identifiers, enter a real 6-digit verification code and resend it once; verify bootstrap and remembered session only after server acceptance.
- [ ] Edit a three-part name; confirm the returned account name and `bootstrap.user.name` agree immediately, and the one-time edit option disappears. Confirm normalisation of spaces/direction marks.
- [ ] Upload JPEG, PNG and device-supported HEIC photos; check compression, EXIF orientation, immediate preview, server `avatar_revision` and unchanged image URL cache invalidation; remove photo and verify across devices.
- [ ] Confirm free-remaining counts versus real server quotas, and subscribed access immediately after a voucher redemption.
- [ ] Select each available plan; confirm only one checkout is created per tap, HTTPS redirect is safe, persisted `order_id` belongs to the account, and returning to Android/pressing Status reads the actual server state.
- [ ] Verify a successful administrator-approved test payment updates `bootstrap.subscribed`, remaining days and prediction access. Pending/failed/timeout must never locally unlock content.
- [ ] Confirm all types of exams, correct-answer and explanation rendering, next/back navigation and prevention of duplicate deductions after a timed-out POST.
- [ ] Challenge once per day (score/XP/quota), contest and seasonal rank, earned/locked achievements, and actual server changes on subsequent GET.
- [ ] Create a 1-day/5-question and a 365-day/500-question study plan; confirm correct POST schema, persisted list and safe recovery after POST timeout (no duplicate creation).
- [ ] Send a school-advertisement request (school/text/duration), check that the internal support inbox contains exactly one structured message and that uncertain submissions are not repeated.
- [ ] Verify correct answers/explanations and server-side unfavorite removal in saved questions, including after returning to the screen.
- [ ] Search Arabic terms through `api/search.php`, inspect weak-topic rates and unread notifications; verify the actual response field names, dates, and new results after refreshing.
- [ ] Start, background, restore and finish 25/45/60-minute focus sessions; compare server activity/XP, check manual reconciliation after dropped responses, and inspect recorded custom-exam history.
- [ ] Create custom exams with every subject/topic, 5–50 questions, all iOS type/state filters, timed and untimed sessions; verify server `session_id`, choice/text input, previous/next, expired timer, final scoring and detailed wrong-answer review.
- [ ] Restart midway through custom practice: ensure the account-scoped draft, current question and deadline are restored without creating a second server session; finish on the server once and verify history.
- [ ] Propose every supported question type with actual topic IDs, options, answer, source and explanation; confirm one submitted item and each moderation status/note on GET, including interrupted POSTs.
- [ ] Exercise registration, recovery, notifications, favorites, support, privacy/deletion flow and every remaining native iOS action.

## Device, connection and visual tests

Code-level visual alignment update (2026-10-11): the Android main navigation now mirrors the iOS four tabs, with a native 220dp four-slide home carousel (swipe/tap/auto-advance), iOS-style 16dp/22dp grouped cards, the V31 home section order, two-column phone/tablet metrics and tools, searchable tests and a server-backed success tab. The account screen uses profile, subscription, services, security and management groups. Dynamic tablet centering, RTL and native 44dp+ navigation targets are explicit. The pre-launcher-asset source passed Kotlin compilation and 42 JVM tests (CI run 38098983488). These are **code-level changes awaiting device screenshot inspection**, not verified parity. Android's Arabic system font and custom line icons are still not identical to SF Arabic/SF Symbols.

The owner-selected iOS launcher JPEG has been reconstructed from the checked-in icon chunks and SHA-256 checked against the existing iOS build workflow; the Android launcher now points at the same bytes. Exact visual rendering inside Android launcher masks and home screens remains to be inspected on a device.

The ordinary-topic exam also now has a fixed previous/next footer, progress bar, separated question/answer/feedback cards and the same server-authoritative navigation gate. Specialized genetics, Tajweed/matching, calculations and long-form answer rendering still require direct iOS/Android side-by-side inspection.

An independent emulator-smoke workflow installs **only the QA debug APK**, checks the native login title and process at 360×640/800×1280 (1.0×/1.3× font scales), exercises real show/hide-password toggles, navigation into recovery and registration, and Android back, and saves screenshots and accessibility XML for all three anonymous screens. The API-35 version passed. An expanded Android **API 26/29/33/35** test matrix awaits its separate run; do not mark those OS versions verified until they pass. Visual inspection of artifact images and authenticated UI testing remain outstanding. No signed install, student login, or full UI parity is implied.

- [ ] Screenshot-compare fonts, icons, colors, sizes, spacing, RTL and motion against the iOS IPA with a defined acceptable tolerance. Current Android UI is **not** declared pixel-perfect.
- [ ] Check 360×640 and 393×852 phone layouts at 1.0×/1.3× font scales, light and dark mode, with keyboard visible.
- [ ] Check a 600dp-width small tablet, an 800dp-width tablet and landscape screens: centered content, two-column home actions, no clipping or text truncation.
- [ ] Rotate during an unanswered exam, daily challenge and an active form. Ensure active state is not silently discarded and system back works.
- [ ] Android API 26, 29, 33 and 35 on real devices/emulators; test accessibility labels, touch areas, edge insets and bottom navigation gesture zones.
- [ ] Simulate slow 2G/packet loss, DNS failure, SSL error, HTTP 401/429/500 and disconnect mid-GET and mid-POST. Check explicit retry and no automatic POST duplication.
- [ ] Reconnect in foreground/background and after process restart with “remember me”; verify session restoration, expiration and safe updates to profile/subscription.
- [ ] Provide and validate an Android push-notification channel (FCM app/project configuration, token-registration API and test delivery); the existing iOS APNs registration endpoint is not an Android push service. Manual inbox loading does **not** satisfy push parity.
- [ ] Compare actual icon/font assets and transitions from the owner-approved iOS build on supported devices; brand-token matching alone is insufficient.

## Signing and distribution gate

- [ ] All functional, server, device and visual checks above pass and are documented.
- [ ] Owner securely supplies production keystore and `MURSHID_RELEASE_KEYSTORE`, `MURSHID_RELEASE_STORE_PASSWORD`, `MURSHID_RELEASE_KEY_ALIAS`, `MURSHID_RELEASE_KEY_PASSWORD` to the protected build environment. Never use debug or temporary signing keys.
- [ ] Finalize version name/code, production application ID, launcher resources and shrinker settings.
- [ ] Run production `testDebugUnitTest`, lint, release build, and `apksigner verify --verbose --print-certs` on the signed output; verify expected certificate fingerprint.
- [ ] Install **only the final signed candidate** on approved test devices, smoke-test every critical flow and confirm no crashes in privacy-safe logs.
- [ ] Obtain owner release approval before sharing any signed APK or publishing to a store.

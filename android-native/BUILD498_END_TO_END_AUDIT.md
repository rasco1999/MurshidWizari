# iPhone Build 498 → Android Native: end-to-end parity audit

Date: 2026-10-11 UTC. The reference is the **actual unsigned iOS IPA** from GitHub Actions artifact `11652990652`, generated at source commit `c4b79a45d2569ddca27febe54b54df6322b577d8`. The Android source is the separate `android-native` branch. This document records observations and **remaining acceptance work**, not a declaration of 100% compatibility.

## Reference binary identification

| Item | Verified reference |
| --- | --- |
| SHA-256 of `.ipa` | `4d536da56d6d6980477b8063ce0e2af19863bb7193ace8a96fe666496955f54e` |
| iOS version/build | `4.8.18` / `498` |
| Identifier/display name | `com.muriq.murshid` / `المرشد الوزاري` |
| iOS minimum/architecture | iOS 16.0, iPhone arm64, portrait |
| Compiler | Xcode 16.4, iOS SDK 18.5 |
| Main binary | `Payload/MurshidWizari.app/MurshidWizari`, Mach-O executable |
| Other IPA entries | iPhone/iPad PNG icons, `Assets.car`, `Info.plist`, `PrivacyInfo.xcprivacy`, `PkgInfo` |
| iPhone icon SHA-256 | `c9111c86a08bc88f3a198b7ece7b012d923b922f62ad46f56108a0fa916c077f` |
| Asset catalog names recoverable | `WelcomeEmblem`, `Academic-Welcome-Emblem.png`, `AppIcon` light/dark/tinted, `LaunchBackground` |

The archive was extracted, its complete file list inspected, Info.plist decoded, icon bytes hashed, binary format checked, and asset-catalog strings inventoried. The effective SwiftUI source was traced through `.github/workflows/build-ios.yml`, which replaces the iOS sources with `patches/*.swift` before building. Compilation source and app binary are distinct kinds of evidence: **this Linux environment cannot launch the iOS IPA or certify every SwiftUI pixel/transition**. The IPA is unsigned and cannot be installed as an ordinary signed iPhone app.

## Navigation and behaviour comparison

Legend: `A` = matched in Android source and exercised by a controlled emulator; `B` = source-level counterpart exists but this flow is not yet accepted end-to-end against the real student backend; `C` = material parity gap or missing independent visual/device proof.

| From first app tap through exit | iOS Build 498 reference | Android Native counterpart | Status |
| --- | --- | --- | --- |
| Cold launch | `V45WelcomeView` ceremonial 2.7-second playback plus 0.3-second fade | `CinematicWelcomeView`, 3-second native Canvas artwork | B — spacing/font/fade still device-comparison work |
| Resume after background ≥3s | `MurshidWizariApp.swift`: welcome replay, queued until Face ID unlock | welcome overlay now replays, queued until Android biometric unlock; first name shown on resume | B — physical background/biometric tests pending |
| Account restoration & offline recovery | `RootView`, asynchronous bootstrap, retry for network failure | remembered API session, splash-independent bootstrap, retry screen | B — live slow-network test pending |
| No session / first visit | `GuestLandingView`: official copy, demo question, platform sections, instruction links, social/policy links | `guestLandingScreen`, native RTL cards and scroll anchors | B — detailed iPhone screenshot tolerances pending |
| Login | `LoginView`: email/phone, password reveal, remember, recovery, register, verification | `loginScreen` and real API client | A — synthetic emulator only; live Android auth unverified |
| Registration & verification | `RegisterV3View`, `VerificationView`, phone/email recovery | `registerScreen`, `verificationScreen`, reset screens | B — live codes and edge cases pending |
| Main tab shell | `MainV3TabView`: الرئيسية، الاختبارات، المرشحات، الإعلانات، حسابي | Native fixed five-tab bar, same destinations | A — tab touches tested on four emulator API levels |
| Home | `V31HomeView`: hero carousel, insight cards, study shortcuts | Native welcome banner, hero, metrics, actions | B — dynamic layout/font parity pending |
| Remote feature flags & update strip | iOS loads `mobile/app-config.php` and gates custom exam, daily challenge, submissions and history; iOS update banner reads an iOS release URL | Android now reads the same nested `features[code].enabled` values and rebuilds home shortcuts; Android-specific update delivery is not configured | B — gates require real server test; C — Android update feed still needed |
| Subjects & chapters | `V3TestsView` / `V3TopicsView`, search, server progress | searchable subject/topic screens, IDs and insights | A — controlled data tested; live Android progress not verified |
| Topic exam | `V3ExamView`: question forms, previous/next, explanations, saved answers | question cards, answer POST reconciliation, notes, nav | A — MCQ/text/rejection/retry in emulator; exotic types B |
| Graded result | `V3ExamResultView`: score, correct/wrong, answered, time, follow-up | same result structure; confirmed grades counted | A — synthetic exam-result screenshot captured |
| Custom practice | `V40CustomExamBuilderView` / `V40CustomExamSessionView` | native practice builder, session, grading and history | B — timer/process-death/device test pending |
| Daily challenge | `V40DailyChallengeView`, one question each 24h | native account-scoped draft, anti-repeat receipt, result | B — only website challenge submission observed |
| Contest/leaderboard/achievements | `V3ContestView`, contest instructions, badges | native contest flow and account-linked daily state | B — live Android write and visual tests pending |
| Predictions & advertisements | `V44PredictionsView`, `V31SchoolAdsView`, popup sheet | subscriber predictions and ad popup, native ads tab | A — synthetic content navigation; live dismissal/popup B |
| Account | `V31AccountView`: avatar, rename, subscription code, inbox, security, support | account groups, avatar handling, recovery, notifications, privacy | B — upload, name write, permissions still unverified |
| Subscription/payment | `V31SubscriptionView`: external hosted checkout/status | native plans and verified return/status flow | C — actual authorised test-payment integration pending |
| Notifications | iOS APNs/device services | native inbox plus Android reminder notification | C — push-token registration and FCM delivery not implemented |
| Biometric lock | real iPhone Face ID | real Android biometric prompt/fingerprint | B — OS hardware differences intentional; test on phone |
| Explicit sign-out | `V31AccountView` → confirm → `APIClient.logout`, keeps session if request throws | confirm → logout API; **now retains session on rejected or unknown receipt**; clears on confirmed success | A — automated synthetic reject then accept covered; live server pending |
| Exit app | iOS home gesture, OS lifecycle | Android system back from guest finishes Activity | C — different platform gestures cannot be pixel-identical |

## What was actually verified on Android

### Follow-up: explicit exam submission and specialized forms

The next Android change follows the actual Build 498 `V3ExamView.optionButton`:
selecting or changing an MCQ/true-false answer is now local, with a separate
`إرسال الإجابة` button. Previously Android sent a grading request immediately
on selection. Arabic option letters, selected styling, real exam year/round,
submission feedback, the result title and first-wrong-question review were
also aligned with the SwiftUI source. Text, calculation, genetics, Tajweed and
English-matching drafts now remain with their question during previous/next
navigation in the current exam. These are **in-memory drafts**, not a claim
of process-death persistence.

`specialized_exam_qa.py` adds controlled UI coverage for true/false,
calculation values/choices, English matching, Tajweed type/reason and Punnett
cells. It checks exact submitted payloads, incomplete/duplicate-choice guards,
draft restoration and first-wrong review. The existing account test now
scrolls the unread row into view before asserting it: API 35 correctly omits
off-screen nodes from its accessibility hierarchy. **New CI results must be
recorded before these new checks are considered passed.**

- The improved native build at commit `13b62e18917791ed856c45bb04b4a3c4e3504fb5` **passed** compilation and unit tests (Actions run `38140898473`), including the production-origin debug APK artifact signed with an Android test/debug key.
- The **entire four-API emulator matrix passed** at the same commit: Android API 26, 29, 33 and 35 (Actions run `38140898498`; four successful jobs). Android 8, 10, 13 and 15 were thus exercised with synthetic local fixtures, not real student accounts.
- The matrix covers login, all five tabs, background-return welcome replay, topic exam, server-confirmed grading, rejected-answer retry, note persistence, text answers, result page, account recovery, configuration loading, rejection-then-confirmed logout and final guest screen. These checks are *not* a substitute for live-backend and side-by-side iPhone testing.
- The Android launcher uses the exact extracted iPhone 120px icon PNG bytes. Android system masks and iOS SpringBoard display can still differ.
- Android UI uses native Kotlin Views/Canvas, not a WebView. Android's Arabic font and self-drawn glyphs are not bitwise-identical to Apple's SF Arabic and SF Symbols.

## Acceptance still required before a claim of complete matching

1. Real **iOS screen recordings** and Android phone recordings in identical app data states from launch, login, all five tabs, exam types and result, recovery, account, sign-out and subsequent relaunch.
2. Measure tolerances for colors, insets, glyphs, RTL, keyboard, dynamic type, scrolling, animation timing and status/navigation bars across sizes, dark/light modes and accessibility settings.
3. Test full authenticated Android workflow against a dedicated non-production student account (including verification codes, account name/avatar mutation, lessons, challenge, service requests, session 401/reconnect and logout).
4. Confirm device push via an Android FCM app/server registration and delivery, plus reminder/biometric behaviours.
5. Use a test gateway for subscription checkout, callback and status reconciliation; no real payment should be triggered as a visual test.
6. Verify the now-wired server feature gates in a real Android session and configure a **separate Android update URL** before enabling any update banner; never offer the iOS IPA download as an Android upgrade.

**Release decision:** A **debug-key test-signed APK may be privately installed for owner review**, as newly authorized. It is not a production-signed app, not a Play Store release, and not certified 100% visually/functionally equivalent. Do not promote it to release until the open checks above have concrete evidence.

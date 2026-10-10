# Android student notification integration

Implementation: native Jetpack Compose inbox + system notifications based on the existing authenticated PHP endpoint.

## Verified backend contract
Current server source `mobile/notifications.php` reads `mobile_student()` and returns JSON `{ok, unread, items:[{id,title,body,is_read,created_at}]}`. It filters by `user_id`. POST JSON accepts `{csrf,read_id}` or `{csrf,read_all:true}`, and also filters updates by the logged-in `user_id`.

## User experience
- Student account binds the inbox to the server's authenticated session, not a client-supplied user ID.
- Notification bell displays unread count, and inbox shows read/unread items, timestamp, manual refresh and mark-one/mark-all actions.
- In foreground the inbox refreshes approximately every 75 seconds while lifecycle is STARTED.
- In the background WorkManager checks every 15 minutes **or later** when Android permits, only for users with a remembered encrypted session, after confirming the server-side student ID matches the locally bound ID.
- Android 13+ system notification permission is requested by user interaction from the inbox screen. Older Android uses OS notification settings.
- Old notices on first sync are baselined, not shown as a burst of historical device alerts.
- Local alerts contain no cached notification text on disk. Their intent opens the same account's inbox, auto-marks that notice read if it remains unread. Android notification cancellation and baseline clearing occur on logout and account switch.

## Limits and what is NOT implemented
- This is **not** Firebase Cloud Messaging or immediate remote push: the current server's `api/push-subscription.php` expects Web Push VAPID subscriptions and cannot accept Android FCM registration tokens. Existing iOS `mobile/apns-register.php` is likewise APNs-specific.
- Genuine instantaneous delivery needs a Firebase project, an Android app configuration/credential, and a server endpoint that binds FCM registration tokens to authenticated students and sends through FCM HTTP v1; do not fabricate tokens or credentials.
- Background polling can be delayed or suppressed by Android battery optimization. No notification guarantee or exactly-15-min scheduling is claimed.
- Live end-to-end delivery and mark-read need a dedicated test student with permission and verified production/staging server endpoints. Do not alter live student data merely to test.

## Test checklist
1. Log in as test student A, grant Android system permission, verify inbox and unread bell count match the server.
2. Mark one notice and all as read; reload and verify server persistence.
3. Send a test notice to A on server. Verify foreground sync, and background alert after scheduled WorkManager execution (not immediate).
4. Tap the system notice, verify the inbox opens to A's notice and mark-read returns successfully.
5. Log out then log in as test student B; verify no A contents/badge/alerts leak.
6. Deny notification permission; inbox must continue to function without system alerts.
7. Disable remember-me; background polling must not be scheduled; foreground inbox remains available.
8. Test airplane mode, weak connection, expired cookie and revoked login; ensure no generic fabricated alerts are shown.

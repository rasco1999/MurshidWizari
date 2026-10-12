"""Disposable QA: native push handling/account isolation and hosted-checkout return.

FCM transport and live money are explicitly NOT tested. The delivery receiver and
HTTPS checkout Activity exist only in src/qa and cannot enter the user APK.
"""
import argparse
import json
import re
import threading
import time
import xml.etree.ElementTree as ET
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

import anonymous_emulator_qa as ui
import student_fixture_qa as fixture

PAY = {'checkout': 0, 'status': 0, 'paid': False}


class Handler(fixture.Handler):
    def respond(self):
        if urlsplit(self.path).path != '/api/payment-checkout.php':
            return super().respond()
        body = json.loads(self.rfile.read(int(self.headers.get('Content-Length', '0'))))
        assert body['csrf'] == 'synthetic-csrf'
        if body['action'] == 'checkout':
            PAY['checkout'] += 1
            assert body['plan'] == 'month3'
            data = {'ok': True, 'order_id': 3401, 'redirect': 'https://checkout.example.test/synthetic'}
        else:
            assert body['action'] == 'status' and body['order_id'] == 3401
            PAY['status'] += 1
            fixture.STATE['subscription_active'] = PAY['paid']
            data = {'ok': True, 'state': 'success' if PAY['paid'] else 'pending',
                    'subscribed': PAY['paid'], 'message': 'الدفع بانتظار التأكيد الاختباري'}
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(json.dumps(data).encode())


def preferences():
    raw = ui.adb('shell', 'run-as', ui.PACKAGE, 'cat', 'shared_prefs/student_push.xml')
    root = ET.fromstring(raw)
    return {node.attrib['name']: node.text if node.tag == 'string' else node.attrib.get('value') for node in root}


def deliver(binding, notification_id):
    ui.adb('shell', 'am', 'broadcast', '-n', f'{ui.PACKAGE}/com.murshidwizari.student.QaPushReceiver',
           '--es', 'binding_id', binding, '--es', 'notification_id', str(notification_id))
    time.sleep(.7)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    out = parser.parse_args().output
    out.mkdir(parents=True, exist_ok=True)
    ui.PACKAGE = 'com.murshidwizari.student.qa'
    api = int(ui.adb('shell', 'getprop', 'ro.build.version.sdk').strip())
    assert ui.PACKAGE.endswith('.qa')
    server = ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    fixture.STATE['subscription_active'] = False
    try:
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'pm', 'clear', ui.PACKAGE)
        if api >= 33:
            ui.adb('shell', 'pm', 'grant', ui.PACKAGE, 'android.permission.POST_NOTIFICATIONS')
        ui.adb('reverse', 'tcp:8765', 'tcp:8765')
        ui.launch_native(); ui.enter_login(393, 852)
        fixture.login_field(0, 'student@example.test'); fixture.login_field(1, 'fixture-pass')
        fixture.button('دخول'); ui.wait_for('حسابي')
        time.sleep(1)
        binding = preferences()['binding']
        deliver('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 1234)
        assert 'last_seen' not in preferences(), 'Wrong-account signal was accepted'
        deliver(binding, 1234)
        assert preferences()['last_seen'] == '1234'
        dump = ui.adb('shell', 'dumpsys', 'notification', '--noredact').decode(errors='replace')
        assert re.search(r'NotificationRecord[^\n]*pkg=' + re.escape(ui.PACKAGE) + r'[^\n]*id=1902', dump), 'OS notification was not posted'
        deliver(binding, 1234); deliver(binding, 1233)
        assert preferences()['last_seen'] == '1234', 'Duplicate/out-of-order signal changed cursor'
        if api >= 33:
            ui.adb('shell', 'pm', 'revoke', ui.PACKAGE, 'android.permission.POST_NOTIFICATIONS')
            deliver(binding, 1235)
            assert preferences()['last_seen'] == '1234', 'Denied permission consumed a signal'
            ui.adb('shell', 'pm', 'grant', ui.PACKAGE, 'android.permission.POST_NOTIFICATIONS')
        deliver(binding, 1235)
        ui.adb('shell', 'cmd', 'statusbar', 'expand-notifications')
        node, _ = ui.wait_for('لديك إشعار جديد. افتح التطبيق للاطلاع عليه.')
        fixture.screenshot(out, 'native-push-notification')
        ui.tap_node(node)
        ui.wait_for('غير المقروءة: 2')
        fixture.screenshot(out, 'push-opens-authenticated-inbox')

        payment_tested = False
        if api >= 31:
            ui.adb('shell', 'pm', 'set-app-links', '--package', ui.PACKAGE, '2', 'all')
            fixture.back(); fixture.button('حسابي'); ui.wait_for('تفعيل الاشتراك بكود')
            fixture.button('اشتراك'); fixture.button('3 أشهر')
            assert PAY['checkout'] == 0
            fixture.button('الانتقال إلى الدفع الخارجي')
            ui.wait_for('بوابة دفع محلية للاختبار')
            assert PAY['checkout'] == 1
            fixture.screenshot(out, 'external-qa-checkout')
            fixture.button('العودة إلى المرشد')
            time.sleep(1)
            ui.reveal('الدفع بانتظار التأكيد الاختباري', 393, 852)
            assert PAY['status'] == 1 and not fixture.STATE['subscription_active'], 'Return alone granted subscription'
            fixture.screenshot(out, 'checkout-pending-after-return')
            PAY['paid'] = True
            fixture.button('تحقق من حالة آخر عملية')
            ui.wait_for('دراستك مستمرة بلا حدّ الأسئلة المجانية')
            assert PAY['status'] == 2 and fixture.STATE['subscription_active']
            assert PAY['checkout'] == 1, 'Status refresh duplicated a checkout'
            fixture.screenshot(out, 'checkout-confirmed-by-server')
            payment_tested = True
            fixture.back()
        else:
            fixture.back()

        fixture.button('حسابي'); ui.wait_for('تفعيل الاشتراك بكود')
        fixture.button('تسجيل الخروج'); ui.wait_for('سيتم إنهاء جلسة حسابك على هذا الجهاز.')
        fixture.button('تسجيل الخروج'); ui.wait_for('مساحة تعلّمك')
        assert 'binding' not in preferences() and 'user_id' not in preferences()
        deliver(binding, 9999)
        assert 'last_seen' not in preferences(), 'Logged-out user received a signal'
        out.joinpath('services-results.json').write_text(json.dumps({
            'passed': True, 'synthetic_only': True, 'push_account_isolation': True,
            'push_dedup_and_ordering': True, 'notification_posted_and_tapped': True,
            'notification_permission_tested': api >= 33, 'logout_clears_binding': True,
            'hosted_checkout_return_tested': payment_tested,
            'subscription_requires_server_confirmation': payment_tested,
            'live_fcm_transport_tested': False, 'real_payment_performed': False
        }, indent=2))
        print('PASS account push handling and synthetic checkout lifecycle', flush=True)
    except Exception:
        fixture.screenshot(out, 'services-failure')
        raise
    finally:
        server.shutdown(); server.server_close()


if __name__ == '__main__':
    main()

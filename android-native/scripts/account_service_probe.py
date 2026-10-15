"""Focused synthetic account-row, inbox count and external checkout checks."""
import argparse
import json
import threading
from http.server import ThreadingHTTPServer
from pathlib import Path

import anonymous_emulator_qa as ui
import student_fixture_qa as fixture


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    out = parser.parse_args().output
    out.mkdir(parents=True, exist_ok=True)
    ui.PACKAGE = 'com.murshidwizari.student.qa'
    fixture.STATE['subscription_active'] = False
    server = ThreadingHTTPServer(('127.0.0.1', 8765), fixture.Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        apk = Path(__file__).resolve().parents[1] / 'app/build/outputs/apk/qa/app-qa.apk'
        ui.adb('install', '-r', str(apk))
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'pm', 'clear', ui.PACKAGE)
        ui.adb('shell', 'wm', 'size', '393x852')
        ui.adb('shell', 'wm', 'density', '160')
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.adb('reverse', 'tcp:8765', 'tcp:8765')
        ui.launch_native(); ui.enter_login(393, 852)
        fixture.login_field(0, 'student@example.test')
        fixture.login_field(1, 'fixture-pass')
        fixture.button('دخول'); ui.wait_for('حسابي')
        fixture.button('حسابي'); ui.wait_for('تفعيل الاشتراك بكود')
        fixture.fill_register_field('MW-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX', 'INVALID')
        fixture.button('تفعيل الاشتراك')
        assert fixture.STATE['redeem_requests'] == 0
        fixture.hide_keyboard_if_visible()
        fixture.button('حسابي'); ui.wait_for('تفعيل الاشتراك بكود')
        ui.reveal('الإشعارات', 393, 852)
        fixture.screenshot(out, 'account-notification-row')
        tree, _ = ui.ui_tree()
        subtitle_exposed = ui.find(tree, 'لديك 2 غير مقروء') is not None
        fixture.button('الإشعارات'); ui.wait_for('غير المقروءة: 2')
        fixture.screenshot(out, 'inbox-unread-count')
        fixture.back(); fixture.button('حسابي'); ui.wait_for('تفعيل الاشتراك بكود')
        fixture.button('اشتراك'); ui.wait_for('ماذا تكسب عند الاشتراك؟')
        fixture.button('3 أشهر'); ui.wait_for('اختر الباقة')
        assert fixture.STATE['checkout_requests'] == 0
        fixture.button('الانتقال إلى الدفع الخارجي')
        ui.reveal('بوابة الدفع التجريبية مغلقة؛ لم تُنشأ عملية دفع.', 393, 852)
        assert fixture.STATE['checkout_requests'] == 1
        assert fixture.STATE['checkout_payload']['plan'] == 'month3'
        fixture.screenshot(out, 'external-checkout-contract')
        out.joinpath('account-service-results.json').write_text(json.dumps({
            'passed': True, 'synthetic_only': True,
            'account_notification_row_reachable': True,
            'account_unread_subtitle_exposed_to_uiautomator': subtitle_exposed,
            'account_row_screenshot_requires_visual_review': not subtitle_exposed,
            'inbox_unread_count': 2, 'invalid_activation_rejected_locally': True,
            'external_checkout_requests': 1, 'real_payment_performed': False,
        }, indent=2))
        print('PASS account row, authoritative inbox count and synthetic checkout; '
              f'account subtitle accessible={subtitle_exposed}', flush=True)
    except Exception:
        fixture.screenshot(out, 'account-service-failure')
        raise
    finally:
        server.shutdown(); server.server_close()


if __name__ == '__main__':
    main()

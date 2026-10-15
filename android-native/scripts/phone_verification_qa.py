"""Focused native phone/email verification regression. Disposable loopback accounts only."""
import argparse
import json
import threading
import time
from http.server import ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

import anonymous_emulator_qa as ui
import student_fixture_qa as fixture

KIND = 'phone'
CONTACT_LABEL = 'الهاتف'

STATE = {'mode': 'normal', 'verified': False, 'posts': 0, 'sends': 0,
         'csrf': 0, 'calls': [], 'bootstrap_after_rotated_cookie': False}


class Handler(fixture.Handler):
    def reply(self, data, status=200, cookie=None):
        self.send_response(status)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        if cookie:
            self.send_header('Set-Cookie', cookie + '; Path=/; HttpOnly')
        self.end_headers()
        self.wfile.write(json.dumps(data, ensure_ascii=False).encode())

    def respond(self):
        path = urlsplit(self.path).path
        STATE['calls'].append([self.command, path])
        if path in ('/verify-phone.php', '/verify-email.php'):
            self.send_response(403)
            self.end_headers()
            self.wfile.write(b'<html><title>Access denied</title></html>')
            return
        if path == '/api/register.php':
            data = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
            assert data['contact_type'] == KIND
            assert data['identifier'] == {'phone': '07701234567', 'email': 'newstudent@example.test'}[KIND]
            assert data['full_name'] == 'Test Student Demo'
            self.reply({'ok': True, f'{KIND}_verification_required': True,
                        f'masked_{KIND}': '0770****567' if KIND == 'phone' else 'n***@example.test', 'message': 'أرسلنا رمز التحقق التجريبي.'},
                       cookie=f'fixture_pending={KIND}-pending')
            return
        if path == f'/api/verify-{KIND}.php':
            assert f'fixture_pending={KIND}-pending' in self.headers.get('Cookie', ''), \
                'Registration session cookie was lost before verification'
            assert self.headers.get('X-Murshid-Client') == 'AndroidNative'
            if self.command == 'GET':
                if STATE['mode'] == 'expired_session':
                    self.reply({'ok': False, 'verification_session_expired': True,
                                'message': f'انتهت جلسة تأكيد {CONTACT_LABEL}. سجّل الدخول بنفس الحساب.'}, 409)
                else:
                    STATE['csrf'] += 1
                    self.reply({'ok': True, 'csrf': f'fixture-csrf-{STATE["csrf"]}', 'pending': True})
                return
            STATE['posts'] += 1
            data = json.loads(self.rfile.read(int(self.headers.get('Content-Length', 0))))
            assert data['csrf'] == f'fixture-csrf-{STATE["csrf"]}', 'Every tap needs its own current CSRF'
            if STATE['mode'] == 'csrf_expired':
                self.reply({'ok': False, 'message': 'انتهت صلاحية الطلب. اضغط تأكيد الحساب مجدداً.'}, 419)
            elif data['action'] == 'resend':
                if STATE['mode'] == 'cooldown':
                    self.reply({'ok': False, 'retry_after': 60, 'message': 'يمكنك إعادة إرسال الرمز بعد 60 ثانية.'}, 429)
                else:
                    STATE['sends'] += 1
                    self.reply({'ok': True, 'retry_after': 60, 'message': 'أرسلنا رمز تحقق جديدًا.'})
            elif data['code'] == '123456':
                STATE['verified'] = True
                self.reply({'ok': True, 'verified': True}, cookie=f'fixture_pending={KIND}-authenticated')
            else:
                self.reply({'ok': False, 'remaining_attempts': 4, 'message': 'رمز التحقق غير صحيح.'}, 422)
            return
        if path == '/mobile/bootstrap.php':
            assert STATE['verified'], 'Wrong/error receipts must never reach account bootstrap'
            assert f'fixture_pending={KIND}-authenticated' in self.headers.get('Cookie', ''), \
                'Rotated authenticated session cookie did not survive'
            STATE['bootstrap_after_rotated_cookie'] = True
        super().respond()


def code(value):
    field = fixture.wait_editable()
    ui.tap_node(field)
    ui.adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(8):
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    ui.adb('shell', 'input', 'text', value)
    fixture.hide_keyboard_if_visible()


def main():
    global KIND, CONTACT_LABEL
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--kind', choices=['phone', 'email'], default='phone')
    args = parser.parse_args()
    KIND = args.kind
    CONTACT_LABEL = 'الهاتف' if KIND == 'phone' else 'البريد الإلكتروني'
    out = args.output
    out.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1', 8765), Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    ui.PACKAGE = 'com.murshidwizari.student.qa'
    try:
        ui.adb('reverse', 'tcp:8765', 'tcp:8765')
        ui.adb('install', '-r', 'android-native/app/build/outputs/apk/qa/app-qa.apk')
        ui.adb('shell', 'pm', 'clear', ui.PACKAGE)
        ui.adb('shell', 'wm', 'size', '393x852')
        ui.adb('shell', 'wm', 'density', '160')
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.launch_native()
        ui.enter_login(393, 852)
        fixture.button('إنشاء حساب طالب جديد')
        ui.wait_for('بيانات الطالب')
        ui.wait_for('السادس العلمي')
        fixture.fill_register_field('مثال: يونس محمد عباس', 'Test%sStudent%sDemo')
        fixture.button('التالي')
        ui.wait_for('طريقة التسجيل')
        if KIND == 'phone':
            fixture.button('رقم الهاتف')
            fixture.fill_register_field('07XXXXXXXXX', '07701234567')
        else:
            fixture.fill_register_field('example@email.com', 'newstudent@example.test')
        fixture.button('التالي')
        ui.wait_for('حماية الحساب')
        fixture.fill_register_field('8 أحرف على الأقل', 'fixture-pass')
        fixture.fill_register_field('أعد كتابة كلمة المرور', 'fixture-pass')
        fixture.button('إنشاء الحساب')
        ui.wait_for('رمز التحقق')
        code('123456')
        STATE['mode'] = 'expired_session'
        fixture.button('تأكيد الحساب')
        ui.wait_for(f'انتهت جلسة تأكيد {CONTACT_LABEL}. سجّل الدخول بنفس الحساب.')
        assert STATE['posts'] == 0 and not STATE['verified']
        fixture.screenshot(out, f'{KIND}-expired-session')

        STATE['mode'] = 'normal'
        code('000000')
        fixture.button('تأكيد الحساب')
        ui.wait_for('رمز التحقق غير صحيح.')
        assert STATE['posts'] == 1 and not STATE['verified']
        fixture.screenshot(out, f'{KIND}-wrong-code')

        STATE['mode'] = 'cooldown'
        fixture.button('إعادة إرسال الرمز')
        ui.wait_for('يمكنك إعادة إرسال الرمز بعد 60 ثانية.')
        assert STATE['sends'] == 0 and STATE['posts'] == 2
        STATE['mode'] = 'normal'
        fixture.button('إعادة إرسال الرمز')
        ui.wait_for('أرسلنا رمز تحقق جديدًا.')
        assert STATE['sends'] == 1 and STATE['posts'] == 3

        code('123456')
        STATE['mode'] = 'csrf_expired'
        fixture.button('تأكيد الحساب')
        ui.wait_for('انتهت صلاحية الطلب. اضغط تأكيد الحساب مجدداً.')
        assert STATE['posts'] == 4 and not STATE['verified']
        time.sleep(.6)
        assert STATE['posts'] == 4, 'A rejected POST must not auto retry'
        STATE['mode'] = 'normal'
        fixture.button('تأكيد الحساب')
        ui.wait_for('حسابي')
        assert STATE['posts'] == 5 and STATE['verified']
        assert STATE['bootstrap_after_rotated_cookie']
        assert not any(path in ('/verify-phone.php', '/verify-email.php') for _, path in STATE['calls'])
        fixture.screenshot(out, f'{KIND}-verified-home')
        (out / f'{KIND}-results.json').write_text(json.dumps({
            'passed': True, 'kind': KIND, 'synthetic_only': True, 'session_cookie_roundtrip': True,
            'authenticated_cookie_rotation': True, 'legacy_html_not_used': True,
            'expired_session_no_post': True, 'wrong_code_blocked': True,
            'cooldown_preserved': True, 'one_resend': True, 'csrf_refresh_after_rejection': True,
            'no_silent_post_retry': True, 'correct_code_bootstraps': True,
        }, indent=2))
    finally:
        server.shutdown()


if __name__ == '__main__':
    main()

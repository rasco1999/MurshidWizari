"""Run the real changed PHP controller over HTTP against disposable session fixtures.

Existing OTP/provider/DB functions are fixture dependencies, not production writes.
"""
import http.cookiejar
import json
import shutil
import socket
import subprocess
import tempfile
import time
import unittest
import urllib.error
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


class PhoneEndpointTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        dest = Path(cls.temp.name)
        (dest / 'api').mkdir()
        (dest / 'config').mkdir()
        (dest / '_test').mkdir()
        shutil.copyfile(ROOT / 'public_html/api/verify-phone.php', dest / 'api/verify-phone.php')
        shutil.copyfile(ROOT / 'tests/config.fixture.php', dest / 'config/config.php')
        (dest / '_test/seed.php').write_text("<?php require __DIR__.'/../config/config.php'; "
            "$data=json_decode(file_get_contents('php://input'),true)?:[]; "
            "foreach($data as $k=>$v)$_SESSION[$k]=$v; "
            "json_response(['ok'=>true,'session_id'=>session_id()]);")
        (dest / '_test/state.php').write_text("<?php require __DIR__.'/../config/config.php'; "
            "json_response(['state'=>$_SESSION,'session_id'=>session_id()]);")
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        cls.base = f'http://127.0.0.1:{port}/'
        cls.log = open(dest / 'php.log', 'w+')
        cls.proc = subprocess.Popen(['php', '-S', f'127.0.0.1:{port}', '-t', str(dest)],
                                    stdout=cls.log, stderr=cls.log)
        for _ in range(40):
            try:
                urllib.request.urlopen(cls.base + '_test/state.php', timeout=1).close()
                break
            except OSError:
                time.sleep(.1)
        else:
            cls.log.seek(0)
            raise RuntimeError(cls.log.read())

    @classmethod
    def tearDownClass(cls):
        cls.proc.terminate()
        cls.proc.wait(timeout=5)
        cls.log.close()
        cls.temp.cleanup()

    def setUp(self):
        self.cookies = http.cookiejar.CookieJar()
        self.client = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(self.cookies))
        _, seeded, _ = self.request('_test/seed.php', {'pending': True})
        self.first_session_id = seeded['session_id']

    def request(self, path='api/verify-phone.php', data=None, method=None):
        raw = data if isinstance(data, bytes) else json.dumps(data).encode() if data is not None else None
        req = urllib.request.Request(self.base + path, data=raw, method=method,
                                     headers={'Content-Type': 'application/json'})
        try:
            reply = self.client.open(req, timeout=5)
        except urllib.error.HTTPError as error:
            reply = error
        with reply:
            return reply.code, json.load(reply), reply.headers

    def seed(self, **values):
        self.request('_test/seed.php', values)

    def state(self):
        return self.request('_test/state.php')[1]

    def post(self, action='verify', code='123456', **extra):
        _, pending, _ = self.request()
        return self.request(data={'csrf': pending['csrf'], 'action': action, 'code': code, **extra})

    def test_get_is_read_only_and_never_restores_an_old_account(self):
        status, data, headers = self.request()
        self.assertEqual(status, 200)
        self.assertTrue(data['pending'])
        self.assertEqual(len(data['csrf']), 64)
        self.assertNotIn('code', data)
        self.assertNotIn('code_hash', data)
        self.assertIn('no-store', headers['Cache-Control'])
        self.assertNotIn('attempts', self.state()['state'])
        self.assertNotIn('sends', self.state()['state'])

    def test_lost_pending_session_is_explicit_and_does_not_verify(self):
        self.seed(expired=True)
        for body in (None, {'csrf':'x', 'action':'verify', 'code':'123456'}):
            status, data, _ = self.request(data=body)
            self.assertEqual(status, 409)
            self.assertTrue(data['verification_session_expired'])
        self.assertNotIn('attempts', self.state()['state'])

    def test_csrf_failure_does_not_consume_an_otp_attempt(self):
        status, data, _ = self.request(data={'csrf':'bad', 'action':'verify', 'code':'123456'})
        self.assertEqual(status, 419)
        self.assertTrue(data['csrf_expired'])
        self.assertNotIn('attempts', self.state()['state'])

    def test_wrong_or_expired_otp_does_not_create_a_login(self):
        status, data, _ = self.post(code='000000')
        self.assertEqual(status, 422)
        self.assertFalse(data['ok'])
        self.assertEqual(data['remaining_attempts'], 4)
        self.seed(otp_expired=True)
        self.assertEqual(self.post()[0], 422)
        self.assertNotIn('user_id', self.state()['state'])

    def test_success_rotates_the_session_and_keeps_the_session_bound_user(self):
        self.seed(_pending_remember_me=1)
        status, data, _ = self.post(user_id=999999, phone='07799999999')
        self.assertEqual(status, 200)
        self.assertTrue(data['verified'])
        snapshot = self.state()
        state = snapshot['state']
        self.assertNotEqual(snapshot['session_id'], self.first_session_id)
        self.assertEqual(state['user_id'], 900001)
        self.assertEqual(state['target_id'], 900001)
        self.assertEqual(state['attempts'], 1)
        self.assertTrue(state['remember_issued'])
        self.assertNotIn('pending', state)
        self.assertNotIn('_pending_remember_me', state)
        self.assertEqual(len(state['_account_session_token']), 64)

    def test_resend_uses_pending_phone_and_preserves_cooldown(self):
        self.seed(cooldown=60)
        status, data, headers = self.post(action='resend')
        self.assertEqual(status, 429)
        self.assertEqual(headers['Retry-After'], '60')
        self.assertNotIn('sends', self.state()['state'])
        self.seed(cooldown=0)
        self.assertEqual(self.post(action='resend', user_id=123, phone='07799999999')[0], 200)
        state = self.state()['state']
        self.assertEqual(state['target_phone'], '07701234567')
        self.assertEqual(state['target_id'], 900001)
        self.assertEqual(state['sends'], 1)
        self.assertNotIn('user_id', state)

    def test_active_other_device_never_receives_a_second_session(self):
        self.seed(guard_busy=True)
        status, data, _ = self.post()
        self.assertEqual(status, 409)
        self.assertTrue(data['phone_verified'])
        self.assertTrue(data['account_in_use'])
        self.assertNotIn('user_id', self.state()['state'])

    def test_guard_failure_never_creates_an_unprotected_session(self):
        self.seed(guard_error=True)
        status, data, _ = self.post()
        self.assertEqual(status, 503)
        self.assertTrue(data['login_required'])
        self.assertNotIn('user_id', self.state()['state'])

    def test_unsupported_and_malformed_requests_do_not_verify(self):
        self.assertEqual(self.request(method='PUT')[0], 405)
        self.assertEqual(self.request(data=b'{bad')[0], 400)
        self.assertEqual(self.post(action='delete')[0], 422)
        self.assertNotIn('attempts', self.state()['state'])


if __name__ == '__main__':
    unittest.main(verbosity=2)

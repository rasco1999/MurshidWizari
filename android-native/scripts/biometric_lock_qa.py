"""White-box lock-lifecycle regression; does NOT claim real biometric acceptance.

Requires the disposable loopback QA package already authenticated by the fixture.
Only the local synthetic lock preference is seeded. No session cookies, keys,
passwords or real account files are read or copied.
"""
import argparse
import json
import threading
import time
from http.server import ThreadingHTTPServer
from pathlib import Path

import anonymous_emulator_qa as ui
import student_fixture_qa as fixture


def assert_shield():
    ui.wait_for('التطبيق مقفل')
    tree, _ = ui.ui_tree()
    texts = {node.attrib.get('text') for node in tree.iter('node')}
    assert 'حسابي' not in texts and fixture.USER['name'] not in texts, \
        'Underlying account must not be accessible through the lock'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    out = parser.parse_args().output
    out.mkdir(parents=True, exist_ok=True)
    ui.PACKAGE = 'com.murshidwizari.student.qa'
    assert ui.PACKAGE.endswith('.qa')
    server = ThreadingHTTPServer(('127.0.0.1', 8765), fixture.Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    fixture.STATE['bootstrap_delay_seconds'] = 6
    try:
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        prefs = out / 'synthetic-lock-preferences.xml'
        prefs.write_text('<?xml version="1.0" encoding="utf-8"?><map>'
                         '<boolean name="biometric_lock" value="true" />'
                         '<long name="biometric_background_at" value="' +
                         str(int(time.time() * 1000) - 60_000) + '" /></map>')
        device_prefs = '/data/local/tmp/murshid-synthetic-lock.xml'
        ui.adb('push', str(prefs), device_prefs)
        ui.adb('shell', 'run-as', ui.PACKAGE, 'cp', device_prefs,
               'shared_prefs/native_preferences.xml')
        ui.adb('reverse', 'tcp:8765', 'tcp:8765')
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        assert_shield()
        time.sleep(8)
        assert any(path == '/mobile/bootstrap.php' for _, path in fixture.STATE['calls'])
        assert_shield()
        fixture.screenshot(out, 'lock-after-delayed-bootstrap')

        # Back may leave the app, but must not navigate to an uncovered account.
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(.4)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        assert_shield()
        # The pending-unlock receipt must survive a restart within 20 seconds.
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        assert_shield()
        time.sleep(8)
        assert_shield()
        fixture.screenshot(out, 'lock-after-process-restart')
        out.joinpath('lock-results.json').write_text(json.dumps({
            'passed': True, 'synthetic_only': True,
            'shield_survives_bootstrap_and_back': True,
            'pending_unlock_survives_process_restart': True,
            'real_biometric_authentication_tested': False
        }, indent=2))
        print('PASS native lock shield survives bootstrap, Back and process restart', flush=True)
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()

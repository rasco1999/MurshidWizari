"""Enroll an emulator fingerprint and exercise the real Android biometric prompt.

No authentication callback is mocked and no app lock preference is seeded.
The emulator PIN and fingerprint are disposable synthetic test credentials.
"""
import argparse
import json
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET
from http.server import ThreadingHTTPServer
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'android-native/scripts'))
import anonymous_emulator_qa as ui
import student_fixture_qa as fixture


def touch(finger_id=1):
    ui.adb('emu', 'finger', 'touch', str(finger_id))
    time.sleep(.3)
    ui.adb('emu', 'finger', 'remove')


def enroll(out):
    ui.adb('shell', 'locksettings', 'set-pin', '2468')
    ui.adb('shell', 'am', 'start', '-a', 'android.settings.FINGERPRINT_ENROLL')
    deadline = time.monotonic() + 140
    pin_entered = False
    scans = 0
    while time.monotonic() < deadline:
        root, _ = ui.ui_tree()
        texts = ui.visible_texts(root)
        print('ENROLL', texts, flush=True)
        if any('Fingerprint added' in t or 'Fingerprint setup complete' in t for t in texts):
            fixture.screenshot(out, 'fingerprint-enrolled-in-settings')
            done = ui.find(root, 'Done')
            if done is None: done = ui.find(root, 'DONE')
            if done is not None: ui.tap_node(done)
            return
        fields = [n for n in root.iter('node') if n.attrib.get('class') == 'android.widget.EditText']
        if fields and not pin_entered:
            ui.tap_node(fields[0]); ui.adb('shell', 'input', 'text', '2468')
            ui.adb('shell', 'input', 'keyevent', 'KEYCODE_ENTER')
            pin_entered = True; time.sleep(1); continue
        clicked = False
        for label in ('I agree', 'I AGREE', 'More', 'MORE', 'Next', 'NEXT', 'Continue', 'Start'):
            node = ui.find(root, label)
            if node is not None and node.attrib.get('enabled') != 'false':
                ui.tap_node(node); clicked = True; time.sleep(.6); break
        if not clicked:
            touch(); scans += 1
        time.sleep(.5)
    fixture.screenshot(out, 'enrollment-failure')
    raise AssertionError(f'Could not finish OS enrollment after {scans} scans')


def native_preferences():
    try:
        raw = ui.adb('shell', 'run-as', ui.PACKAGE, 'cat', 'shared_prefs/native_preferences.xml')
    except subprocess.CalledProcessError:
        return {}
    return {n.attrib['name']: n.attrib.get('value', n.text) for n in ET.fromstring(raw)}


def lock_switch():
    ui.reveal('قفل حقيقي ببصمة الجهاز', 393, 852)
    root, _ = ui.ui_tree()
    switch = next(n for n in root.iter('node') if n.attrib.get('class') == 'android.widget.Switch'
                  and n.attrib.get('content-desc') == 'قفل حقيقي ببصمة الجهاز')
    assert switch.attrib['enabled'] == 'true', 'Enrolled fingerprint is unavailable to the app'
    ui.tap_node(switch)


def main():
    parser = argparse.ArgumentParser(); parser.add_argument('--output', type=Path, required=True)
    out = parser.parse_args().output; out.mkdir(parents=True, exist_ok=True)
    ui.PACKAGE = 'com.murshidwizari.student.qa'
    assert ui.adb('shell', 'getprop', 'ro.kernel.qemu').strip() == b'1', 'Emulator only'
    server = ThreadingHTTPServer(('127.0.0.1', 8765), fixture.Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        ui.adb('shell', 'wm', 'size', '393x852'); ui.adb('shell', 'wm', 'density', '160')
        enroll(out)
        ui.adb('install', '-r', 'android-native/app/build/outputs/apk/qa/app-qa.apk')
        ui.adb('reverse', 'tcp:8765', 'tcp:8765')
        ui.launch_native(); ui.enter_login(393, 852)
        fixture.login_field(0, 'student@example.test'); fixture.login_field(1, 'fixture-pass')
        fixture.button('دخول'); ui.wait_for('حسابي'); fixture.button('حسابي')
        lock_switch(); ui.wait_for('أكد بصمة الجهاز لتفعيل قفل المرشد الوزاري')
        fixture.screenshot(out, 'real-os-prompt-enable')
        node, _ = ui.wait_for('إلغاء'); ui.tap_node(node)
        assert native_preferences().get('biometric_lock') != 'true', 'Cancel enabled lock'
        lock_switch(); ui.wait_for('أكد بصمة الجهاز لتفعيل قفل المرشد الوزاري')
        touch(2); time.sleep(.6)
        assert native_preferences().get('biometric_lock') != 'true', 'Unenrolled finger enabled lock'
        touch(1); time.sleep(1)
        assert native_preferences().get('biometric_lock') == 'true', 'OS success did not enable lock'
        fixture.screenshot(out, 'lock-enabled-after-os-success')
        ui.adb('shell', 'input', 'keyevent', 'KEYCODE_HOME'); time.sleep(23)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        ui.wait_for('افتح المرشد الوزاري باستخدام البصمة')
        node, _ = ui.wait_for('إلغاء'); ui.tap_node(node)
        ui.wait_for('التطبيق مقفل')
        root, _ = ui.ui_tree()
        assert ui.find(root, fixture.USER['name']) is None and ui.find(root, 'حسابي') is None
        fixture.screenshot(out, 'cancel-keeps-account-locked')
        fixture.button('فتح باستخدام البصمة'); ui.wait_for('افتح المرشد الوزاري باستخدام البصمة')
        touch(1); ui.wait_for('حسابي')
        assert native_preferences().get('biometric_unlock_required') != 'true'
        fixture.screenshot(out, 'account-restored-after-os-success')
        out.joinpath('biometric-prompt-results.json').write_text(json.dumps({
            'passed': True, 'emulator_only': True, 'physical_phone_tested': False,
            'real_android_prompt_tested': True, 'os_fingerprint_enrollment': True,
            'cancel_does_not_enable_or_unlock': True, 'unrecognized_finger_rejected': True,
            'recognized_finger_enables_and_unlocks': True
        }, indent=2))
        print('PASS real OS fingerprint enrollment, cancellation, rejection and successful unlock', flush=True)
    except Exception:
        fixture.screenshot(out, 'biometric-failure')
        raise
    finally:
        server.shutdown(); server.server_close()


if __name__ == '__main__': main()

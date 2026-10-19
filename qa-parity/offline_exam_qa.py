"""Real UI/offline process-death and ambiguous-receipt checks on synthetic data only."""
import argparse
import json
import threading
import time
from http.server import ThreadingHTTPServer
from pathlib import Path

import anonymous_emulator_qa as ui
import student_fixture_qa as fixture


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', type=Path, required=True)
    out = parser.parse_args().output
    out.mkdir(parents=True, exist_ok=True)
    ui.PACKAGE = 'com.murshidiq.app.qa'
    server = ThreadingHTTPServer(('127.0.0.1', 8765), fixture.Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()

    def open_offline_topic():
        ui.wait_for('وضع المراجعة دون إنترنت', seconds=28)
        fixture.button('اختباراتي المحفوظة')
        ui.wait_for('اختباراتي دون إنترنت')
        fixture.button('موضوع فحص افتراضي')
        ui.wait_for('ما حاصل جمع اثنين وثلاثة؟')

    try:
        qa_apk = Path(__file__).resolve().parents[1] / 'app/build/outputs/apk/qa/app-qa.apk'
        ui.adb('install', '-r', str(qa_apk))
        ui.adb('shell', 'wm', 'size', '393x852')
        ui.adb('shell', 'wm', 'density', '160')
        ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        ui.adb('shell', 'svc', 'wifi', 'enable')
        ui.adb('shell', 'svc', 'data', 'enable')
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'pm', 'clear', ui.PACKAGE)
        ui.adb('reverse', 'tcp:8765', 'tcp:8765')
        ui.launch_native(); ui.enter_login(393, 852)
        fixture.login_field(0, 'student@example.test'); fixture.login_field(1, 'fixture-pass')
        fixture.button('دخول'); ui.wait_for('حسابي')
        fixture.button('الاختبارات'); fixture.button('الرياضيات')
        fixture.button('موضوع فحص افتراضي'); ui.wait_for('ما حاصل جمع اثنين وثلاثة؟')
        assert not fixture.STATE['answers'], 'Download consumed an answer'
        ui.adb('shell', 'svc', 'wifi', 'disable'); ui.adb('shell', 'svc', 'data', 'disable')
        time.sleep(1)
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        open_offline_topic()
        fixture.button('خمسة'); fixture.button('حفظ الإجابة على الجهاز')
        ui.wait_for('نتيجة تدريبية: إجابة صحيحة ✓')
        fixture.screenshot(out, 'offline-answer-feedback')
        fixture.button('السؤال التالي'); ui.wait_for('اكتب ناتج أربعة زائد أربعة.')
        fixture.input_text(fixture.wait_editable(), '8'); fixture.hide_keyboard_if_visible()
        fixture.button('حفظ الإجابة على الجهاز')
        ui.wait_for('نتيجة تدريبية: إجابة صحيحة ✓')
        assert not fixture.STATE['answers'], 'Offline answers reached server'
        assert not any(p == '/api/progress.php' for _, p in fixture.STATE['calls'])
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        open_offline_topic()
        ui.wait_for('تم حفظ إجابتك على هذا الجهاز ✓')
        fixture.screenshot(out, 'offline-answer-survives-process-death')
        # Change only the second question on the remote side. Its old answer
        # must remain a draft and never be graded against the new revision.
        fixture.QUESTIONS[1]['text'] = 'مسألة تغيرت بعد تنزيلها'
        fixture.STATE['lose_grade_reply_once'] = True
        ui.adb('shell', 'svc', 'wifi', 'enable'); ui.adb('shell', 'svc', 'data', 'enable')
        time.sleep(2)
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        ui.wait_for('حسابي', seconds=28)
        deadline = time.time() + 20
        while time.time() < deadline and '101' not in fixture.STATE['answers']:
            time.sleep(.4)
        assert '101' in fixture.STATE['answers'], 'Queued answer did not sync'
        ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
        ui.adb('shell', 'am', 'start', '-n', f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
        ui.wait_for('حسابي', seconds=28)
        fixture.button('مدربي الذكي · مراجعة دون إنترنت'); ui.wait_for('مدربك الذكي')
        fixture.button('اختباراتي المحفوظة دون إنترنت')
        ui.wait_for('إجابات مؤجلة للمزامنة: 1', seconds=25)
        fixture.button('موضوع فحص افتراضي'); ui.wait_for('أجبت سابقاً على الخادم')
        fixture.screenshot(out, 'synced-answer-keeps-durable-receipt')
        assert '102' not in fixture.STATE['answers'], 'Edited question was silently regraded'
        grade_calls = sum(p == '/api/progress.php' for _, p in fixture.STATE['calls'])
        assert grade_calls == 1, f'Ambiguous receipt duplicated submission: {grade_calls}'
        out.joinpath('offline-results.json').write_text(json.dumps({
            'passed': True, 'synthetic_only': True, 'no_offline_network_writes': True,
            'process_death_preserves_answers': True, 'ambiguous_receipt_reconciled': True,
            'edited_question_draft_preserved': True, 'official_grading_requests': grade_calls,
        }, indent=2))
        print('PASS offline process death, local feedback, edited revisions and grade-once sync', flush=True)
    except Exception:
        fixture.screenshot(out, 'offline-exam-failure')
        raise
    finally:
        ui.adb('shell', 'svc', 'wifi', 'enable'); ui.adb('shell', 'svc', 'data', 'enable')
        server.shutdown(); server.server_close()


if __name__ == '__main__':
    main()

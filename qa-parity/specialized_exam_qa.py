"""Native specialized-question acceptance against synthetic, loopback-only data.

No production credentials, paid submissions or personal student content are used.
Runs after the main fixture in the same disposable emulator, in the QA package.
"""
import argparse
import json
import threading
import time
from http.server import ThreadingHTTPServer
from pathlib import Path

import student_fixture_qa as fixture
import anonymous_emulator_qa as ui


def encoded(value):
    return json.dumps(value, ensure_ascii=False, separators=(',', ':'))


QUESTIONS = [
    {'id': 201, 'type': 'true_false', 'text': 'الشمس نجم: صح أم خطأ؟',
     'correct_answer': 'صح', 'explanation': 'الشمس نجم يشع الضوء والحرارة.'},
    {'id': 202, 'type': 'calculation', 'text': 'أكمل مراحل المسألة الحسابية الافتراضية.',
     'calculation': {'stages': [
         {'key': 'given', 'kind': 'info', 'label': 'المعطيات', 'value': '7 + 5'},
         {'key': 'value', 'kind': 'number', 'label': 'الناتج', 'placeholder': 'الناتج العددي', 'unit': 'وحدة'},
         {'key': 'operation', 'kind': 'choice', 'label': 'العملية الحسابية', 'options': [
             {'value': 'add', 'label': 'عملية الجمع'}, {'value': 'subtract', 'label': 'عملية الطرح'}]}]},
     'correct_answer': encoded({'stages': {'value': '12', 'operation': 'add'}}),
     'explanation': 'نجمع سبعة وخمسة فنحصل على اثني عشر.'},
    {'id': 203, 'type': 'text', 'text': 'صل كل كلمة إنجليزية بمعناها المناسب.',
     'english_match': {'left': {'1': 'Sun', '2': 'Earth'}, 'right': {'a': 'Star', 'b': 'Planet'}},
     'correct_answer': '1-a, 2-b', 'explanation': 'الشمس نجم والأرض كوكب.'},
    {'id': 204, 'type': 'match', 'text': 'حدد نوع المد في الكلمة واكتب السبب.',
     'match_items': [{'id': 71, 'mad_word': 'قال'}],
     'correct_answer': encoded({'items': {'71': {'type': 'مد طبيعي', 'reason': 'madd_letter'}}}),
     'explanation': 'هذا مثال افتراضي للتحقق من نقل نوع المد والسبب إلى الخادم.'},
    {'id': 205, 'type': 'genetics', 'text': 'أكمل مربعي بانيت في المسألة الوراثية الافتراضية.',
     'genetics': {'stages': [
         {'key': 'given', 'kind': 'info', 'label': 'الأبوين', 'value': 'AA × Aa'},
         {'key': 'grid', 'kind': 'punnett', 'label': 'مربع بانيت', 'cells': [
             {'placeholder': 'التركيب الأول'}, {'placeholder': 'التركيب الثاني'}]}]},
     'correct_answer': encoded({'stages': {'grid_0': 'AA', 'grid_1': 'Aa'}}),
     'explanation': 'في هذا المثال الاختباري التركيبان هما AA وAa.'}
]


def top():
    for _ in range(3):
        ui.adb('shell', 'input', 'swipe', '196', '230', '196', '710', '230')
    time.sleep(.25)


def pick(description, choice):
    # A stage's caption may have the same text as its Spinner's accessible
    # label. Tap the actual native Spinner, not the adjacent caption.
    for attempt in range(6):
        tree, _ = ui.ui_tree()
        spinner = next((node for node in tree.iter('node')
                        if node.attrib.get('class') == 'android.widget.Spinner'
                        and node.attrib.get('content-desc') == description), None)
        if spinner is not None:
            ui.tap_node(spinner)
            break
        ui.adb('shell', 'input', 'swipe', '196', '700', '196', '260', '270')
        time.sleep(.3)
    else:
        raise AssertionError(f'Cannot reveal native picker {description!r}')
    fixture.button(choice)
    time.sleep(.25)


def tap_match(prefix):
    for direction in ('up', 'down', 'up'):
        for _ in range(8):
            tree, _ = ui.ui_tree()
            node = next((n for n in tree.iter('node') if n.attrib.get('content-desc', '').startswith(prefix)), None)
            if node is not None:
                l,t,r,b = map(int, ui.BOUNDS.fullmatch(node.attrib['bounds']).groups())
                if r>l and b>t and t>40 and b<782:
                    ui.tap_node(node);time.sleep(.2);return
            a,b = (680,280) if direction == 'up' else (280,680)
            ui.adb('shell','input','swipe','196',str(a),'196',str(b),'250')
    raise AssertionError('Native matching control missing: '+prefix)


def request_count():
    return sum(path == '/api/progress.php' for _, path in fixture.STATE['calls'])


def next_question(marker):
    fixture.button('التالي ›')
    ui.wait_for(marker)


def submit(qid, expected, correct=True):
    count = request_count()
    fixture.button('إرسال الإجابة')
    ui.wait_for('إجابة صحيحة' if correct else 'راجع الإجابة')
    assert request_count() == count + 1, 'One explicit submit must produce exactly one request'
    answer = fixture.STATE['answers'][str(qid)]
    actual = answer['text']
    if isinstance(expected, dict):
        actual = json.loads(actual)
    assert actual == expected, (qid, actual, expected)
    assert answer['correct'] == correct, (qid, answer)


def run(out):
    fixture.QUESTIONS[:] = QUESTIONS
    ui.PACKAGE = 'com.murshidiq.app.qa'
    ui.adb('install', '-r', str(Path(__file__).resolve().parents[1] / 'app/build/outputs/apk/qa/app-qa.apk'))
    ui.adb('shell', 'am', 'force-stop', ui.PACKAGE)
    ui.adb('shell', 'pm', 'clear', ui.PACKAGE)
    ui.adb('reverse', 'tcp:8765', 'tcp:8765')
    ui.adb('shell', 'wm', 'size', '393x852')
    ui.adb('shell', 'wm', 'density', '160')
    ui.adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    ui.launch_native()
    ui.enter_login(393, 852)
    fixture.login_field(0, 'student@example.test')
    fixture.login_field(1, 'fixture-pass')
    fixture.button('دخول')
    ui.wait_for('حسابي')
    fixture.button('الاختبارات')
    fixture.button('الرياضيات')
    fixture.button('موضوع فحص افتراضي')
    ui.wait_for(QUESTIONS[0]['text'])

    fixture.button('خطأ')
    fixture.button('صح')
    assert request_count() == 0, 'True/false selection must not submit until confirmed'
    fixture.screenshot(out, 'true-false-selected')
    submit(201, 'صح')

    next_question(QUESTIONS[1]['text'])
    fixture.button('إرسال الإجابة')
    assert request_count() == 1, 'Incomplete stages cannot be sent'
    top()
    fixture.fill_register_field('الناتج العددي', '12')
    pick('العملية الحسابية', 'عملية الجمع')
    fixture.button('‹ السابق')
    ui.wait_for(QUESTIONS[0]['text'])
    next_question(QUESTIONS[1]['text'])
    ui.reveal('12', 393, 852)
    ui.reveal('عملية الجمع', 393, 852)
    assert request_count() == 1, 'Draft restoration cannot submit'
    fixture.screenshot(out, 'calculation-draft-restored')
    submit(202, {'stages': {'value': '12', 'operation': 'add'}})

    next_question('صِل كل عنصر من القائمة A بما يناسبه من القائمة B.')
    tap_match('A1:');tap_match('Ba:')
    tap_match('A2:');tap_match('Ba:')
    # Reusing B moves its connection; the incomplete board must not submit.
    ui.wait_for('تم توصيل 1 من 2')
    fixture.button('إرسال الإجابة')
    assert request_count() == 2, 'Incomplete/duplicate match destinations cannot be sent'
    top()
    tap_match('A1:');tap_match('Ba:')
    tap_match('A2:');tap_match('Bb:')
    ui.wait_for('تم توصيل 2 من 2')
    fixture.screenshot(out, 'english-matching-selected')
    submit(203, '1-a, 2-b')

    next_question(QUESTIONS[3]['text'])
    pick('نوع المد: قال', 'مد طبيعي')
    fixture.fill_register_field('اكتب السبب', 'madd_letter')
    fixture.screenshot(out, 'tajweed-complete')
    submit(204, {'items': {'71': {'type': 'مد طبيعي', 'reason': 'madd_letter'}}})

    next_question(QUESTIONS[4]['text'])
    fixture.fill_register_field('التركيب الأول', 'AA')
    fixture.fill_register_field('التركيب الثاني', 'aa')
    fixture.button('‹ السابق')
    ui.wait_for(QUESTIONS[3]['text'])
    next_question(QUESTIONS[4]['text'])
    ui.reveal('AA', 393, 852)
    ui.reveal('aa', 393, 852)
    fixture.screenshot(out, 'genetics-draft-restored')
    submit(205, {'stages': {'grid_0': 'AA', 'grid_1': 'aa'}}, correct=False)
    fixture.button('عرض النتيجة ›')
    ui.wait_for('نتيجة الاختبار')
    fixture.screenshot(out, 'specialized-exam-result')
    fixture.button('راجع الأسئلة التي تحتاج انتباها')
    ui.wait_for(QUESTIONS[4]['text'])
    ui.wait_for('راجع الإجابة')
    assert request_count() == 5, 'Review cannot resubmit answers'
    fixture.screenshot(out, 'first-wrong-review')
    out.joinpath('specialized-results.json').write_text(json.dumps({
        'passed': True, 'synthetic_only': True, 'explicit_submit': True,
        'true_false': True, 'calculation': True, 'english_matching': True,
        'tajweed': True, 'punnett_genetics': True, 'drafts_survive_previous_next': True,
        'incomplete_and_duplicate_choices_rejected_locally': True,
        'review_opens_first_wrong': True, 'grading_requests': request_count()
    }, indent=2))
    print('PASS specialized native exam forms, payloads, local drafts and review', flush=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--output', required=True, type=Path)
    out = parser.parse_args().output
    out.mkdir(parents=True, exist_ok=True)
    server = ThreadingHTTPServer(('127.0.0.1', 8765), fixture.Handler)
    threading.Thread(target=server.serve_forever, daemon=True).start()
    try:
        run(out)
    except Exception:
        fixture.screenshot(out, 'failure')
        raise
    finally:
        server.shutdown()
        server.server_close()


if __name__ == '__main__':
    main()

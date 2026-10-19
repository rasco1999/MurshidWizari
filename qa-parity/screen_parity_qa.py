"""Tap real native pages against a synthetic server; capture the final APK UI."""
import argparse, json, threading, time
from pathlib import Path
from http.server import ThreadingHTTPServer
import anonymous_emulator_qa as ui
import student_fixture_qa as f


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();out=args.output;out.mkdir(parents=True,exist_ok=True)
    ui.PACKAGE='com.murshidiq.app.qa'
    server=ThreadingHTTPServer(('127.0.0.1',8765),f.Handler)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    ui.adb('reverse','tcp:8765','tcp:8765')
    apk=Path(__file__).resolve().parents[1]/'app/build/outputs/apk/qa/app-qa.apk'
    ui.adb('install','-r',str(apk))
    f.BOOT['ads'].append({'id':2,'school':'مدرسة ثانية افتراضية','text':'إعلان المدرسة الثانية للتحقق من ظهور جميع المدارس.','active':True})
    results=[]
    def capture(name,marker):
        ui.wait_for(marker);ui.capture(out,name,marker)
        results.append(name)
    def reveal(text):
        for direction in ('up','down','up'):
            for _ in range(10):
                root,_=ui.ui_tree();node=ui.find(root,text)
                if node is not None:
                    bounds=ui.BOUNDS.fullmatch(node.attrib.get('bounds',''))
                    if bounds:
                        l,t,r,b=map(int,bounds.groups())
                        if r>l and b>t and 24<(t+b)//2<828:return node
                scroll=next((n for n in root.iter('node') if n.attrib.get('class')=='android.widget.ScrollView'
                    and n.attrib.get('scrollable')=='true'),None)
                if scroll is None:
                    time.sleep(.8)
                    continue
                l,t,r,b=map(int,ui.BOUNDS.fullmatch(scroll.attrib['bounds']).groups())
                start=t+(b-t)*3//4;end=t+(b-t)//4
                if direction=='down':start,end=end,start
                ui.adb('shell','input','swipe',str((l+r)//2),str(start),str((l+r)//2),str(end),'250')
                time.sleep(.15)
        raise AssertionError(('Missing native control',text,ui.visible_texts(root)))
    def tap(text):
        ui.tap_node(reveal(text));time.sleep(.6)
        print('TAP',text,flush=True)
    try:
        ui.adb('shell','wm','size','393x852');ui.adb('shell','wm','density','160')
        ui.adb('shell','settings','put','system','font_scale','1.0')
        ui.adb('shell','pm','clear',ui.PACKAGE)
        ui.launch_native();capture('01-guest','تعلّم بتركيز. تقدّم بثقة.')
        ui.enter_login(393,852);capture('02-login','سجّل الدخول وواصل دراستك من حيث توقفت')
        tap('إظهار كلمة المرور');ui.wait_for('إخفاء كلمة المرور');tap('إخفاء كلمة المرور')
        tap('نسيت كلمة المرور؟');capture('03-password-recovery','استعادة كلمة المرور')
        f.back();ui.wait_for('سجّل الدخول وواصل دراستك من حيث توقفت')
        tap('إنشاء حساب طالب جديد');capture('04-registration','بيانات الطالب')
        f.back();ui.wait_for('سجّل الدخول وواصل دراستك من حيث توقفت')
        f.login_field(0,'student@example.test');f.login_field(1,'fixture-pass');tap('دخول')
        capture('05-home','حسابي')
        tap('الإشعارات');capture('06-notifications','الإشعارات')
        f.back();ui.wait_for('حسابي')
        tap('الإعلانات');reveal('مدرسة ثانية افتراضية');capture('07-all-school-ads','مدرسة ثانية افتراضية')
        tap('المرشحات');reveal('مرشح فحص افتراضي');tap('عرض الإجابة والتوضيح⌄')
        capture('08-prediction-expanded','إجابة المرشح الافتراضي')
        tap('حسابي');reveal('مراجعاتي المحفوظة');capture('09-account','مراجعاتي المحفوظة')
        tap('مراجعاتي المحفوظة');capture('10-coach','مدربك الذكي')
        f.back();reveal('مراجعاتي المحفوظة')
        tap('خدمة العملاء');capture('11-support','اكتب رسالة لفريق الدعم')
        # Support and Coach must return to Account, including after async redraw.
        f.back();reveal('مراجعاتي المحفوظة');capture('12-account-back','مراجعاتي المحفوظة')
        for theme in ('بنفسجي','أورورا','ملكي'):
            tap('اختيار ألوان '+theme);time.sleep(1);reveal('اختر هويتك اللونية')
            capture('13-palette-'+str(len(results)),'اختر هويتك اللونية')
        for mode in ('داكن','فاتح','النظام'):
            tap('إضاءة '+mode);time.sleep(1);reveal('إضاءة التطبيق')
            capture('14-appearance-'+str(len(results)),'إضاءة التطبيق')
        tap('الاختبارات');capture('15-subjects','الرياضيات')
        tap('الرياضيات');capture('16-topics','موضوع فحص افتراضي')
        tap('موضوع فحص افتراضي');capture('17-question','ما حاصل جمع اثنين وثلاثة؟')
        before=len(f.STATE['answers']);tap('خمسة')
        assert len(f.STATE['answers'])==before,'Selecting an option must not consume an answer'
        tap('إرسال الإجابة');ui.wait_for('إجابة صحيحة')
        assert len(f.STATE['answers'])==before+1
        capture('18-correct-answer','إجابة صحيحة')
        # Build the local coach notebook via an actual confirmed answer.
        f.back();ui.wait_for('موضوع فحص افتراضي');f.back();ui.wait_for('حسابي')
        tap('حسابي');tap('مراجعاتي المحفوظة');tap('ابدأ مراجعتك الموصى بها')
        capture('19-coach-question','ما حاصل جمع اثنين وثلاثة؟')
        official_answers=len(f.STATE['answers'])
        tap('اكشف الإجابة');ui.wait_for('الإجابة النموذجية');tap('تذكّرت')
        capture('20-coach-completed','أحسنت، انتهت جلسة المراجعة!')
        assert len(f.STATE['answers'])==official_answers,'Local recall must not submit another official answer'
        assert ui.adb('shell','pidof',ui.PACKAGE).strip()
        logs=ui.adb('logcat','-d','-v','brief','AndroidRuntime:E','*:S')
        assert b'FATAL EXCEPTION' not in logs,logs.decode(errors='replace')
        (out/'results.json').write_text(json.dumps({'screens':results,'passed':True},ensure_ascii=False,indent=2))
        print('PASS native page/button navigation, themes, all school ads, exam, coach, and no runtime crash',flush=True)
    finally:
        (out/'last-screen.png').write_bytes(ui.adb('exec-out','screencap','-p'))
        (out/'last-screen.xml').write_bytes(ui.ui_tree()[1])
        (out/'runtime.log').write_bytes(ui.adb('logcat','-d','-v','threadtime','AndroidRuntime:E','*:S'))
        server.shutdown()

if __name__=='__main__':main()

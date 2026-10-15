"""Real Android controls at representative dp/density/font/IME sizes. Synthetic API only."""
import argparse,json,threading,time,struct
from pathlib import Path
from http.server import ThreadingHTTPServer
import anonymous_emulator_qa as ui
import student_fixture_qa as fixture


def main():
    p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--smoke',action='store_true');p.add_argument('--case');a=p.parse_args()
    a.output.mkdir(parents=True,exist_ok=True)
    server=ThreadingHTTPServer(('127.0.0.1',8765),fixture.Handler)
    threading.Thread(target=server.serve_forever,daemon=True).start()
    ui.PACKAGE='com.murshidwizari.student.qa'
    ui.adb('reverse','tcp:8765','tcp:8765');ui.adb('install','-r','android-native/app/build/outputs/apk/qa/app-qa.apk')
    cases=[('phone-360dp-density2',720,1600,320,1.0),('small-320dp',640,1280,320,1.0)]
    if not a.smoke:cases += [('large-font',393,852,160,1.6),('tablet',800,1280,160,1.3),('landscape',800,360,160,1.0)]
    if a.case:
        cases=[c for c in cases if c[0]==a.case]
        assert len(cases)==1
    results=[]
    try:
        for name,w,h,density,scale in cases:
            out=a.output/name;out.mkdir(exist_ok=True)
            ui.adb('shell','am','force-stop',ui.PACKAGE);ui.adb('shell','pm','clear',ui.PACKAGE)
            ui.adb('shell','settings','put','system','accelerometer_rotation','0')
            ui.adb('shell','settings','put','system','user_rotation','0')
            ui.adb('shell','wm','size',f'{min(w,h)}x{max(w,h)}' if name=='landscape' else f'{w}x{h}')
            ui.adb('shell','wm','density',str(density));ui.adb('shell','settings','put','system','font_scale',str(scale))
            if name=='landscape':ui.adb('shell','settings','put','system','user_rotation','1')
            time.sleep(2)
            ui.launch_native()
            if name=='landscape':
                ui.adb('shell','wm','user-rotation','lock','1')
                time.sleep(2)
            png=ui.adb('exec-out','screencap','-p');actual=struct.unpack('>II',png[16:24]);assert actual==(w,h),(name,actual)
            (out/'display-dimensions.json').write_text(json.dumps({'expected':[w,h],'actual':actual}))
            if name=='landscape':
                (out/'00-guest.png').write_bytes(ui.adb('exec-out','screencap','-p'))
                (out/'00-guest.xml').write_bytes(ui.ui_tree()[1])
            ui.enter_login(w,h)
            if name=='landscape':
                (out/'00-login.png').write_bytes(ui.adb('exec-out','screencap','-p'))
                (out/'00-login.xml').write_bytes(ui.ui_tree()[1])
            def button(text):ui.tap_visible(text,w,h)
            def capture(tag,marker):ui.capture(out,tag,marker)
            button('إنشاء حساب طالب جديد');ui.wait_for('بيانات الطالب');ui.wait_for('السادس العلمي')
            capture('01-register','بيانات الطالب')
            tree,_=ui.ui_tree();next_initial=ui.find(tree,'التالي') is not None
            if name in ('phone-360dp-density2','small-320dp'):assert next_initial,'Phone registration should show Next without scrolling'
            field=ui.reveal('مثال: يونس محمد عباس',w,h);fixture.input_text(field,'Test%sStudent%sDemo');time.sleep(.8)
            ui.wait_for('Test Student Demo');capture('02-keyboard-name','Test Student Demo')
            # The active input remains in the visible window; no rebuild loses its text.
            node,_=ui.wait_for('Test Student Demo');left,top,right,bottom=map(int,ui.BOUNDS.fullmatch(node.attrib['bounds']).groups());assert right>left and bottom>top
            fixture.hide_keyboard_if_visible();button('التالي');ui.wait_for('طريقة التسجيل')
            field=ui.reveal('example@email.com',w,h);fixture.input_text(field,'newstudent@example.test');fixture.hide_keyboard_if_visible()
            button('التالي');ui.wait_for('حماية الحساب');capture('03-password','حماية الحساب')
            field=ui.reveal('8 أحرف على الأقل',w,h);fixture.input_text(field,'fixture-pass');fixture.hide_keyboard_if_visible()
            field=ui.reveal('أعد كتابة كلمة المرور',w,h);fixture.input_text(field,'fixture-pass');fixture.hide_keyboard_if_visible()
            # Back through all steps retains name/contact/password draft.
            button('رجوع للخطوة السابقة');ui.wait_for('طريقة التسجيل');ui.wait_for('newstudent@example.test')
            button('رجوع للخطوة السابقة');ui.wait_for('بيانات الطالب');ui.wait_for('Test Student Demo')
            ui.adb('shell','input','keyevent','KEYCODE_BACK');ui.wait_for('إظهار كلمة المرور')
            def fill(hint,value):fixture.input_text(ui.reveal(hint,w,h),value);fixture.hide_keyboard_if_visible()
            fill('example@email.com أو 07XXXXXXXXX','student@example.test');fill('أدخل كلمة المرور','fixture-pass');button('دخول');ui.wait_for('حسابي')
            capture('04-home','حسابي')
            for tab,marker,tag in [('الإعلانات','مدرسة افتراضية','05-ads'),('المرشحات','مرشح فحص افتراضي','06-predictions'),('حسابي','تفعيل الاشتراك بكود','07-account'),('الاختبارات','الرياضيات','08-subjects')]:
                button(tab);ui.reveal(marker,w,h);capture(tag,marker)
            button('الرياضيات');ui.wait_for('موضوع فحص افتراضي');capture('09-topics','موضوع فحص افتراضي')
            button('موضوع فحص افتراضي');ui.wait_for('ما حاصل جمع اثنين وثلاثة؟');capture('10-question','ما حاصل جمع اثنين وثلاثة؟')
            button('خمسة');capture('11-answer','ما حاصل جمع اثنين وثلاثة؟')
            assert ui.adb('shell','pidof',ui.PACKAGE).strip()
            results.append({'case':name,'width_px':w,'height_px':h,'density':density,'font_scale':scale,'initial_next_visible':next_initial,'registration_draft_retained':True,'keyboard_input_passed':True,'student_tabs_exam_passed':True})
            (a.output/'results.json').write_text(json.dumps(results,ensure_ascii=False,indent=2));print('PASS '+name,flush=True)
    finally:
        (a.output/'android-runtime.log').write_bytes(ui.adb('logcat','-d','-v','threadtime','AndroidRuntime:E','*:S'))
        (a.output/'last-screen.png').write_bytes(ui.adb('exec-out','screencap','-p'))
        (a.output/'last-screen.xml').write_bytes(ui.ui_tree()[1])
        (a.output/'window-state.txt').write_bytes(ui.adb('shell','dumpsys','window','windows'))
        server.shutdown();ui.adb('shell','settings','put','system','user_rotation','0');ui.adb('shell','wm','size','reset');ui.adb('shell','wm','density','reset');ui.adb('shell','settings','put','system','font_scale','1.0')

if __name__=='__main__':main()

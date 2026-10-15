"""Synthetic end-to-end UI checks. Local QA origin only, no production accounts."""
import argparse, json, subprocess, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
import anonymous_emulator_qa as ui

USER={'id':900001,'name':'طالب فحص افتراضي','grade':'السادس العلمي','grade_id':1,'email':'student@example.test','can_edit_name':True}
SUBJECTS=[{'id':1,'name':'الرياضيات'},{'id':2,'name':'اللغة العربية'}]
STATE={'notes':{},'answers':{},'calls':[],'reject_once':False,'logout_reject_once':False,
       'registered':False,'verified':False,'resend_count':0,'invalid_code_count':0,
       'registered_methods':[],'verified_methods':[],'subscription_active':True,
       'checkout_payload':None,'checkout_requests':0,'redeem_requests':0}
BOOT={'ok':True,'csrf':'synthetic-csrf','user':USER,'subscribed':True,'free_remaining':15,'free_limit':15,'subscription_expires_at_unix':int(time.time())+7*86400,
      'plans':[{'id':'month1','label':'شهر واحد','amount':8000},{'id':'month3','label':'3 أشهر','amount':15000}],
      'subjects':SUBJECTS,'stats':{},'ads':[{'id':1,'school':'مدرسة افتراضية','text':'إعلان محلي للتحقق من عرض الإعلانات','active':True}]}
QUESTIONS=[{'id':101,'text':'ما حاصل جمع اثنين وثلاثة؟','type':'mcq','options':[{'id':1,'text':'خمسة'},{'id':2,'text':'ستة'}],'correct_answer':'خمسة','explanation':'نجمع اثنين مع ثلاثة فيكون الناتج خمسة.','submitted':False}, {'id':102,'text':'اكتب ناتج أربعة زائد أربعة.','type':'text','correct_answer':'8','explanation':'أربعة مضافاً إليها أربعة تساوي ثمانية.','submitted':False}]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_GET(self): self.respond()
 def do_POST(self): self.respond()
 def respond(self):
  parsed=urlsplit(self.path); path=parsed.path; qs=parse_qs(parsed.query)
  payload=self.rfile.read(int(self.headers.get('Content-Length','0')))
  if 'application/x-www-form-urlencoded' in self.headers.get('Content-Type',''):
   body={key:values[0] for key,values in parse_qs(payload.decode()).items()}
  else: body=json.loads(payload or b'{}')
  STATE['calls'].append((self.command,path))
  data={'ok':True}
  if path=='/api/login.php': data={'ok':True} if body.get('password')=='fixture-pass' else {'ok':False,'message':'بيانات الدخول غير صحيحة.'}
  elif path=='/api/register.php':
   assert body.get('full_name')=='Test Student Demo',body
   assert body.get('contact_type') in ('email','phone') and body.get('grade_id')==1,body
   assert body.get('identifier')==('newstudent@example.test' if body['contact_type']=='email' else '07701234567'),body
   assert body.get('gender')=='male' and body.get('company_website')=='',body
   assert body.get('password')==body.get('password_confirmation')=='fixture-pass',body
   STATE['registered']=True
   STATE['registered_methods'].append(body['contact_type'])
   if body['contact_type']=='email':
    data={'ok':True,'email_verification_required':True,
          'masked_email':'n***@example.test','message':'أرسلنا رمز التأكيد إلى بريد الطالب.'}
   else:
    data={'ok':True,'phone_verification_required':True,
          'masked_phone':'077****4567','message':'أرسلنا رمز التأكيد إلى الهاتف.'}
  elif path in ('/verify-email.php','/verify-phone.php'):
   if self.command=='GET':
    html='<form><input name="csrf" value="synthetic-csrf"></form>'
   elif body.get('action')=='resend':
    STATE['resend_count']+=1
    html='<div class="form-message success">أرسلنا رمز تحقق جديدًا.</div>'
   elif body.get('action')=='verify' and body.get('code')=='123456':
    STATE['verified']=True
    STATE['verified_methods'].append('phone' if path=='/verify-phone.php' else 'email')
    html='<div class="form-message success">تم تأكيد الحساب.</div>'
   else:
    STATE['invalid_code_count']+=1
    html='<div class="form-message error">رمز التحقق غير صحيح.</div>'
   self.send_response(200);self.send_header('Content-Type','text/html; charset=utf-8')
   self.send_header('Set-Cookie','fixture_session=synthetic; Path=/')
   self.end_headers();self.wfile.write(html.encode());return
  elif path=='/mobile/bootstrap.php':
   time.sleep(STATE.get('bootstrap_delay_seconds',0))
   data={**BOOT,'subscribed':STATE['subscription_active'],
         'free_remaining':0 if not STATE['subscription_active'] else 15}
  elif path=='/mobile/homepage.php': data={**BOOT,'subscribed':STATE['subscription_active'],
                                          'free_remaining':0 if not STATE['subscription_active'] else 15}
  elif path=='/mobile/account.php': data={**BOOT,'subscribed':STATE['subscription_active'],
                                         'subscription_days_remaining':30,'can_change_name':False}
  elif path=='/mobile/notifications.php': data={'ok':True,'unread':2,'items':[]}
  elif path=='/api/payment-checkout.php':
   if body.get('action')=='checkout':
    STATE['checkout_requests']+=1
    STATE['checkout_payload']=body
    assert body.get('plan') in ('month1','month3'),body
    assert body.get('customer_name')==USER['name'],body
    assert body.get('customer_phone')=='07700000000',body
    assert 'payment_method' not in body,'The active iOS V31 checkout has no in-app method picker'
    data={'ok':False,'message':'بوابة الدفع التجريبية مغلقة؛ لم تُنشأ عملية دفع.'}
   else: data={'ok':True,'subscribed':False,'message':'لم يؤكد الخادم التفعيل بعد.'}
  elif path=='/mobile/redeem-subscription-code.php':
   STATE['redeem_requests']+=1
   data={'ok':False,'message':'كود تجريبي غير صالح.'}
  elif path=='/mobile/bound-password-recovery.php':
   data={'ok':True,'contacts':{'email':{'value':'student@example.test','enabled':True},'phone':{'value':'','enabled':False}}}
  elif path=='/mobile/insights.php': data={'ok':True,'insights':{'chapters':[]},'weekly':{}}
  elif path=='/mobile/app-config.php': data={'ok':True,'features':{'custom_exam':{'enabled':True},'daily_challenge':{'enabled':True},'student_question_submit':{'enabled':False},'attempt_history':{'enabled':True}},'release':{}}
  elif path=='/mobile/predictions.php': data={'ok':True,'predictions':[{'id':1,'question_text':'مرشح فحص افتراضي','question':'مرشح فحص افتراضي','text':'مرشح فحص افتراضي','correct_answer':'إجابة المرشح الافتراضي','explanation':'شرح المرشح الافتراضي','chapter_name':'موضوع الفحص','topic':'موضوع الفحص','probability':80}]}
  elif path=='/mobile/topics.php': data={'ok':True,'topics':[{'id':1,'name':'موضوع فحص افتراضي','count':2}]}
  elif path=='/mobile/exam.php':
   data={'ok':True,'csrf':'synthetic-csrf','subscribed':True,'questions':[{**q,**({'submitted':True,'answer':STATE['answers'][str(q['id'])]} if str(q['id']) in STATE['answers'] else {})} for q in QUESTIONS]}
  elif path=='/api/progress.php':
   if STATE['reject_once']:
    STATE['reject_once']=False
    data={'ok':False,'message':'رفض مؤكد للتجربة: الإجابة لم تُحفظ.'}
   else:
    grades={}
    for key,answer in body['answers'].items():
     q=next(q for q in QUESTIONS if str(q['id'])==key)
     try: correct=json.loads(answer['text'])==json.loads(q['correct_answer'])
     except (ValueError,TypeError): correct=answer['text']==q['correct_answer']
     STATE['answers'][key]={**answer,'correct':correct}
     grades[key]={'is_correct':correct,'correct_answer':q['correct_answer'],'explanation':q['explanation']}
    data={'ok':True,'grades':grades,'subscribed':True,'free_remaining':15}
  elif path=='/mobile/question-notes.php':
   key=str(body.get('question_id',qs.get('question_id',[''])[0]))
   if self.command=='POST': STATE['notes'][key]=body['note']
   data={'ok':True,'note':STATE['notes'].get(key,'')}
  elif path=='/mobile/school-ad-popup.php': data={'ok':True,'ads':[]}
  elif path=='/mobile/logout.php':
   if STATE['logout_reject_once']:
    STATE['logout_reject_once']=False
    data={'ok':False,'message':'رفض الخادم تسجيل الخروج؛ الجلسة محفوظة.'}
  elif path in ['/mobile/contest.php','/mobile/season.php']: data={'ok':True,'config':{'active':True},'mine':{'rank':1,'xp':5,'correct':1},'leaderboard':[{'rank':1,'name':USER['name'],'xp':5,'correct':1,'grade':'السادس العلمي'}]}
  elif path=='/mobile/contest-instructions.php': data={'ok':True,'prize_iqd':1000000,'end_at':'2027-01-01','rules':[{'title':'قاعدة فحص افتراضية','text':'تأتي قواعد التحدي من الخادم.'}],'points':[]}
  elif path=='/api/grades.php': data={'ok':True,'grades':[{'id':1,'name':'السادس العلمي'}]}
  raw=json.dumps(data,ensure_ascii=False).encode()
  lose_reply=path=='/api/progress.php' and STATE.pop('lose_grade_reply_once',False)
  if lose_reply: raw=json.dumps({'ok':False,'message':'Synthetic receipt lost after server acceptance'}).encode()
  self.send_response(502 if lose_reply else 200);self.send_header('Content-Type','application/json');self.send_header('Set-Cookie','fixture_session=synthetic; Path=/');self.end_headers();self.wfile.write(raw)

def wait_editable(seconds=16):
 deadline=time.time()+seconds
 while time.time()<deadline:
  tree,_=ui.ui_tree()
  field=next((n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText'
      and n.attrib.get('enabled')!='false'),None)
  if field is not None: return field
  time.sleep(.35)
 raise AssertionError('Expected editable native input after transition: '+repr(ui.visible_texts(tree)))

def input_text(node,value):
 ui.tap_node(node);time.sleep(.35);ui.adb('shell','input','text',value)
def hide_keyboard_if_visible():
 # KEYCODE_BACK can navigate to the *guest* screen on API 26 when a virtual
 # keyboard was never shown. Only send it when Android reports a visible IME.
 tree,_=ui.ui_tree()
 keyboard_nodes=any('inputmethod' in n.attrib.get('package','').lower()
  or 'keyboard' in n.attrib.get('package','').lower() for n in tree.iter('node'))
 status=ui.adb('shell','dumpsys','input_method').decode(errors='replace')
 if keyboard_nodes or 'mInputShown=true' in status:
  back()
def visible_login_fields():
 # Some freshly booted API-26 emulators briefly replace the app hierarchy
 # while the IME or launcher is settling. Wait for the *native* two-field form.
 last=[]
 for attempt in range(18):
  tree,_=ui.ui_tree()
  fields=[n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText']
  if len(fields)>=2: return fields
  last=ui.visible_texts(tree)
  launcher=ui.find(tree,"Pixel Launcher isn't responding")
  close=ui.find(tree,"Close app")
  if launcher is not None and close is not None:
   ui.tap_node(close)
   print('RECOVER emulator Pixel Launcher dialog before fixture login',flush=True)
  time.sleep(1)
 raise AssertionError(f'Expected native login fields after waiting, found {last!r}')
def visible_login_field(index):
 # When Android 15 opens the IME it resizes the form and may clip the *other*
 # input from UIAutomator entirely. Locate each field by its password flag
 # rather than assuming the two are simultaneously visible.
 last=[]
 for attempt in range(18):
  tree,_=ui.ui_tree()
  last=ui.visible_texts(tree)
  match=next((n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText'
      and (n.attrib.get('password')=='true')==(index==1)),None)
  if match is not None: return match
  hide_keyboard_if_visible()
  ui.adb('shell','input','swipe','196','410','196','200','330')
  time.sleep(.35)
 raise AssertionError(f'Could not reveal login field {index}: {last!r}')
def type_ascii_keyevents(value):
 # AOSP API26 fallback for synthetic ASCII fixture strings only.
 special={'.':56,'@':77,'-':69}
 for character in value:
  if 'a'<=character<='z': code=29+ord(character)-ord('a')
  elif '0'<=character<='9': code=7+ord(character)-ord('0')
  else: code=special.get(character)
  if code is None: raise AssertionError('Unsupported synthetic QA keyevent')
  ui.adb('shell','input','keyevent',str(code))

def login_field(index,value):
 # A cold API-26 emulator can drop the first adb text event while its IME is
 # opening. Confirm that the native EditText actually changed before moving
 # on; a successful adb shell exit alone does not prove text was delivered.
 for attempt in range(4):
  field=visible_login_field(index)
  seen=field.attrib.get('text','')
  hint=('example@email.com أو 07XXXXXXXXX' if index==0 else 'أدخل كلمة المرور')
  if seen==value or (index==1 and seen not in ('',hint)):
   hide_keyboard_if_visible()
   return
  ui.tap_node(field);time.sleep(.5)
  if seen not in ('',hint):
   ui.adb('shell','input','keyevent','KEYCODE_MOVE_END')
   for _ in range(len(seen)+2):ui.adb('shell','input','keyevent','KEYCODE_DEL')
  ui.adb('shell','input','text',value);time.sleep(.45)
  seen=visible_login_field(index).attrib.get('text','')
  if seen==value or (index==1 and seen not in ('',hint)):
   hide_keyboard_if_visible()
   return
  if attempt>=1 and ui.adb('shell','getprop','ro.build.version.sdk').strip()==b'26':
   # Record focus state before changing the injection mechanism; do not hide a
   # genuine focus failure behind an unconditional test pass.
   native_field=visible_login_field(index)
   print('API26 IME fallback focus before tap:',native_field.attrib.get('focused'),flush=True)
   ui.tap_node(native_field)
   time.sleep(.6)
   current=visible_login_field(index).attrib.get('text','')
   if current not in ('',hint):
    ui.adb('shell','input','keyevent','KEYCODE_MOVE_END')
    for _ in range(len(current)+3):ui.adb('shell','input','keyevent','KEYCODE_DEL')
   type_ascii_keyevents(value)
   time.sleep(.7)
   seen=visible_login_field(index).attrib.get('text','')
   if seen==value or (index==1 and seen not in ('',hint)):
    print('RECOVER API26 input through Android keyevents',flush=True)
    hide_keyboard_if_visible()
    return
  print(f'RETRY input for login field {index} on emulator (attempt {attempt+1}, actual={seen!r})',flush=True)
 raise AssertionError(f'Native login field {index} failed to retain typed value; last visible={seen!r}')
def button(text): ui.tap_visible(text,393,852)
def back(): ui.adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.6)
def fill_register_field(hint,value):
 for attempt in range(12):
  tree,_=ui.ui_tree()
  field=next((n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText'
       and n.attrib.get('text')==hint),None)
  if field is not None:
   input_text(field,value)
   hide_keyboard_if_visible()
   return
  ui.adb('shell','input','swipe','196','690','196','290','340');time.sleep(.45)
 raise AssertionError(f'Cannot find registration field {hint!r}: {ui.visible_texts(tree)!r}')
def screenshot(out,name):
 tree,raw=ui.ui_tree();out.joinpath(name+'.xml').write_bytes(raw);out.joinpath(name+'.png').write_bytes(ui.adb('exec-out','screencap','-p'));print('PASS',name,flush=True)
def main():
 parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,required=True);args=parser.parse_args();args.output.mkdir(parents=True,exist_ok=True)
 server=ThreadingHTTPServer(('127.0.0.1',8765),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
 ui.PACKAGE='com.murshidwizari.student.qa'
 ui.adb('reverse','tcp:8765','tcp:8765');ui.adb('install','-r','android-native/app/build/outputs/apk/qa/app-qa.apk')
 ui.adb('shell','wm','size','393x852');ui.adb('shell','wm','density','160');ui.adb('shell','settings','put','system','font_scale','1.0')
 ui.launch_native();ui.enter_login(393,852)
 login_field(0,'student@example.test');login_field(1,'fixture-pass')
 username=visible_login_field(0)
 assert username.attrib.get('text')=='student@example.test',username.attrib
 # Android masks password accessibility text on some API levels.
 secret=visible_login_field(1)
 assert secret.attrib.get('text') not in ('','أدخل كلمة المرور'),secret.attrib
 button('دخول')
 ui.wait_for('حسابي');screenshot(args.output,'student-home-light')
 # iPhone Build 498 replays its cinematic welcome after a real background
 # visit longer than three seconds. Verify Android's overlay without killing
 # the student's in-progress Activity or losing the authenticated session.
 replayed=False
 for attempt in range(2):
  # First wait for the home activity transition before counting time away.
  # Newer emulator GPUs may not dispatch Activity.onStop synchronously with
  # the HOME keypress; a 4s wall-clock sleep is therefore insufficient.
  live_log=subprocess.Popen(['adb','logcat','-v','brief','-s','MurshidWelcome:I','*:S'],
                           stdout=subprocess.PIPE,stderr=subprocess.PIPE)
  ui.adb('shell','input','keyevent','KEYCODE_HOME')
  time.sleep(7.0)
  ui.adb('shell','am','start','-n',f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
  time.sleep(.25)
  args.output.joinpath('student-welcome-after-background.png').write_bytes(
   ui.adb('exec-out','screencap','-p'))
  time.sleep(.55)
  live_log.terminate()
  logged,errors=live_log.communicate(timeout=8)
  log=logged.decode(errors='replace')
  replayed='background-replay-started' in log
  if replayed:break
  print('Replay attempt',attempt+1,'log:',repr(log[-1000:]),'stderr:',repr(errors[-300:]),flush=True)
 assert replayed,'Returning from HOME for >3s must replay the native welcome overlay'
 time.sleep(3.2)
 tree,_=ui.ui_tree()
 assert not any(n.attrib.get('content-desc','').startswith('منصة المرشد الوزاري. أهلًا، ') for n in tree.iter('node')),'Welcome overlay must disappear after playback'
 ui.wait_for('حسابي')
 # Capture the reference iPhone's dark appearance as well as light Android.
 # The toggle is a native accessible header control, not a test-only shortcut.
 button('تبديل المظهر');ui.wait_for('حسابي');screenshot(args.output,'student-home-dark')
 button('الرئيسية');ui.wait_for('حسابي')
 # Latest iPhone smart coach sits after the four metric cards.
 ui.reveal('مدربي الذكي · مراجعة دون إنترنت',393,852)
 button('مدربي الذكي · مراجعة دون إنترنت')
 ui.wait_for('مراجعة اليوم')
 ui.wait_for('بعد أول سؤال مصحّح سيُبنى لك دفتر مراجعة شخصيًا.')
 screenshot(args.output,'student-coach-empty')
 back();ui.wait_for('حسابي')
 # A newly published school advertisement must raise a RED account-scoped
 # badge without needing an app restart; visiting Ads clears the unread mark.
 BOOT['ads'].append({'id':9,'school':'مدرسة ثانية افتراضية',
   'text':'هذا إعلان تم نشره أثناء فتح التطبيق','active':True})
 ui.wait_for('إعلانات جديدة: 1',seconds=48)
 screenshot(args.output,'student-new-ad-badge')
 button('الإعلانات');ui.wait_for('مدرسة افتراضية');screenshot(args.output,'student-ads')
 ui.wait_for('مدرسة ثانية افتراضية')
 tree,_=ui.ui_tree()
 assert ui.find(tree,'إعلانات جديدة: 1') is None,'Opening the Ads tab must mark the visible publication as read'
 button('المرشحات');ui.wait_for('مرشح فحص افتراضي');screenshot(args.output,'student-predictions')
 button('عرض الإجابة والتوضيح⌄');ui.wait_for('إجابة المرشح الافتراضي');screenshot(args.output,'student-prediction-answer')
 button('حسابي');ui.wait_for('تفعيل الاشتراك بكود');screenshot(args.output,'student-account')
 # The owner iPhone has three accent palettes and system/light/dark controls.
 # Assert choices persist as device preferences, not server mutations.
 def saved_appearance():
  return ui.adb('shell','run-as',ui.PACKAGE,'cat','shared_prefs/native_preferences.xml').decode(errors='replace')
 ui.reveal('اختر هويتك اللونية',393,852)
 button('اختيار ألوان بنفسجي');ui.reveal('اختر هويتك اللونية',393,852)
 assert 'accent_style' in saved_appearance() and 'violet' in saved_appearance()
 screenshot(args.output,'student-account-violet')
 button('اختيار ألوان أورورا');ui.reveal('اختر هويتك اللونية',393,852)
 assert 'aurora' in saved_appearance()
 screenshot(args.output,'student-account-aurora')
 button('اختيار ألوان ملكي');ui.reveal('اختر هويتك اللونية',393,852)
 assert 'royal' in saved_appearance()
 button('إضاءة فاتح');ui.reveal('اختر هويتك اللونية',393,852)
 assert 'light' in saved_appearance()
 screenshot(args.output,'student-account-light')
 button('إضاءة داكن');ui.reveal('اختر هويتك اللونية',393,852)
 assert 'dark' in saved_appearance()
 screenshot(args.output,'student-account-dark')
 button('استعادة / تغيير كلمة المرور');ui.wait_for('استعادة آمنة لبيانات حسابك الحالي')
 tree,_=ui.ui_tree()
 assert any('student@example.test' in n.attrib.get('text','') for n in tree.iter('node')),'Account recovery must show the registered email'
 screenshot(args.output,'student-bound-recovery');back()
 button('الاختبارات');ui.wait_for('الرياضيات');screenshot(args.output,'student-tests')
 button('الرياضيات');ui.wait_for('موضوع فحص افتراضي');screenshot(args.output,'student-topics')
 button('موضوع فحص افتراضي')
 ui.wait_for('ما حاصل جمع اثنين وثلاثة؟');screenshot(args.output,'student-exam-before-answer')
 button('ستة')
 assert not STATE['answers'],'Selecting an option must not submit or consume a free answer'
 button('خمسة')
 assert not any(path=='/api/progress.php' for _,path in STATE['calls']), 'Changing a selection must stay local'
 screenshot(args.output,'student-exam-selected')
 button('إرسال الإجابة');ui.wait_for('إجابة صحيحة');screenshot(args.output,'student-exam-graded')
 button('•••');ui.wait_for('ملاحظتي الخاصة');button('ملاحظتي الخاصة');ui.wait_for('حفظ')
 # API 26 sometimes drops ADB IME injection while the AlertDialog is
 # settling. Verify the visible note field rather than assuming input worked.
 for attempt in range(4):
  tree,_=ui.ui_tree()
  field=wait_editable()
  actual=field.attrib.get('text','')
  if actual=='fixture-note': break
  # Android 8 accessibility may expose the hint as text even when
  # the actual editable field is empty.
  if actual not in ('', 'مثال: راجع هذه القاعدة قبل الامتحان'):
   raise AssertionError(f'Note input unexpectedly contained {actual!r}')
  ui.tap_node(field);time.sleep(.7)
  ui.adb('shell','input','text','fixture-note');time.sleep(.6)
 else:
  raise AssertionError('Native note entry did not accept text on this emulator')
 hide_keyboard_if_visible()
 button('حفظ')
 deadline=time.time()+12
 while time.time()<deadline and STATE['notes'].get('101')!='fixture-note':
  time.sleep(.3)
 assert STATE['notes'].get('101')=='fixture-note',STATE['notes']
 button('•••');ui.wait_for('ملاحظتي الخاصة');button('ملاحظتي الخاصة');ui.wait_for('fixture-note');screenshot(args.output,'student-note-restored');button('إغلاق')
 # Return to top before tapping the fixed exam footer.
 ui.adb('shell','input','swipe','196','250','196','740','300');time.sleep(.4)
 button('التالي ›');ui.wait_for('اكتب ناتج أربعة زائد أربعة.');screenshot(args.output,'student-text-question')
 assert len(STATE['answers'])==1,'Opening/navigating questions must not submit answers'
 assert sum(1 for m,p in STATE['calls'] if p=='/api/progress.php')==1,'Exactly one grading request'
 tree,_=ui.ui_tree();field=wait_editable()
 input_text(field,'8');hide_keyboard_if_visible()
 # A definitive application-level rejection must leave the form retryable.
 # This is not a timeout: uncertain submissions must still be reconciled.
 STATE['reject_once']=True
 button('إرسال الإجابة');time.sleep(1)
 assert '102' not in STATE['answers'],'Rejected answer must not be counted'
 button('إرسال الإجابة');ui.wait_for('إجابة صحيحة');screenshot(args.output,'student-text-question-graded')
 button('عرض النتيجة ›');ui.wait_for('أداء ممتاز');screenshot(args.output,'student-exam-summary')
 tree,_=ui.ui_tree()
 assert not any('السؤال التالي' in n.attrib.get('content-desc','') for n in tree.iter('node')), (
  'Completed exam must not leave an inactive navigation footer')
 assert sum(1 for m,p in STATE['calls'] if p=='/api/progress.php')==3,'One rejection plus two accepted answers'
 assert any(path=='/mobile/app-config.php' for _,path in STATE['calls']),'Home must request the same remote feature gates as iOS'
 # End-to-end exit: a rejected server logout must retain the active account;
 # the subsequently accepted logout must return to the exact guest landing.
 back();ui.wait_for('موضوع فحص افتراضي')
 back();ui.wait_for('الرياضيات')
 button('الرئيسية');ui.wait_for('حسابي')
 ui.reveal('مدربي الذكي · مراجعة دون إنترنت',393,852)
 button('مدربي الذكي · مراجعة دون إنترنت')
 ui.wait_for('مراجعة اليوم')
 screenshot(args.output,'student-coach-saved')
 # The self-review session remains local and cannot consume a free answer.
 prior_grades=sum(1 for m,p in STATE['calls'] if p=='/api/progress.php')
 button('ابدأ مراجعة اليوم')
 ui.wait_for('ما حاصل جمع اثنين وثلاثة؟')
 button('أظهر الإجابة')
 ui.wait_for('الإجابة الصحيحة')
 screenshot(args.output,'student-coach-revealed')
 button('أتقنت هذا السؤال')
 ui.wait_for('مراجعة اليوم')
 assert sum(1 for m,p in STATE['calls'] if p=='/api/progress.php')==prior_grades, (
  'The local coach must never send a billable answer')
 back();ui.wait_for('حسابي')
 # A previously signed-in student must reopen the offline coach after process
 # death in airplane mode; no bootstrap request may be made on the flight.
 bootstrap_before=sum(1 for _,p in STATE['calls'] if p=='/mobile/bootstrap.php')
 # Sending ACTION_AIRPLANE_MODE_CHANGED from shell is blocked by newer
 # permission policy and also fails on API26. Disable both real transports;
 # the test cares that no usable network remains, not about the UI icon.
 ui.adb('shell','svc','wifi','disable')
 ui.adb('shell','svc','data','disable')
 time.sleep(1.5)
 ui.adb('shell','am','force-stop',ui.PACKAGE)
 ui.adb('shell','am','start','-n',f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
 ui.wait_for('وضع المراجعة دون إنترنت',seconds=28)
 screenshot(args.output,'student-offline-entry')
 assert sum(1 for _,p in STATE['calls'] if p=='/mobile/bootstrap.php')==bootstrap_before,(
  'Airplane-mode offline access must not contact the server')
 button('مدربي الذكي · مراجعة دون إنترنت')
 ui.wait_for('مراجعة اليوم')
 button('ما حاصل جمع اثنين وثلاثة؟')
 ui.wait_for('جلسة مراجعة')
 screenshot(args.output,'student-offline-coach')
 back();ui.wait_for('مراجعة اليوم')
 back();ui.wait_for('وضع المراجعة دون إنترنت')
 ui.adb('shell','svc','wifi','enable')
 ui.adb('shell','svc','data','enable')
 time.sleep(3)
 button('العودة إلى الوضع المتصل')
 ui.wait_for('حسابي',seconds=28)
 button('حسابي');ui.wait_for('تفعيل الاشتراك بكود')
 STATE['logout_reject_once']=True
 button('تسجيل الخروج');ui.wait_for('سيتم إنهاء جلسة حسابك على هذا الجهاز.')
 button('تسجيل الخروج');time.sleep(.9)
 ui.wait_for('تسجيل الخروج')
 assert sum(1 for m,p in STATE['calls'] if p=='/mobile/logout.php')==1,'One rejected logout must not clear the session'
 button('تسجيل الخروج');ui.wait_for('سيتم إنهاء جلسة حسابك على هذا الجهاز.')
 button('تسجيل الخروج');ui.wait_for('مساحة تعلّمك')
 assert sum(1 for m,p in STATE['calls'] if p=='/mobile/logout.php')==2,'Confirmed logout must not repeat the request'
 screenshot(args.output,'student-logged-out')
 # The active iOS RegisterV3View is a true three-step wizard, not the older
 # single-page RegisterView. Check each stage before email verification.
 ui.enter_login(393,852)
 button('إنشاء حساب طالب جديد')
 ui.wait_for('بيانات الطالب');screenshot(args.output,'student-register-step1')
 ui.wait_for('السادس العلمي')
 fill_register_field('مثال: يونس محمد عباس','Test%sStudent%sDemo')
 button('التالي');ui.wait_for('طريقة التسجيل')
 screenshot(args.output,'student-register-step2')
 fill_register_field('example@email.com','newstudent@example.test')
 button('التالي');ui.wait_for('حماية الحساب')
 screenshot(args.output,'student-register-step3')
 fill_register_field('8 أحرف على الأقل','fixture-pass')
 fill_register_field('أعد كتابة كلمة المرور','fixture-pass')
 ui.wait_for('كلمتا المرور متطابقتان')
 button('إنشاء الحساب')
 ui.wait_for('رمز التحقق');screenshot(args.output,'student-verification')
 assert STATE['registered'],'Registration POST must be accepted by synthetic server'
 # Resend is separate from attempting an invalid code and must retain session.
 button('إعادة إرسال الرمز');time.sleep(.8)
 assert STATE['resend_count']==1,'Resend must hit the verification endpoint'
 fill_register_field('000000','000000')
 button('تأكيد الحساب');ui.wait_for('رمز التحقق غير صحيح.')
 assert STATE['invalid_code_count']==1 and not STATE['verified'],'Wrong OTP cannot activate'
 tree,_=ui.ui_tree()
 otp=wait_editable()
 ui.tap_node(otp)
 ui.adb('shell','input','keyevent','KEYCODE_MOVE_END')
 for _ in range(6):ui.adb('shell','input','keyevent','KEYCODE_DEL')
 ui.adb('shell','input','text','123456');hide_keyboard_if_visible()
 button('تأكيد الحساب');ui.wait_for('حسابي')
 assert STATE['verified'],'Correct OTP must activate the same pending account'
 screenshot(args.output,'student-verified-account')

 # Complete the other real iPhone registration route, including empty-field
 # validation, segment resets and the Android hardware-back step semantics.
 button('حسابي');ui.wait_for('تفعيل الاشتراك بكود')
 button('تسجيل الخروج');ui.wait_for('سيتم إنهاء جلسة حسابك على هذا الجهاز.')
 button('تسجيل الخروج');ui.wait_for('مساحة تعلّمك')
 ui.enter_login(393,852)
 button('إنشاء حساب طالب جديد');ui.wait_for('بيانات الطالب')
 registrations_before=len(STATE['registered_methods'])
 button('التالي');ui.wait_for('بيانات الطالب')
 assert len(STATE['registered_methods'])==registrations_before, 'Incomplete student name cannot register'
 fill_register_field('مثال: يونس محمد عباس','Test%sStudent%sDemo')
 button('التالي');ui.wait_for('طريقة التسجيل')
 button('رقم الهاتف');ui.wait_for('07XXXXXXXXX')
 button('التالي');ui.wait_for('طريقة التسجيل')
 assert len(STATE['registered_methods'])==registrations_before, 'Empty phone cannot register'
 fill_register_field('07XXXXXXXXX','07701234567')
 # SwiftUI clears the identifier on each email/phone segment switch.
 button('البريد الإلكتروني');ui.wait_for('example@email.com')
 button('رقم الهاتف');ui.wait_for('07XXXXXXXXX')
 tree,_=ui.ui_tree()
 assert not any(n.attrib.get('text')=='07701234567' for n in tree.iter('node')), 'Switching contact type must clear stale phone'
 fill_register_field('07XXXXXXXXX','07701234567')
 screenshot(args.output,'student-phone-registration-step2')
 button('التالي');ui.wait_for('حماية الحساب')
 back();ui.wait_for('طريقة التسجيل')
 tree,_=ui.ui_tree()
 assert any(n.attrib.get('text')=='07701234567' for n in tree.iter('node')), 'Hardware back must preserve phone draft'
 button('رجوع للخطوة السابقة');ui.wait_for('بيانات الطالب')
 tree,_=ui.ui_tree()
 assert any(n.attrib.get('text')=='Test Student Demo' for n in tree.iter('node')), 'Back navigation must preserve student name'
 button('التالي');ui.wait_for('طريقة التسجيل')
 button('التالي');ui.wait_for('حماية الحساب')
 fill_register_field('8 أحرف على الأقل','fixture-pass')
 button('إنشاء الحساب');ui.wait_for('حماية الحساب')
 assert len(STATE['registered_methods'])==registrations_before, 'Missing password confirmation cannot register'
 fill_register_field('أعد كتابة كلمة المرور','fixture-pass')
 ui.wait_for('كلمتا المرور متطابقتان')
 button('إنشاء الحساب');ui.wait_for('تأكيد رقم الهاتف')
 ui.wait_for('رمز التحقق')
 screenshot(args.output,'student-phone-verification')
 assert STATE['registered_methods']==['email','phone'], STATE['registered_methods']
 fill_register_field('000000','123456')
 button('تأكيد الحساب');ui.wait_for('حسابي')
 assert STATE['verified_methods']==['email','phone'], STATE['verified_methods']
 screenshot(args.output,'student-phone-verified-account')

 # Real Build 498 uses V31SubscriptionView: provider selection is NOT inside
 # the app. Verify the screenshot-backed hero, two dynamic price cards, account
 # activation code and exact backend payload against a rejecting fake checkout.
 button('حسابي');ui.wait_for('تفعيل الاشتراك بكود')
 button('تسجيل الخروج');ui.wait_for('سيتم إنهاء جلسة حسابك على هذا الجهاز.')
 button('تسجيل الخروج');ui.wait_for('مساحة تعلّمك')
 STATE['subscription_active']=False
 ui.enter_login(393,852)
 login_field(0,'student@example.test');login_field(1,'fixture-pass')
 button('دخول');ui.wait_for('حسابي')
 button('حسابي');ui.wait_for('تفعيل الاشتراك بكود')
 screenshot(args.output,'student-unsubscribed-account')
 fill_register_field('MW-XXXX-XXXX-XXXX-XXXX-XXXX-XXXX','INVALID')
 button('تفعيل الاشتراك');time.sleep(.6)
 assert STATE['redeem_requests']==0,'Malformed code must not contact the redemption endpoint'
 hide_keyboard_if_visible()
 # Android 15 clips off-screen accessibility nodes. Reveal the inbox row
 # before asserting the unread count; never assume it shares the code viewport.
 ui.reveal('لديك 2 غير مقروء',393,852)
 screenshot(args.output,'student-account-unread-count')
 # Account ends at the red logout card per the user's final iPhone reference.
 for _ in range(4):
  ui.adb('shell','input','swipe','196','740','196','245','270');time.sleep(.12)
 ui.wait_for('تسجيل الخروج')
 screenshot(args.output,'student-account-logout-footer')
 # Move to the top, clear the test code, then exercise monthly/quarterly plans.
 for _ in range(4):
  ui.adb('shell','input','swipe','196','230','196','770','330');time.sleep(.2)
 button('اشتراك');ui.wait_for('لا تحفظ الإجابة فقط… افهم أين تتطور')
 screenshot(args.output,'student-subscription-hero')
 ui.wait_for('ماذا تكسب عند الاشتراك؟')
 button('3 أشهر');ui.wait_for('اختر الباقة')
 screenshot(args.output,'student-payment-plans')
 assert STATE['checkout_requests']==0,'Selecting plans must not submit a payment'
 button('الانتقال إلى الدفع الخارجي')
 for _ in range(4):
  ui.adb('shell','input','swipe','196','725','196','250','280');time.sleep(.2)
 ui.wait_for('بوابة الدفع التجريبية مغلقة؛ لم تُنشأ عملية دفع.')
 assert STATE['checkout_requests']==1 and STATE['checkout_payload']['plan']=='month3',STATE['checkout_payload']
 screenshot(args.output,'student-payment-error')
 args.output.joinpath('fixture-results.json').write_text(json.dumps({'passed':True,'grading_requests':3,'rejected_then_retryable':True,'summary_without_footer':True,'note_roundtrip':True,'welcome_replays_on_resume':True,'config_loaded':True,'logout_reject_then_accept':True,'register_and_verify':True,'registration_methods':STATE['registered_methods'],'verification_methods':STATE['verified_methods'],'phone_segment_resets':True,'registration_draft_survives_back':True,'incomplete_registration_blocked':True,'account_and_payment_v31':True,'invalid_activation_rejected_locally':True,'checkout_requests':STATE['checkout_requests'],'checkout_external_only':True,'resend_count':STATE['resend_count'],'invalid_code_count':STATE['invalid_code_count'],'synthetic_only':True},indent=2))
 print('PASS synthetic account, subscription, external checkout contract, both registration routes and grading',flush=True)
 server.shutdown()
if __name__=='__main__': main()

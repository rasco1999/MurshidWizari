"""Synthetic end-to-end UI checks. Local QA origin only, no production accounts."""
import argparse, json, threading, time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit, parse_qs
import anonymous_emulator_qa as ui

USER={'id':900001,'name':'طالب فحص افتراضي','grade':'السادس العلمي','grade_id':1,'email':'student@example.test','can_edit_name':True}
SUBJECTS=[{'id':1,'name':'الرياضيات'},{'id':2,'name':'اللغة العربية'}]
STATE={'notes':{},'answers':{},'calls':[],'reject_once':False}
BOOT={'ok':True,'csrf':'synthetic-csrf','user':USER,'subscribed':True,'free_remaining':15,'free_limit':15,'subjects':SUBJECTS,'stats':{},'ads':[{'id':1,'school':'مدرسة افتراضية','text':'إعلان محلي للتحقق من عرض الإعلانات','active':True}]}
QUESTIONS=[{'id':101,'text':'ما حاصل جمع اثنين وثلاثة؟','type':'mcq','options':[{'id':1,'text':'خمسة'},{'id':2,'text':'ستة'}],'correct_answer':'خمسة','explanation':'نجمع اثنين مع ثلاثة فيكون الناتج خمسة.','submitted':False}, {'id':102,'text':'اكتب ناتج أربعة زائد أربعة.','type':'text','correct_answer':'8','explanation':'أربعة مضافاً إليها أربعة تساوي ثمانية.','submitted':False}]
class Handler(BaseHTTPRequestHandler):
 def log_message(self,*args): pass
 def do_GET(self): self.respond()
 def do_POST(self): self.respond()
 def respond(self):
  parsed=urlsplit(self.path); path=parsed.path; qs=parse_qs(parsed.query)
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
  STATE['calls'].append((self.command,path))
  data={'ok':True}
  if path=='/api/login.php': data={'ok':True} if body.get('password')=='fixture-pass' else {'ok':False,'message':'بيانات الدخول غير صحيحة.'}
  elif path=='/mobile/bootstrap.php': data=BOOT
  elif path=='/mobile/homepage.php': data=BOOT
  elif path=='/mobile/account.php': data={**BOOT,'subscription_days_remaining':30,'can_edit_name':True}
  elif path=='/mobile/bound-password-recovery.php':
   data={'ok':True,'contacts':{'email':{'value':'student@example.test','enabled':True},'phone':{'value':'','enabled':False}}}
  elif path=='/mobile/insights.php': data={'ok':True,'insights':{'chapters':[]},'weekly':{}}
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
     q=next(q for q in QUESTIONS if str(q['id'])==key); correct=answer['text']==q['correct_answer']
     STATE['answers'][key]={**answer,'correct':correct}
     grades[key]={'is_correct':correct,'correct_answer':q['correct_answer'],'explanation':q['explanation']}
    data={'ok':True,'grades':grades,'subscribed':True,'free_remaining':15}
  elif path=='/mobile/question-notes.php':
   key=str(body.get('question_id',qs.get('question_id',[''])[0]))
   if self.command=='POST': STATE['notes'][key]=body['note']
   data={'ok':True,'note':STATE['notes'].get(key,'')}
  elif path=='/mobile/school-ad-popup.php': data={'ok':True,'ads':[]}
  elif path in ['/mobile/contest.php','/mobile/season.php']: data={'ok':True,'config':{'active':True},'mine':{'rank':1,'xp':5,'correct':1},'leaderboard':[{'rank':1,'name':USER['name'],'xp':5,'correct':1,'grade':'السادس العلمي'}]}
  elif path=='/mobile/contest-instructions.php': data={'ok':True,'prize_iqd':1000000,'end_at':'2027-01-01','rules':[{'title':'قاعدة فحص افتراضية','text':'تأتي قواعد التحدي من الخادم.'}],'points':[]}
  elif path=='/api/grades.php': data={'ok':True,'grades':[{'id':1,'name':'السادس العلمي'}]}
  raw=json.dumps(data,ensure_ascii=False).encode()
  self.send_response(200);self.send_header('Content-Type','application/json');self.send_header('Set-Cookie','fixture_session=synthetic; Path=/');self.end_headers();self.wfile.write(raw)

def input_text(node,value):
 ui.tap_node(node);ui.adb('shell','input','text',value)
def login_field(index,value):
 # Hide the keyboard between inputs: opening it scrolls/resizes the native
 # form, so coordinates cached before typing the first field become stale.
 tree,_=ui.ui_tree()
 fields=[n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText']
 if len(fields)<2: raise AssertionError(f'Expected two login inputs, found {len(fields)}')
 input_text(fields[index],value)
 back()
def button(text): ui.tap_visible(text,393,852)
def back(): ui.adb('shell','input','keyevent','KEYCODE_BACK');time.sleep(.6)
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
 tree,_=ui.ui_tree()
 fields=[n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText']
 assert fields[0].attrib.get('text')=='student@example.test',fields[0].attrib
 # Android masks password accessibility text on some API levels.
 assert fields[1].attrib.get('text') not in ('','أدخل كلمة المرور'),fields[1].attrib
 button('دخول')
 ui.wait_for('حسابي');screenshot(args.output,'student-home-light')
 # Capture the reference iPhone's dark appearance as well as light Android.
 # The toggle is a native accessible header control, not a test-only shortcut.
 button('تبديل المظهر');ui.wait_for('حسابي');screenshot(args.output,'student-home-dark')
 button('الإعلانات');ui.wait_for('مدرسة افتراضية');screenshot(args.output,'student-ads')
 button('المرشحات');ui.wait_for('مرشح فحص افتراضي');screenshot(args.output,'student-predictions')
 button('عرض الإجابة والتوضيح⌄');ui.wait_for('إجابة المرشح الافتراضي');screenshot(args.output,'student-prediction-answer')
 button('حسابي');ui.wait_for('تفعيل الاشتراك بكود');screenshot(args.output,'student-account')
 button('استعادة / تغيير كلمة المرور');ui.wait_for('استعادة آمنة لبيانات حسابك الحالي')
 tree,_=ui.ui_tree()
 assert any('student@example.test' in n.attrib.get('text','') for n in tree.iter('node')),'Account recovery must show the registered email'
 screenshot(args.output,'student-bound-recovery');back()
 button('الاختبارات');ui.wait_for('الرياضيات');screenshot(args.output,'student-tests')
 button('الرياضيات');ui.wait_for('موضوع فحص افتراضي');screenshot(args.output,'student-topics')
 button('موضوع فحص افتراضي')
 ui.wait_for('ما حاصل جمع اثنين وثلاثة؟');screenshot(args.output,'student-exam-before-answer')
 button('خمسة');ui.wait_for('إجابة صحيحة');screenshot(args.output,'student-exam-graded')
 button('ملاحظتي الخاصة');ui.wait_for('حفظ');tree,_=ui.ui_tree();field=next(n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText')
 input_text(field,'fixture-note');back();button('حفظ');time.sleep(1)
 assert STATE['notes'].get('101')=='fixture-note',STATE['notes']
 button('ملاحظتي الخاصة');ui.wait_for('fixture-note');screenshot(args.output,'student-note-restored');button('إغلاق')
 # Return to top before tapping the fixed exam footer.
 ui.adb('shell','input','swipe','196','250','196','740','300');time.sleep(.4)
 button('التالي ›');ui.wait_for('اكتب ناتج أربعة زائد أربعة.');screenshot(args.output,'student-text-question')
 assert len(STATE['answers'])==1,'Opening/navigating questions must not submit answers'
 assert sum(1 for m,p in STATE['calls'] if p=='/api/progress.php')==1,'Exactly one grading request'
 tree,_=ui.ui_tree();field=next(n for n in tree.iter('node') if n.attrib.get('class')=='android.widget.EditText')
 input_text(field,'8');back()
 # A definitive application-level rejection must leave the form retryable.
 # This is not a timeout: uncertain submissions must still be reconciled.
 STATE['reject_once']=True
 button('تصحيح الإجابة');time.sleep(1)
 assert '102' not in STATE['answers'],'Rejected answer must not be counted'
 button('تصحيح الإجابة');ui.wait_for('إجابة صحيحة');screenshot(args.output,'student-text-question-graded')
 button('عرض النتيجة ›');ui.wait_for('مراجعة جميع الأسئلة');screenshot(args.output,'student-exam-summary')
 tree,_=ui.ui_tree()
 assert not any('السؤال التالي' in n.attrib.get('content-desc','') for n in tree.iter('node')), (
  'Completed exam must not leave an inactive navigation footer')
 assert sum(1 for m,p in STATE['calls'] if p=='/api/progress.php')==3,'One rejection plus two accepted answers'
 args.output.joinpath('fixture-results.json').write_text(json.dumps({'passed':True,'grading_requests':3,'rejected_then_retryable':True,'summary_without_footer':True,'note_roundtrip':True,'synthetic_only':True},indent=2))
 print('PASS synthetic authenticated UI and grading semantics',flush=True)
 server.shutdown()
if __name__=='__main__': main()

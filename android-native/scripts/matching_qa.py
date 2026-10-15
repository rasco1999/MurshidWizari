"""Actual native editors in normal, daily and custom exams; synthetic loopback server only."""
import argparse,json,threading,time
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.parse import urlsplit
import anonymous_emulator_qa as ui
import student_fixture_qa as fixture

MATCH={'left':{'1':'x-ray','2':'security','3':'conveyor','4':'crime','5':'metal','6':'Speeding'},'right':{'a':'camera','b':'belt','c':'machine','d':'detector','e':'ticket','f':'scene'}}
ANSWER='1-c, 2-a, 3-b, 4-f, 5-d, 6-e'
QUESTION={'id':2004,'type':'text','text':'صِل كل عنصر من القائمة A بما يناسبه من القائمة B.','english_match':MATCH,'correct_answer':ANSWER,'options':[],'explanation':'توصيل صحيح وفق مفتاح السؤال الأصلي.','submitted':False}
STATE={'daily_posts':0,'practice_posts':0}
class Handler(fixture.Handler):
 def respond(self):
  path=urlsplit(self.path).path
  if path not in ['/mobile/challenge.php','/mobile/practice.php']:return super().respond()
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
  if path.endswith('challenge.php'):
   if self.command=='POST':
    assert body['answers']['2004']==ANSWER,body
    STATE['daily_posts']+=1;data={'ok':True,'done':True,'score':1,'total':1,'correct_answer':ANSWER,'given_answer':ANSWER}
   else:data={'ok':True,'csrf':'synthetic-csrf','available':True,'done':False,'questions':[{**QUESTION,'question_text':QUESTION['text']}]}
  elif body.get('action')=='create':data={'ok':True,'session_id':53,'total':1}
  elif body.get('action')=='finish':
   assert body['answers']['2004']==ANSWER,body
   STATE['practice_posts']+=1;data={'ok':True,'correct':1,'answered':1,'total':1,'score':100,'grades':{'2004':True},'review':[]}
  else:data={'ok':True,'session':{'id':53,'title':'اختبار توصيل اصطناعي','status':'active','subject':'الرياضيات','total':1,'duration_seconds':0},'questions':[QUESTION]}
  raw=json.dumps(data,ensure_ascii=False).encode();self.send_response(200);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(raw)

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',type=Path,required=True);p.add_argument('--large-font',action='store_true');a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
 server=ThreadingHTTPServer(('127.0.0.1',8765),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
 fixture.QUESTIONS=[QUESTION];ui.PACKAGE='com.murshidwizari.student.qa'
 ui.adb('reverse','tcp:8765','tcp:8765');ui.adb('install','-r','android-native/app/build/outputs/apk/qa/app-qa.apk')
 width,height=393,852
 ui.adb('shell','wm','size',f'{width}x{height}');ui.adb('shell','wm','density','160');ui.adb('shell','settings','put','system','font_scale','1.6' if a.large_font else '1.0')
 def button(text):ui.tap_visible(text,width,height)
 def capture(name):fixture.screenshot(a.output,name)
 def login():
  ui.adb('shell','am','force-stop',ui.PACKAGE);ui.adb('shell','pm','clear',ui.PACKAGE);fixture.STATE['answers']={}
  ui.launch_native();ui.enter_login(width,height);fixture.login_field(0,'student@example.test');fixture.login_field(1,'fixture-pass');button('دخول');ui.wait_for('حسابي')
 def tap(prefix):
  for direction in ['up','down','up']:
   for _ in range(8):
    tree,_=ui.ui_tree();node=next((n for n in tree.iter('node') if n.attrib.get('content-desc','').startswith(prefix)),None)
    if node is not None:
     l,t,r,b=map(int,ui.BOUNDS.fullmatch(node.attrib['bounds']).groups())
     if r>l and b>t and t>40 and b<height-70:ui.tap_node(node);time.sleep(.15);return
    y1,y2=(height*3//4,height//3) if direction=='up' else (height//3,height*3//4)
    ui.adb('shell','input','swipe',str(width//2),str(y1),str(width//2),str(y2),'250');time.sleep(.2)
  raise AssertionError('Cannot find native match '+prefix)
 def fill(route):
  tap('A1:');tap('Bc:');capture(route+'-partial')
  for left,right in [('2','a'),('3','b'),('4','f'),('5','d'),('6','e')]:tap('A'+left+':');tap('B'+right+':')
  capture(route+'-complete')
 try:
  login();button('ادرس');button('الرياضيات');button('موضوع فحص افتراضي');ui.wait_for('صِل كل عنصر من القائمة A بما يناسبه من القائمة B.');capture('exam-columns');fill('exam');button('إرسال الإجابة')
  for _ in range(15):
   if '2004' in fixture.STATE['answers']:break
   time.sleep(.4)
  assert fixture.STATE['answers']['2004']['text']==ANSWER;capture('exam-graded')
  login();button('تحدي اليوم');ui.wait_for('سؤال واحد يصنع فرقاً');fill('daily');button('إنهاء تحدي اليوم');button('إرسال وتصحيح');ui.wait_for('أكملت تحدي اليوم!');assert STATE['daily_posts']==1;capture('daily-graded')
  login();button('اختبار مخصص');button('ابدأ الاختبار');ui.wait_for('صِل كل عنصر من القائمة A بما يناسبه من القائمة B.');fill('practice');button('إنهاء الاختبار والتصحيح');button('إنهاء')
  for _ in range(15):
   if STATE['practice_posts']:break
   time.sleep(.4)
  assert STATE['practice_posts']==1;capture('practice-graded')
  (a.output/'results.json').write_text(json.dumps({'passed':True,'routes':['exam','daily','practice'],'font_scale':1.6 if a.large_font else 1.0,'state':STATE},ensure_ascii=False,indent=2));print('PASS actual native matching in three routes',flush=True)
 finally:
  (a.output/'runtime.log').write_bytes(ui.adb('logcat','-d','-v','threadtime','AndroidRuntime:E','*:S'));capture('last-screen');server.shutdown()
  ui.adb('shell','wm','size','reset');ui.adb('shell','wm','density','reset');ui.adb('shell','settings','put','system','font_scale','1.0')
if __name__=='__main__':main()

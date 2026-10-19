"""Direct public entry and iPhone external checkout, using synthetic loopback data."""
import argparse,json,threading,time
from pathlib import Path
from http.server import ThreadingHTTPServer
from urllib.parse import urlsplit
import anonymous_emulator_qa as ui
import student_fixture_qa as f

PAY={'mode':'reject','checkouts':0,'status_calls':0,'status_subscribed':False,'last_payload':None}
class Handler(f.Handler):
 def respond(self):
  path=urlsplit(self.path).path
  assert not self.headers.get('X-Murshid-Play-Policy'), 'Obsolete age-gate protocol returned'
  assert not self.headers.get('X-Murshid-Family-Grant'), 'An approval token was sent'
  assert path!='/mobile/family-access.php', 'Startup created an approval request'
  if path!='/api/payment-checkout.php':return super().respond()
  body=json.loads(self.rfile.read(int(self.headers.get('Content-Length','0'))) or b'{}')
  assert body['csrf']=='synthetic-csrf',body
  if body['action']=='checkout':
   PAY['checkouts']+=1;PAY['last_payload']=body
   assert body['plan']=='month3',body
   assert body['customer_name']==f.USER['name'] and body['customer_phone']=='07700000000',body
   assert not any(k in body for k in ('payment_method','amount','card_number','card_cvc')),body
   time.sleep(1.6)
   if PAY['mode']=='reject':d={'ok':False,'message':'رفض تجريبي واضح؛ لم تُنشأ عملية دفع.'}
   elif PAY['mode']=='unsafe':d={'ok':True,'order_id':11,'redirect':'javascript:alert(1)'}
   else:d={'ok':True,'order_id':11,'redirect':'https://checkout.example.test/invoice/11'}
  else:
   assert body['action']=='status' and body['order_id']==11,body
   PAY['status_calls']+=1
   d={'ok':True,'subscribed':PAY['status_subscribed'],'state':'paid' if PAY['status_subscribed'] else 'pending',
      'message':'الدفع قيد التحقق.'}
  raw=json.dumps(d,ensure_ascii=False).encode();self.send_response(200)
  self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(raw)

def main():
 p=argparse.ArgumentParser();p.add_argument('--output',required=True,type=Path);out=p.parse_args().output
 out.mkdir(parents=True,exist_ok=True)
 server=ThreadingHTTPServer(('127.0.0.1',8765),Handler);threading.Thread(target=server.serve_forever,daemon=True).start()
 ui.adb('reverse','tcp:8765','tcp:8765')
 def snap(name,marker):
  reveal(marker);ui.capture(out,name,marker)
 def reveal(text):
  for direction in ('up','down','up'):
   for _ in range(10):
    root,_=ui.ui_tree();node=ui.find(root,text)
    if node is not None:
     m=ui.BOUNDS.fullmatch(node.attrib.get('bounds',''))
     if m:
      l,t,r,b=map(int,m.groups())
      if r>l and b>t and 24<(t+b)//2<822:return node
    scroll=next((n for n in root.iter('node') if n.attrib.get('class')=='android.widget.ScrollView' and n.attrib.get('scrollable')=='true'),None)
    if scroll is None:time.sleep(.5);continue
    l,t,r,b=map(int,ui.BOUNDS.fullmatch(scroll.attrib['bounds']).groups())
    a,z=t+(b-t)*3//4,t+(b-t)//4
    if direction=='down':a,z=z,a
    ui.adb('shell','input','swipe',str((l+r)//2),str(a),str((l+r)//2),str(z),'250')
  raise AssertionError((text,ui.visible_texts(root)))
 def button(text):ui.tap_node(reveal(text));time.sleep(.35)
 def footer():
  root,_=ui.ui_tree()
  for text in ('الشروط والأحكام','سياسة الخصوصية'):
   n=ui.find(root,text);assert n is not None,text
   l,t,r,b=map(int,ui.BOUNDS.fullmatch(n.attrib['bounds']).groups());assert r>l and b>t and t>650,(text,n.attrib)
  assert ui.find(root,'الخصوصية والفئة العمرية') is None
  assert ui.find(root,'موافقة ولي الأمر') is None
 try:
  ui.adb('shell','wm','size','393x852');ui.adb('shell','wm','density','160')
  ui.adb('shell','settings','put','system','font_scale','1.0')
  # Exercise the actual delivered APK, including a second launch with existing preferences.
  ui.PACKAGE='com.murshidiq.app.preview49824'
  build=Path(__file__).resolve().parents[1]/'app/build/outputs/apk'
  ui.adb('install','-r',str(build/'preview/app-preview.apk'));ui.adb('shell','pm','clear',ui.PACKAGE)
  ui.launch_native();footer();snap('01-preview-direct-entry','تعلّم بتركيز. تقدّم بثقة.')
  ui.adb('shell','am','start','-a','android.settings.SETTINGS')
  ui.adb('shell','am','start','-n',f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
  ui.wait_for('تعلّم بتركيز. تقدّم بثقة.');footer()
  ui.launch_native();footer();snap('02-preview-second-entry','تعلّم بتركيز. تقدّم بثقة.')
  # The QA app uses exactly the same direct entry and checkout UI with a local API.
  ui.PACKAGE='com.murshidiq.app.qa'
  ui.adb('install','-r',str(build/'qa/app-qa.apk'));ui.adb('shell','pm','clear',ui.PACKAGE)
  f.STATE['subscription_active']=False
  # Current server payload supports key as well as id. Invalid prices never appear.
  f.BOOT['plans']=[{'key':'month1','label':'شهر واحد','amount':8000},{'key':'month3','label':'3 أشهر','amount':15000},
                  {'key':'bad','label':'باقة غير صالحة','amount':0}]
  ui.launch_native();footer();ui.enter_login(393,852);footer()
  button('إنشاء حساب طالب جديد');ui.wait_for('بيانات الطالب');footer();snap('03-register-legal-footer','بيانات الطالب')
  f.back();ui.wait_for('سجّل الدخول وواصل دراستك من حيث توقفت');footer()
  f.login_field(0,'student@example.test');f.login_field(1,'fixture-pass');button('دخول');ui.wait_for('حسابي')
  button('حسابي');button('اشتراك');ui.wait_for('لا تحفظ الإجابة فقط… افهم أين تتطور')
  snap('04-subscription-hero','لا تحفظ الإجابة فقط… افهم أين تتطور')
  button('اختيار 3 أشهر');reveal('اختيار شهر واحد')
  root,_=ui.ui_tree()
  assert ui.find(root,'اختيار 3 أشهر').attrib.get('selected')=='true'
  assert ui.find(root,'باقة غير صالحة') is None
  assert PAY['checkouts']==0,'Selecting a plan initiated payment'
  snap('05-iPhone-plan-cards','اختر الباقة')
  node=reveal('الانتقال إلى الدفع الخارجي')
  # Two real taps while the first request is pending must create one invoice request.
  ui.tap_node(node);ui.tap_node(node)
  reveal('رفض تجريبي واضح؛ لم تُنشأ عملية دفع.')
  assert PAY['checkouts']==1 and not f.STATE['subscription_active']
  snap('06-checkout-rejection','رفض تجريبي واضح؛ لم تُنشأ عملية دفع.')
  PAY['mode']='unsafe';button('الانتقال إلى الدفع الخارجي')
  reveal('تعذر تجهيز رابط الدفع الخارجي الآمن.')
  assert PAY['checkouts']==2 and not f.STATE['subscription_active']
  snap('07-unsafe-redirect-rejected','تعذر تجهيز رابط الدفع الخارجي الآمن.')
  PAY['mode']='success';button('الانتقال إلى الدفع الخارجي')
  snap('08-external-provider','بوابة دفع محلية للاختبار')
  assert PAY['checkouts']==3 and not f.STATE['subscription_active'],'Redirect granted subscription'
  button('العودة إلى المرشد')
  reveal('تحقق من حالة آخر عملية');button('تحقق من حالة آخر عملية');reveal('الدفع قيد التحقق.')
  assert not f.STATE['subscription_active']
  snap('09-pending-payment','الدفع قيد التحقق.')
  # The order reference must survive real process death.
  ui.adb('shell','am','force-stop',ui.PACKAGE)
  ui.adb('shell','am','start','-n',f'{ui.PACKAGE}/{ui.MAIN_ACTIVITY}')
  ui.wait_for('حسابي');button('حسابي');button('اشتراك');reveal('تحقق من حالة آخر عملية')
  snap('10-order-survives-process-death','تحقق من حالة آخر عملية')
  PAY['status_subscribed']=True
  button('تحقق من حالة آخر عملية');reveal('لم يؤكد الخادم تفعيل الاشتراك بعد.')
  assert not f.STATE['subscription_active']
  snap('11-bootstrap-not-confirmed','لم يؤكد الخادم تفعيل الاشتراك بعد.')
  f.STATE['subscription_active']=True
  button('تحقق من حالة آخر عملية')
  ui.wait_for('لا تحتاج إلى أي عملية دفع الآن.')
  snap('12-server-confirmed-subscription','لا تحتاج إلى أي عملية دفع الآن.')
  root,_=ui.ui_tree();assert ui.find(root,'الانتقال إلى الدفع الخارجي') is None
  assert not any(path=='/mobile/play-subscription.php' for _,path in f.STATE['calls'])
  logs=ui.adb('logcat','-d','-v','brief','AndroidRuntime:E','*:S');assert b'FATAL EXCEPTION' not in logs,logs
  result={'passed':True,'direct_preview_entry':True,'no_age_or_approval_request':True,'legal_footer':True,
          'plans_from_server':True,'rejects_invalid_redirect':True,'no_duplicate_checkout':True,
          'order_survives_process_death':True,'bootstrap_confirms_entitlement':True,
          'checkout_requests':PAY['checkouts'],'synthetic_only':True}
  (out/'results.json').write_text(json.dumps(result,indent=2))
  print('PASS direct startup, legal footer, iPhone checkout, process death, pending/confirmed payment',flush=True)
 finally:
  (out/'last-screen.png').write_bytes(ui.adb('exec-out','screencap','-p'))
  (out/'last-screen.xml').write_bytes(ui.ui_tree()[1])
  (out/'runtime.log').write_bytes(ui.adb('logcat','-d','-v','threadtime','AndroidRuntime:E','*:S'))
  server.shutdown();server.server_close()
if __name__=='__main__':main()

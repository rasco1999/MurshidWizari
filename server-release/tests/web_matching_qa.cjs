const fs=require('fs'), path=require('path');
const {chromium}=require(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES?path.join(process.env.CODEX_PRIMARY_RUNTIME_NODE_MODULES,'playwright'):'playwright');
const root=process.env.V53_WEB_ROOT||'server_work/public_html';
const sets=JSON.parse(fs.readFileSync(path.join(root,'includes/data/sixth_english_match_sets.json'),'utf8'));
const css=fs.readFileSync(path.join(root,'assets/css/matching-v53.css'),'utf8');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage();const results=[];
 fs.mkdirSync('question_audit/web-evidence',{recursive:true});
 try {
  for(const width of [320,390,768])for(const dark of [false,true])for(const scale of [1,1.6])for(let i=0;i<sets.length;i++){
   await page.setViewportSize({width,height:844});
   await page.setContent(`<html lang="ar" dir="rtl"><meta name="viewport" content="width=device-width,initial-scale=1"><style>:root{--card:${dark?'#20242b':'#f4f7fc'};--text:${dark?'#fff':'#152033'}}*{box-sizing:border-box}body{margin:0;padding:12px;background:${dark?'#000':'#fff'};color:var(--text);font:${16*scale}px Arial,sans-serif}main{max-width:800px;margin:auto}button{font:inherit} ${css}</style><main><h2>صِل عناصر القائمتين</h2><form><div id="matching" data-v53-matching><input type="hidden" name="answer"></div><button type="submit">تسليم الإجابة</button></form></main></html>`);
   await page.locator('#matching').evaluate((host,set)=>{host.dataset.definition=JSON.stringify({left:set.left,right:set.right});},sets[i]);
   await page.addScriptTag({path:path.resolve(root,'assets/js/matching-v53.js')});
   const sizing=await page.evaluate(()=>({overflow:document.documentElement.scrollWidth>innerWidth,clipped:[...document.querySelectorAll('.v53-match-item')].some(b=>b.scrollWidth>b.clientWidth+1||b.scrollHeight>b.clientHeight+1)}));
   if(sizing.overflow||sizing.clipped)throw Error(JSON.stringify({width,dark,scale,i,sizing}));
   results.push({width,dark,scale,set:i,passed:true});
   if(i===0&&width===320){
    await page.screenshot({path:`question_audit/web-evidence/320-${dark?'dark':'light'}-${scale}.png`,fullPage:true});
   }
  }
  // Exercise the actual editor's disabled/incomplete form behavior and conflict editing.
  const set=sets.at(-1), left=Object.keys(set.left).sort((a,b)=>Number(a)-Number(b));
  await page.locator('form>button').click();
  if(!await page.locator('[role=status]').textContent().then(t=>t.includes('أكمل')))throw Error('Incomplete form not blocked');
  for(const k of left){
   await page.locator('.v53-match-columns>div').nth(0).getByRole('button').filter({hasText:new RegExp('^'+k+'\\.')}).click();
   const target=set.map[k];
   await page.locator('.v53-match-columns>div').nth(1).getByRole('button').filter({hasText:new RegExp('^'+target+'\\.')}).click();
  }
  const actual=await page.locator('input[name=answer]').inputValue();
  if(actual!==left.map(k=>`${k}-${set.map[k]}`).join(', '))throw Error('Wrong complete serialization '+actual);
  await page.locator('.v53-match-columns>div').nth(0).getByRole('button').filter({hasText:new RegExp('^'+left[0]+'\\.')}).click();
  await page.locator('.v53-match-columns>div').nth(1).getByRole('button').filter({hasText:new RegExp('^'+set.map[left[1]]+'\\.')}).click();
  if(await page.locator('input[name=answer]').inputValue())throw Error('Editing occupied target retained a complete answer');
  fs.writeFileSync('question_audit/web-evidence/results.json',JSON.stringify({layouts:results,interactionAssertions:3},null,2));
  console.log('PASS '+results.length+' layouts and 3 actual editor interaction assertions');
 } finally {await browser.close();}
})().catch(e=>{console.error(e);process.exit(1)});

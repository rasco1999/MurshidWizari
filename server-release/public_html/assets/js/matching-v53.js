/* Accessible, scoped matching editor. Stores only complete one-to-one answers. */
(() => {
  'use strict';
  function mount(host) {
    const def = JSON.parse(host.dataset.definition || '{}');
    const input = host.querySelector('input');
    const left = Object.entries(def.left || {}).sort((a,b) => Number(a[0])-Number(b[0]));
    const right = Object.entries(def.right || {}).sort((a,b) => a[0].localeCompare(b[0]));
    const pairs = {}; let selected = '';
    const board = document.createElement('div'); board.className = 'v53-match-columns'; board.dir = 'ltr';
    const status = document.createElement('p'); status.setAttribute('role','status');
    host.append(board,status);
    const complete = () => left.length > 0 && left.every(([k]) => pairs[k] && right.some(([r]) => r===pairs[k])) && new Set(Object.values(pairs)).size===left.length;
    const make = (label, action) => {const b=document.createElement('button');b.type='button';b.textContent=label;b.addEventListener('click',action);return b;};
    function render() {
      board.replaceChildren();
      for (const [heading, entries, side] of [['A',left,'left'],['B',right,'right']]) {
        const column=document.createElement('div');const title=document.createElement('h3');title.textContent=heading;column.append(title);
        for (const [key,text] of entries) {
          const partner=side==='left'?pairs[key]:Object.keys(pairs).find(k=>pairs[k]===key);
          const b=make(`${key}. ${text}${partner ? '  ↔ '+(side==='left'?'B':'A')+partner : ''}`,()=>{
            if(side==='left') {selected=key;} else if(selected) {
              for(const k of Object.keys(pairs))if(pairs[k]===key)delete pairs[k];
              pairs[selected]=key;selected='';
            }
            render();
          });
          b.dir='auto';b.className='v53-match-item'+(partner?' is-linked':'')+(side==='left'&&selected===key?' is-selected':'');
          b.setAttribute('aria-pressed',String(side==='left'&&selected===key));
          b.setAttribute('aria-label',`${heading}${key}: ${text}${partner?'، مرتبط بـ '+partner:''}`);
          column.append(b);
        }
        board.append(column);
      }
      status.textContent=selected?'اختر العنصر المناسب من القائمة B.':`تم توصيل ${Object.keys(pairs).length} من ${left.length} — اضغط عنصرًا من A ثم ما يناسبه من B. للتعديل اختر العنصر مجدداً.`;
      input.value=complete()?left.map(([k])=>`${k}-${pairs[k]}`).join(', '):'';
      input.dispatchEvent(new Event('input',{bubbles:true}));
    }
    host.closest('form')?.addEventListener('submit',event=>{
      if(!complete()) {event.preventDefault();event.stopImmediatePropagation();status.textContent='أكمل جميع التوصيلات أولاً.';board.querySelector('button')?.focus();}
    },true);
    render();
  }
  window.MurshidMatching = {mount};
  document.querySelectorAll('[data-v53-matching]').forEach(mount);
})();

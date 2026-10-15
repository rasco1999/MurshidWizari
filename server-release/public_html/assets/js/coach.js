(function () {
  'use strict';
  var app = document.getElementById('coachApp');
  if (!app) return;
  var csrf = app.dataset.csrf, base = app.dataset.base, aiOn = app.dataset.ai === '1';
  var $ = function (id) { return document.getElementById(id); };
  var items = [], i = 0, right = 0, locked = false, tick = null, secs = 0, current = null;
  var LIMIT = 45; // ثوانٍ لكل سؤال

  function post(url, body) {
    body.csrf = csrf;
    return fetch(base + url, { method: 'POST', credentials: 'same-origin', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify(body) })
      .then(function (r) { return r.json(); });
  }
  function shuffle(a) { a = a.slice(); for (var k = a.length - 1; k > 0; k--) { var j = Math.floor(Math.random() * (k + 1)); var t = a[k]; a[k] = a[j]; a[j] = t; } return a; }

  function startTimer() {
    clearInterval(tick); secs = LIMIT; render();
    tick = setInterval(function () { secs--; render(); if (secs <= 0) { clearInterval(tick); if (!locked) answer('', true); } }, 1000);
    function render() { var t = $('sessTimer'); t.textContent = secs + ' ث'; t.classList.toggle('low', secs <= 10); }
  }

  function show() {
    current = items[i]; locked = false;
    $('sessBar').style.width = (i / items.length * 100) + '%';
    $('sessMeta').textContent = current.subject_name + ' — ' + current.chapter_name + '  ·  ' + (i + 1) + '/' + items.length;
    $('sessQ').textContent = current.question_text;
    $('sessFeedback').hidden = true; $('nextQ').hidden = true; $('aiBox').hidden = true;
    if ($('askAi')) $('askAi').hidden = true;
    var box = $('sessOpts'); box.innerHTML = '';
    if (current.english_match && window.MurshidMatching) {
      var host = document.createElement('div'); host.dataset.v53Matching = '';
      host.dataset.definition = JSON.stringify(current.english_match);
      var input = document.createElement('input'); input.type = 'hidden'; host.appendChild(input);
      var submit = document.createElement('button'); submit.type = 'button'; submit.className = 'primary-btn';
      submit.textContent = 'تسليم التوصيلات'; submit.disabled = true;
      input.addEventListener('input', function() { submit.disabled = !input.value; });
      submit.onclick = function() { if (input.value) answer(input.value, false, submit); };
      box.appendChild(host); box.appendChild(submit); window.MurshidMatching.mount(host);
    } else if (current.options && current.options.length) {
      shuffle(current.options).forEach(function (o) {
        var b = document.createElement('button'); b.type = 'button'; b.className = 'coach-opt'; b.textContent = o;
        b.onclick = function () { answer(o, false, b); }; box.appendChild(b);
      });
    } else {
      var inp = document.createElement('input'); inp.type = 'text'; inp.className = 'coach-input'; inp.placeholder = 'اكتب إجابتك'; inp.autocomplete = 'off';
      var go = document.createElement('button'); go.type = 'button'; go.className = 'primary-btn'; go.textContent = 'تحقق';
      go.onclick = function () { if (inp.value.trim()) answer(inp.value.trim(), false, go); };
      inp.onkeydown = function (e) { if (e.key === 'Enter') go.click(); };
      box.appendChild(inp); box.appendChild(go); inp.focus();
    }
    startTimer();
  }

  function answer(val, timedOut, btn) {
    if (locked) return; locked = true; clearInterval(tick);
    Array.prototype.forEach.call($('sessOpts').querySelectorAll('button,input'), function (x) { x.disabled = true; });
    post('/api/coach.php', { question_id: current.id, answer: timedOut ? '—' : val }).then(function (r) {
      if (!r.ok) { $('sessFeedback').hidden = false; $('sessFeedback').textContent = r.message || 'حدث خطأ'; $('nextQ').hidden = false; return; }
      if (r.is_correct) right++;
      if (btn && btn.classList) btn.classList.add(r.is_correct ? 'ok' : 'bad');
      Array.prototype.forEach.call($('sessOpts').querySelectorAll('.coach-opt'), function (o) { if (o.textContent === r.correct_answer) o.classList.add('ok'); });
      var f = $('sessFeedback'); f.hidden = false; f.className = 'coach-feedback ' + (r.is_correct ? 'ok' : 'bad');
      f.textContent = (timedOut ? 'انتهى الوقت. ' : (r.is_correct ? 'إجابة صحيحة. ' : 'إجابة خاطئة. ')) + 'الصحيح: ' + r.correct_answer +
        (r.explanation ? '\n' + r.explanation : '') +
        (r.is_correct ? '\nسيعود هذا السؤال بعد ' + (r.next_in_days || 1) + ' يوم.' : '\nسيعود بعد دقائق لتثبيته.');
      $('nextQ').hidden = false; if ($('askAi')) $('askAi').hidden = false;
    }).catch(function () { $('sessFeedback').hidden = false; $('sessFeedback').textContent = 'تعذّر الاتصال.'; $('nextQ').hidden = false; });
  }

  $('nextQ').onclick = function () {
    i++; if (i >= items.length) return finish(); show();
  };
  function finish() {
    clearInterval(tick); $('session').hidden = true; $('done').hidden = false;
    $('doneLine').textContent = 'أجبت صحيحاً عن ' + right + ' من ' + items.length + '. الأسئلة الخاطئة ستعود إليك قريباً.';
  }
  if ($('askAi')) $('askAi').onclick = function () {
    var b = $('aiBox'); b.hidden = false; b.textContent = 'جارٍ إعداد الشرح…';
    post('/api/study-ai.php', { question_id: current.id }).then(function (r) { b.textContent = r.ok ? r.answer : (r.message || 'تعذّر الشرح'); });
  };
  $('startReview').onclick = function () {
    this.disabled = true;
    fetch(base + '/api/coach.php?limit=10', { credentials: 'same-origin' }).then(function (r) { return r.json(); }).then(function (r) {
      items = (r.items || []); if (!items.length) { $('dueLine').textContent = 'لا توجد أسئلة مستحقة الآن.'; return; }
      i = 0; right = 0; $('session').hidden = false; $('session').scrollIntoView({ behavior: 'smooth' }); show();
    });
  };
})();

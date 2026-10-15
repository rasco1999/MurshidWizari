<?php
declare(strict_types=1);

/** Presentation only: never changes account, curriculum or payment state. */
function platform_bar(string $role='student'): void {
    $base=rtrim(BASE_URL,'/');
    $admin=$role==='admin';
    $teacher=$role==='teacher';
    $section=$admin?'admin':($teacher?'teacher':'student');
    $studentHome=$base.'/';
    $current=basename((string)($_SERVER['SCRIPT_NAME']??''));
    if(!$admin && !$teacher && $current==='index.php') $current='dashboard.php';
    if($current==='curriculum.php')$current='subjects.php';
    $links=$admin?[
        ['dashboard.php','home','نظرة عامة'],['subjects.php','layers','المواد والمواضيع'],
        ['questions.php','book','بنك الأسئلة'],['predictions.php','target','إدارة المرشحات'],
        ['question-workflow.php','layers','مراحل المراجعة'],
        ['question-reports.php','question','بلاغات الأسئلة'],
        ['users.php','users','الطلاب'],['teachers.php','teacher','المعلمون'],
        ['account-requests.php','user','طلبات حذف الحساب'],['team-access.php','shield','صلاحيات الإدارة'],
        ['subscriptions.php','card','الاشتراكات'],['profits.php','chart','الإيرادات'],['payment-gateways.php','card','بوابات الدفع'],
        ['school-ads.php','megaphone','الإعلانات'],['stories.php','celebrate','قصص غيّر جو'],['contest.php','trophy','المنافسة'],
        ['support.php','chat','الدعم والرسائل'],['question-requests.php','plus','طلبات الأسئلة'],
        ['avatar-moderation.php','user','صور الحسابات'],
        ['seasons.php','calendar','المواسم'],['codes.php','ticket','رموز التفعيل'],
        ['analytics.php','analytics','التحليلات'],['audit.php','clipboard','سجل العمليات'],
        ['backup.php','database','النسخ الاحتياطي'],['system-health.php','activity','صحة النظام'],['content-audit.php','checklist','تدقيق بنك الأسئلة'],
        ['security.php','shield','أمان الإدارة'],['change_password.php','lock','كلمة المرور']
    ]:($teacher?[
        ['dashboard.php','home','الرئيسية'],['questions.php','book','أسئلتي']
    ]:[
        ['dashboard.php','home','الرئيسية'],['coach.php','brain','المدرب الذكي'],['stories.php','celebrate','غيّر جو'],
        ['tests.php','checklist','الاختبارات'],['review.php','brain','المراجعة'],['favorites.php','star','المفضلة'],
        ['success.php','chart','تقدمي'],['predictions.php','target','المرشحات'],
        ['study-plan.php','calendar','خطة الدراسة'],['study-timer.php','timer','وقت التركيز'],
        ['center.php','trophy','المنافسة'],
        ['account.php','user','حسابي'],['support.php','chat','الدعم']
    ]);
    if($admin && function_exists('admin_can') && function_exists('current_user')){
        $capByHref=[
            'dashboard.php'=>'dashboard','subjects.php'=>'curriculum','questions.php'=>'questions','predictions.php'=>'curriculum','question-workflow.php'=>'questions','question-reports.php'=>'reports',
            'users.php'=>'users','teachers.php'=>'users','account-requests.php'=>'account_requests','team-access.php'=>'owner','subscriptions.php'=>'subscriptions','profits.php'=>'profits','payment-gateways.php'=>'profits',
            'school-ads.php'=>'school_ads','stories.php'=>'dashboard','contest.php'=>'contest','support.php'=>'support','question-requests.php'=>'question_requests','avatar-moderation.php'=>'avatars','seasons.php'=>'seasons',
            'codes.php'=>'codes','analytics.php'=>'analytics','audit.php'=>'owner','backup.php'=>'owner','system-health.php'=>'owner','content-audit.php'=>'questions','security.php'=>'owner','change_password.php'=>'dashboard'
        ];
        $cu=current_user();$uid=(int)($cu['id']??0);
        $links=array_values(array_filter($links,static function(array $link)use($capByHref,$uid):bool{
            $cap=$capByHref[$link[0]]??null;
            return $cap!==null && admin_can(db(),$uid,$cap);
        }));
    }
    if(!$admin && !$teacher){
        require_once __DIR__ . '/school_ads.php';
        $headerSchoolAds = school_ads_active();
        $menuAvatar='';
        try {
            $signedIn=current_user();
            if($signedIn && !empty($signedIn['id'])) {
                $signedProfile=profile((int)$signedIn['id']);
                $menuAvatar=avatar_url($signedProfile['avatar_path']??null) ?: student_avatar_url($signedProfile['avatar_code']??null,$signedIn['gender']??null);
            }
        } catch(Throwable $e) { $menuAvatar=''; }
    } else {
        $headerSchoolAds = [];
        $menuAvatar='';
    }
    ?>
    <a class="skip-link" href="#platform-content">انتقل إلى المحتوى</a>
    <header class="platform-topbar">
      <a class="platform-brand" href="<?=e(!$admin&&!$teacher?$studentHome:$base.'/'.$section.'/dashboard.php')?>"><img src="<?=e($base)?>/assets/img/site-brand.png?v=20260928-v56" width="44" height="44" alt=""><span><b>منصة المرشد الوزاري</b><small><?=$admin?'مساحة الإدارة':($teacher?'مساحة المعلم':'مساحة تعلّمك')?></small></span></a>
      <?php if(!$admin):?><nav class="platform-nav" aria-label="التنقل"><?php foreach(array_slice($links,0,5) as [$href,$icon,$label]): $navUrl=(!$teacher&&$href==='dashboard.php')?$studentHome:$base.'/'.$section.'/'.$href;?><a href="<?=e($navUrl)?>" <?=strtok($href,'#')===$current&&!str_contains($href,'#')?'aria-current="page"':''?>><?=e($label)?></a><?php endforeach;?></nav><?php endif;?>
      <?php if($headerSchoolAds) school_ads_render_ticker($headerSchoolAds, '', 'platform-school-ad'); ?>
      <div class="platform-actions"><button type="button" class="theme-toggle" aria-label="تبديل المظهر"></button><?php if(!$admin):?><a class="icon-button" href="<?=e($base.'/'.$section.'/'.($teacher?'questions.php':'notifications.php'))?>" aria-label="<?=$teacher?'أسئلتي':'الإشعارات'?>"><?=ui_icon($teacher?'book':'bell')?></a><?php endif;?><button class="icon-button platform-menu-button" type="button" aria-label="فتح القائمة" aria-controls="platformMenu" aria-expanded="false"><?=ui_icon('menu')?></button></div>
    </header>
    <div class="platform-backdrop" hidden></div>
    <nav id="platformMenu" class="platform-menu <?=$admin?'admin-rail':'student-menu'?>" aria-label="<?=$admin?'قائمة الإدارة':'جميع الخدمات'?>">
      <div class="rail-label"><?=$admin?'إدارة منصتك':'كل شيء في مكان واحد'?><button class="icon-button platform-menu-close" type="button" aria-label="إغلاق القائمة"><?=ui_icon('close')?></button></div>
      <?php foreach($links as [$href,$icon,$label]):$active=strtok($href,'#')===$current&&!str_contains($href,'#');$menuUrl=(!$admin&&!$teacher&&$href==='dashboard.php')?$studentHome:$base.'/'.$section.'/'.$href;?><a href="<?=e($menuUrl)?>" <?=$active?'aria-current="page"':''?>><?php if($href==='account.php' && $menuAvatar):?><img class="platform-account-avatar" src="<?=e($menuAvatar)?>" alt="" width="24" height="24" loading="lazy"><?php else:?><?=ui_icon($icon)?><?php endif;?><span><?=e($label)?></span></a><?php endforeach;?>
      <div class="rail-footer">
        <span>منصة تعليمية مستقلة</span>
        <div class="site-social-links" aria-label="حسابات منصة المرشد الوزاري على مواقع التواصل">
        <a class="site-social-link" style="box-sizing:border-box!important;width:24px!important;height:24px!important;min-width:24px!important;min-height:24px!important;max-width:24px!important;max-height:24px!important;padding:0!important;margin:0!important;display:inline-grid!important;place-items:center!important;line-height:0!important;font-size:0!important;flex:0 0 24px!important;overflow:hidden!important" href="https://www.instagram.com/murshid.iq/" target="_blank" rel="noopener noreferrer" aria-label="Instagram منصة المرشد الوزاري" title="Instagram">
          <svg width="11" height="11" style="box-sizing:border-box!important;width:11px!important;height:11px!important;min-width:11px!important;min-height:11px!important;max-width:11px!important;max-height:11px!important;display:block!important;flex:none!important" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><rect x="3" y="3" width="18" height="18" rx="5"></rect><circle cx="12" cy="12" r="4.2"></circle><circle cx="17.4" cy="6.7" r="1"></circle></svg>
        </a>
        <a class="site-social-link" style="box-sizing:border-box!important;width:24px!important;height:24px!important;min-width:24px!important;min-height:24px!important;max-width:24px!important;max-height:24px!important;padding:0!important;margin:0!important;display:inline-grid!important;place-items:center!important;line-height:0!important;font-size:0!important;flex:0 0 24px!important;overflow:hidden!important" href="https://www.facebook.com/Murshidiq/" target="_blank" rel="noopener noreferrer" aria-label="Facebook منصة المرشد الوزاري" title="Facebook">
          <svg width="11" height="11" style="box-sizing:border-box!important;width:11px!important;height:11px!important;min-width:11px!important;min-height:11px!important;max-width:11px!important;max-height:11px!important;display:block!important;flex:none!important" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M14 8.5V6.8c0-.8.5-1.3 1.5-1.3H18V2.2c-.6-.1-1.8-.2-3-.2-3 0-5 1.8-5 5.2v1.3H7v3.7h3V22h4v-9.8h3.3l.6-3.7H14Z"></path></svg>
        </a>
        <a class="site-social-link" style="box-sizing:border-box!important;width:24px!important;height:24px!important;min-width:24px!important;min-height:24px!important;max-width:24px!important;max-height:24px!important;padding:0!important;margin:0!important;display:inline-grid!important;place-items:center!important;line-height:0!important;font-size:0!important;flex:0 0 24px!important;overflow:hidden!important" href="https://t.me/El_Murshed_iq" target="_blank" rel="noopener noreferrer" aria-label="Telegram منصة المرشد الوزاري" title="Telegram">
          <svg width="11" height="11" style="box-sizing:border-box!important;width:11px!important;height:11px!important;min-width:11px!important;min-height:11px!important;max-width:11px!important;max-height:11px!important;display:block!important;flex:none!important" viewBox="0 0 24 24" aria-hidden="true" focusable="false"><path d="M21.6 3.3 2.8 10.6c-1.3.5-1.3 1.2-.2 1.6l4.8 1.5 1.9 5.8c.2.6.1.8.8.8.5 0 .8-.2 1.1-.5l2.3-2.2 4.8 3.5c.9.5 1.5.2 1.7-.8l3.1-14.7c.3-1.2-.5-1.8-1.5-1.3ZM9 13.4l9.4-5.9c.5-.3.9-.1.5.2l-7.8 7-.3 3.4-1.8-4.7Z"></path></svg>
        </a>
      </div>
        <a href="<?=e($base)?>/logout.php<?=$admin?'?scope=admin':''?>"><?=ui_icon('logout')?> تسجيل الخروج</a>
      </div>
    </nav>
    <span id="platform-content" class="anchor-target" tabindex="-1"></span>
    <?php
}

<?php
declare(strict_types=1);
require __DIR__.'/../public_html/includes/android_school_fcm.php';
$a=['id'=>14,'school_name'=>'مدرسة الاختبار','ad_text'=>'الإعلان الأول','updated_at'=>'2026-10-13 08:00:00','start_at'=>'2026-10-13 08:00:00'];
$b=$a; $b['ad_text']='الإعلان الثاني';
if (strlen(android_school_fcm_revision($a))!==64 || android_school_fcm_revision($a)===android_school_fcm_revision($b)) {
    throw new RuntimeException('School-ad deduplication regression');
}
echo "Revision tests passed\n";

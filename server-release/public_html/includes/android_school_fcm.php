<?php
declare(strict_types=1);
function android_school_fcm_revision(array $ad): string {
    return hash('sha256',implode("\0",[(string)($ad['id']??0),(string)($ad['school_name']??''),
        (string)($ad['ad_text']??''),(string)($ad['updated_at']??''),(string)($ad['start_at']??'')]));
}
function android_school_fcm_install(PDO $db): void {
    $db->exec("CREATE TABLE IF NOT EXISTS android_school_fcm_meta(
       id TINYINT UNSIGNED PRIMARY KEY, installed_at DATETIME NOT NULL
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
    $db->exec("INSERT IGNORE INTO android_school_fcm_meta(id,installed_at) VALUES(1,NOW())");
    $db->exec("CREATE TABLE IF NOT EXISTS android_school_fcm_delivery(
       user_id INT UNSIGNED NOT NULL, ad_id BIGINT UNSIGNED NOT NULL,
       revision CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
       created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
       PRIMARY KEY(user_id,ad_id,revision), INDEX idx_school_ad(ad_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
}
function android_school_fcm_tick(PDO $db, int $limit=75): array {
    $stat=['queued'=>0,'checked'=>0,'errors'=>0];
    $limit=max(1,min(150,$limit));
    if (!(int)$db->query("SELECT GET_LOCK('murshid_android_school_fcm',0)")->fetchColumn()) return $stat;
    try {
        $installed=(string)$db->query('SELECT installed_at FROM android_school_fcm_meta WHERE id=1')->fetchColumn();
        if (!$installed) return $stat;
        $ads=$db->prepare("SELECT id,school_name,ad_text,created_at,updated_at,start_at
            FROM school_ads WHERE is_enabled=1
              AND COALESCE(start_at,CONCAT(start_date,' 00:00:00'))<=NOW()
              AND COALESCE(end_at,CONCAT(end_date,' 23:59:59'))>=NOW()
              AND (max_impressions=0 OR impressions<max_impressions)
              AND (created_at>=? OR updated_at>=? OR COALESCE(start_at,CONCAT(start_date,' 00:00:00'))>?)
            ORDER BY id LIMIT 40");
        $ads->execute([$installed,$installed,$installed]);
        foreach($ads->fetchAll(PDO::FETCH_ASSOC) as $ad) {
            if ($stat['queued'] >= $limit) break;
            $id=(int)$ad['id']; if ($id<=0) continue;
            $rev=android_school_fcm_revision($ad);
            $users=$db->prepare("SELECT DISTINCT d.user_id FROM android_fcm_devices d
                JOIN users u ON u.id=d.user_id WHERE u.role='student' AND u.status='active'
                AND NOT EXISTS(SELECT 1 FROM android_school_fcm_delivery x
                    WHERE x.user_id=d.user_id AND x.ad_id=? AND x.revision=?)
                ORDER BY d.user_id LIMIT ?");
            $users->bindValue(1,$id,PDO::PARAM_INT);
            $users->bindValue(2,$rev,PDO::PARAM_STR);
            $users->bindValue(3,$limit-$stat['queued'],PDO::PARAM_INT);
            $users->execute();
            $stat['checked']++;
            foreach($users->fetchAll(PDO::FETCH_COLUMN) as $userId) {
                try {
                    $db->beginTransaction();
                    $ins=$db->prepare('INSERT IGNORE INTO android_school_fcm_delivery(user_id,ad_id,revision) VALUES(?,?,?)');
                    $ins->execute([(int)$userId,$id,$rev]);
                    if ($ins->rowCount()===1) {
                        $insert=$db->prepare('INSERT INTO notifications(user_id,title,body) VALUES(?,?,?)');
                        $insert->execute([(int)$userId,'إعلان مدرسة جديد','لديك إعلان مدرسة جديد في منصة المرشد الوزاري.']);
                        $stat['queued']++;
                    }
                    $db->commit();
                } catch (Throwable $e) {
                    if ($db->inTransaction()) $db->rollBack();
                    $stat['errors']++;
                }
            }
        }
    } finally { $db->query("SELECT RELEASE_LOCK('murshid_android_school_fcm')"); }
    return $stat;
}

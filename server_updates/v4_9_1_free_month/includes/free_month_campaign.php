<?php
declare(strict_types=1);

// Shared read helpers; the schema migration is applied by an administrator,
// never implicitly from the student-facing request path.
function free_month_campaign(PDO $pdo, bool $forUpdate = false): array {
    $query = 'SELECT id,enabled,starts_at_utc,ends_at_utc,capacity,claimed_count FROM murshid_free_month_campaigns WHERE id=1';
    if ($forUpdate) $query .= ' FOR UPDATE';
    $row = $pdo->query($query)->fetch(PDO::FETCH_ASSOC);
    if (!is_array($row)) throw new RuntimeException('Free month campaign not installed');
    return $row;
}

function free_month_status(PDO $pdo, array $campaign, ?int $userId = null): array {
    $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
    $start = DateTimeImmutable::createFromFormat('!Y-m-d H:i:s',
        (string) $campaign['starts_at_utc'], new DateTimeZone('UTC'));
    $end = DateTimeImmutable::createFromFormat('!Y-m-d H:i:s',
        (string) ($campaign['ends_at_utc'] ?? ''), new DateTimeZone('UTC'));
    if (!$start || !$end || $end <= $start) throw new RuntimeException('Invalid free-month schedule');
    $claimed = max(0, (int) $campaign['claimed_count']);
    $capacity = (int) $campaign['capacity'];
    $remaining = max(0, $capacity - $claimed);
    $enabled = (int) $campaign['enabled'] === 1;
    $started = $now >= $start;
    $expired = $now >= $end;
    $alreadyClaimed = false;
    $eligibleNewStudent = false;
    if ($userId !== null) {
        $q = $pdo->prepare('SELECT id FROM murshid_free_month_claims WHERE user_id=? LIMIT 1');
        $q->execute([$userId]);
        $alreadyClaimed = (bool) $q->fetchColumn();

        // `users.created_at` is written using the DB server's local NOW().
        // Shift UTC campaign limits by that same session offset, including
        // on installations whose MySQL timezone differs from PHP/Baghdad.
        $u = $pdo->prepare('SELECT created_at >= DATE_ADD(?, INTERVAL TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),NOW()) SECOND) AS after_start,
                created_at < DATE_ADD(?, INTERVAL TIMESTAMPDIFF(SECOND,UTC_TIMESTAMP(),NOW()) SECOND) AS before_end,
                email,email_verified_at,phone,phone_verified_at
                FROM users WHERE id=? AND role=\'student\' AND status=\'active\' LIMIT 1');
        $u->execute([$start->format('Y-m-d H:i:s'),$end->format('Y-m-d H:i:s'),$userId]);
        $row = $u->fetch(PDO::FETCH_ASSOC) ?: null;
        if ($row) {
            $verified = (empty($row['email']) || !empty($row['email_verified_at'])) &&
                (empty($row['phone']) || !empty($row['phone_verified_at']));
            $eligibleNewStudent = $verified && (int)$row['after_start'] === 1 && (int)$row['before_end'] === 1;
        }
    }
    return [
        'enabled' => $enabled,
        'started' => $started,
        'expired' => $expired,
        'full' => $remaining === 0,
        'capacity' => $capacity,
        'claimed' => $claimed,
        'remaining' => $remaining,
        'already_claimed' => $alreadyClaimed,
        'eligible_new_student' => $eligibleNewStudent,
        'starts_at_utc' => $start->format('Y-m-d\TH:i:s\Z'),
        'ends_at_utc' => $end->format('Y-m-d\TH:i:s\Z'),
        'state' => !$enabled ? 'paused' : (!$started ? 'scheduled' : ($expired ? 'expired' : ($remaining > 0 ? 'open' : 'full'))),
    ];
}

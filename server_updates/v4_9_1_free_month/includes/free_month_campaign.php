<?php
declare(strict_types=1);

// Shared read helpers; the schema migration is applied by an administrator,
// never implicitly from the student-facing request path.
function free_month_campaign(PDO $pdo, bool $forUpdate = false): array {
    $query = 'SELECT id,enabled,starts_at_utc,capacity,claimed_count FROM murshid_free_month_campaigns WHERE id=1';
    if ($forUpdate) $query .= ' FOR UPDATE';
    $row = $pdo->query($query)->fetch(PDO::FETCH_ASSOC);
    if (!is_array($row)) throw new RuntimeException('Free month campaign not installed');
    return $row;
}

function free_month_status(PDO $pdo, array $campaign, ?int $userId = null): array {
    $now = new DateTimeImmutable('now', new DateTimeZone('UTC'));
    $start = DateTimeImmutable::createFromFormat('!Y-m-d H:i:s',
        (string) $campaign['starts_at_utc'], new DateTimeZone('UTC'));
    if (!$start) throw new RuntimeException('Invalid free-month schedule');
    $claimed = max(0, (int) $campaign['claimed_count']);
    $capacity = (int) $campaign['capacity'];
    $remaining = max(0, $capacity - $claimed);
    $enabled = (int) $campaign['enabled'] === 1;
    $started = $now >= $start;
    $alreadyClaimed = false;
    if ($userId !== null) {
        $q = $pdo->prepare('SELECT id FROM murshid_free_month_claims WHERE user_id=? LIMIT 1');
        $q->execute([$userId]);
        $alreadyClaimed = (bool) $q->fetchColumn();
    }
    return [
        'enabled' => $enabled,
        'started' => $started,
        'full' => $remaining === 0,
        'capacity' => $capacity,
        'claimed' => $claimed,
        'remaining' => $remaining,
        'already_claimed' => $alreadyClaimed,
        'starts_at_utc' => $start->format('Y-m-d\TH:i:s\Z'),
        'state' => !$enabled ? 'paused' : (!$started ? 'scheduled' : ($remaining > 0 ? 'open' : 'full')),
    ];
}

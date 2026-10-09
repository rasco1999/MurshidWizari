<?php
declare(strict_types=1);

// CLI only; do not expose code generation through an unauthenticated web route.
// Install under public_html/tools/ or run with MURSHID_PUBLIC_HTML set.
if (PHP_SAPI !== 'cli') {
    http_response_code(404);
    exit;
}

$args = getopt('', ['months:', 'count:', 'batch::', 'valid-until::']);
$months = filter_var($args['months'] ?? null, FILTER_VALIDATE_INT);
$count = filter_var($args['count'] ?? null, FILTER_VALIDATE_INT);
$batch = (string) ($args['batch'] ?? 'manual');
$validUntil = (string) ($args['valid-until'] ?? '');

if ($months === false || $months < 1 || $months > 36 ||
    $count === false || $count < 1 || $count > 100 ||
    strlen($batch) > 80 || !preg_match('/^[A-Za-z0-9_-]+$/D', $batch)) {
    fwrite(STDERR, "Usage: php issue-subscription-codes.php --months=1 --count=10 [--batch=school1] [--valid-until=2026-12-31]\n");
    exit(2);
}

$expiry = null;
if ($validUntil !== '') {
    $date = DateTimeImmutable::createFromFormat('!Y-m-d', $validUntil);
    if (!$date || $date->format('Y-m-d') !== $validUntil) {
        fwrite(STDERR, "Invalid --valid-until date; use YYYY-MM-DD.\n");
        exit(2);
    }
    $expiry = $validUntil . ' 23:59:59';
}

$publicHtml = rtrim((string) (getenv('MURSHID_PUBLIC_HTML') ?: dirname(__DIR__)), '/');
$config = $publicHtml . '/config/config.php';
if (!is_file($config)) {
    fwrite(STDERR, "Site config not found. Set MURSHID_PUBLIC_HTML to your public_html path.\n");
    exit(2);
}
require_once $config;

$pdo = db();
$insert = $pdo->prepare("INSERT INTO subscription_access_codes
    (code_hash, months, status, batch_label, valid_until)
    VALUES (?, ?, 'available', ?, ?)");

fputcsv(STDOUT, ['code', 'months', 'batch', 'valid_until']);
for ($n = 0; $n < $count; $n++) {
    // 96 random bits: guessing a valid issued code is computationally infeasible.
    $hex = strtoupper(bin2hex(random_bytes(12)));
    $normalized = 'MW' . $hex;
    $display = 'MW-' . implode('-', str_split($hex, 4));
    $insert->execute([hash('sha256', $normalized), $months, $batch, $expiry]);
    fputcsv(STDOUT, [$display, $months, $batch, $validUntil]);
}

fwrite(STDERR, "Codes generated. Copy these plaintext codes now; only hashes are stored in MySQL. Never commit their output to Git.\n");

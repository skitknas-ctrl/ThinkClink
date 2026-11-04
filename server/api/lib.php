<?php
// Shared helpers for the Myntkorgen API. Included by the endpoints, never called directly.
// Requires PHP 8.1+ with mysqli (mysqlnd). Database settings come from config.php on the server.
declare(strict_types=1);

if (!defined('MK_API')) {
    http_response_code(404);
    exit;
}

const MK_MAX_BODY = 262144; // 256 KB per request is plenty for one level

set_exception_handler(function (Throwable $e): void {
    error_log('myntkorgen api: ' . $e->getMessage());
    mk_fail(500, 'server_error');
});

/** CORS for testing in a browser (the app itself does not need it). Answers OPTIONS directly. */
function mk_cors(): void
{
    header('Access-Control-Allow-Origin: *');
    header('Access-Control-Allow-Methods: GET, POST, OPTIONS');
    header('Access-Control-Allow-Headers: Content-Type, X-Api-Key');
    if (($_SERVER['REQUEST_METHOD'] ?? '') === 'OPTIONS') {
        http_response_code(204);
        exit;
    }
}

function mk_json(int $status, array $data): never
{
    http_response_code($status);
    header('Content-Type: application/json; charset=utf-8');
    header('Cache-Control: no-store');
    echo json_encode($data, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES | JSON_PRESERVE_ZERO_FRACTION);
    exit;
}

function mk_fail(int $status, string $error): never
{
    mk_json($status, ['ok' => false, 'error' => $error]);
}

function mk_require_method(string $method): void
{
    if (($_SERVER['REQUEST_METHOD'] ?? '') !== $method) {
        mk_fail(405, 'method_not_allowed');
    }
}

function mk_db(): mysqli
{
    $cfg = require __DIR__ . '/config.php'; // ['db_host','db_name','db_user','db_pass','db_charset']
    mysqli_report(MYSQLI_REPORT_ERROR | MYSQLI_REPORT_STRICT);
    $db = new mysqli($cfg['db_host'], $cfg['db_user'], $cfg['db_pass'], $cfg['db_name']);
    $db->set_charset($cfg['db_charset']);
    return $db;
}

/** Checks the X-Api-Key header and the given permission column; returns the api_clients row. */
function mk_client(mysqli $db, string $permission): array
{
    $allowed = ['can_send_stats', 'can_read_questions', 'can_edit_questions'];
    if (!in_array($permission, $allowed, true)) {
        mk_fail(500, 'server_error');
    }
    $key = $_SERVER['HTTP_X_API_KEY'] ?? '';
    if ($key === '' || strlen($key) > 200) {
        mk_fail(401, 'missing_api_key');
    }
    $hash = hash('sha256', $key);
    $st = $db->prepare('SELECT id, name, can_send_stats, can_read_questions, can_edit_questions
                          FROM api_clients WHERE key_hash = ? AND active = 1');
    $st->bind_param('s', $hash);
    $st->execute();
    $row = $st->get_result()->fetch_assoc();
    if (!$row) {
        mk_fail(401, 'invalid_api_key');
    }
    if ((int)$row[$permission] !== 1) {
        mk_fail(403, 'permission_denied');
    }
    $up = $db->prepare('UPDATE api_clients SET last_used_at = NOW() WHERE id = ?');
    $id = (int)$row['id'];
    $up->bind_param('i', $id);
    $up->execute();
    return $row;
}

/** Reads and decodes the JSON request body. */
function mk_body(): array
{
    $raw = file_get_contents('php://input', false, null, 0, MK_MAX_BODY + 1);
    if ($raw === false || $raw === '') {
        mk_fail(400, 'empty_body');
    }
    if (strlen($raw) > MK_MAX_BODY) {
        mk_fail(413, 'body_too_large');
    }
    $data = json_decode($raw, true, 32);
    if (!is_array($data)) {
        mk_fail(400, 'invalid_json');
    }
    return $data;
}

/** Integer query parameter with default and range check. */
function mk_query_int(string $name, ?int $default, int $min, int $max): ?int
{
    if (!isset($_GET[$name]) || $_GET[$name] === '') {
        return $default;
    }
    $v = filter_var($_GET[$name], FILTER_VALIDATE_INT);
    if ($v === false || $v < $min || $v > $max) {
        mk_fail(400, "invalid_$name");
    }
    return $v;
}

// ---- Validation of decoded JSON fields. $path is only used in the error message. ----

function mk_int(array $a, string $key, int $min, int $max, string $path, bool $nullable = false, ?int $default = null): ?int
{
    if (!array_key_exists($key, $a) || $a[$key] === null) {
        if ($nullable || $default !== null) {
            return $default;
        }
        mk_fail(400, "missing_field:$path.$key");
    }
    $v = $a[$key];
    if (is_bool($v)) {
        $v = $v ? 1 : 0;
    }
    if (is_float($v) && floor($v) === $v) {
        $v = (int)$v;
    }
    if (!is_int($v) || $v < $min || $v > $max) {
        mk_fail(400, "invalid_field:$path.$key");
    }
    return $v;
}

function mk_num(array $a, string $key, float $min, float $max, string $path): float
{
    $v = $a[$key] ?? null;
    if (!is_int($v) && !is_float($v)) {
        mk_fail(400, (array_key_exists($key, $a) ? 'invalid_field:' : 'missing_field:') . "$path.$key");
    }
    $v = (float)$v;
    if (!is_finite($v) || $v < $min || $v > $max) {
        mk_fail(400, "invalid_field:$path.$key");
    }
    return $v;
}

function mk_str(array $a, string $key, int $maxLen, string $path, bool $nullable = false, ?string $pattern = null): ?string
{
    if (!array_key_exists($key, $a) || $a[$key] === null) {
        if ($nullable) {
            return null;
        }
        mk_fail(400, "missing_field:$path.$key");
    }
    $v = $a[$key];
    if (!is_string($v) || mb_strlen($v) > $maxLen || ($pattern !== null && !preg_match($pattern, $v))) {
        mk_fail(400, "invalid_field:$path.$key");
    }
    return $v;
}

function mk_enum(array $a, string $key, array $values, string $path): string
{
    $v = $a[$key] ?? null;
    if (!is_string($v) || !in_array($v, $values, true)) {
        mk_fail(400, (array_key_exists($key, $a) ? 'invalid_field:' : 'missing_field:') . "$path.$key");
    }
    return $v;
}

function mk_list(array $a, string $key, int $maxItems, string $path): array
{
    $v = $a[$key] ?? [];
    if (!is_array($v) || !array_is_list($v) || count($v) > $maxItems) {
        mk_fail(400, "invalid_field:$path.$key");
    }
    foreach ($v as $item) {
        if (!is_array($item)) {
            mk_fail(400, "invalid_field:$path.$key");
        }
    }
    return $v;
}

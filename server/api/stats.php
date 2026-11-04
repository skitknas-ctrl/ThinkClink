<?php
// POST stats.php – anonymous statistics for one played level (key needs can_send_stats).
//
// Body (JSON):
//   { "session": { "uuid", "app_version", "settings": {name: int, …}, "world_height", "grade" },
//     "level":   { "level", "attempt", "outcome", … see mk_level_fields below },
//     "misses":  [ { "level_time", "mf_planned", "mf_effective", "distance", "miss_by", "basket_width", "slow_active" }, … ],
//     "answers": [ { "purpose", "question_id", "subject_code", "difficulty", "question_text",
//                    "correct", "timed_out", "answer_ms", "time_limit_ms" }, … ] }
//
// Sending the same level again (same session uuid, level and attempt) is harmless: the answer is
// {"ok":true,"duplicate":true} and nothing is stored twice. So the app can simply retry until it gets 200.
declare(strict_types=1);
define('MK_API', true);
require __DIR__ . '/lib.php';

mk_cors();
mk_require_method('POST');
$db = mk_db();
mk_client($db, 'can_send_stats');
$body = mk_body();

// ---- Session ----
$s = $body['session'] ?? null;
if (!is_array($s)) {
    mk_fail(400, 'missing_field:session');
}
$uuid = strtolower(mk_str($s, 'uuid', 36, 'session', false, '/^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/'));
$appVersion = mk_str($s, 'app_version', 30, 'session', false, '/^[0-9A-Za-z.\-_+]{1,30}$/');
$settings = $s['settings'] ?? null;
if (!is_array($settings) || array_is_list($settings) && $settings !== [] || count($settings) > 80) {
    mk_fail(400, 'invalid_field:session.settings');
}
foreach ($settings as $k => $v) {
    if (!is_string($k) || !preg_match('/^[A-Za-z][A-Za-z0-9_]{0,40}$/', $k) || !is_int($v) || $v < -1000000 || $v > 1000000) {
        mk_fail(400, 'invalid_field:session.settings');
    }
}
ksort($settings);
$settingsJson = json_encode((object)$settings);
$settingsHash = hash('sha256', $settingsJson);
$worldHeight = mk_int($s, 'world_height', 100, 5000, 'session', true);
$grade = mk_int($s, 'grade', 1, 12, 'session', true);

// ---- Level ----
$l = $body['level'] ?? null;
if (!is_array($l)) {
    mk_fail(400, 'missing_field:level');
}
$lv = [
    'level' => mk_int($l, 'level', 1, 65535, 'level'),
    'attempt' => mk_int($l, 'attempt', 1, 255, 'level'),
    'outcome' => mk_enum($l, 'outcome', ['completed', 'game_over', 'abandoned'], 'level'),
    'speed_level' => mk_int($l, 'speed_level', 0, 255, 'level'),
    'size_level' => mk_int($l, 'size_level', 0, 255, 'level'),
    'mf_start_effective' => mk_num($l, 'mf_start_effective', 0, 99999, 'level'),
    'lives_start' => mk_int($l, 'lives_start', 0, 255, 'level'),
    'lives_lost' => mk_int($l, 'lives_lost', 0, 255, 'level'),
    'lives_bought' => mk_int($l, 'lives_bought', 0, 255, 'level', false, 0),
    'speed_bought' => mk_int($l, 'speed_bought', 0, 255, 'level', false, 0),
    'size_bought' => mk_int($l, 'size_bought', 0, 255, 'level', false, 0),
    'coins_planned' => mk_int($l, 'coins_planned', 0, 65535, 'level'),
    'coins_caught' => mk_int($l, 'coins_caught', 0, 65535, 'level'),
    'bills_planned' => mk_int($l, 'bills_planned', 0, 255, 'level', false, 0),
    'bills_caught' => mk_int($l, 'bills_caught', 0, 255, 'level', false, 0),
    'gems_planned' => mk_int($l, 'gems_planned', 0, 255, 'level', false, 0),
    'gems_caught' => mk_int($l, 'gems_caught', 0, 255, 'level', false, 0),
    'ground_used' => mk_int($l, 'ground_used', 0, 255, 'level', false, 0),
    'slow_used' => mk_int($l, 'slow_used', 0, 255, 'level', false, 0),
    'balance_end' => mk_int($l, 'balance_end', 0, 2000000000, 'level'),
    'play_seconds' => mk_num($l, 'play_seconds', 0, 99999, 'level'),
];

// ---- Misses ----
$misses = [];
foreach (mk_list($body, 'misses', 1000, 'body') as $i => $m) {
    $p = "misses[$i]";
    $misses[] = [
        mk_num($m, 'level_time', 0, 9999, $p),
        mk_num($m, 'mf_planned', 0, 99999, $p),
        mk_num($m, 'mf_effective', 0, 99999, $p),
        mk_int($m, 'distance', 0, 65535, $p),
        mk_num($m, 'miss_by', -9999, 99999, $p),
        mk_num($m, 'basket_width', 0, 9999, $p),
        mk_int($m, 'slow_active', 0, 1, $p, false, 0),
    ];
}

// ---- Answers ----
$answers = [];
$questionIds = [];
foreach (mk_list($body, 'answers', 200, 'body') as $i => $a) {
    $p = "answers[$i]";
    $qid = mk_int($a, 'question_id', 1, 4294967295, $p, true);
    if ($qid !== null) {
        $questionIds[$qid] = true;
    }
    $answers[] = [
        'purpose' => mk_enum($a, 'purpose', ['speed', 'money', 'life', 'gate'], $p),
        'question_id' => $qid,
        'subject_code' => mk_str($a, 'subject_code', 30, $p, false, '/^[a-z0-9_]{1,30}$/'),
        'difficulty' => mk_int($a, 'difficulty', 1, 5, $p),
        'question_text' => mk_str($a, 'question_text', 500, $p, true),
        'correct' => mk_int($a, 'correct', 0, 1, $p),
        'timed_out' => mk_int($a, 'timed_out', 0, 1, $p, false, 0),
        'answer_ms' => mk_int($a, 'answer_ms', 0, 3600000, $p),
        'time_limit_ms' => mk_int($a, 'time_limit_ms', 0, 3600000, $p),
    ];
}

// Question ids that no longer exist are stored as NULL instead of failing the whole level.
$known = [];
if ($questionIds) {
    $ids = implode(',', array_map('intval', array_keys($questionIds)));
    $res = $db->query("SELECT id FROM questions WHERE id IN ($ids)");
    while ($row = $res->fetch_row()) {
        $known[(int)$row[0]] = true;
    }
}

// ---- Store everything in one transaction ----
$db->begin_transaction();
try {
    $st = $db->prepare('INSERT INTO game_sessions (session_uuid, app_version, settings_hash, settings_json, world_height, grade)
                        VALUES (?, ?, ?, ?, ?, ?)
                        ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)');
    $st->bind_param('ssssii', $uuid, $appVersion, $settingsHash, $settingsJson, $worldHeight, $grade);
    $st->execute();
    $sessionId = (int)$db->insert_id;

    $st = $db->prepare('INSERT INTO level_results
        (session_id, level, attempt, outcome, speed_level, size_level, mf_start_effective, lives_start, lives_lost,
         lives_bought, speed_bought, size_bought, coins_planned, coins_caught, bills_planned, bills_caught,
         gems_planned, gems_caught, ground_used, slow_used, balance_end, play_seconds)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
    $st->bind_param(
        'iiisiidiiiiiiiiiiiiiid',
        $sessionId, $lv['level'], $lv['attempt'], $lv['outcome'], $lv['speed_level'], $lv['size_level'],
        $lv['mf_start_effective'], $lv['lives_start'], $lv['lives_lost'], $lv['lives_bought'], $lv['speed_bought'],
        $lv['size_bought'], $lv['coins_planned'], $lv['coins_caught'], $lv['bills_planned'], $lv['bills_caught'],
        $lv['gems_planned'], $lv['gems_caught'], $lv['ground_used'], $lv['slow_used'], $lv['balance_end'],
        $lv['play_seconds']
    );
    try {
        $st->execute();
    } catch (mysqli_sql_exception $e) {
        if ($e->getCode() === 1062) { // this level was already stored
            $db->rollback();
            mk_json(200, ['ok' => true, 'duplicate' => true]);
        }
        throw $e;
    }

    if ($misses) {
        $st = $db->prepare('INSERT INTO coin_misses
            (session_id, level, attempt, level_time, mf_planned, mf_effective, distance, miss_by, basket_width, slow_active)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
        foreach ($misses as $m) {
            [$t, $mfp, $mfe, $dist, $by, $bw, $slow] = $m;
            $st->bind_param('iiidddiddi', $sessionId, $lv['level'], $lv['attempt'], $t, $mfp, $mfe, $dist, $by, $bw, $slow);
            $st->execute();
        }
    }

    if ($answers) {
        $st = $db->prepare('INSERT INTO question_answers
            (session_id, level, purpose, question_id, subject_code, difficulty, question_text, correct, timed_out, answer_ms, time_limit_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)');
        foreach ($answers as $a) {
            $qid = ($a['question_id'] !== null && isset($known[$a['question_id']])) ? $a['question_id'] : null;
            $st->bind_param(
                'iisisisiiii',
                $sessionId, $lv['level'], $a['purpose'], $qid, $a['subject_code'], $a['difficulty'],
                $a['question_text'], $a['correct'], $a['timed_out'], $a['answer_ms'], $a['time_limit_ms']
            );
            $st->execute();
        }
    }

    $db->commit();
} catch (Throwable $e) {
    $db->rollback();
    throw $e;
}

mk_json(200, ['ok' => true, 'stored' => ['misses' => count($misses), 'answers' => count($answers)]]);

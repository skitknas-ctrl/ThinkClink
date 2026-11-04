<?php
// GET questions.php – subjects and questions for the app (key needs can_read_questions).
//
// The app keeps a local copy and asks only for what changed since last time:
//   ?since=<unix seconds>&after_id=<id>   cursor from the previous answer (next_since, next_after_id); both 0 the first time
//   &limit=<1–1000>                       default 500; "more": true means call again with the new cursor
//   &subject=<code>  &grade=<1–12>        optional filters
// Drafts are never sent. Retired questions are sent with status "retired" so the app can remove them.
declare(strict_types=1);
define('MK_API', true);
require __DIR__ . '/lib.php';

mk_cors();
mk_require_method('GET');
$db = mk_db();
mk_client($db, 'can_read_questions');

$since = mk_query_int('since', 0, 0, 4102444800);
$afterId = mk_query_int('after_id', 0, 0, PHP_INT_MAX);
$limit = mk_query_int('limit', 500, 1, 1000);
$grade = mk_query_int('grade', null, 1, 12);
$subject = $_GET['subject'] ?? '';
if ($subject !== '' && !preg_match('/^[a-z0-9_]{1,30}$/', $subject)) {
    mk_fail(400, 'invalid_subject');
}

$subjects = [];
$res = $db->query('SELECT code, name, sort_order, active FROM subjects ORDER BY sort_order, name');
while ($row = $res->fetch_assoc()) {
    $subjects[] = [
        'code' => $row['code'],
        'name' => $row['name'],
        'sort_order' => (int)$row['sort_order'],
        'active' => (int)$row['active'] === 1,
    ];
}

$sql = "SELECT q.id, s.code AS subject, q.grade_min, q.grade_max, q.difficulty, q.language,
               q.question_text, q.answer, q.wrong1, q.wrong2, q.wrong3, q.wrong4, q.wrong5,
               q.explanation, q.image_url, q.status, UNIX_TIMESTAMP(q.updated_at) AS updated
          FROM questions q
          JOIN subjects s ON s.id = q.subject_id
         WHERE q.status <> 'draft'
           AND (q.updated_at > FROM_UNIXTIME(?) OR (q.updated_at = FROM_UNIXTIME(?) AND q.id > ?))";
$types = 'iii';
$params = [$since, $since, $afterId];
if ($subject !== '') {
    $sql .= ' AND s.code = ?';
    $types .= 's';
    $params[] = $subject;
}
if ($grade !== null) {
    $sql .= ' AND ? BETWEEN q.grade_min AND q.grade_max';
    $types .= 'i';
    $params[] = $grade;
}
$sql .= ' ORDER BY q.updated_at, q.id LIMIT ?';
$types .= 'i';
$params[] = $limit + 1;

$st = $db->prepare($sql);
$st->bind_param($types, ...$params);
$st->execute();
$res = $st->get_result();

$questions = [];
while ($row = $res->fetch_assoc()) {
    $wrong = [];
    foreach (['wrong1', 'wrong2', 'wrong3', 'wrong4', 'wrong5'] as $w) {
        if ($row[$w] !== null && $row[$w] !== '') {
            $wrong[] = $row[$w];
        }
    }
    $questions[] = [
        'id' => (int)$row['id'],
        'subject' => $row['subject'],
        'grade_min' => (int)$row['grade_min'],
        'grade_max' => (int)$row['grade_max'],
        'difficulty' => (int)$row['difficulty'],
        'language' => $row['language'],
        'text' => $row['question_text'],
        'answer' => $row['answer'],
        'wrong' => $wrong,
        'explanation' => $row['explanation'],
        'image_url' => $row['image_url'],
        'status' => $row['status'],
        'updated' => (int)$row['updated'],
    ];
}

$more = count($questions) > $limit;
if ($more) {
    array_pop($questions);
}
$last = end($questions);

mk_json(200, [
    'ok' => true,
    'server_time' => time(),
    'subjects' => $subjects,
    'questions' => $questions,
    'more' => $more,
    'next_since' => $last ? $last['updated'] : $since,
    'next_after_id' => $last ? $last['id'] : $afterId,
]);

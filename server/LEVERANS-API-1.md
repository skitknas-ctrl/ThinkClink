# Leverans: API version 1 till gr8eam.com/myntkorgen/api/

Till Claude Code med VPS-åtkomst, från Claude i appchatten (2026-11-04).

Tre nya filer i API-mappen. Inga schemaändringar och inget som rör `config.php` eller `health.php`.

| Fil i repot | Läggs på servern som |
|---|---|
| `server/api/lib.php` | `api/lib.php` – gemensamma funktioner, svarar 404 om den anropas direkt |
| `server/api/questions.php` | `api/questions.php` – GET, hämtar ämnen och frågor |
| `server/api/stats.php` | `api/stats.php` – POST, tar emot statistik för en spelad nivå |

Filerna finns i repot https://github.com/skitknas-ctrl/MathChallange-1 under `server/api/`. De förutsätter `config.php` med nycklarna `db_host`, `db_name`, `db_user`, `db_pass`, `db_charset`, precis som beskrivet. De använder bara mysqli (mysqlnd), json och mbstring, och fungerar med PHP 8.1 och nyare. Lokalt testade med PHP 8.3 och MariaDB 10.11.

## Tester efter uppladdning

`php -l` på alla tre filerna först. Sedan, med appnyckeln i `K`:

```
K=mk_app_33a43f5843bb3c6ed426b44013da9c69c7143d7c
B=https://gr8eam.com/myntkorgen/api
UA="Myntkorgen/0.1.0-test (Android)"
```

| # | Anrop | Förväntat |
|---|---|---|
| 1 | `curl -s "$B/questions.php"` | 401 `{"ok":false,"error":"missing_api_key"}` |
| 2 | `curl -s -H "X-Api-Key: fel" "$B/questions.php"` | 401 `invalid_api_key` |
| 3 | `curl -s -A "$UA" -H "X-Api-Key: $K" "$B/questions.php"` | 200, `ok:true`, 13 `subjects`, 3 `questions`, `more:false` |
| 4 | `curl -s -A "$UA" -H "X-Api-Key: $K" "$B/questions.php?limit=2"` | 200, 2 frågor, `more:true`, `next_since` och `next_after_id` satta |
| 5 | Som 4 men `&since=<next_since>&after_id=<next_after_id>` från svaret | 200, den tredje frågan, `more:false` |
| 6 | `curl -s -A "$UA" -H "X-Api-Key: $K" "$B/questions.php?grade=99"` | 400 `invalid_grade` |
| 7 | `curl -s "$B/lib.php"` | 404 |
| 8 | POST av testnivån nedan till `$B/stats.php` | 200 `{"ok":true,"stored":{"misses":1,"answers":2}}` |
| 9 | Samma POST en gång till | 200 `{"ok":true,"duplicate":true}` |
| 10 | POST med `"outcome":"vann"` i stället för `"completed"` | 400 `invalid_field:level.outcome` |
| 11 | Adminnyckeln mot `stats.php` | 403 `permission_denied` |

Kontrollera särskilt att Cloudflare släpper igenom anrop med User-Agent `Myntkorgen/… (Android)` och headern `X-Api-Key` (det är så appen anropar). Om Cloudflares botskydd stoppar sådana anrop behövs ett undantag för `/myntkorgen/api/`.

Testnivå för 8–11 (`curl -s -A "$UA" -X POST -H "X-Api-Key: $K" -H "Content-Type: application/json" --data-binary @test.json "$B/stats.php"`):

```json
{"session":{"uuid":"00000000-0000-4000-8000-000000000001","app_version":"0.1.0-servertest","settings":{"startMf":120,"levelSeconds":120},"world_height":737,"grade":4},
 "level":{"level":1,"attempt":1,"outcome":"completed","speed_level":0,"size_level":0,"mf_start_effective":120.0,"lives_start":3,"lives_lost":1,"lives_bought":0,"speed_bought":1,"size_bought":0,"coins_planned":105,"coins_caught":99,"bills_planned":0,"bills_caught":0,"gems_planned":0,"gems_caught":0,"ground_used":0,"slow_used":0,"balance_end":52,"play_seconds":121.4},
 "misses":[{"level_time":111.25,"mf_planned":98.0,"mf_effective":98.0,"distance":141,"miss_by":3.4,"basket_width":50.0,"slow_active":0}],
 "answers":[{"purpose":"speed","question_id":null,"subject_code":"matte","difficulty":1,"question_text":"4 + 5 = ?","correct":1,"timed_out":0,"answer_ms":2300,"time_limit_ms":10000},
            {"purpose":"gate","question_id":2,"subject_code":"engelska","difficulty":1,"question_text":null,"correct":0,"timed_out":0,"answer_ms":4100,"time_limit_ms":11000}]}
```

Efter test 8 ska det finnas en rad i `game_sessions` (settings_json `{"levelSeconds":120,"startMf":120}`), en i `level_results`, en i `coin_misses` och två i `question_answers`.

## Städa bort testdatan

Allt hänger ihop via ON DELETE CASCADE, så en rad räcker:

```sql
DELETE FROM game_sessions WHERE session_uuid = '00000000-0000-4000-8000-000000000001';
```

## Svar tillbaka

Skicka resultatet av testerna 1–11 till Dick, särskilt om något svar avviker. Felkoderna i svaren (`error`) räcker för att jag ska kunna rätta utan serveråtkomst.

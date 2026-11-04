# Leverans: API version 1 till gr8eam.com/myntkorgen/api/

Till Claude Code med VPS-åtkomst, från Claude i appchatten (2026-11-04).

Två nya filer i API-mappen. Inga schemaändringar och inget som rör `config.php`, `health.php` eller ert `questions.php`. (En tidigare version av den här leveransen hade också ett questions.php; det är borttaget, ert används.) Vad appen skickar beskrivs i `server/STATISTIK-API.md`.

| Fil i repot | Läggs på servern som |
|---|---|
| `server/api/lib.php` | `api/lib.php` – nyckelkontroll, JSON-svar och fältkontroller; svarar 404 om den anropas direkt. Finns det redan en lib.php: döp om till `stats_lib.php` och ändra require-raden i stats.php |
| `server/api/stats.php` | `api/stats.php` – POST, tar emot statistik för en spelad nivå |

Filerna finns i repot https://github.com/skitknas-ctrl/ThinkClink under `server/api/`. De förutsätter `config.php` med nycklarna `db_host`, `db_name`, `db_user`, `db_pass`, `db_charset`, precis som beskrivet. De använder bara mysqli (mysqlnd), json och mbstring, och fungerar med PHP 8.1 och nyare. Lokalt testade med PHP 8.3 och MariaDB 10.11.

## Tester efter uppladdning

`php -l` på båda filerna först. Sedan, med appnyckeln i `K`:

```
K=mk_app_33a43f5843bb3c6ed426b44013da9c69c7143d7c
B=https://gr8eam.com/myntkorgen/api
UA="Myntkorgen/0.1.0-test (Android)"
```

| # | Anrop | Förväntat |
|---|---|---|
| 1 | `curl -s -X POST -d '{}' "$B/stats.php"` | 401 `{"ok":false,"error":"missing_api_key"}` |
| 2 | `curl -s -X POST -H "X-Api-Key: fel" -d '{}' "$B/stats.php"` | 401 `invalid_api_key` |
| 3 | `curl -s -A "$UA" -H "X-Api-Key: $K" "$B/stats.php"` (GET) | 405 `method_not_allowed` |
| 4 | `curl -s -A "$UA" -X POST -H "X-Api-Key: $K" -d '{inte json' "$B/stats.php"` | 400 `invalid_json` |
| 5 | POST av `server/stats-exempel.json` | 200 `{"ok":true,"stored":{"misses":3,"answers":3}}` |
| 6 | Samma som 5 en gång till | 200 `{"ok":true,"duplicate":true}` |
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

Efter test 8 ska det finnas en rad i `game_sessions` (settings_json `{"levelSeconds":120,"startMf":120}`), en i `level_results`, en i `coin_misses` och två i `question_answers`. Test 5 ger ytterligare ett spel med alla inställningar.

## Städa bort testdatan

Allt hänger ihop via ON DELETE CASCADE, så det räcker att ta bort spelen:

```sql
DELETE FROM game_sessions WHERE session_uuid IN ('00000000-0000-4000-8000-000000000001', '3f1c2a9e-5b7d-4e21-9c3a-1d2e3f4a5b6c');
```

## Svar tillbaka

Skicka resultatet av testerna 1–11 till Dick, särskilt om något svar avviker. Felkoderna i svaren (`error`) räcker för att jag ska kunna rätta utan serveråtkomst.

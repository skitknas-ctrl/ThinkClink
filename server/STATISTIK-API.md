# Statistik-API:t – vad appen skickar

Till Claude Code med VPS-åtkomst, från Claude i appchatten (2026-11-04). Gäller appen från testbygge 0.1.0-test.5.

## Kort version

Appen gör **ett enda anrop för statistik**: `POST https://gr8eam.com/myntkorgen/api/stats.php`, ett anrop per spelad nivå. Spel, nivåresultat, missade mynt och frågesvar kommer i samma JSON. Sammanhållet blir det enkelt för appen att skicka om och för servern att spara allt i en transaktion.

Det finns redan en **färdig, testad implementation** i repot (https://github.com/skitknas-ctrl/MathChallange-1):

- `server/api/stats.php` – själva anropet
- `server/api/lib.php` – nyckelkontroll, JSON-svar och fältkontroller, som bara stats.php använder

Den är skriven mot schemaversion 1 och använder `config.php` och mysqli så som ni beskrivit. Den är testad lokalt med PHP 8.3 och MariaDB 10.11, både med handskrivna testfall och med statistik från nio nivåer som spelmotorn själv spelat. Förslag: ladda upp de två filerna som de är. Testerna och städningen efteråt står i `server/LEVERANS-API-1.md`.

Om det redan finns en `lib.php` med annat innehåll: döp om min till `stats_lib.php` och ändra `require __DIR__ . '/lib.php'` i stats.php. Vill ni hellre bygga ett eget stats.php räcker det att följa kontraktet nedan, så fungerar appen.

## Anropet

```
POST /myntkorgen/api/stats.php
X-Api-Key: <appens nyckel>              (behöver can_send_stats = 1)
Content-Type: application/json; charset=utf-8
User-Agent: Myntkorgen/<version> (Android)
```

Kroppen är högst 256 kB. En nivå har i praktiken 1–10 kB.

### Kroppen

Ett fullständigt exempel som spelmotorn skapat finns i `server/stats-exempel.json`.

**`session`** – ett spel. Samma `uuid` i alla nivåer i samma spel.

| Fält | Typ | Kolumn i `game_sessions` |
|---|---|---|
| `uuid` | sträng, UUID v4 (36 tecken), slumpad per spel | `session_uuid` |
| `app_version` | sträng ≤ 30, `[0-9A-Za-z.\-_+]` | `app_version` |
| `settings` | objekt med cirka 30 heltal (alla testinställningar) | `settings_json` (nycklarna sorterade), `settings_hash` = SHA-256 av den JSON-texten |
| `world_height` | heltal 100–5000 | `world_height` |
| `grade` | heltal 1–12 | `grade` |

**`level`** – en rad i `level_results`. Fälten heter som kolumnerna: `level` (≥ 1), `attempt` (1–255), `outcome` (`completed`, `game_over` eller `abandoned`), `speed_level`, `size_level`, `mf_start_effective` (decimal), `lives_start`, `lives_lost`, `lives_bought`, `speed_bought`, `size_bought`, `coins_planned`, `coins_caught`, `bills_planned`, `bills_caught`, `gems_planned`, `gems_caught`, `ground_used`, `slow_used`, `balance_end`, `play_seconds` (decimal). Alla heltal är ≥ 0.

**`misses`** – 0–1000 rader i `coin_misses`: `level_time`, `mf_planned`, `mf_effective`, `miss_by`, `basket_width` (decimaler), `distance` (heltal), `slow_active` (0/1). `session_id`, `level` och `attempt` tas från nivån.

**`answers`** – 0–200 rader i `question_answers`: `purpose` (`speed`, `money`, `life` eller `gate`), `question_id` (id i `questions`, eller null för mattetal som appen skapar), `subject_code`, `difficulty` (1–5), `question_text` (bara för appens mattetal, annars null), `correct` (0/1), `timed_out` (0/1), `answer_ms`, `time_limit_ms`. `level` tas från nivån.

Decimaltal skickas med två decimaler, t.ex. `133.33`.

### Vad servern ska göra

1. Kontrollera nyckeln och alla fält. Vid fel: 400 med `{"ok":false,"error":"invalid_field:level.outcome"}` eller liknande.
2. I en transaktion:
   - lägg in spelet eller hämta det befintliga på `session_uuid`,
   - lägg in nivån,
   - lägg in missarna och svaren.
3. **Samma nivå två gånger** (samma `session_uuid`, `level` och `attempt`, alltså den unika nyckeln i `level_results`): spara ingenting nytt och svara `200 {"ok":true,"duplicate":true}`. Appen skickar om när den inte fått svar, så detta händer på riktigt.
4. Ett `question_id` som inte finns (frågan raderad) sparas som NULL i stället för att hela nivån avvisas.
5. Svara `200 {"ok":true,"stored":{"misses":<antal>,"answers":<antal>}}`.

### Hur appen reagerar på svaret

| Svar | Appen |
|---|---|
| 200 | Tar bort nivån ur sin kö |
| 400, 413 | Tar bort nivån (innehållet blev avvisat, att skicka igen hjälper inte) och visar felet på startskärmen |
| 401, 403, 429, 5xx, inget nät | Behåller nivån och försöker igen senare |

### När appen skickar

- När nästa nivå startar skickas den förra, så att butiksbesöket efter nivån kommer med (köp och frågesvar).
- När spelet tar slut (`game_over`) eller man går till menyn mitt i en nivå (`abandoned`).
- Utan nät läggs nivåerna i en kö i appen, högst 500. Kön skickas vid nästa nivå, när appen startar och när den kommer tillbaka från bakgrunden.
- Spelaren kan stänga av statistiken under Testinställningar. Då töms kön och inget skickas.

## Om fråge-API:t

Appen använder nu ert format:

- första gången, eller efter fel 400 på `since`, hämtar den `questions.php` utan parametrar,
- därefter `questions.php?since=<server_time från förra svaret>`, URL-kodat (`+01:00` skickas som `%2B01%3A00`),
- den läser `question_text`, `subject_name`, `aktuell_till` och `image_url`,
- den slutar använda en fråga lokalt när `aktuell_till` har passerats,
- den laddar ner bilder (bara https, högst 600 kB, utan nyckel, med samma User-Agent). En fråga med bild används först när bilden finns i telefonen.

En sak att kontrollera: om `since` jämförs med `updated_at > since`, och `server_time` sätts efter att frågan körts, kan en fråga som ändras under samma sekund missas. Antingen sätts `server_time` innan urvalet görs, eller så jämförs med `>=`. Dubbletter skadar inte, appen skriver bara över frågan.

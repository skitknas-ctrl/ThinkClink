-- =====================================================================
-- MathChallange-1 / Myntkorgen – databas för frågor och anonym spelstatistik
-- Schemaversion 1, 2026-11-04
--
-- Kräver MySQL 8.0.16+ eller MariaDB 10.4+ (InnoDB, utf8mb4, CHECK-villkor, JSON).
-- Kör hela filen i en tom databas, t.ex. i phpMyAdmin under fliken SQL,
-- eller:  mysql -u ANVÄNDARE -p DATABASNAMN < schema.sql
--
-- Appen ansluter ALDRIG direkt till databasen (då skulle lösenordet ligga i appen).
-- Den pratar med ett litet API på servern, som i sin tur använder databasen.
--
-- Inga personuppgifter sparas: inga namn, e-postadresser, IP-adresser eller enhets-id.
-- Ett spel identifieras bara av ett slumpat id som appen skapar för varje nytt spel.
-- =====================================================================

SET NAMES utf8mb4;

-- Om databasen inte redan finns, ta bort kommentartecknen på de två raderna nedan.
-- CREATE DATABASE IF NOT EXISTS myntkorgen CHARACTER SET utf8mb4 COLLATE utf8mb4_swedish_ci;
-- USE myntkorgen;

CREATE TABLE schema_info (
  version     INT UNSIGNED NOT NULL,
  applied_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

INSERT INTO schema_info (version) VALUES (1);

-- ---------------------------------------------------------------------
-- 1. API-nycklar
-- Själva nyckeln sparas aldrig, bara dess SHA-256. API:t hashar nyckeln
-- som appen skickar och jämför. Exempel längst ned i filen.
-- ---------------------------------------------------------------------
CREATE TABLE api_clients (
  id                  INT UNSIGNED NOT NULL AUTO_INCREMENT,
  name                VARCHAR(60)  NOT NULL,
  key_hash            CHAR(64)     NOT NULL COMMENT 'SHA-256 av nyckeln i hex',
  can_send_stats      TINYINT(1)   NOT NULL DEFAULT 0,
  can_read_questions  TINYINT(1)   NOT NULL DEFAULT 0,
  can_edit_questions  TINYINT(1)   NOT NULL DEFAULT 0,
  active              TINYINT(1)   NOT NULL DEFAULT 1,
  created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  last_used_at        TIMESTAMP    NULL DEFAULT NULL,
  PRIMARY KEY (id),
  UNIQUE KEY uq_api_clients_key (key_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

-- ---------------------------------------------------------------------
-- 2. Ämnen
-- ---------------------------------------------------------------------
CREATE TABLE subjects (
  id          SMALLINT UNSIGNED NOT NULL AUTO_INCREMENT,
  code        VARCHAR(30)  NOT NULL COMMENT 'Kort kod som appen använder, t.ex. matte',
  name        VARCHAR(60)  NOT NULL COMMENT 'Namnet som visas i appen',
  sort_order  SMALLINT     NOT NULL DEFAULT 0,
  active      TINYINT(1)   NOT NULL DEFAULT 1,
  PRIMARY KEY (id),
  UNIQUE KEY uq_subjects_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

INSERT INTO subjects (code, name, sort_order) VALUES
  ('matte',      'Matematik',          10),
  ('svenska',    'Svenska',            20),
  ('engelska',   'Engelska',           30),
  ('biologi',    'Biologi',            40),
  ('fysik',      'Fysik',              50),
  ('kemi',       'Kemi',               60),
  ('geografi',   'Geografi',           70),
  ('historia',   'Historia',           80),
  ('religion',   'Religionskunskap',   90),
  ('samhalle',   'Samhällskunskap',   100),
  ('teknik',     'Teknik',            110),
  ('musik',      'Musik',             120),
  ('idrott',     'Idrott och hälsa',  130);

-- ---------------------------------------------------------------------
-- 3. Frågor
-- Ett rätt svar och 1–5 felaktiga. Appen visar rätt svar plus de felaktiga
-- som finns, i slumpad ordning. Årskurs 1–9 = grundskolan, 10–12 = gymnasiet år 1–3.
-- Frågor tas inte bort utan sätts till 'retired', så att appen vid nästa
-- hämtning ser att de ska bort (appen hämtar det som ändrats sedan updated_at).
-- ---------------------------------------------------------------------
CREATE TABLE questions (
  id             INT UNSIGNED      NOT NULL AUTO_INCREMENT,
  subject_id     SMALLINT UNSIGNED NOT NULL,
  grade_min      TINYINT UNSIGNED  NOT NULL COMMENT 'Lägsta årskurs, 1–12',
  grade_max      TINYINT UNSIGNED  NOT NULL COMMENT 'Högsta årskurs, 1–12',
  difficulty     TINYINT UNSIGNED  NOT NULL DEFAULT 3 COMMENT '1 lättast – 5 svårast inom årskursspannet',
  language       CHAR(2)           NOT NULL DEFAULT 'sv',
  question_text  VARCHAR(500)      NOT NULL,
  answer         VARCHAR(120)      NOT NULL COMMENT 'Rätt svar',
  wrong1         VARCHAR(120)      NOT NULL,
  wrong2         VARCHAR(120)      NULL DEFAULT NULL,
  wrong3         VARCHAR(120)      NULL DEFAULT NULL,
  wrong4         VARCHAR(120)      NULL DEFAULT NULL,
  wrong5         VARCHAR(120)      NULL DEFAULT NULL,
  explanation    VARCHAR(500)      NULL DEFAULT NULL COMMENT 'Kan visas efter svaret',
  image_url      VARCHAR(255)      NULL DEFAULT NULL COMMENT 'För framtida bildfrågor',
  status         ENUM('draft','active','retired') NOT NULL DEFAULT 'draft',
  source         VARCHAR(120)      NULL DEFAULT NULL COMMENT 'Vem som skrivit eller var frågan kommer ifrån',
  created_at     TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at     TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY ix_questions_sync (updated_at),
  KEY ix_questions_pick (subject_id, status, grade_min, grade_max, difficulty),
  CONSTRAINT fk_questions_subject FOREIGN KEY (subject_id) REFERENCES subjects (id),
  CONSTRAINT ck_questions_grade CHECK (grade_min BETWEEN 1 AND 12 AND grade_max BETWEEN grade_min AND 12),
  CONSTRAINT ck_questions_difficulty CHECK (difficulty BETWEEN 1 AND 5)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

-- Tre exempelfrågor, så att API:t går att prova direkt.
INSERT INTO questions (subject_id, grade_min, grade_max, difficulty, question_text, answer, wrong1, wrong2, wrong3, wrong4, wrong5, status, source)
SELECT id, 1, 4, 1, 'Vad heter Sveriges huvudstad?', 'Stockholm', 'Göteborg', 'Malmö', 'Uppsala', 'Lund', 'Västerås', 'active', 'exempel'
  FROM subjects WHERE code = 'geografi';
INSERT INTO questions (subject_id, grade_min, grade_max, difficulty, question_text, answer, wrong1, wrong2, wrong3, wrong4, wrong5, status, source)
SELECT id, 1, 4, 1, 'Vad heter ”hund” på engelska?', 'dog', 'cat', 'horse', 'bird', 'cow', 'fish', 'active', 'exempel'
  FROM subjects WHERE code = 'engelska';
INSERT INTO questions (subject_id, grade_min, grade_max, difficulty, question_text, answer, wrong1, wrong2, wrong3, wrong4, wrong5, status, source)
SELECT id, 3, 6, 2, 'Hur många ben har en insekt?', '6', '4', '8', '10', '2', '12', 'active', 'exempel'
  FROM subjects WHERE code = 'biologi';

-- ---------------------------------------------------------------------
-- 4. Spel (en rad per nytt spel)
-- session_uuid slumpas av appen för varje nytt spel och kopplas inte till
-- enhet eller person. Två spel på samma telefon går inte att koppla ihop.
-- ---------------------------------------------------------------------
CREATE TABLE game_sessions (
  id             BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  session_uuid   CHAR(36)          NOT NULL,
  app_version    VARCHAR(30)       NOT NULL,
  settings_hash  CHAR(64)          NOT NULL COMMENT 'SHA-256 av settings_json, räknas ut av API:t. Grupperar spel med samma inställningar.',
  settings_json  JSON              NOT NULL COMMENT 'Alla testinställningar när spelet startade',
  world_height   SMALLINT UNSIGNED NULL DEFAULT NULL COMMENT 'Spelplanens höjd i spelpunkter (påverkar hur länge mynten syns)',
  grade          TINYINT UNSIGNED  NULL DEFAULT NULL COMMENT 'Årskurs om spelaren valt en',
  received_at    TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_sessions_uuid (session_uuid),
  KEY ix_sessions_settings (settings_hash),
  CONSTRAINT ck_sessions_grade CHECK (grade IS NULL OR grade BETWEEN 1 AND 12)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

-- ---------------------------------------------------------------------
-- 5. Resultat per spelad nivå
-- attempt räknas upp om samma nivå spelas om (t.ex. efter att appen stängts mitt i).
-- ---------------------------------------------------------------------
CREATE TABLE level_results (
  id                  BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  session_id          BIGINT UNSIGNED   NOT NULL,
  level               SMALLINT UNSIGNED NOT NULL,
  attempt             TINYINT UNSIGNED  NOT NULL DEFAULT 1,
  outcome             ENUM('completed','game_over','abandoned') NOT NULL,
  speed_level         TINYINT UNSIGNED  NOT NULL COMMENT 'Köpta fartsteg (0–30) när nivån startade',
  size_level          TINYINT UNSIGNED  NOT NULL COMMENT 'Korgstorlek (0–4) när nivån startade',
  mf_start_effective  DECIMAL(6,1)      NOT NULL COMMENT 'Upplevd MF vid nivåns start, med spelarens korgfart',
  lives_start         TINYINT UNSIGNED  NOT NULL,
  lives_lost          TINYINT UNSIGNED  NOT NULL,
  lives_bought        TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  speed_bought        TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'Fartköp i butiken under eller efter nivån',
  size_bought         TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  coins_planned       SMALLINT UNSIGNED NOT NULL,
  coins_caught        SMALLINT UNSIGNED NOT NULL,
  bills_planned       TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  bills_caught        TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  gems_planned        TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  gems_caught         TINYINT UNSIGNED  NOT NULL DEFAULT 0,
  ground_used         TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'Antal markkorgar',
  slow_used           TINYINT UNSIGNED  NOT NULL DEFAULT 0 COMMENT 'Antal slowmotion',
  balance_end         INT UNSIGNED      NOT NULL COMMENT 'Mynt i plånboken när nivån slutade',
  play_seconds        DECIMAL(6,1)      NOT NULL COMMENT 'Speltid utan pauser och butik',
  created_at          TIMESTAMP         NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uq_level_results (session_id, level, attempt),
  KEY ix_level_results_level (level),
  CONSTRAINT fk_level_results_session FOREIGN KEY (session_id) REFERENCES game_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

-- ---------------------------------------------------------------------
-- 6. Missade mynt – visar vid vilken MF spelarna börjar tappa liv
-- ---------------------------------------------------------------------
CREATE TABLE coin_misses (
  id            BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  session_id    BIGINT UNSIGNED   NOT NULL,
  level         SMALLINT UNSIGNED NOT NULL,
  attempt       TINYINT UNSIGNED  NOT NULL DEFAULT 1,
  level_time    DECIMAL(6,2)      NOT NULL COMMENT 'Sekunder in i nivån',
  mf_planned    DECIMAL(6,1)      NOT NULL COMMENT 'MF som nivån planerades för',
  mf_effective  DECIMAL(6,1)      NOT NULL COMMENT 'MF med spelarens korgfart',
  distance      SMALLINT UNSIGNED NOT NULL COMMENT 'Avstånd från förra myntet, spelpunkter',
  miss_by       DECIMAL(6,1)      NOT NULL COMMENT 'Hur långt utanför korgkanten myntet landade, spelpunkter',
  basket_width  DECIMAL(5,1)      NOT NULL,
  slow_active   TINYINT(1)        NOT NULL DEFAULT 0,
  PRIMARY KEY (id),
  KEY ix_coin_misses_session (session_id, level),
  KEY ix_coin_misses_mf (mf_effective),
  CONSTRAINT fk_coin_misses_session FOREIGN KEY (session_id) REFERENCES game_sessions (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

-- ---------------------------------------------------------------------
-- 7. Svar på frågor – visar vilka frågor och ämnen som är lätta eller svåra
-- purpose: speed = fartköp, money = matte för pengar, life = matte för liv,
--          gate = krav för att få starta nästa nivå (planerat).
-- ---------------------------------------------------------------------
CREATE TABLE question_answers (
  id             BIGINT UNSIGNED   NOT NULL AUTO_INCREMENT,
  session_id     BIGINT UNSIGNED   NOT NULL,
  level          SMALLINT UNSIGNED NOT NULL,
  purpose        ENUM('speed','money','life','gate') NOT NULL,
  question_id    INT UNSIGNED      NULL DEFAULT NULL COMMENT 'Fråga från databasen; NULL för mattefrågor som appen skapar själv',
  subject_code   VARCHAR(30)       NOT NULL,
  difficulty     TINYINT UNSIGNED  NOT NULL,
  question_text  VARCHAR(500)      NULL DEFAULT NULL COMMENT 'Bara för frågor som appen skapar, t.ex. 7 × 8',
  correct        TINYINT(1)        NOT NULL,
  timed_out      TINYINT(1)        NOT NULL DEFAULT 0,
  answer_ms      INT UNSIGNED      NOT NULL COMMENT 'Tid till svar i millisekunder',
  time_limit_ms  INT UNSIGNED      NOT NULL,
  PRIMARY KEY (id),
  KEY ix_answers_question (question_id),
  KEY ix_answers_subject (subject_code, difficulty),
  CONSTRAINT fk_answers_session FOREIGN KEY (session_id) REFERENCES game_sessions (id) ON DELETE CASCADE,
  CONSTRAINT fk_answers_question FOREIGN KEY (question_id) REFERENCES questions (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_swedish_ci;

-- ---------------------------------------------------------------------
-- 8. Färdiga sammanställningar (öppna dem som vanliga tabeller i phpMyAdmin)
-- ---------------------------------------------------------------------

-- Hur svår varje nivå är med en viss uppsättning inställningar
CREATE OR REPLACE VIEW v_level_difficulty AS
SELECT s.settings_hash,
       r.level,
       COUNT(*)                                                        AS attempts,
       ROUND(AVG(r.outcome = 'completed') * 100, 1)                    AS completed_pct,
       ROUND(AVG(r.lives_lost), 2)                                     AS avg_lives_lost,
       ROUND(AVG(r.coins_caught / NULLIF(r.coins_planned, 0)) * 100, 1) AS caught_pct,
       ROUND(AVG(r.mf_start_effective), 1)                             AS avg_mf_start,
       ROUND(AVG(CAST(r.level AS SIGNED) - 1 - CAST(r.speed_level AS SIGNED)), 2) AS avg_speed_steps_behind
  FROM level_results r
  JOIN game_sessions s ON s.id = r.session_id
 GROUP BY s.settings_hash, r.level;

-- Vid vilken upplevd MF mynten missas (i steg om 5)
CREATE OR REPLACE VIEW v_misses_by_mf AS
SELECT FLOOR(mf_effective / 5) * 5 AS mf_from,
       COUNT(*)                    AS misses,
       ROUND(AVG(miss_by), 1)      AS avg_miss_by,
       ROUND(AVG(distance), 0)     AS avg_distance
  FROM coin_misses
 GROUP BY FLOOR(mf_effective / 5) * 5;

-- Hur ofta frågor besvaras rätt, per ämne, svårighet och fråga
CREATE OR REPLACE VIEW v_question_stats AS
SELECT subject_code,
       difficulty,
       question_id,
       COUNT(*)                          AS answers,
       ROUND(AVG(correct) * 100, 1)      AS correct_pct,
       ROUND(AVG(timed_out) * 100, 1)    AS timeout_pct,
       ROUND(AVG(answer_ms) / 1000, 1)   AS avg_seconds
  FROM question_answers
 GROUP BY subject_code, difficulty, question_id;

-- ---------------------------------------------------------------------
-- 9. Nycklar – gör detta efteråt, med egna långa slumpade nycklar.
-- Nyckel för appen: den kommer att synas för den som undersöker appen,
-- så den får bara skicka statistik och läsa frågor.
-- Adminnyckel: hemlig, bara för dig, får lägga in och ändra frågor.
-- ---------------------------------------------------------------------
-- INSERT INTO api_clients (name, key_hash, can_send_stats, can_read_questions)
--   VALUES ('Appen', SHA2('BYT-UT-MOT-EN-LÅNG-SLUMPAD-NYCKEL-1', 256), 1, 1);
-- INSERT INTO api_clients (name, key_hash, can_read_questions, can_edit_questions)
--   VALUES ('Admin', SHA2('BYT-UT-MOT-EN-ANNAN-LÅNG-SLUMPAD-NYCKEL-2', 256), 1, 1);

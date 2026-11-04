# MathChallange-1

Fånga mynt med en korg, köp större korgar genom att lösa huvudräkning. Spelet heter MYNTKORGEN i appen.

## Spelregler

- Korgen flyttas med två aktiva fält i vänster och höger kant (10 % av bredden vardera, i mittersta 50 % av höjden). Håll fingret i fältet så rör sig korgen. Fälten påverkar inte fallande föremål.
- Varje nivå är 120 sekunder och blir svårare medan den pågår. Hur svårt det är styrs av MF, möjlighetsfaktorn: hur mycket tid korgen har på sig att hinna från ett mynt till nästa.
- Ett missat mynt kostar ett liv. Man börjar med 3 liv och kan ha högst 6.
- Mellan nivåerna och när man tappar ett liv öppnas butiken: **snabbare korg** (kräver rätt svar på en mattefråga), **större korg** (för ädelstenar), **liv**, och mattefrågor som ger pengar eller liv.
- Köper man inte snabbare korg inför varje nivå blir det snabbt för svårt.
- Från nivå 3 faller ibland sedlar (värda 100 mynt) och ädelstenar. Stenar köper större korg, en extra korg på marken i 10 s eller slowmotion i 10 s.
- Korgen dras av en gubbe, en lastcykel, en åsna … och till slut tåg och jetmotorer: en ny dragare för varje fartköp.

Alla regler och siffror står i [DESIGN.md](DESIGN.md). Värdena kan ändras under **Testinställningar** på startskärmen och under Paus.

## Bygga

Projektet byggs med Gradle och Android SDK. Ingen Android Studio behövs.

    ./gradlew assembleDebug

GitHub Actions bygger en test-APK vid varje push till `main` och publicerar den som utgåvan `0.1.0-test.<byggnummer>` (äldre testutgåvor tas bort). Senaste bygget finns alltid här:

    https://github.com/skitknas-ctrl/MathChallange-1/releases/latest/download/mathchallange-1-debug.apk

Med Obtainium: lägg till `https://github.com/skitknas-ctrl/MathChallange-1`, inga extra inställningar behövs.

Testbyggen signeras med nyckeln i `app/debug.keystore` (endast för test) så att de kan installeras ovanpå varandra. Appen får id `io.github.skitknasctrl.mathchallange1.debug` och installeras alltså vid sidan av en framtida riktig utgåva. Riktiga utgåvor (taggar `v*`) signeras med en nyckel som lämnas via GitHub Secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Struktur

- `app/src/main/kotlin/.../Engine.kt` – spelregler och spelläge (ren Kotlin)
- `Renderer.kt`, `Draggers.kt` – all grafik, ritad med vektorgrafik via det lilla rit-gränssnittet i `Gfx.kt`
- `GameView.kt`, `MainActivity.kt`, `AndroidGfx.kt`, `Sound.kt` – Android-delen
- `Rules.kt` – testinställningarna, `MathQuiz.kt` – mattefrågorna, `Synth.kt` – ljudeffekterna
- `DESIGN.md` – reglerna
- `.github/workflows/build.yml` – bygge och testutgåvor

Inga bildfiler, inga ljudfiler och inga AndroidX-beroenden.

## Ursprung

Första versionen av koden skrevs av ChatGPT som en ensam `MainActivity.kt`. Projektstrukturen och bygget runt den gjordes med Claude.

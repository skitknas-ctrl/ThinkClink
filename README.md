# MathChallange-1

Fånga mynt med en korg, köp större korgar genom att lösa huvudräkning. Spelet heter MYNTKORGEN i appen.

## Spelregler

- Dra korgen åt sidan för att fånga fallande mynt. Varje fångat mynt ger ett mynt i saldot och en poäng.
- Missar du för många mynt är omgången slut. Rekordet sparas.
- Tryck **Köp korg** när saldot räcker. Priset dras direkt och du får en räkneuppgift med sex svarsalternativ.
- Rätt svar ger större korg och en återbetalning som är större ju snabbare du svarar. Fel svar eller slut på tiden förbrukar insatsen.
- Det finns tio uppgraderingar. Mattesvårigheten ökar ett steg varannan uppgradering.
- Fart och myntflöde ökar för varje hel spelminut (högst tre gånger startvärdena).

Alla värden (antal tillåtna missar, fallhastighet, myntflöde, svarstid, pris med mera) kan ändras under **Testinställningar** på startskärmen och under Paus.

## Bygga

Projektet byggs med Gradle och Android SDK. Ingen Android Studio behövs.

    ./gradlew assembleDebug

GitHub Actions bygger en test-APK vid varje push till `main` och publicerar den som utgåvan `0.1.0-test.<byggnummer>` (äldre testutgåvor tas bort). Senaste bygget finns alltid här:

    https://github.com/skitknas-ctrl/MathChallange-1/releases/latest/download/mathchallange-1-debug.apk

Med Obtainium: lägg till `https://github.com/skitknas-ctrl/MathChallange-1`, inga extra inställningar behövs.

Testbyggen signeras med nyckeln i `app/debug.keystore` (endast för test) så att de kan installeras ovanpå varandra. Appen får id `io.github.skitknasctrl.mathchallange1.debug` och installeras alltså vid sidan av en framtida riktig utgåva. Riktiga utgåvor (taggar `v*`) signeras med en nyckel som lämnas via GitHub Secrets: `RELEASE_KEYSTORE_BASE64`, `RELEASE_KEYSTORE_PASSWORD`, `RELEASE_KEY_ALIAS`, `RELEASE_KEY_PASSWORD`.

## Struktur

- `app/src/main/kotlin/.../MainActivity.kt` – hela spelet: logik, ritning på Android Canvas, pekhantering och testinställningar. Inga bildfiler och inga AndroidX-beroenden.
- `.github/workflows/build.yml` – bygge och testutgåvor.

## Ursprung

Första versionen av koden skrevs av ChatGPT som en ensam `MainActivity.kt`. Projektstrukturen och bygget runt den gjordes med Claude.

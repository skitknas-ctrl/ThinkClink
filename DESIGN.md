# ThinkClink – design

Version 2026-11-04. Reglerna nedan är Dicks beslut. Där han godkänt Claudes förslag står det **[förslag, godkänt]**. Alla siffror markerade *(inst.)* går att ändra under Testinställningar under utvecklingen.

Spelplanen är 360 spelpunkter bred på alla telefoner. "Bildpunkter" i diskussionen betyder spelpunkter, så att spelet blir lika på alla skärmar.

## 1. Grundidé

Fånga fallande mynt med en korg. Spelet går i nivåer om 120 sekunder som blir svårare medan de pågår. Mellan nivåerna finns en butik där mynten köper fart och liv, och ädelstenarna köper större korg. Fartköp kräver rätt svar på en huvudräkningsfråga.

Varje fartköp hör ihop med en nivå. Den som köper fart inför varje nivå startar alltid med samma marginal. Den som slarvar känner det redan efter en minut.

## 2. Styrning

- Två osynliga styrfält: vänstra och högra halvan av skärmen, i de mittersta 50 % av höjden. Håll fingret i ett fält så rör sig korgen åt det hållet. Fälten påverkar inte fallande föremål.
- Ingen tröghet: korgen går med full fart direkt och stannar direkt.
- Korgen är 25 % mindre än i första versionen: 66 → 50 spelpunkter.

## 3. MF – möjlighetsfaktorn

**Definition.** MF = (tiden mellan förra myntets landning och detta myntets landning) ÷ (tiden korgen behöver för att åka mellan de två landningsplatserna) × 100. Avståndet mäts mellan korgens mittpunkter. Eftersom korgen fångar ett mynt som träffar någonstans på korgen finns lite extra marginal, så även mynt under MF 100 går att fånga.

**Placering [förslag, godkänt].** Varje mynt får en slumpad landningsplats. Spelet räknar sedan fram när myntet ska landa så att MF blir exakt det aktuella värdet, och släpper myntet så långt i förväg som falltiden kräver. Undantag: korta avstånd skulle ge nästan samtidiga landningar, så det går alltid minst 0,35 s *(inst.)* mellan två landningar.

**Nästa mynt syns alltid i tid.** Nästa mynt måste synas innan förra myntet landar, annars vet spelaren inte vart korgen ska. Därför får en förflyttning ta högst en falltid vid aktuell MF. Det begränsar hur långt bort nästa mynt kan landa: på nivå 1 högst cirka 145 spelpunkter från förra, på nivå 10 cirka 240. Senare täcker det hela skärmen. (Upptäckt i simuleringen: utan regeln kom ett långt bort liggande mynt ibland först en sekund efter att förra landat, och då tappade även en perfekt spelare liv.)

Sedlar och ädelstenar ingår inte i beräkningen.

**Inom en nivå.** MF börjar på Y = 120 *(inst.)* och sänks med 1 *(inst.)* var Z = 5:e sekund *(inst.)*. Efter X = 120 s *(inst.)* har MF nått 96. Lägre MF betyder att mynten kommer tätare.

**Mellan nivåerna.** Varje nivå är beräknad för en viss korgfart, referensfarten. Nivå 1 har 70 spelpunkter/s *(inst.)*. Nästa nivå har referensfart × (Y ÷ MF vid halvtid) = × 120/108 = × 1,111. Ett fartköp höjer korgen exakt så mycket. Följden:

- Har du köpt fart startar nästa nivå på MF 120 igen. Det känns som en lättnad, eftersom tätheten bara är som vid halvtid förra nivån.
- Har du inte köpt fart startar du på MF 108 och slutar på 86. Då förlorar du sannolikt liv mot slutet.
- Ligger du två fartköp efter startar du på 97 och slutar på 78.

Ändras Y, X, Z eller sänkningen ändras fartsteget automatiskt.

**Exempel med standardvärdena** (spelmotorn körd med 60 slumpade nivåer per rad):

| Nivå | Referensfart (sp/s) | Myntets falltid | Mynt per nivå |
|---|---|---|---|
| 1 | 70 | 2,50 s | ~105 |
| 2 | 78 | 2,38 s | ~112 |
| 3 | 86 | 2,26 s | ~119 |
| 5 | 107 | 2,04 s | ~134 |
| 10 | 181 | 1,58 s | ~183 |
| 13 | 248 | 1,35 s | ~223 |
| 20 | 518 | 0,94 s | ~320 |
| 31 | 1651 | 0,54 s | ~341 |

Från ungefär nivå 15–20 är det minsta tiden mellan landningar som begränsar tätheten, inte MF. Där går också gränsen för vad en människa hinner med.

**Kontroll med en datorspelare** som alltid åker raka vägen till nästa mynt: köper den fart inför varje nivå tappar den inga liv på nivå 1–8. Köper den aldrig fart klarar den nivå 1–2 men tappar 5–8 liv på nivå 3. En människa reagerar långsammare, så marginalen behövs.

**Nivån genereras i förväg.** Alla landningsplatser och tider räknas ut innan nivån startar. Då vet spelet exakt hur många mynt nivån har, och priserna kan räknas på det.

## 4. Fallande föremål

- **Mynt:** värde 1. Falltiden anges i sekunder, så att den blir lika på alla telefoner. Den börjar på 2,5 s *(inst.)* och blir kortare hela tiden, även över nivågränser, med 5 % *(inst.)* per nivå. **[förslag, godkänt]** Fallfarten går alltså aldrig tillbaka vid ny nivå, bara tätheten.
- **Sedel:** värde 100 *(inst.)*. Sedlar kommer först från nivå 3 *(inst.)*. Därefter finns för vart 200:e fallna mynt *(inst.)* 33 % chans *(inst.)* att en sedel släpps. Den singlar i sidled och faller tre gånger så långsamt som mynten. Ingen livförlust om den missas. Räknas som ett föremål i statistiken.
- **Ädelsten:** faller dubbelt så ofta som sedlar, och med högre chans: från nivå 3, för vart 100:e fallna mynt *(inst.)*, 50 % chans *(inst.)*. Faller långsamt men rakt. Missad sten kostar inget liv.
- **Dynamitgubbe:** på senare nivåer, ska undvikas. Måste då ingå i MF-beräkningen. Kommer inte i första bygget.

Med standardvärdena får man (om man fångar alla) ungefär 7 stenar till nivå 10, 21 till nivå 20 och 38 till nivå 31.

## 5. Liv

- Spelet börjar med 3 liv *(inst.)*. Ett missat mynt kostar ett liv. Missad sedel eller sten kostar inget.
- Man kan köpa hur många liv som helst under spelet, men aldrig ha mer än 3 extra liv *(inst.)* samtidigt, alltså högst 6 liv.
- När liven tar slut är spelet över, och rekordet sparas.
- När man förlorar ett liv mitt i en nivå pausas spelet och hela butiken öppnas (avsnitt 6). När man går tillbaka till nivån står mynten stilla i 3 sekunder (med nedräkning) medan korgen går att flytta.

## 6. Butiken

Butiken öppnas mellan nivåerna och när man förlorat ett liv mitt i en nivå.

- **Fart:** ett steg = × 1,111 (följer av MF-inställningarna). Pris: 60 % *(inst.)* av myntantalet i nivån (den nyss spelade, eller den pågående). Priset stiger alltså automatiskt för varje nivå. Kräver rätt svar på en mattefråga: insatsen dras direkt, fel svar eller slut på tiden gör att den går förlorad, rätt svar ger köpet och upp till 30 % *(inst.)* tillbaka beroende på hur snabbt man svarade.
- **Större korg:** bara för ädelstenar, se avsnitt 7.
- **Liv:** kostar halva kapitalet du hade när du kom in i butiken. Köper du två vid samma besök kostar det andra hälften så mycket, alltså en fjärdedel av kapitalet. Högst 2 per besök. Inget mattekrav. **[förslag, godkänt]** Minsta pris är 1 mynt.
- **Matte för pengar (mellan nivåerna):** högst 3 frågor med 10 s *(inst.)* betänketid vardera. Man väljer lätt, medel eller svår för varje fråga. Belöningen är 5, 10 eller 15 % *(inst.)* av myntantalet i nivån. Fel svar ger ingenting. **[förslag, godkänt]**
- **Matte för liv (när man förlorat ett liv):** högst 3 frågor med 10 s vardera. Första rätta svaret ger ett liv.
- **Starta nästa nivå:** kräver rätt svar på en fråga *(inst.)*, se avsnitt 13.

Frågorna i butiken kan vara mattefrågor eller kunskapsfrågor från databasen, se avsnitt 13.

## 7. Ädelstenar

| Vad | Kostnad | Var |
|---|---|---|
| Större korg, steg 1–4 | 3, 6, 9, 12 stenar | Butiken |
| Markkorg i 10 s: en extra korg ställs där korgen står | 1 sten | Knapp under nivån |
| Slowmotion i 10 s: regnet och nivåklockan går halv fart, korgen full fart | 1 sten | Knapp under nivån |

Korgen blir 20 % större för varje steg, räknat på förra storleken: 50 → 60 → 72 → 86 → 104 spelpunkter. Totalt fem storlekar.

Slowmotion fördubblar MF under tiden den gäller.

Under nivån visar knappraden bara stenknapparna och Paus.

## 8. Mattesvårighet

Svårigheten går från 1 till 5. Startnivån är 1 *(inst.)* och höjs ett steg var tredje spelnivå. **[förslag, godkänt]** Vid matte för pengar ger "lätt" ett steg under den aktuella svårigheten, "medel" den aktuella och "svår" ett steg över.

## 9. Dragarna (grafik)

Korgen dras av en figur eller ett fordon som byts för varje fartköp. Den andra nivån av varje figur markeras med en guldstjärna. **[förslag, godkänt]** Figuren står till vänster om korgen: åt höger knuffar den, åt vänster vänder den sig om och drar. Figurer och fordon försvinner ut ur bild vid kanterna, och tåget sticker ut på båda sidor. Allt ritas med vektorgrafik i koden, i en enkel tecknad stil.

| Fartsteg | Dragare |
|---|---|
| 1–2 | Gubbe |
| 3–4 | Starkare gubbe |
| 5–6 | Muskelgubbe |
| 7–8 | Lastcykel |
| 9–10 | Åsna |
| 11–12 | Oxe |
| 13–14 | Motorcykel |
| 15–16 | Traktor |
| 17–18 | Lastbil |
| 19–20 | Litet ånglok, ett i vardera ände |
| 21–22 | Stort ånglok |
| 23–24 | Ellok |
| 25–26 | X2000 |
| 27–31 | Jetmotorer i fem storlekar |

Totalt 31 fartsteg, vilket motsvarar nivå 31 i tabellen i avsnitt 3.

## 10. Sparning

Spelet sparas när en nivå startar och när butiken öppnas mellan nivåerna. Stängs appen mitt i en nivå börjar den nivån om från början, med det läge man hade när nivån startade. **[förslag, godkänt]**

## 11. Ljud

Klirr för mynt, prassel för sedel, ett eget ljud för ädelsten, ett för förlorat liv, ett för köp, ett för fel svar och ett för klarad nivå. Ljuden skapas i koden, utan ljudfiler, och kan stängas av under Testinställningar.

## 12. Testinställningar

| Inställning | Standard |
|---|---|
| Start-MF (Y) | 120 |
| Nivålängd (X) | 120 s |
| MF sänks var … (Z) | 5 s |
| MF-sänkning per steg | 1 |
| Korgfart nivå 1 | 70 sp/s |
| Minsta tid mellan landningar | 350 ms |
| Myntets falltid vid start | 2500 ms |
| Falltiden kortas per nivå | 5 % |
| Sedlar och stenar från nivå | 3 |
| Sedelchans var … fallna mynt | 200 |
| Sedelchans | 33 % |
| Sedelns värde | 100 |
| Stenchans var … fallna mynt | 100 |
| Stenchans | 50 % |
| Liv vid start | 3 |
| Max extra liv i ägo | 3 |
| Fartpris, % av nivåns mynt | 60 |
| Svarstid | 10 s |
| Max återbetalning vid snabbt svar | 30 % |
| Mattebelöning lätt/medel/svår, % | 5/10/15 |
| Mattesvårighet, startnivå | 1 |
| Rätta svar som krävs för att starta nästa nivå | 1 |
| Årskurs för kunskapsfrågor | 4 |
| Andel kunskapsfrågor i stället för matte | 50 % |
| Skicka anonym statistik | på |
| Ljud | på |
| Visa MF under spel | på |

Inställningarna från första versionen (tillåtna missar, myntflöde, max mynt samtidigt, ökning per minut, grundpris) är borta, eftersom MF ersätter dem.

## 13. Kunskapsfrågor

Spelet riktar sig till skolelever, så frågorna är inte bara matte. Kunskapsfrågor hämtas från databasen på servern (`server/schema.sql`, tabellen `questions`) när det finns internet, och sparas i appen så att spelet fungerar utan uppkoppling.

- Varje gång spelet ställer en fråga (fartköp, matte för pengar eller liv, frågan för att starta nivån) blir det med 50 % *(inst.)* chans en kunskapsfråga för vald årskurs *(inst.)*, annars en mattefråga.
- Spelet väljer en fråga med svårighet så nära den önskade som möjligt och ställer inte samma fråga två gånger i samma spel. Finns ingen kvar blir det matte.
- Kunskapsfrågor får en extra sekund svarstid per 20 tecken frågetext, för läsningen.
- Finns en förklaring i databasen visas den efter svaret.
- **Bilder:** har frågan en bild (`image_url`) visas den ovanför frågetexten, så stor som skärmen tillåter. Bilden laddas ner till telefonen när frågorna hämtas, och frågan används först när bilden finns där.
- **Aktuella frågor:** har frågan ett slutdatum (`aktuell_till`) slutar appen använda den efter det datumet, även om den hämtades tidigare.
- Frågorna hämtas från serverns `questions.php`: allt första gången, sedan bara det som ändrats (`since`).
- **För att starta nästa nivå** måste man svara rätt på 1 fråga *(inst.)*. Fel svar ger ingen straff, men man får en ny fråga när man försöker igen.
- Startskärmen visar hur många frågor som finns för vald årskurs och när de senast hämtades.

Appen hette först MathChallange-1 men bytte namn till ThinkClink (repo och app-id `io.github.skitknasctrl.thinkclink`) den 4 november 2026, eftersom spelet inte längre bara handlar om matte. App-id:t bör inte bytas igen efter att appen spridits, eftersom det då blir en ny app för den som redan har den.

## 14. Anonym statistik

För att se hur lätt eller svårt spelet är med olika inställningar skickar appen statistik till servern (`server/api/stats.php`). Den kan stängas av under Testinställningar.

- En post per spelad nivå: utfall, inställningar, upplevd MF, tappade och köpta liv, köp, fångade mynt, sedlar och stenar, speltid.
- En rad per missat mynt: när i nivån, planerad och upplevd MF, avstånd från förra myntet, hur långt utanför korgen.
- En rad per besvarad fråga: syfte, ämne, svårighet, fråge-id eller mattetal, rätt eller fel, svarstid.
- Ingen information om personen eller telefonen: inga namn, inget enhets-id, ingen IP-adress. Varje spel får ett slumpat id, så två spel på samma telefon går inte att koppla ihop.
- Nivån skickas när nästa nivå startar (så att butiksbesöket efter nivån kommer med), när spelet tar slut eller när man går till menyn mitt i en nivå. Utan internet läggs nivåerna i kö i appen och skickas senare.
- Sammanställningar finns som vyer i databasen: `v_level_difficulty`, `v_misses_by_mf` och `v_question_stats`.

Innan appen sprids till skolor behöver det stå tydligt i appen vad som skickas, och GDPR-frågan bör kontrolleras. Appar på F-Droid som skickar statistik kan få en varningsmärkning.

## 15. Senare

- Dynamitgubbe att undvika på senare nivåer.
- Vad händer efter fartsteg 31? I dag: inget mer att köpa, nivåerna fortsätter tills liven tar slut.
- Val av årskurs och ämnen för spelaren (i dag bara i Testinställningar).

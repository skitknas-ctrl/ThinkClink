# MathChallange-1 – design (utkast)

Utkast 2026-11-04. Inget av detta är byggt ännu. Det som står här är Dicks svar plus Claudes förslag där något saknades; förslagen är markerade **[förslag]** och öppna frågor **[öppen]**. Alla siffror markerade *(inst.)* går att ändra under Testinställningar under utvecklingen.

Spelplanen är 360 spelpunkter bred på alla telefoner. "Bildpunkter" i diskussionen betyder spelpunkter, så att spelet blir lika på alla skärmar.

## 1. Grundidé

Fånga fallande mynt med en korg. Spelet går i nivåer om 120 sekunder som blir svårare medan de pågår. Mellan nivåerna finns en butik där mynten köper fart, storlek och liv. Fart och storlek kräver rätt svar på en huvudräkningsfråga.

Varje fartköp hör ihop med en nivå. Den som köper fart inför varje nivå startar alltid med samma marginal. Den som slarvar känner det redan efter en minut.

## 2. Styrning

- Två kantfält, 10 % av bredden i vänster och höger kant, i de mittersta 50 % av höjden. Håll fingret i fältet så rör sig korgen. Fälten påverkar inte fallande föremål.
- Ingen tröghet: korgen går med full fart direkt och stannar direkt.
- Korgen är 25 % mindre än i första versionen: 66 → 50 spelpunkter.

## 3. MF – möjlighetsfaktorn

**Definition.** MF = (tiden mellan förra myntets landning och detta myntets landning) ÷ (tiden korgen behöver för att åka mellan de två landningsplatserna) × 100. Avståndet mäts mellan korgens mittpunkter. Eftersom korgen fångar ett mynt som träffar någonstans på korgen finns lite extra marginal, så även mynt under MF 100 går att fånga.

**Placering.** Varje mynt får en slumpad landningsplats. Spelet räknar sedan fram när myntet ska landa så att MF blir exakt det aktuella värdet, och släpper myntet så långt i förväg som falltiden kräver. **[förslag]** Mynt läggs alltså på exakt aktuell MF, inte bara minst. Det gör att svårigheten följer MF jämnt. Undantag: korta avstånd skulle ge nästan samtidiga landningar, så det går alltid minst 0,35 s *(inst.)* mellan två landningar.

Sedlar och ädelstenar ingår inte i beräkningen.

**Inom en nivå.** MF börjar på Y = 120 *(inst.)* och sänks med 1 *(inst.)* var Z = 5:e sekund *(inst.)*. Efter X = 120 s *(inst.)* har MF nått 96. Lägre MF betyder att mynten kommer tätare.

**Mellan nivåerna.** Varje nivå är beräknad för en viss korgfart, referensfarten. Nivå 1 har 70 spelpunkter/s *(inst.)*. Nästa nivå har referensfart × (Y ÷ MF vid halvtid) = × 120/108 = × 1,111. Ett fartköp höjer korgen exakt så mycket. Följden:

- Har du köpt fart startar nästa nivå på MF 120 igen. Det känns som en lättnad, eftersom tätheten bara är som vid halvtid förra nivån.
- Har du inte köpt fart startar du på MF 108 och slutar på 86. Då förlorar du sannolikt liv mot slutet.
- Ligger du två fartköp efter startar du på 97 och slutar på 78.

Ändras Y, X, Z eller sänkningen ändras fartsteget automatiskt.

**Exempel med standardvärdena** (simulerat, medelvärde av 200 slumpade nivåer):

| Nivå | Referensfart (sp/s) | Tid att korsa skärmen | Mynt per nivå |
|---|---|---|---|
| 1 | 70 | 4,4 s | ~74 |
| 2 | 78 | 4,0 s | ~82 |
| 3 | 86 | 3,6 s | ~90 |
| 5 | 107 | 2,9 s | ~110 |
| 10 | 181 | 1,7 s | ~176 |
| 13 | 248 | 1,3 s | ~224 |
| 20 | 518 | 0,6 s | ~323 |
| 31 | 1651 | 0,2 s | ~342 |

Från ungefär nivå 15–20 är det minsta tiden mellan landningar som begränsar tätheten, inte MF. Där går också gränsen för vad en människa hinner med.

**Nivån genereras i förväg.** Alla landningsplatser och tider räknas ut innan nivån startar. Då vet spelet exakt hur många mynt nivån har, och priserna kan räknas på det.

## 4. Fallande föremål

- **Mynt:** värde 1. Falltiden anges i sekunder, så att den blir lika på alla telefoner. Den börjar på 2,5 s *(inst.)* och blir kortare hela tiden, även över nivågränser, med 5 % *(inst.)* per nivå. **[förslag]** Fallfarten går alltså aldrig tillbaka vid ny nivå, bara tätheten.
- **Sedel:** värde 100. Kommer med 33 % *(inst.)* sannolikhet en gång per nivå, vid en slumpad tidpunkt. Den singlar i sidled och faller mycket långsammare än mynten (tre gånger myntets falltid). Ingen livförlust om den missas. Räknas som ett föremål i statistiken.
- **Ädelsten:** som sedeln, med 33 % *(inst.)* sannolikhet per nivå. Faller långsamt men rakt. Missad sten kostar inget liv.
- **Dynamitgubbe:** på senare nivåer, ska undvikas. Måste då ingå i MF-beräkningen. Kommer inte i första bygget.

## 5. Liv

- Spelet börjar med 3 liv. Ett missat mynt kostar ett liv. Missad sedel eller sten kostar inget.
- Man kan aldrig ha fler än 3 extra liv, alltså högst 6 samtidigt. **[öppen]**
- När liven tar slut är spelet över, och rekordet sparas.
- När man förlorar ett liv mitt i en nivå pausas spelet och man erbjuds att köpa ett liv eller räkna sig till ett, se avsnitt 6. **[öppen]**

## 6. Butiken

Butiken öppnas mellan nivåerna. Mitt i en nivå finns en mindre variant, se nedan.

**Mellan nivåerna:**

- **Fart:** ett steg = × 1,111 (följer av MF-inställningarna). Pris: 60 % *(inst.)* av myntantalet i nivån du nyss spelade. Priset stiger alltså automatiskt för varje nivå.
- **Storlek:** +17 spelpunkter per steg, 25 % mindre steg än i första versionen. Högst 10 steg (50 → 220). Pris: 40 % *(inst.)* av myntantalet i nivån du nyss spelade.
- **Mattekrav för fart och storlek (som nu):** insatsen dras direkt. Fel svar eller slut på tiden gör att insatsen går förlorad. Rätt svar ger köpet och upp till 30 % *(inst.)* tillbaka, beroende på hur snabbt man svarade.
- **Liv:** kostar halva kapitalet du hade när du kom in i butiken. Köper du två vid samma besök kostar det andra hälften så mycket, alltså en fjärdedel av kapitalet. Högst 2 per besök. Inget mattekrav. **[förslag]**
- **Matte för pengar:** högst 3 frågor med 10 s *(inst.)* betänketid vardera. Man väljer lätt, medel eller svår för varje fråga. Belöningen är 5, 10 eller 15 % *(inst.)* av myntantalet i nivån du nyss spelade. Fel svar ger ingenting. **[förslag]**
- **Ädelstenar:** växlas mot permanent större korg här. De andra stenköpen görs under nivån, se avsnitt 7.

**Mitt i en nivå (när man förlorat ett liv):** köpa liv (samma prisregel), eller lösa högst 3 frågor med 10 s vardera för att få ett liv. **[öppen]** Gäller det tre rätt av tre, och ska även snabbare korg erbjudas här?

## 7. Ädelstenar

| Vad | Kostnad | Var |
|---|---|---|
| Permanent större korg (ett storlekssteg) | 2 stenar **[förslag]** | Butiken |
| Markkorg i 10 s: en extra korg ställs där korgen står | 1 sten **[förslag]** | Knapp under nivån |
| Slowmotion i 10 s: regnet och nivåklockan går halv fart, korgen full fart | 1 sten **[förslag]** | Knapp under nivån |

Slowmotion fördubblar MF under tiden den gäller.

Under nivån visar knappraden bara Paus och stenknapparna. Köp av storlek och fart görs i butiken.

## 8. Mattesvårighet

Svårigheten går från 1 till 5 som nu. Startnivån är 1 *(inst.)* och höjs ett steg var tredje spelnivå. **[förslag]** Vid matte för pengar ger "lätt" ett steg under den aktuella svårigheten, "medel" den aktuella och "svår" ett steg över.

## 9. Dragarna (grafik)

Korgen dras av en figur eller ett fordon som byts för varje fartköp. Den andra nivån av varje figur markeras med en guldstjärna **[förslag]**. Figurer och fordon försvinner ut ur bild vid kanterna, och tåget sticker ut på båda sidor. Allt ritas med vektorgrafik i koden, i en enkel tecknad stil.

| Fartsteg | Dragare |
|---|---|
| 1–2 | Gubbe, smal |
| 3–4 | Gubbe, kraftigare |
| 5–6 | Gubbe, muskulös |
| 7–8 | Lastcykel |
| 9–10 | Åsna |
| 11–12 | Oxe |
| 13–14 | Motorcykel |
| 15–16 | Traktor |
| 17–18 | Lastbil |
| 19–20 | Litet ånglok, ett i vardera ände |
| 21–22 | Större ånglok |
| 23–24 | Ellok |
| 25–26 | X2000 |
| 27–31 | Jetlågor i fem storlekar |

Totalt 31 fartsteg, vilket motsvarar nivå 31 i tabellen i avsnitt 3.

## 10. Sparning

Spelet sparas vid varje butiksbesök. Stängs appen mitt i en nivå börjar den nivån om från början, med det läge man hade när nivån startade. **[förslag]**

## 11. Ljud

Klirr för mynt, prassel för sedel, ett eget ljud för ädelsten, ett för förlorat liv och ett för köp. Ljuden skapas i koden, utan ljudfiler.

## 12. Testinställningar

| Inställning | Standard |
|---|---|
| Start-MF (Y) | 120 |
| Nivålängd (X) | 120 s |
| MF sänks var … (Z) | 5 s |
| MF-sänkning per steg | 1 |
| Korgfart nivå 1 | 70 sp/s |
| Minsta tid mellan landningar | 0,35 s |
| Myntets falltid vid start | 2,5 s |
| Falltiden kortas per nivå | 5 % |
| Chans för sedel per nivå | 33 % |
| Chans för ädelsten per nivå | 33 % |
| Sedelns värde | 100 |
| Startliv | 3 |
| Fartpris, % av förra nivåns mynt | 60 |
| Storlekspris, % av förra nivåns mynt | 40 |
| Max återbetalning vid snabbt svar | 30 % |
| Svarstid | 10 s |
| Mattebelöning lätt/medel/svår, % | 5/10/15 |
| Mattesvårighet, startnivå | 1 |

Inställningarna från första versionen (tillåtna missar, myntflöde, max mynt samtidigt, ökning per minut, grundpris) försvinner, eftersom MF ersätter dem.

## 13. Öppna frågor

- **Ö1.** När man förlorar ett liv mitt i en nivå: ska man erbjudas snabbare korg (inledningen av svaret), eller bara liv och matte för liv (p5)?
- **Ö2.** Matte för liv mitt i en nivå: krävs tre rätt av tre för ett liv, eller räcker färre?
- **Ö3.** "Högst tre extra liv": betyder det högst 6 liv samtidigt?
- **Ö4.** En sedel är värd 100, men hela nivå 1 har bara cirka 74 mynt. En fångad sedel tidigt i spelet ger alltså mer än en hel nivå. Är det meningen, eller ska sedlarna komma först från en senare nivå (eller vara värda mindre i början)?
- **Ö5.** Stämmer förslagen i avsnitt 4 (fallfarten går aldrig tillbaka), 7 (stenpriser) och 8 (mattesvårighet)?
- **Ö6.** Vad händer efter fartsteg 31? Förslag: inget mer att köpa, nivåerna fortsätter tills liven tar slut.

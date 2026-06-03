# Car Video Private

Private Android/Android Auto media experiment for personal use.

**Belangrijk:** dit project is bedoeld voor eigen gebruik/sideload. Het is niet bedoeld voor Play Store publicatie. Gebruik video alleen wanneer de auto stilstaat/geparkeerd is. Tijdens rijden is audio-only de veilige modus.

## Wat is aangepast in deze private versie

- Gradle conflict in `gradle.properties` gefixt.
- `app/build/` en andere build-output uit het project gehaald.
- `.gitignore` uitgebreid zodat GitHub proper blijft.
- Appnaam aangepast naar **Car Video Private**.
- `allowBackup=false` gezet voor privédata.
- Stream resolver robuuster gemaakt:
  - korte cache voor directe stream-URL's;
  - retry bij netwerk/extractor fouten;
  - audio-only fallback;
  - max 720p voor video/head-unit stabiliteit.
- Player robuuster gemaakt:
  - wachtrij wordt lazy geladen;
  - niet meer 20 streams tegelijk resolven;
  - bij playback error probeert hij automatisch naar het volgende item te gaan.
- Lokale geschiedenis toegevoegd:
  - tab **Recent**;
  - betere **For You** op basis van likes + history.

## Openen in Android Studio

1. Download/clone deze repo.
2. Open de hoofdmap in Android Studio.
3. Laat Gradle syncen.
4. Build: `Build > Make Project`.
5. APK maken:

```bash
./gradlew assembleDebug
```

APK staat dan normaal in:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## GitHub Actions build

Als je dit naar GitHub uploadt en naar `main` pusht, maakt de workflow automatisch een debug APK release.

## Android Auto privé testen

1. Zet Android Auto developer mode aan.
2. Activeer unknown sources/developer apps in Android Auto.
3. Installeer de APK op je telefoon.
4. Start eerst de app op de telefoon en speel iets af.
5. Open daarna Android Auto.

## Veilig gebruik

- Gebruik audio-only tijdens het rijden.
- Video/head-unit mode alleen wanneer je geparkeerd staat.
- YouTube/SoundCloud extractors kunnen breken door wijzigingen aan hun kant. Daarom is retry/cache/fallback toegevoegd, maar 100% garantie bestaat niet.

## Volgende logische verbeteringen

- Echte `MediaLibraryService` voor nog betere Android Auto audio browsing.
- Room database in plaats van JSON/SharedPreferences.
- Playlists maken/bewerken.
- Export/import van privédata.
- Eigen app icoon.

# CarVideoApp

Persoonlijke Android-app: YouTube-streams afspelen met ExoPlayer, met video op het
Android Auto-scherm en naadloos doorspelende audio op de achtergrond.

## Modules / klassen

| Onderdeel | Bestand |
|-----------|---------|
| YouTube extractor (NewPipe) | `extractor/YouTubeExtractorService.kt`, `extractor/OkHttpDownloader.kt` |
| Gedeelde speler + surface-logica | `player/PlayerHolder.kt` |
| Achtergrond-mediaservice (Media3) | `player/PlaybackService.kt` |
| Android Auto entry | `carapp/VideoCarAppService.kt`, `VideoSession.kt`, `VideoScreen.kt` |
| Telefoon-UI | `MainActivity.kt` |

## Hoe het surface-schakelen werkt

Eén gedeelde `ExoPlayer` leeft in `PlayerHolder`. De `MediaSession` (in
`PlaybackService`) houdt audio + systeembediening levend. De `VideoScreen`
claimt via `SurfaceCallback` de Surface van het autoscherm:

- `onSurfaceAvailable` -> `player.setVideoSurface(...)` -> video rendert op het scherm.
- `onSurfaceDestroyed` (auto rijdt / scherm weg) -> `player.clearVideoSurface()` ->
  video stopt, **audio loopt gewoon door**. Geen pauze, geen herstart.

## Setup

1. Open in Android Studio (Giraffe+), JDK 17.
2. Gradle sync. NewPipeExtractor komt via JitPack (zie `settings.gradle.kts`).
3. Genereer een Gradle wrapper jar indien nodig: `gradle wrapper` (of laat
   Android Studio dit doen).
4. Run op een toestel met Android 10+ (minSdk 29, targetSdk 37).

## Android Auto testen

Gebruik de **Desktop Head Unit (DHU)** uit het Android SDK om de VIDEO-categorie
lokaal te testen. Zet ontwikkelaarsmodus aan in de Android Auto-app.

## Belangrijke caveats

- **NewPipe scrapet YouTube** en schendt YouTube's ToS; het breekt bij
  YouTube-wijzigingen. Pin de versie en update periodiek. Bedoeld voor persoonlijk gebruik.
- De **`androidx.car.app.category.VIDEO`-categorie is beperkt**: een gepubliceerde
  app moet door Google worden goedgekeurd om op echte head units te draaien.
  Voor eigen gebruik werkt het via developer mode / DHU.
- Video afspelen tijdens het rijden is in veel auto's/regio's geblokkeerd door de
  host; het surface-callback-gedrag respecteert dat automatisch.

## Android 17 / API 37

`compileSdk` en `targetSdk` staan op **37 (Android 17)**. Let op:

- Je hebt de **Android 17 SDK** nodig in Android Studio (SDK Manager) en **AGP 8.7+**.
- Je Galaxy Z Fold 7 draait momenteel **One UI 8.5 = Android 16 (API 36)**. Een
  app met `targetSdk 37` installeert en draait prima op Android 16; je krijgt
  alleen de API-37-gedragsregels zodra je toestel naar Android 17 update.
- Gedragswijzigingen bij `targetSdk 37`: Certificate Transparency staat standaard
  aan (NewPipe/YouTube-verkeer is normaal CT-conform), local-network-toegang
  vereist runtime-permissie (niet gebruikt hier), en native libs via
  `System.load()` moeten read-only zijn (niet van toepassing).

# CarVideoApp

Een hypermoderne persoonlijke Android-app voor het streamen van YouTube/SoundCloud naar je telefoon en Android Auto. Ontworpen met een focus op een premium UI, naadloze achtergrond-playback en volledige ondersteuning voor de nieuwste Android-versies.

## 🚀 Belangrijke Features

### 🎬 Android Auto Video
- **Naadloze Transitie**: Video wordt automatisch naar het autoscherm gestuurd via `SurfaceCallback`.
- **Veiligheid Eerst**: Audio speelt ononderbroken door wanneer het scherm door het systeem wordt geblokkeerd (bijv. tijdens het rijden).
- **Auto Controls**: Volledige bediening (Play/Pause/Next/Prev) direct op het autoscherm.

### 🎵 Premium Achtergrond Playback
- **Media3 Service**: Gebruikt `MediaSessionService` voor robuuste achtergrond-audio die nooit stopt.
- **System Integration**: Volledige ondersteuning voor de Android Media Controls, inclusief het lockscreen en de **Samsung "Now Playing" bar**.
- **Notificatie Controls**: Rijke notificaties met knoppen voor Play/Pause, Volgende, Vorige en een **Like-knop**.

### 🎨 Next-Gen UI (Android 17 Ready)
- **Glassmorphism**: Een moderne interface met transparante "glass" effecten en blur.
- **Dynamic Content Theming**: De interface analyseert de thumbnail van de huidige video en past automatisch het kleurverloop (gradient) van de achtergrond aan (`androidx.palette`).
- **Nachtmodus**: Volledige ondersteuning voor Licht, Donker en Systeem-thema's, instelbaar via het nieuwe Settings-menu.
- **Android 17 Ready**: Geoptimaliseerd voor API 37, inclusief ondersteuning voor de nieuwste systeemgedragingen en permissies.

## 🛠 Modules / Klassen

| Onderdeel | Bestand |
|-----------|---------|
| YouTube & SoundCloud Extractor | `extractor/YouTubeExtractorService.kt` |
| Dynamische Kleur Extractie | `ui/NowPlayingBar.kt` (via Palette API) |
| Gedeelde Speler Logica | `player/PlayerHolder.kt` |
| Mediabediening & Notificaties | `player/PlaybackService.kt` |
| Android Auto Integratie | `carapp/VideoCarAppService.kt`, `VideoScreen.kt` |
| Moderne UI & Settings | `MainActivity.kt`, `ui/Theme.kt` |

## ⚙️ Setup & Eisen

1. **SDK**: Vereist Android Studio (Meerkat+) met **Android 17 (API 37)** SDK.
2. **Gradle**: Maakt gebruik van AGP 8.9.3 en Gradle 8.11.1 voor optimale compatibiliteit.
3. **Permissies**: Zorg dat je bij het eerste gebruik toestemming geeft voor **Meldingen**. Dit is essentieel voor de bediening op het lockscreen.
4. **Samsung Optimalisatie**: Voor de beste ervaring op Samsung-toestellen, zet de batterij-instelling van de app op "Onbeperkt".

## ⚠️ Belangrijke Informatie

- **Privacy & TOS**: Maakt gebruik van NewPipeExtractor voor het scrapen van publieke streams. Bedoeld voor persoonlijk gebruik.
- **Android Auto Developer Mode**: Omdat de app in de `VIDEO` categorie valt, moet "Ontwikkelaarsinstellingen" in de Android Auto-app op je telefoon aanstaan om de app te zien op je head unit of DHU.
- **Achtergrond Playback**: De app maakt gebruik van `WAKE_LOCK` en `FOREGROUND_SERVICE_MEDIA_PLAYBACK` om te garanderen dat je muziek nooit stopt, zelfs niet in diepe slaapstand.

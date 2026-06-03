# CarVideoApp

Een hypermoderne persoonlijke Android-app voor het streamen van YouTube/SoundCloud naar je telefoon en Android Auto. Ontworpen met een focus op een premium UI, naadloze achtergrond-playback en volledige ondersteuning voor de nieuwste Android-versies.

## 🚀 Belangrijke Features

### 🎬 Android Auto Video (Echte Player UI)
- **Vaste Bediening**: Knoppen voor Vorige, Play/Pause en Volgende zijn nu vast onderdeel van de layout via `NavigationTemplate`.
- **Zijbalk Playlist**: Direct overzicht van de volgende video's aan de zijkant van het autoscherm.
- **Full-Screen Focus**: Video speelt op de volledige achtergrond terwijl de interface er vloeiend overheen zweeft.
- **Naadloze Transitie**: Audio speelt ononderbroken door wanneer het scherm door het systeem wordt geblokkeerd.

### 🎵 Premium Achtergrond Playback
- **Media3 Service**: Gebruikt `MediaSessionService` gecombineerd met een handmatige `MediaStyle` notificatie voor maximale compatibiliteit.
- **Lockscreen & Samsung Bar**: Volledige integratie met de systeem-mediabediening en de Samsung "Now Playing" bar.
- **Notificatie Controls**: Rijke notificaties inclusief een werkende **Like-knop (Ster)** en skip-knoppen.

### 🎨 Next-Gen UI (Android 17 Ready)
- **Glassmorphism**: Moderne, semi-transparante interface met blur-effecten op videokaarten en de spelerbalk.
- **Dynamic Content Theming**: De achtergrond van de player past zich automatisch aan de kleuren van de video-thumbnail aan via de Palette API.
- **Nachtmodus**: Handmatige en automatische thema-schakeling (Licht/Donker/Systeem) via het instellingenmenu.
- **Android 17 Ready**: Volledig geoptimaliseerd voor API 37 en de nieuwste Gradle-standaarden.

## 🚧 Status & Wat nog niet werkt

Hoewel de app technisch zeer geavanceerd is, zijn er een aantal onderdelen nog in ontwikkeling:

- **For You & Trending**: Deze secties tonen momenteel een basislijst (vaak gebaseerd op trending YouTube data). De persoonlijke aanbevelingen en slimme algoritmes voor "For You" moeten nog verder worden uitgewerkt.
- **Android Auto Zichtbaarheid**: Op sommige systemen moet "Onbekende bronnen" in de Android Auto-instellingen op de telefoon aanstaan om de app te zien, omdat deze gebruik maakt van de beperkte `POI/VIDEO` categorieën.
- **Zoekfunctie in de auto**: De zoekfunctie op het autoscherm is voorbereid maar vereist nog verdere integratie met het on-screen keyboard van de auto-host.

## 🛠 Modules / Klassen

| Onderdeel | Bestand |
|-----------|---------|
| YouTube & SoundCloud Extractor | `extractor/YouTubeExtractorService.kt` |
| Dynamische Kleur Extractie | `ui/NowPlayingBar.kt` (via Palette API) |
| Gedeelde Speler Logica | `player/PlayerHolder.kt` |
| Mediabediening & Notificaties | `player/PlaybackService.kt` |
| Android Auto Integratie | `carapp/VideoCarAppService.kt`, `VideoScreen.kt`, `MainCarScreen.kt` |
| Moderne UI & Settings | `MainActivity.kt`, `ui/Theme.kt` |

## ⚙️ Setup & Eisen

1. **SDK**: Vereist Android Studio (Meerkat+) met **Android 17 (API 37)** SDK.
2. **Gradle**: Maakt gebruik van AGP 8.9.3 en Gradle 8.11.1.
3. **Permissies**: Geef bij de eerste start toestemming voor **Meldingen**.
4. **Samsung Optimalisatie**: Zet de batterij-instelling van de app op "Onbeperkt" voor de beste achtergrond-ervaring.

## ⚠️ Belangrijke Informatie

- **Privacy & TOS**: Maakt gebruik van NewPipeExtractor. Uitsluitend voor persoonlijk gebruik.
- **Android Auto Developer Mode**: Essentieel om de VIDEO-categorie te laten werken op echte head units of DHU.

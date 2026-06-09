# CarVideoApp: Expert Roadmap

Dit document bevat de stappen van de 5 experts om de CarVideoApp naar een professioneel, stabiel en modern niveau te tillen.

## 🟢 1. Media Streaming Expert (Focus: Betrouwbaarheid)
- [x] **Automatische Failover:** Als een YouTube stream faalt, probeer automatisch hetzelfde nummer te vinden op SoundCloud (en vice versa).
- [x] **Adaptive Bitrate (HLS/DASH):** Implementeer adaptieve streaming om haperingen bij slecht bereik (tunnels/buitengebied) te voorkomen.
- [x] **Pre-fetch Engine:** Verbeter de logica om de volgende 2 nummers alvast 'heet' te houden (metadata + url alvast klaarzetten).

## ⚪ 2. Android Automotive UX Specialist (Focus: Veiligheid)
- [x] **Voice Commands:** Basis gelegd voor Assistant Search en Media Actions.
- [x] **Rij-veiligheid (Restricted Mode):** Verberg de video-view automatisch zodra de auto sneller dan 0.1 m/s rijdt (via Car Hardware API).
- [ ] **Optimalisatie Touch Targets:** Zorg dat alle knoppen in de auto-interface voldoen aan de 44dp minimum regel voor veilig gebruik.

## ⚪ 3. Modern UI & Design Engineer (Focus: Esthetiek)
- [ ] **Shared Element Transitions:** Laat de album art vloeiend 'vliegen' van de mini-player naar de full-screen player.
- [x] **Expressive Shapes:** Implementeer de nieuwe Android 17 'morphing' animaties voor knoppen en containers.
- [x] **Dynamic Color Depth:** Breid het kleurenpalet uit zodat de hele app (inclusief navigatiebalk) meekleurt met de huidige track.

## 🟢 4. Local Data & ML Architect (Focus: Intelligentie)
- [x] **Room Database Migratie:** Vervang SharedPreferences door een echte database voor snellere toegang tot duizenden nummers.
- [x] **Contextuele Aanbevelingen:** Een lokaal algoritme dat muziek suggereert op basis van tijdstip (bijv. rustige/vertrouwde muziek in de avond, ontdekkingen 's ochtends).
- [x] **Smart Search:** Voeg lokale zoekfunctionaliteit toe aan de geschiedenis en likes. Lokale resultaten krijgen prioriteit en zijn offline beschikbaar.

## ⚪ 5. Performance & Stability Engineer (Focus: Duurzaamheid)
- [ ] **Intelligent Offline / Caching:** Proactief de top 10 meest beluisterde nummers lokaal opslaan.
- [ ] **Thermal Management:** Optimaliseer de CPU-last tijdens video-playback om oververhitting in het dashboard te voorkomen.
- [ ] **Crash Analytics:** Implementeer een robuust lokaal log-systeem voor offline debugging.

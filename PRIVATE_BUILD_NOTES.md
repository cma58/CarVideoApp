# Private build notes

Deze versie is bewust niet Play Store-ready gemaakt. De focus is privégebruik:

- sideload APK;
- Android Auto developer mode;
- audio-first playback;
- video als experimentele/parked laag;
- geen officiële YouTube/SoundCloud API vereist.

## Belangrijkste technische keuzes

### Lazy queue resolving

Vroeger probeerde de app elk item in de queue tegelijk te resolven. Dat kan traag zijn of extractor blocks veroorzaken.

Nu:

1. huidig item resolven;
2. direct starten met spelen;
3. volgende 2 items prefetch;
4. rest één per één toevoegen.

### Stream quality

Voor video kiest de resolver liever muxed MP4 tot 720p. Dat is stabieler op car/head-unit schermen dan telkens de zwaarste stream.

### Local For You

Geen server, geen account. De For You-lijst gebruikt:

- likes;
- recent afgespeelde items;
- trending als opvulling.

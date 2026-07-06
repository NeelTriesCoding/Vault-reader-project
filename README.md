# Vault — AI Reading & Knowledge Map

Vault is an Android app that fuses an **ebook reader** (PDF / EPUB / MOBI / TXT) with a
**Zettelkasten-style note system** and an **AI knowledge map**. As you read you can
highlight sentences and capture notes; Google's Gemini auto-titles, tags, and
summarizes each note, then links related notes into an interactive visual map.

## Features

- **Reader** — swipe-paged reading with sepia/light/dark themes, adjustable font
  (pages re-flow to the chosen size), sentence highlighting, quick note capture,
  pinch/double-tap zoom for PDFs, and per-book reading-progress tracking.
- **Notes** — folder and list views, full-text search, batch tag editing, and
  AI enrichment (title, tags, ~10-sentence summary) via Gemini.
- **Visual Map** — force-directed knowledge graph of notes and their links, with
  drag-to-reposition (persisted), auto-arrange, fit-to-screen, tap-to-edit links,
  and relationship-colored edges.

## Architecture

- **UI**: Jetpack Compose, Material 3. Screens live in `ui/screens/`; the map
  layout math is in `ui/map/MapLayoutEngine.kt`.
- **State**: `ui/VaultViewModel.kt` (MVVM) exposing `StateFlow`s.
- **Data**: Room (`data/`), with format parsing in `data/parsing/BookParser.kt`
  and sentence splitting in `data/parsing/SentenceSplitter.kt`.
- **AI**: Retrofit + Moshi Gemini client in `api/`, with the enrichment contract
  isolated in `api/AiEnrichmentService.kt`.
- The Gemini API key is stored **encrypted** at rest (`data/SecureKeyStore.kt`).

## Run Locally

**Prerequisites:** [Android Studio](https://developer.android.com/studio)

1. Open the project in Android Studio and let it sync Gradle.
2. Provide a Gemini API key one of two ways:
   - **In-app (recommended):** run the app and paste your key in the **Settings** tab, or
   - **Build-time:** create a `.env` file in the project root with
     `GEMINI_API_KEY=your_key_here` (see `.env.example`).
3. Run on an emulator or device.

Get a Gemini API key at https://aistudio.google.com/apikey.

## Tests

Unit tests for the pure logic (pagination, sentence splitting, map layout, tag
merging) live in `app/src/test/`. Run them with:

```
./gradlew testDebugUnitTest
```

# Chroma Library

An offline colour library for Android: 32,004 named colours, collections, favourites and a detail view for every colour. No account, no ads, no internet needed.

## Features
- **Explore** – every colour, search by name or HEX, filter by family, sort (name, hue, lightness, vividness), endless scrolling, random colour.
- **Collections** – unlimited folders with a name and cover colour; save one colour into many folders; rename, reorder, delete; move colours between folders.
- **Favorites** – heart any colour.
- **Colour detail** – large preview, HEX / RGB / HSL / HSV with copy buttons, favourite, save to collections, nearby shades and similar colours.
- **Settings** – system/light/dark theme, grid size (2–4 columns), show/hide HEX, export and import a backup, clear data, about.

## Data
- Source: [meodai/color-names](https://github.com/meodai/color-names) (MIT), package `color-name-list` 14.86.0, the full `colornames.json` list (32,004 colours).
- `tools/build_db.py` turns that file into `app/src/main/assets/colors.db` (SQLite). Names and HEX values are copied unchanged; RGB, HSL, HSV and the colour family are computed from the HEX value. Indexes make search and sorting fast.
- User data (favourites, collections) lives in a separate local database, keyed by HEX.

## Code layout (`app/src/main/java/com/chroma/library`)
- `data/` – models, read-only colour database, user database, `Repo` (the only entry point for the UI).
- `domain/` – colour maths and formatting.
- `ui/` – screens, adapters, sheets, the floating tab bar.

## Build
GitHub Actions builds `app-debug.apk` on every push (artifact `ChromaLibrary-apk`). The debug keystore is committed so new builds install over old ones.

## Attribution
Colour names © David Aerne and contributors, MIT License (see `tools/COLOR_NAMES_LICENSE.txt`).

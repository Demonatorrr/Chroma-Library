# Chroma Library

An offline colour library for Android with **official names only**: CSS named colours, X11 colour names, Google Material Design, Apple system colours and Tailwind CSS. Collections, favourites, a detail view for every colour and a colour picker. No account, no ads, no internet needed.

## What is inside
- **Explore** – search by official name or HEX; filter by palette (Web, X11, Material, Apple, Tailwind); "All" shows only the named-colour lists (Web, X11); Material, Apple and Tailwind appear under their own chip and by hue; sort.
- **Picker** – hue/saturation wheel, brightness bar and HEX field. Shows the exact official name when the colour is in a palette; otherwise "No official name" plus the nearest official colour. Names are never invented.
- **Folders** – unlimited collections with a cover colour. **Favorites**, **Settings** (theme, grid, backup/restore).
- **Apple palette** – iOS system colours in light and dark, system grays, backgrounds and grouped backgrounds, handy when designing UI.

## Sources (`tools/sources/`)
- X11: `rgb.txt` from X.Org (MIT), spaced names, numbered variants kept, gray ramps dropped.
- Apple: iOS system colours (UIKit/SwiftUI), opaque values only (translucent labels, fills and separators are not included).
- Web: CSS named colours, npm `color-name` 2.1.1 (MIT).
- Material: npm `material-colors` 1.2.6 (ISC).
- Tailwind: Tailwind CSS 3.4.17 default palette (MIT).

`python3 tools/build_db.py` rebuilds `app/src/main/assets/colors.db`. A HEX that appears in several palettes is stored once; its other names are kept as "also". Pantone and other licensed palettes are not included.

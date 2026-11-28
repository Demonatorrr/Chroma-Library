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

## Data model (v1.3)
- `entries` – one row per source and name: source, code, name, name type (always Official), category, reference URL, licence, verification level. The same HEX can have several entries; each keeps its own identity.
- `colors` – one row per HEX for browsing, derived from `entries` (main name, sources, family, HSL/HSV).
- Verification levels: **verified** (checked against the owner's published table: CSS against the W3C table, `tools/sources/w3c_named_colors.txt`), **source** (taken from the owner's own files: X11 `rgb.txt`, Tailwind), **published** (Apple and Material values from published references, not checked against the owner's site), **imported** (from a file you supply).
- CMYK is a plain RGB conversion and is shown as an estimate. There are no descriptive or generated names.

## Pantone, RAL and NCS
These collections are licensed, so the app does not bundle or scrape them. Use **Settings → Import a palette file** with data you are licensed to use (templates in `tools/templates/`). The colors appear under your source name, with your codes, and are marked as imported.

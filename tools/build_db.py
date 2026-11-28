"""Builds app/src/main/assets/colors.db from the published palettes in tools/sources/.

Every source keeps its own identity: the `entries` table has one row per (source, name), with the
source's own code, reference URL, licence and how the value was verified. `colors` has one row per HEX
(the browsable list) and is derived from `entries`. Nothing is invented; there are no descriptive names.
Pantone, RAL and NCS are licensed and are NOT bundled: the app imports your own licensed files instead.

Only names published by the palette owners are used (CSS and X11 named colours, Apple system colours,
Material Design, Tailwind CSS). Nothing is invented. A HEX that appears in several palettes is
stored once; its first name (Web, X11, Apple, Material, Tailwind) is the main name and the
others go into `also`.
"""
import colorsys, json, os, sqlite3, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "sources")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "colors.db")


def fold(s):
    s = unicodedata.normalize("NFKD", s)
    return "".join(c for c in s if not unicodedata.combining(c)).lower()


def hx(v):
    v = v.lstrip("#").upper()
    return "".join(c * 2 for c in v) if len(v) == 3 else v


def title(slug):
    return " ".join(w.capitalize() for w in slug.replace("-", " ").split())


def family(h, s, l):
    if s < 0.12 or l < 0.07 or l > 0.95:
        return "Neutral"
    if 10 <= h < 50 and l < 0.42:
        return "Brown"
    if l >= 0.72 and (h >= 330 or h < 15):
        return "Pink"
    if h < 15 or h >= 345:
        return "Red"
    if h < 40:
        return "Orange"
    if h < 70:
        return "Yellow"
    if h < 165:
        return "Green"
    if h < 200:
        return "Teal"
    if h < 255:
        return "Blue"
    if h < 300:
        return "Purple"
    return "Pink"



SOURCES = {
    # id: (organisation, category, reference, licence, verification)
    "CSS": ("W3C", "Named color (web standard)", "https://www.w3.org/TR/css-color/#named-colors",
            "Open web standard (W3C)", "verified"),
    "X11": ("X.Org", "Named color (X11)", "https://gitlab.freedesktop.org/xorg/app/rgb",
            "X11 licence (MIT-style)", "source"),
    "Apple": ("Apple", "UI system color", "https://developer.apple.com/design/human-interface-guidelines/color",
              "Values published for developers; names belong to Apple", "published"),
    "Material": ("Google", "Design palette", "https://m2.material.io/design/color/the-color-system.html",
                 "Published palette (npm material-colors, ISC)", "published"),
    "Tailwind": ("Tailwind Labs", "Design palette", "https://tailwindcss.com/docs/customizing-colors",
                 "MIT", "source"),
}
PRIO = {"CSS": 1, "X11": 2, "Apple": 3, "Material": 4, "Tailwind": 5}
ROLES = ("Label", "Background", "Separator")  # UI roles: a colour called "Red 500" reads better than "Label"
MAIN_SOURCES = ("CSS", "X11")                  # what Explore's "All" shows (plus anything the user imports)

# (source, code, name, hex)
entries = []

# CSS: cross-checked against the W3C table (tools/sources/w3c_named_colors.txt)
w3c = {l.split()[0]: hx(l.split()[1]) for l in open(os.path.join(SRC, "w3c_named_colors.txt")) if l.strip()}
css = json.load(open(os.path.join(SRC, "css.json")))
assert {k: hx(v) for k, v in css.items()} == w3c, "css.json differs from the W3C table"

x11 = json.load(open(os.path.join(SRC, "x11.json")))
x11_name = {n.replace(" ", "").lower(): n for n, _ in x11}
x11_name["rebeccapurple"] = "Rebecca Purple"  # CSS Color 4, not in X11
css_names = {}
for kw, v in css.items():
    nm = x11_name.get(kw, kw.capitalize())  # spaced spelling of the CSS keyword
    css_names[nm] = hx(v)
    entries.append(("CSS", kw, nm, hx(v)))

for nm, v in x11:
    # X11 and CSS disagree on a few colours (Green, Gray, Maroon, Purple): keep both, label the X11 one
    clash = nm in css_names and css_names[nm] != hx(v)
    entries.append(("X11", nm.lower(), nm + " (X11)" if clash else nm, hx(v)))

apple = json.load(open(os.path.join(SRC, "apple.json")))
for name, v in apple["light"].items():
    entries.append(("Apple", "", name, hx(v)))
for name, v in apple["dark"].items():
    entries.append(("Apple", "", name + " (Dark)", hx(v)))

mat = json.load(open(os.path.join(SRC, "material.json")))
for fam, shades in mat.items():
    if not isinstance(shades, dict) or fam.startswith(("dark-", "light-")):
        continue
    for shade, v in shades.items():
        entries.append(("Material", "", f"{title(fam)} {shade.upper() if shade[0] == 'a' else shade}", hx(v)))

tw = json.load(open(os.path.join(SRC, "tailwind.json")))
for fam, shades in tw.items():
    if fam == "_single":
        continue
    for shade, v in shades.items():
        entries.append(("Tailwind", f"{fam}-{shade}", f"{title(fam)} {shade}", hx(v)))

by_hex = {}
for st, code, name, h6 in entries:
    assert len(h6) == 6, (name, h6)
    by_hex.setdefault(h6, []).append((st, code, name))

if os.path.exists(OUT):
    os.remove(OUT)
db = sqlite3.connect(OUT)
db.execute("PRAGMA journal_mode=DELETE")
db.execute(
    """CREATE TABLE entries(
        id INTEGER PRIMARY KEY, hex TEXT NOT NULL, source TEXT NOT NULL, org TEXT NOT NULL, code TEXT NOT NULL,
        name TEXT NOT NULL, name_type TEXT NOT NULL, category TEXT NOT NULL, ref TEXT NOT NULL,
        license TEXT NOT NULL, verification TEXT NOT NULL, prio INTEGER NOT NULL, imported INTEGER NOT NULL DEFAULT 0)"""
)
db.execute("CREATE INDEX idx_entries_hex ON entries(hex)")
db.execute(
    """CREATE TABLE colors(
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, name_search TEXT NOT NULL, hex TEXT NOT NULL UNIQUE,
        r INTEGER, g INTEGER, b INTEGER, h REAL, s REAL, l REAL, v REAL, hue_key REAL, family TEXT,
        sets TEXT NOT NULL, also TEXT NOT NULL, code TEXT NOT NULL, category TEXT NOT NULL, main INTEGER NOT NULL)"""
)

eid = 0
for h6, names in by_hex.items():
    for st, code, name in names:
        org, cat, ref, lic, ver = SOURCES[st]
        prio = PRIO[st] * 10 + (100 if any(w in name for w in ROLES) else 0)
        eid += 1
        db.execute("INSERT INTO entries VALUES(?,?,?,?,?,?,?,?,?,?,?,?,0)",
                   (eid, h6, st, org, code, name, "Official", cat, ref, lic, ver, prio))

rows = []
for i, h6 in enumerate(by_hex):
    es = db.execute("SELECT source, code, name, category FROM entries WHERE hex=? ORDER BY prio, id", (h6,)).fetchall()
    st, code, name, cat = es[0]
    sets = []
    for e in es:
        if e[0] not in sets:
            sets.append(e[0])
    also = " · ".join(f"{e[2]} ({e[0]})" for e in es[1:])
    r, g, b = (int(h6[k : k + 2], 16) for k in (0, 2, 4))
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    h *= 360
    fam = family(h, s, l)
    key = 1000 + l if fam == "Neutral" else h + l / 1000
    # every official name and code is searchable
    search = fold(" ".join([e[2] for e in es] + [e[1] for e in es if e[1]]))
    main = 1 if any(x in MAIN_SOURCES for x in sets) else 0
    rows.append((i, name, search, h6, r, g, b, round(h, 2), round(s, 4), round(l, 4), round(v, 4), round(key, 4), fam,
                 "|" + "|".join(sets) + "|", also, code, cat, main))
db.executemany("INSERT INTO colors VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
for idx in ("name_search", "h", "s", "l", "hue_key", "family", "main"):
    db.execute(f"CREATE INDEX idx_{idx} ON colors({idx})")
db.execute("PRAGMA user_version=4")
db.commit()
db.execute("VACUUM")
db.close()
counts = {}
for st, _, _, _ in entries:
    counts[st] = counts.get(st, 0) + 1
print(len(entries), "entries ->", len(rows), "unique colours", counts, os.path.getsize(OUT) // 1024, "KB")

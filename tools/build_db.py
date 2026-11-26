"""Builds app/src/main/assets/colors.db from the official palettes in tools/sources/.

Only names published by the palette owners are used (Apple system colours, CSS named colours,
Material Design, Tailwind CSS). Nothing is invented. A HEX that appears in several palettes is
stored once; its first name (Apple, then Web, Material, Tailwind) is the main name and the
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


entries = []  # (set, name, hex) in priority order

apple = json.load(open(os.path.join(SRC, "apple.json")))
for name, v in apple["light"].items():
    entries.append(("Apple", name, hx(v)))
for name, v in apple["dark"].items():
    entries.append(("Apple", name + " (Dark)", hx(v)))

for name, v in json.load(open(os.path.join(SRC, "css.json"))).items():
    entries.append(("Web", name, hx(v)))

mat = json.load(open(os.path.join(SRC, "material.json")))
for fam, shades in mat.items():
    if not isinstance(shades, dict) or fam.startswith(("dark-", "light-")):
        continue
    for shade, v in shades.items():
        entries.append(("Material", f"{title(fam)} {shade.upper() if shade[0] == 'a' else shade}", hx(v)))

tw = json.load(open(os.path.join(SRC, "tailwind.json")))
for fam, shades in tw.items():
    if fam == "_single":
        continue
    for shade, v in shades.items():
        entries.append(("Tailwind", f"{title(fam)} {shade}", hx(v)))

ROLES = ("Label", "Background", "Separator")  # UI roles: a colour called "Red 500" reads better than "Label"
groups = {}
for st, name, h6 in entries:
    assert len(h6) == 6, (name, h6)
    groups.setdefault(h6, []).append((st, name))
for h6, names in groups.items():
    names.sort(key=lambda e: any(w in e[1] for w in ROLES))  # stable: keeps palette priority otherwise

if os.path.exists(OUT):
    os.remove(OUT)
db = sqlite3.connect(OUT)
db.execute("PRAGMA journal_mode=DELETE")
db.execute(
    """CREATE TABLE colors(
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, name_search TEXT NOT NULL, hex TEXT NOT NULL UNIQUE,
        r INTEGER, g INTEGER, b INTEGER, h REAL, s REAL, l REAL, v REAL, hue_key REAL, family TEXT,
        sets TEXT NOT NULL, also TEXT NOT NULL)"""
)
rows = []
for i, (h6, names) in enumerate(groups.items()):
    st, name = names[0]
    sets = []
    for s_, _ in names:
        if s_ not in sets:
            sets.append(s_)
    also = " · ".join(f"{n} ({s_})" for s_, n in names[1:])
    r, g, b = (int(h6[k : k + 2], 16) for k in (0, 2, 4))
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    h *= 360
    fam = family(h, s, l)
    key = 1000 + l if fam == "Neutral" else h + l / 1000
    # names that also appear in the search text: all official names, so searching any of them finds the colour
    search = fold(" ".join([name] + [n for _, n in names[1:]]))
    rows.append((i, name, search, h6, r, g, b, round(h, 2), round(s, 4), round(l, 4), round(v, 4), round(key, 4), fam,
                 "|" + "|".join(sets) + "|", also))
db.executemany("INSERT INTO colors VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
for idx in ("name_search", "h", "s", "l", "hue_key", "family"):
    db.execute(f"CREATE INDEX idx_{idx} ON colors({idx})")
db.execute("PRAGMA user_version=2")
db.commit()
db.execute("VACUUM")
db.close()
counts = {}
for st, _, _ in entries:
    counts[st] = counts.get(st, 0) + 1
print(len(entries), "names ->", len(rows), "unique colours", counts, os.path.getsize(OUT) // 1024, "KB")

"""Builds app/src/main/assets/colors.db from tools/colornames.json (meodai/color-names, MIT).

Names and HEX values are copied exactly as published. Everything else (RGB, HSL, HSV, family)
is computed from the HEX value.
"""
import colorsys, json, os, sqlite3, unicodedata

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "colornames.json")
OUT = os.path.join(HERE, "..", "app", "src", "main", "assets", "colors.db")


def fold(s):
    s = unicodedata.normalize("NFKD", s)
    return "".join(c for c in s if not unicodedata.combining(c)).lower()


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


data = json.load(open(SRC, encoding="utf-8"))
if os.path.exists(OUT):
    os.remove(OUT)
db = sqlite3.connect(OUT)
db.execute("PRAGMA journal_mode=DELETE")
db.execute(
    """CREATE TABLE colors(
        id INTEGER PRIMARY KEY, name TEXT NOT NULL, name_search TEXT NOT NULL, hex TEXT NOT NULL UNIQUE,
        r INTEGER, g INTEGER, b INTEGER, h REAL, s REAL, l REAL, v REAL, hue_key REAL, family TEXT)"""
)
seen = set()
rows = []
for i, c in enumerate(data):
    hx = c["hex"].lstrip("#").upper()
    assert len(hx) == 6 and hx not in seen, hx
    seen.add(hx)
    r, g, b = (int(hx[k : k + 2], 16) for k in (0, 2, 4))
    h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
    _, _, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    h *= 360
    fam = family(h, s, l)
    key = 1000 + l if fam == "Neutral" else h + l / 1000
    rows.append((i, c["name"], fold(c["name"]), hx, r, g, b, round(h, 2), round(s, 4), round(l, 4), round(v, 4), round(key, 4), fam))
db.executemany("INSERT INTO colors VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?)", rows)
for idx in ("name_search", "h", "s", "l", "hue_key", "family"):
    db.execute(f"CREATE INDEX idx_{idx} ON colors({idx})")
db.execute("PRAGMA user_version=1")
db.commit()
db.execute("VACUUM")
db.close()
print(len(rows), "colours ->", os.path.getsize(OUT) // 1024, "KB")

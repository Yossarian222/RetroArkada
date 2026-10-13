#!/usr/bin/env python3
"""
Z FBNeo DAT (ClrMame Pro XML) vyrobí kompaktný index pre appku: app/src/main/assets/fbneo.idx

Riadok = jeden ROM set:  name<TAB>cloneof<TAB>bios(0/1)<TAB>popis<TAB>vlastné CRC<TAB>zdieľané CRC
CRC sú hex oddelené čiarkou. Appka podľa nich spozná arkádovú hru v zipe aj so zlým názvom
a premenuje ho na meno, ktoré FBNeo pozná (napr. "Metal Slug (World).zip" -> "mslug.zip").
"""
import sys
import xml.etree.ElementTree as ET

src, out = sys.argv[1], sys.argv[2]
lines = []
for game in ET.parse(src).getroot().iter("game"):
    own, shared = [], []
    for rom in game.iter("rom"):
        crc = rom.get("crc")
        if not crc or rom.get("status") == "nodump":
            continue
        (shared if rom.get("merge") else own).append(crc.lower())
    desc = (game.findtext("description") or "").replace("\t", " ").replace("\n", " ").strip()
    lines.append("\t".join([
        game.get("name"),
        game.get("cloneof") or "",
        "1" if game.get("isbios") == "yes" else "0",
        desc,
        ",".join(dict.fromkeys(own)),
        ",".join(dict.fromkeys(shared)),
    ]))
if len(lines) < 1000:
    sys.exit(f"Podozrivo málo hier v DAT: {len(lines)}")
# Obyčajný text: build Androidu by .gz aj tak rozbalil a premenoval (APK je komprimované samo)
with open(out, "w", encoding="utf-8") as f:
    f.write("\n".join(lines) + "\n")
print(f"✓ {len(lines)} arkádových setov -> {out}")

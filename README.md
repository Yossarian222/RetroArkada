# Retro Arkáda

Jednoduchá a pekná retro herná konzola pre **Android TV na Raspberry Pi 5**, ovládaná **Xbox ovládačom**.
Pod kapotou bežia overené libretro jadrá (ako v RetroArch), ale bez zložitých menu.

## Konzoly

| Konzola | Jadro | Súbory |
|---|---|---|
| NES / Famicom | FCEUmm | `.nes` `.fds` |
| Super Nintendo | Snes9x | `.sfc` `.smc` |
| Nintendo 64 | Mupen64Plus-Next | `.n64` `.z64` `.v64` |
| Game Boy / Color | Gambatte | `.gb` `.gbc` |
| Game Boy Advance | mGBA | `.gba` |
| Mega Drive / Master System / Game Gear | Genesis Plus GX | `.md` `.gen` `.smd` `.bin` / `.sms` / `.gg` |
| PlayStation | PCSX ReARMed | `.chd` `.pbp` `.cue`+`.bin` `.m3u` |
| PC Engine | Beetle PCE Fast | `.pce` |
| Atari 2600 | Stella | `.a26` |
| Arcade | FinalBurn Neo | `.zip` (ROM sety) |

Hry sa dajú nahrať aj zazipované. Aplikácia ich sama rozbalí.

## Inštalácia

1. Na TV si nainštaluj aplikáciu **Downloader** (alebo ľubovoľný prehliadač).
2. Stiahni `https://github.com/Yossarian222/RetroArkada/releases/download/latest/RetroArkada.apk`
3. Nainštaluj. Nové verzie sa inštalujú ako aktualizácia a uložené pozície zostanú zachované.

## Pridávanie hier

- **Z mobilu alebo PC:** otvor *Pridať hry*, naskenuj QR kód (alebo zadaj adresu `http://<ip-tv>:8080`)
  a pretiahni súbory do stránky.
- **USB kľúč:** v *Nastaveniach* povoľ prístup k úložisku. Hry daj do priečinka `Roms`
  (napr. `Roms/snes/…`, `Roms/psx/…`). Konzola sa rozpozná podľa priečinka alebo podľa prípony.
  Hry môžu byť aj zazipované – rozbalia sa automaticky pri spustení (drží sa len pár posledných).
- **Z PC cez adb na USB v RPi:** spusti `scripts/nahraj-hry.bat` (dvojklik) alebo
  `.\scripts\nahraj-hry.ps1 -Zdroj "E:\Roms"`. Skript nájde USB kľúč v RPi a nahrá naň len nové
  a zmenené hry do `Roms/`. Po zapojení USB kľúča sa knižnica obnoví sama.
- **BIOS** (napr. `scph5501.bin` pre PS1) môžeš dať aj na USB do priečinka `Roms/bios` – appka si ho
  skopíruje sama. PS1 hry s originálnym BIOSom bežia stabilnejšie.
- **Obaly** sa sťahujú automaticky z libretro-thumbnails, keď sa súbor volá podľa No-Intro alebo Redump,
  napr. `Super Mario World (USA).sfc`.
- **BIOS** (voliteľný, napr. `scph5501.bin` pre PS1) nahraj cez web s voľbou *BIOS*.

## Ovládanie

| Tlačidlo | V menu | V hre |
|---|---|---|
| A | spustiť hru | tlačidlo dole (B na SNES) |
| B | späť | tlačidlo vpravo (A na SNES) |
| Y | obľúbená | tlačidlo vľavo |
| X | detail hry | tlačidlo hore |
| View + Menu, tlačidlo Xbox, Späť | – | herné menu (uložiť, načítať, rýchlo vpred, filter, reštart, koniec) |

Tlačidlá sa mapujú podľa polohy, takže rozloženie sedí s originálnymi ovládačmi.
Ľavá páčka funguje aj ako šípky pri konzolách bez analógu. LT a RT sú L2 a R2.

Pri odchode z hry sa postup uloží automaticky a nabudúce hra pokračuje tam, kde si skončil.

## Build

APK sa zostavuje v GitHub Actions (`.github/workflows/build.yml`):

```
./scripts/download-cores.sh     # stiahne jadrá z buildbot.libretro.com
./gradlew assembleRelease
```

Emulácia je postavená na knižnici [LibretroDroid](https://github.com/Swordfish90/LibretroDroid) (GPLv3).
Aplikácia neobsahuje žiadne hry. Používaj len vlastné zálohy hier.

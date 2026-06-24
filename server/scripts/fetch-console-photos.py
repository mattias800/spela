#!/usr/bin/env python3
"""Fetch console hardware photos from Wikimedia Commons into the embedded
backend asset dir, and (re)generate the credits manifest.

We bundle the images in-repo and serve them ourselves (see #1441) rather than
hotlinking Commons at runtime, so the app is self-contained if a file is later
moved or deleted upstream.

- Files are named by the console's LOWERCASE ABBREVIATION (matching the
  console-icons/console-logos convention and the /photo handler lookup).
- Source photos are mostly the public-domain "Evan Amos" collection; a few are
  CC-BY-SA (kept unmodified-but-resized, with attribution recorded below). The
  per-file author + license is fetched from the Commons API and written to
  CREDITS.json so attribution is accurate and auditable.

Usage:  python3 server/scripts/fetch-console-photos.py
Re-run any time to refresh; it overwrites the images + CREDITS.json.
"""

import json
import os
import re
import sys
import time
import urllib.parse
import urllib.request

UA = "SpelaConsolePhotoFetcher/1.0 (https://github.com/mattias800/spela; bundling public-domain console photos)"
WIDTH = 640
OUT = os.path.normpath(
    os.path.join(os.path.dirname(os.path.abspath(__file__)),
                 "..", "internal", "api", "static", "console-photos")
)

# console abbreviation (lowercase) -> Commons File: name
PHOTOS = {
    "a26": "Atari-2600-Wood-4Sw-Set.png",
    "a52": "Atari-5200-4-Port-wController-L.jpg",
    "cv": "ColecoVision-wController-L.jpg",
    "chaf": "Fairchild-Channel-F.png",
    "o2": "Magnavox-Odyssey-2-Console-Set.png",
    "intv": "Intellivision-Console-Set.png",
    "vec": "Vectrex-Console-Set.png",
    "nes": "NES-Console-Set.png",
    "sms": "Sega-Master-System-Set.png",
    "a78": "Atari-7800-Console-Set.png",
    "pce": "TurboGrafx16-Console-Set.png",
    "gen": "Sega-Genesis-Mk2-6button.jpg",
    "gb": "Game-Boy-FL.png",
    "lynx": "Atari-Lynx-I-Handheld.png",
    "gg": "Sega-Game-Gear-WB.png",
    "snes": "SNES-Mod1-Console-Set.png",
    "neogeo": "Neo-Geo-AES-Console-Set.png",
    "3do": "3DO-FZ1-Console-Set.png",
    "jag": "Atari-Jaguar-Console-Set.png",
    "sat": "Sega-Saturn-Console-Set-Mk1.png",
    "psx": "PSX-Console-wController.png",
    "vb": "Virtual-Boy-Set.png",
    "n64": "N64-Console-Set.png",
    "dc": "Dreamcast-Console-Set.png",
    "ps2": "PS2-Fat-Console-Set.png",
    "gc": "GameCube-Console-Set.png",
    "xbox": "Xbox-Console-Set.png",
    "gba": "Nintendo-Game-Boy-Advance-Purple-FL.png",
    "nds": "Nintendo-DS-Lite-Black-Open.png",
    "psp": "PSP-1000.png",
    "x360": "Xbox-360S-Console-Set.png",
    "ps3": "Sony-PlayStation-3-CECHA01-wController-L.jpg",
    "wii": "Wii-Console.png",
    "3ds": "Nintendo-3DS-AquaOpen.png",
    "vita": "PlayStation-Vita-1101-FL.png",
    "wiiu": "Wii_U_Console_and_Gamepad.png",
    "xone": "Microsoft-Xbox-One-Console-Set-wKinect.jpg",
    "nsw": "Nintendo_Switch_Console.png",
    "xsx": "Xbox_Series_X_and_S_with_controllers_(transparent_background).png",
    "sg1k": "Sega-SG-1000-Console-Set.png",
    "sgx": "SuperGrafx-Console-Set.png",
    "pcecd": "NEC-TurboGrafx-16-CD-FL.png",
    "scd": "Sega-CD-Model1-Set.jpg",
    "cdi": "CD-i-910-Console-Set.jpg",
    "acd32": "Amiga-CD32-wController-L-TRSP.png",
    "neocd": "Neo-Geo-CD-TopLoader-wController-FL.png",
    "32x": "Sega-Genesis-32X-01.jpg",
    "fds": "Nintendo-Famicom-Disk-System.jpg",
    "gw": "Game&watch-donkey-kong-2.png",
    "gbc": "Nintendo-Game-Boy-Color-FL.png",
    "ngp": "Neo-Geo-Pocket-Color-Anthra-Left.png",
    "ws": "WonderSwan-Color-Blue-Left.png",
    "pcfx": "PC-FX-Console-Set.png",
    "ps4": "PS4-Console-wDS4.png",
    "ps5": "PlayStation 5 and DualSense with transparent background.png",
    "pkmn": "Pokemon mini.png",
    # Home computers
    "a800": "Atari-800-Computer-FL.jpg",
    "c64": "Commodore-64-Computer-FL.png",
    "vic20": "Commodore-VIC-20-FL.png",
    "amiga": "Amiga 500 Plus (transparent background).png",
    "msx1": "Sony HitBit HB-10P (Transparent Background).png",
    "c128": "Commodore-128.png",
    "pet": "Commodore 2001 Series-IMG 0448b.jpg",
    "plus4": "Commodore Plus-4.jpg",
    # NOTE: `dos` intentionally omitted — the only clean Commons IBM PC 5150
    # photo is CC-BY-SA with no recorded author (can't attribute compliantly),
    # so DOS falls back to its logo. Re-add if a properly-credited photo is
    # sourced. See #1441.
}


def fetch(url: str) -> bytes:
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=90) as r:
        return r.read()


def strip_html(s: str) -> str:
    return re.sub(r"\s+", " ", re.sub(r"<[^>]+>", "", s or "")).strip()


def main() -> int:
    os.makedirs(OUT, exist_ok=True)
    manifest = []
    failures = []
    for abbr, fname in PHOTOS.items():
        enc = urllib.parse.quote(fname.replace(" ", "_"))
        try:
            api = ("https://commons.wikimedia.org/w/api.php?action=query&titles=File:"
                   + enc + "&prop=imageinfo&iiprop=extmetadata&format=json")
            page = next(iter(json.loads(fetch(api))["query"]["pages"].values()))
            em = page.get("imageinfo", [{}])[0].get("extmetadata", {})
            license_name = em.get("LicenseShortName", {}).get("value", "UNKNOWN")
            author = strip_html(em.get("Artist", {}).get("value", ""))

            ext = fname.rsplit(".", 1)[1].lower()
            ext = "jpg" if ext in ("jpeg",) else ext
            data = fetch("https://commons.wikimedia.org/wiki/Special:FilePath/"
                         + enc + "?width=" + str(WIDTH))
            out_file = abbr + "." + ext
            with open(os.path.join(OUT, out_file), "wb") as f:
                f.write(data)

            manifest.append({
                "console": abbr,
                "file": out_file,
                "title": fname,
                "author": author,
                "license": license_name,
                "source": "https://commons.wikimedia.org/wiki/File:" + enc,
                "bytes": len(data),
            })
            print(f"  ok  {abbr:8} {out_file:14} {license_name:18} {author}")
        except Exception as e:  # noqa: BLE001 — best-effort fetch tool
            failures.append((abbr, fname, str(e)))
            print(f"FAIL  {abbr:8} {fname}: {e}", file=sys.stderr)
        time.sleep(0.3)

    manifest.sort(key=lambda m: m["console"])
    with open(os.path.join(OUT, "CREDITS.json"), "w") as f:
        json.dump({
            "note": ("Console hardware photos from Wikimedia Commons, bundled and "
                     "served by Spela. Most are public-domain (Evan Amos); CC-BY-SA "
                     "files are reproduced unmodified (resized only) with attribution."),
            "photos": manifest,
        }, f, indent=2)
        f.write("\n")

    print(f"\n{len(manifest)} photos written to {OUT}; {len(failures)} failed.")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())

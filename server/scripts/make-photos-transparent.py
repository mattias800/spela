#!/usr/bin/env python3
"""Make white-background console photos transparent.

Second step of the console-photo pipeline (after fetch-console-photos.py), for
sources that aren't already transparent. Evan Amos studio shots on solid white
read as an ugly box on the coloured cards.

PREFER A TRANSPARENT SOURCE: many Evan Amos photos exist on Commons as both a
white-bg `.jpg` and a transparent `.png` of the same shot — point
fetch-console-photos.py at the `.png` and skip keying entirely. Real
transparency has clean anti-aliased edges; keying a white bg never cuts as
cleanly (it leaves a jagged/haloed boundary). Only key photos with no
transparent twin.

Per-file rule:
  - Top-left already transparent -> skip (fine as-is).
  - Top-left exactly white        -> remove the background with a flood-fill from
                                     a white border (with a fuzz tolerance), then
                                     (re)write as PNG. Flood-fill — unlike a
                                     global `-transparent white` — only clears the
                                     *connected* background, so white/light parts
                                     INSIDE the console are protected.
  - Otherwise                     -> skip and report.

Tune PHOTO_FUZZ (default 10) for the tolerance: higher removes more near-white
halo but risks eating very light consoles (PC-FX, cream Famicom) — drop to ~6
for those. JPGs become PNGs; the old .jpg is removed and CREDITS.json updated.
Re-running is idempotent: already-transparent files are skipped.

Requires ImageMagick (`magick`).
"""
import json
import os
import subprocess

OUT = os.path.join(os.path.dirname(__file__), "..", "internal", "api", "static", "console-photos")
OUT = os.path.normpath(OUT)
WHITE = (255, 255, 255)


def corner_rgba(path: str) -> tuple[int, int, int, int]:
    """Top-left pixel as 0-255 RGBA via ImageMagick (alpha forced on)."""
    fmt = ("%[fx:int(255*p{0,0}.r)] %[fx:int(255*p{0,0}.g)] "
           "%[fx:int(255*p{0,0}.b)] %[fx:int(255*p{0,0}.a)]")
    out = subprocess.check_output(["magick", path, "-alpha", "on", "-format", fmt, "info:"])
    return tuple(int(x) for x in out.decode().split())  # type: ignore[return-value]


def main() -> int:
    creds_path = os.path.join(OUT, "CREDITS.json")
    creds = json.load(open(creds_path))
    by_console = {p["console"]: p for p in creds["photos"]}

    files = sorted(f for f in os.listdir(OUT) if f.lower().endswith((".png", ".jpg")))
    changed = 0
    for f in files:
        abbr = f.rsplit(".", 1)[0]
        path = os.path.join(OUT, f)
        r, g, b, a = corner_rgba(path)

        if a == 0:
            print(f"  skip  {f:16} already transparent")
            continue
        if (r, g, b) != WHITE:
            print(f"  skip  {f:16} corner not white (rgb {r},{g},{b})")
            continue

        out_name = abbr + ".png"
        out_path = os.path.join(OUT, out_name)
        tmp = out_path + ".tmp.png"
        fuzz = os.environ.get("PHOTO_FUZZ", "10")
        # Flood-fill from a 1px white border so the whole edge-connected
        # background drains out (with a fuzz tolerance to catch near-white +
        # the anti-aliased halo), then shave the border. Interior white/light
        # parts of the console are untouched.
        subprocess.check_call(["magick", path, "-alpha", "set",
                               "-bordercolor", "white", "-border", "1",
                               "-fuzz", f"{fuzz}%", "-fill", "none",
                               "-draw", "alpha 0,0 floodfill", "-shave", "1x1", tmp])
        os.replace(tmp, out_path)
        if out_name != f:  # was a .jpg
            os.remove(path)
        nbytes = os.path.getsize(out_path)
        if abbr in by_console:
            by_console[abbr]["file"] = out_name
            by_console[abbr]["bytes"] = nbytes
        changed += 1
        print(f"  KEYED {f:16} -> {out_name} ({nbytes} bytes)")

    creds["photos"] = sorted(by_console.values(), key=lambda m: m["console"])
    with open(creds_path, "w") as fh:
        json.dump(creds, fh, indent=2)
        fh.write("\n")

    print(f"\n{changed} file(s) keyed to transparent.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

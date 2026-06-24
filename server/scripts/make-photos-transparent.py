#!/usr/bin/env python3
"""Make white-background console photos transparent.

Second step of the console-photo pipeline (after fetch-console-photos.py).
Many Wikimedia source photos are studio shots on a solid white background,
which reads as an ugly white box on the coloured console cards. This keys the
white out to transparency.

Per-file rule (intentionally simple + safe):
  - Read the top-left pixel.
  - If it is already transparent  -> skip (file is fine as-is).
  - If it is exactly white        -> replace every exactly-white pixel with
                                     transparency and (re)write as PNG.
  - Otherwise (a coloured/photo edge bleeds to the corner) -> skip and report.

JPGs that get keyed become PNGs (JPEG has no alpha); the old .jpg is removed
and CREDITS.json is updated (file + bytes). Re-running is idempotent: already
transparent files are skipped.

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
        # -fuzz 0 => only the exact white colour is keyed (no near-white).
        subprocess.check_call(["magick", path, "-alpha", "set", "-fuzz", "0",
                               "-transparent", "white", tmp])
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

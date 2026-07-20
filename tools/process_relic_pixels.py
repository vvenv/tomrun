#!/usr/bin/env python3
"""Bake tomrun fancy relic PNGs into drawable-nodpi icons.

Usage:
  python3 tools/process_relic_pixels.py              # sync + process all
  python3 tools/process_relic_pixels.py 10           # only id 10
  python3 tools/process_relic_pixels.py 10 --crop-top 0.11
  python3 tools/process_relic_pixels.py 28 --pixelate 112
  python3 tools/process_relic_pixels.py --list
"""
from __future__ import annotations

import argparse
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
PIXEL = ROOT / "tools/relic_assets/pixel"
ASSETS = Path.home() / ".cursor/projects/Users-liwenfu-vvenv-tomrun/assets"
OUT = ROOT / "app/src/main/res/drawable-nodpi"
SIZE = 256
RELIC_COUNT = 32


def sync_from_cursor_assets(only: int | None = None) -> None:
    PIXEL.mkdir(parents=True, exist_ok=True)
    if not ASSETS.is_dir():
        return
    for p in ASSETS.glob("relic_fancy_*.png"):
        if "_raw" in p.name:
            continue
        try:
            rid = int(p.stem.split("_")[-1])
        except ValueError:
            continue
        if only is not None and rid != only:
            continue
        target = PIXEL / p.name
        if not target.exists() or p.stat().st_mtime > target.stat().st_mtime:
            target.write_bytes(p.read_bytes())
            print(f"sync {p.name}")


def process(
    src: Path,
    dest: Path,
    *,
    crop_top: float = 0.0,
    dark_thresh: int = 85,
    size: int = SIZE,
    pixelate: int | None = None,
) -> None:
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    if crop_top > 0:
        im = im.crop((0, int(h * crop_top), w, h))
        w, h = im.size
    px = im.load()
    samples = [px[2, 2], px[w - 3, 2], px[2, h - 3], px[w - 3, h - 3]]
    br = sum(c[0] for c in samples) // 4
    bg = sum(c[1] for c in samples) // 4
    bb = sum(c[2] for c in samples) // 4
    out = Image.new("RGBA", (w, h))
    op = out.load()
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            dark = (r + g + b) < dark_thresh
            near = abs(r - br) + abs(g - bg) + abs(b - bb) < 50 and (br + bg + bb) < 170
            op[x, y] = (0, 0, 0, 0) if (dark or near) else (r, g, b, a)
    alpha = out.split()[-1]
    bbox = alpha.point(lambda v: 255 if v > 20 else 0).getbbox()
    if bbox:
        l, t, r, b = bbox
        pad = int(max(r - l, b - t) * 0.06) + 2
        out = out.crop((max(0, l - pad), max(0, t - pad), min(w, r + pad), min(h, b + pad)))
    side = max(out.size)
    canvas = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    canvas.paste(out, ((side - out.width) // 2, (side - out.height) // 2), out)
    # Optional chunky pixel look: box-downsample, palette quantize, nearest upscale
    if pixelate and pixelate > 0 and pixelate < size:
        canvas = canvas.resize((pixelate, pixelate), Image.Resampling.BOX)
        alpha = canvas.split()[-1]
        q = canvas.convert("RGB").quantize(colors=32, method=Image.Quantize.MEDIANCUT).convert("RGBA")
        q.putalpha(alpha)
        canvas = q.resize((size, size), Image.Resampling.NEAREST)
    else:
        canvas = canvas.resize((size, size), Image.Resampling.LANCZOS)
    r, g, b, a = canvas.split()
    a = a.point(lambda v: 0 if v < 25 else 255 if v > 180 else v)
    OUT.mkdir(parents=True, exist_ok=True)
    Image.merge("RGBA", (r, g, b, a)).save(dest, "PNG", optimize=True)
    print(f"{dest.name} {dest.stat().st_size}")


def coverage() -> None:
    have = {int(p.stem.split("_")[-1]) for p in OUT.glob("relic_fancy_*.png")}
    missing = sorted(set(range(RELIC_COUNT)) - have)
    print(f"drawable: {len(have)}/{RELIC_COUNT}")
    if missing:
        print("missing ids:", missing)


def main() -> None:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("ids", nargs="*", type=int, help="Relic ids to process (default: all in pixel/)")
    ap.add_argument("--crop-top", type=float, default=0.0, help="Fraction of height to crop from top (e.g. 0.11 for title)")
    ap.add_argument("--dark", type=int, default=85, help="RGB sum threshold treated as transparent bg")
    ap.add_argument("--size", type=int, default=SIZE, help="Output square size")
    ap.add_argument(
        "--pixelate",
        type=int,
        default=0,
        help="Chunky pixel grid size before nearest upscale (e.g. 96); 0=off",
    )
    ap.add_argument("--no-sync", action="store_true", help="Skip Cursor assets sync")
    ap.add_argument("--list", action="store_true", help="Show drawable coverage and exit")
    args = ap.parse_args()

    if args.list:
        coverage()
        return

    only = args.ids[0] if len(args.ids) == 1 else None
    if not args.no_sync:
        if args.ids:
            for i in args.ids:
                sync_from_cursor_assets(i)
        else:
            sync_from_cursor_assets(None)

    sources: list[Path] = []
    if args.ids:
        for i in args.ids:
            p = PIXEL / f"relic_fancy_{i:02d}.png"
            if not p.exists():
                raise SystemExit(f"missing source: {p}")
            sources.append(p)
    else:
        sources = sorted(
            p for p in PIXEL.glob("relic_fancy_*.png") if "_raw" not in p.name
        )

    for src in sources:
        process(
            src,
            OUT / src.name,
            crop_top=args.crop_top,
            dark_thresh=args.dark,
            size=args.size,
            pixelate=args.pixelate or None,
        )
    coverage()


if __name__ == "__main__":
    main()

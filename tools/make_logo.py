from pathlib import Path

from PIL import Image

src_path = Path(
    r"C:\Users\zongt\AppData\Roaming\Cursor\User\globalStorage"
    r"\anysphere.cursor-agent-worker\worker-data\cursor-agent-worker-1fa1276e65"
    r"\projects\c-Users-zongt-Projects-NoirNetInfo\assets"
    r"\FB5D0C69-6D52-4CCF-916E-CA95B77BE2B2_L0_001.jpg"
)
root = Path(r"C:\Users\zongt\Projects\NoirNetInfo")
drawable = root / "app/src/main/res/drawable"
drawable.mkdir(parents=True, exist_ok=True)

im = Image.open(src_path).convert("RGBA")
# Keep a project-local copy so later rebuilds do not depend on Cursor asset paths.
local = root / "tools/logo_source.png"
im.save(local, optimize=True)
im.save(drawable / "logo_app.png", optimize=True)

w, h = im.size
# Face sits on the left; Japanese sticker text is on the right.
side = min(w, h)
left = max(0, int(w * 0.04))
if left + side > w:
    left = max(0, w - side)
top = max(0, (h - side) // 8)
if top + side > h:
    top = max(0, h - side)
crop = im.crop((left, top, left + side, top + side))

fg = 432
canvas = Image.new("RGBA", (fg, fg), (255, 255, 255, 255))
inner = int(fg * 0.86)
face = crop.resize((inner, inner), Image.Resampling.LANCZOS)
off = (fg - inner) // 2
canvas.paste(face, (off, off), face)
canvas.save(drawable / "ic_launcher_foreground.png", optimize=True)

for name, dim in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    d = root / f"app/src/main/res/mipmap-{name}"
    d.mkdir(parents=True, exist_ok=True)
    icon = crop.resize((dim, dim), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (dim, dim), (255, 255, 255, 255))
    out.paste(icon, (0, 0), icon)
    out.save(d / "ic_launcher.png", optimize=True)
    out.save(d / "ic_launcher_round.png", optimize=True)

print("ok", im.size, (drawable / "logo_app.png").stat().st_size)

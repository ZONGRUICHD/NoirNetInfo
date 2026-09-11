from pathlib import Path

from PIL import Image

src_path = Path(
    r"C:\Users\zongt\.cursor\projects\c-Users-zongt-Projects-NoirNetInfo\assets"
    r"\c__Users_zongt_AppData_Roaming_Cursor_User_workspaceStorage_"
    r"945fdf69fa1146a0e0310d10877f834c_images_ChatGPT_Image_2026_9_11____11_16_47-9f899686-61e8-449a-aa18-3616f1cc0245.jpg"
)
root = Path(r"C:\Users\zongt\Projects\NoirNetInfo")
drawable = root / "app/src/main/res/drawable"
drawable.mkdir(parents=True, exist_ok=True)

im = Image.open(src_path).convert("RGBA")
im.save(drawable / "logo_app.png", optimize=True)

w, h = im.size
side = h
left = 20
crop = im.crop((left, 0, min(left + side, w), side))
if crop.size[0] != crop.size[1]:
    square = Image.new("RGBA", (side, side), (0, 0, 0, 255))
    square.paste(crop, (0, 0), crop)
    crop = square

fg = 432
canvas = Image.new("RGBA", (fg, fg), (0, 0, 0, 255))
inner = int(fg * 0.78)
face = crop.resize((inner, inner), Image.Resampling.LANCZOS)
off = (fg - inner) // 2
canvas.paste(face, (off, off), face)
canvas.save(drawable / "ic_launcher_foreground.png", optimize=True)

for name, dim in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    d = root / f"app/src/main/res/mipmap-{name}"
    d.mkdir(parents=True, exist_ok=True)
    icon = crop.resize((dim, dim), Image.Resampling.LANCZOS)
    out = Image.new("RGBA", (dim, dim), (0, 0, 0, 255))
    out.paste(icon, (0, 0), icon)
    out.save(d / "ic_launcher.png", optimize=True)
    out.save(d / "ic_launcher_round.png", optimize=True)

print("ok", (drawable / "logo_app.png").stat().st_size)

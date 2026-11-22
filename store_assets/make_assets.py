"""生成 Google Play 上架素材：512x512 图标、1024x500 功能图、1080x1920 手机截图。

所有图形由 PIL 现场绘制，截图来自真机（Redmi 23049RAD8C / HyperOS, 1080x2400）。
"""
from __future__ import annotations

import os
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.abspath(__file__))
RAW = os.path.join(ROOT, "raw")
OUT = os.path.join(ROOT, "out")
GLYPH = os.path.join(RAW, "auto_glyph.png")

ACCENT = (76, 111, 255)
ACCENT_DEEP = (56, 83, 224)
INK = (15, 18, 24)
SUB = (107, 114, 128)
LINE = (226, 231, 242)

F_ZH_B = "C:/Windows/Fonts/msyhbd.ttc"
F_ZH = "C:/Windows/Fonts/msyh.ttc"
F_EN_B = "C:/Windows/Fonts/segoeuib.ttf"
F_EN = "C:/Windows/Fonts/segoeui.ttf"


def font(path: str, size: int) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(path, size)


def fit(path: str, text: str, size: int, max_w: int) -> ImageFont.FreeTypeFont:
    while size > 12:
        f = font(path, size)
        if f.getlength(text) <= max_w:
            return f
        size -= 2
    return font(path, size)


def wrap(draw: ImageDraw.ImageDraw, text: str, f, max_w: int) -> list[str]:
    if f.getlength(text) <= max_w:
        return [text]
    # 中文按字切，英文按词切
    if any("\u4e00" <= ch <= "\u9fff" for ch in text):
        lines, cur = [], ""
        for ch in text:
            if f.getlength(cur + ch) > max_w:
                lines.append(cur)
                cur = ch
            else:
                cur += ch
        lines.append(cur)
        return lines
    words, lines, cur = text.split(" "), [], ""
    for w in words:
        trial = (cur + " " + w).strip()
        if f.getlength(trial) > max_w:
            lines.append(cur)
            cur = w
        else:
            cur = trial
    lines.append(cur)
    return lines


def vertical_gradient(size, top, bottom) -> Image.Image:
    w, h = size
    grad = Image.new("RGB", (1, h))
    px = grad.load()
    for y in range(h):
        t = y / max(1, h - 1)
        px[0, y] = tuple(round(top[i] + (bottom[i] - top[i]) * t) for i in range(3))
    return grad.resize((w, h), Image.BICUBIC)


def diagonal_gradient(size, c0, c1) -> Image.Image:
    w, h = size
    small = Image.new("RGB", (64, 64))
    px = small.load()
    for y in range(64):
        for x in range(64):
            t = (x / 63 * 0.55 + y / 63 * 0.45)
            px[x, y] = tuple(round(c0[i] + (c1[i] - c0[i]) * t) for i in range(3))
    return small.resize((w, h), Image.BICUBIC)


def radial_glow(size, center, radius, color, strength=1.0) -> Image.Image:
    w, h = size
    layer = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(layer)
    d.ellipse(
        [center[0] - radius, center[1] - radius, center[0] + radius, center[1] + radius],
        fill=round(255 * strength),
    )
    layer = layer.filter(ImageFilter.GaussianBlur(radius * 0.55))
    glow = Image.new("RGB", (w, h), color)
    return glow, layer


def rounded_mask(size, radius) -> Image.Image:
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, size[0] - 1, size[1] - 1], radius=radius, fill=255)
    return m


def drop_shadow(size, radius, blur, opacity, spread=0) -> Image.Image:
    w, h = size
    pad = blur * 3
    canvas = Image.new("L", (w + pad * 2, h + pad * 2), 0)
    ImageDraw.Draw(canvas).rounded_rectangle(
        [pad - spread, pad - spread, pad + w + spread, pad + h + spread],
        radius=radius + spread,
        fill=opacity,
    )
    return canvas.filter(ImageFilter.GaussianBlur(blur))


# ---------------------------------------------------------------- icon


def draw_glyph(k_box: int = 2048) -> Image.Image:
    """把 Material screen_rotation 的透明底图案居中缩放到 k_box 画布。"""
    glyph = Image.open(GLYPH).convert("RGBA")
    side = round(k_box * 0.58)
    glyph = glyph.resize((side, side), Image.LANCZOS)
    layer = Image.new("RGBA", (k_box, k_box), (0, 0, 0, 0))
    layer.alpha_composite(glyph, ((k_box - side) // 2, (k_box - side) // 2))
    return layer


def make_icon(size=512) -> Image.Image:
    S = size * 4
    bg = diagonal_gradient((S, S), (96, 124, 255), (54, 78, 214))
    glow, mask = radial_glow((S, S), (S * 0.26, S * 0.18), S * 0.6, (255, 255, 255), 0.22)
    bg = Image.composite(glow, bg, mask.point(lambda v: int(v * 0.55)))
    glyph = draw_glyph(S)
    bg = Image.alpha_composite(bg.convert("RGBA"), glyph)
    return bg.resize((size, size), Image.LANCZOS).convert("RGBA")  # Play 要求 32-bit PNG


# ---------------------------------------------------------------- screenshots


STATUS_BAR = 100


def load_shot(name: str, crop: bool = True) -> Image.Image:
    img = Image.open(os.path.join(RAW, name)).convert("RGB")
    if not crop:
        return img
    return img.crop((0, STATUS_BAR, img.width, img.height))


def clean_status_bar(img: Image.Image, height: int = STATUS_BAR) -> Image.Image:
    """用状态栏下方的底色盖住状态栏（保留布局，只抹掉时钟/网速等图标）。"""
    out = img.copy()
    band = out.crop((0, height, out.width, height + 8))
    fill = band.resize((1, 1), Image.BOX).getpixel((0, 0))
    ImageDraw.Draw(out).rectangle([0, 0, out.width, height - 1], fill=fill)
    return out


def place_phone(canvas: Image.Image, shot: Image.Image, x: int, y: int, width: int, bezel=6, radius=None):
    h = round(shot.height * width / shot.width)
    if radius is None:
        radius = max(18, round((h + bezel * 2) * 0.072))
    out_w, out_h = width + bezel * 2, h + bezel * 2
    shot_r = shot.resize((width, h), Image.LANCZOS)

    shadow = drop_shadow((out_w, out_h), radius + bezel, blur=38, opacity=110, spread=2)
    pad = 38 * 3
    canvas.paste(Image.new("RGB", shadow.size, (86, 104, 158)), (x - bezel - pad, y - bezel - pad + 26), shadow)

    frame = Image.new("RGBA", (out_w, out_h), (0, 0, 0, 0))
    ImageDraw.Draw(frame).rounded_rectangle([0, 0, out_w - 1, out_h - 1], radius=radius + bezel, fill=(13, 16, 24, 255))
    inner = shot_r.convert("RGBA")
    inner.putalpha(rounded_mask((width, h), radius))
    frame.alpha_composite(inner, (bezel, bezel))
    outline = Image.new("RGBA", (out_w, out_h), (0, 0, 0, 0))
    ImageDraw.Draw(outline).rounded_rectangle(
        [0, 0, out_w - 1, out_h - 1], radius=radius + bezel, outline=(255, 255, 255, 40), width=2
    )
    frame = Image.alpha_composite(frame, outline)
    canvas.paste(frame, (x - bezel, y - bezel), frame)


SCREEN_W, SCREEN_H = 1080, 1920


def screenshot_canvas(copy: dict, shot_name: str) -> Image.Image:
    base = vertical_gradient((SCREEN_W, SCREEN_H), (250, 251, 255), (230, 235, 248))
    glow, mask = radial_glow((SCREEN_W, SCREEN_H), (SCREEN_W * 0.5, 1180), 760, ACCENT, 0.16)
    base = Image.composite(glow, base, mask)
    d = ImageDraw.Draw(base)

    zh = copy["lang"] == "zh"
    eyebrow_font = font(F_ZH, 26) if zh else font(F_EN, 26)
    label = copy["eyebrow"]
    # 字距拉开一点，读起来更像标签
    x = 80
    for ch in label:
        d.text((x, 78), ch, font=eyebrow_font, fill=ACCENT)
        x += eyebrow_font.getlength(ch) + 3

    head_font = fit(F_ZH_B if zh else F_EN_B, copy["head"], 64 if zh else 58, SCREEN_W - 160)
    d.text((80, 120), copy["head"], font=head_font, fill=INK)

    sub_font = font(F_ZH if zh else F_EN, 30)
    lines = wrap(d, copy["sub"], sub_font, SCREEN_W - 160)
    y = 120 + head_font.size + 26
    for ln in lines[:2]:
        d.text((80, y), ln, font=sub_font, fill=SUB)
        y += 44

    shot = load_shot(shot_name)
    place_phone(base, shot, x=(SCREEN_W - 800) // 2, y=372, width=800)
    return base


# ---------------------------------------------------------------- feature graphic

FG_W, FG_H = 1024, 500


def feature_graphic(copy: dict) -> Image.Image:
    zh = copy["lang"] == "zh"
    bg = diagonal_gradient((FG_W, FG_H), (13, 18, 48), (31, 45, 132))
    glow, mask = radial_glow((FG_W, FG_H), (FG_W * 0.78, FG_H * 0.4), 420, ACCENT, 0.55)
    bg = Image.composite(glow, bg, mask)
    base = bg.convert("RGBA")

    # 背景里的大号 Material 图案，作为品牌符号
    mark = draw_glyph(420)
    mark.putalpha(mark.getchannel("A").point(lambda v: round(v * 0.10)))
    base.alpha_composite(mark, (FG_W - 420 - 4, (FG_H - 420) // 2))

    icon = make_icon(96).convert("RGBA")
    icon.putalpha(rounded_mask((96, 96), 24))
    base.alpha_composite(icon, (64, 96))

    d = ImageDraw.Draw(base)
    name_font = font(F_ZH_B if zh else F_EN_B, 44)
    d.text((64, 208), copy["name"], font=name_font, fill=(255, 255, 255))

    tag_font = font(F_ZH if zh else F_EN, 25)
    d.text((64, 270), copy["tag"], font=tag_font, fill=(186, 197, 236))

    feat_font = font(F_ZH if zh else F_EN, 21)
    y = 324
    for item in copy["feats"]:
        d.ellipse([66, y + 9, 78, y + 21], fill=(126, 152, 255))
        d.text((96, y), item, font=feat_font, fill=(214, 222, 248))
        y += 42

    shot = load_shot("landscape/en.png" if not zh else "landscape/zh.png", crop=False)
    # 状态栏里有一堆系统图标，缩到 400px 宽后只剩噪点，用底色盖掉
    shot = clean_status_bar(shot)
    target_w = 400
    h = round(shot.height * target_w / shot.width)
    ph = Image.new("RGB", (1, 1))
    place_phone(base, shot, x=574, y=252 - h // 2, width=target_w, bezel=4, radius=20)
    return base.convert("RGB")


# ---------------------------------------------------------------- copy

SHOTS = {
    "zh": [
        ("portrait/zh/01_main.png", {
            "lang": "zh", "eyebrow": "01 / 方向锁定",
            "head": "一个界面，五个方向",
            "sub": "自动加四向锁定，摄像头圆点就是方向锚点",
        }),
        ("portrait/zh/02_ball.png", {
            "lang": "zh", "eyebrow": "02 / 悬浮球",
            "head": "悬浮球，随处换向",
            "sub": "点开是罗盘式控制盘，不用回到应用",
        }),
        ("portrait/zh/04_dark.png", {
            "lang": "zh", "eyebrow": "03 / 外观",
            "head": "深浅色，跟着系统走",
            "sub": "夜间深底不刺眼，中英界面应用内直接切换",
        }),
    ],
    "en": [
        ("portrait/en/01_main.png", {
            "lang": "en", "eyebrow": "01 / ORIENTATION",
            "head": "Five directions, one screen",
            "sub": "Auto plus four locked directions, the camera dot marks the way",
        }),
        ("portrait/en/02_ball.png", {
            "lang": "en", "eyebrow": "02 / FLOATING BALL",
            "head": "Switch from any screen",
            "sub": "Tap the ball for a five-way compass dial",
        }),
        ("portrait/en/03_dark.png", {
            "lang": "en", "eyebrow": "03 / APPEARANCE",
            "head": "Light or dark, follows you",
            "sub": "Follows the system, switches in-app, bilingual by design",
        }),
    ],
}

FEATURE = {
    "zh": {
        "lang": "zh", "name": "屏幕旋转", "tag": "无 Root 锁定屏幕方向",
        "feats": ["五个方向，摄像头圆点标朝向", "通知栏 / 悬浮球 / 磁贴三处切换", "深浅色跟随系统 · 中英双语"],
    },
    "en": {
        "lang": "en", "name": "Screen Rotation", "tag": "Lock any orientation, no root",
        "feats": ["Five directions, camera-dot icons", "Bar, floating ball or Quick Settings tile", "Light & dark · English & Chinese"],
    },
}


def main():
    os.makedirs(os.path.join(OUT, "screenshots"), exist_ok=True)
    make_icon(512).save(os.path.join(OUT, "icon-512.png"))
    make_icon(1024).save(os.path.join(OUT, "icon-1024.png"))
    for lang in ("zh", "en"):
        feature_graphic(FEATURE[lang]).save(os.path.join(OUT, f"feature-graphic-1024x500-{lang}.png"))
        folder = os.path.join(OUT, "screenshots", lang)
        os.makedirs(folder, exist_ok=True)
        for i, (shot, copy) in enumerate(SHOTS[lang], start=1):
            screenshot_canvas(copy, shot).save(os.path.join(folder, f"{i:02d}.png"))
    print("done")


if __name__ == "__main__":
    main()

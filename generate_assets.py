import os
from PIL import Image, ImageDraw, ImageFont

def get_font(size, is_bold=False):
    fonts = [
        "/System/Library/Fonts/HelveticaNeue.ttc",
        "/System/Library/Fonts/Avenir Next.ttc",
        "/Library/Fonts/Arial.ttf"
    ]
    for font_path in fonts:
        if os.path.exists(font_path):
            try:
                index = 2 if is_bold and "HelveticaNeue" in font_path else 0
                return ImageFont.truetype(font_path, size, index=index)
            except:
                pass
    return ImageFont.load_default()

def create_master_icon():
    size = 1024
    img = Image.new('RGBA', (size, size), (20, 20, 22, 255))
    draw = ImageDraw.Draw(img)
    
    center_x, center_y = size // 2, size // 2 - 100
    r = 200
    
    # Left circle (Cyan)
    draw.ellipse([center_x - r - 60, center_y - r, center_x + r - 60, center_y + r], outline=(80, 210, 230, 255), width=16)
    # Right circle (Coral)
    draw.ellipse([center_x - r + 60, center_y - r, center_x + r + 60, center_y + r], outline=(250, 110, 120, 255), width=16)
    
    # Text
    font = get_font(110, is_bold=False)
    text = "VividOrbit"
    
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w = bbox[2] - bbox[0]
    
    draw.text((center_x - text_w // 2, center_y + 260), text, font=font, fill=(245, 245, 250, 255))
    
    # Save
    os.makedirs("app/src/main/assets/brand", exist_ok=True)
    img.save("app/src/main/assets/brand/app_icon.png")
    
    # Export downscaled versions for mipmaps and drawables
    img.resize((192, 192), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
    img.resize((144, 144), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-xxhdpi/ic_launcher.png")
    img.resize((96, 96), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-xhdpi/ic_launcher.png")
    img.resize((72, 72), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-hdpi/ic_launcher.png")
    img.resize((48, 48), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-mdpi/ic_launcher.png")
    
    img.resize((192, 192), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-xxxhdpi/ic_launcher.png")
    img.resize((144, 144), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-xxhdpi/ic_launcher.png")
    img.resize((96, 96), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-xhdpi/ic_launcher.png")
    img.resize((72, 72), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-hdpi/ic_launcher.png")
    img.resize((48, 48), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-mdpi/ic_launcher.png")
    
    img.resize((512, 512), Image.Resampling.LANCZOS).save("app/src/main/res/drawable/logo.png")
    img.resize((512, 512), Image.Resampling.LANCZOS).save("app/src/main/res/drawable-night/logo.png")
    
    return img

def create_tv_banner():
    width, height = 1280, 720
    img = Image.new('RGBA', (width, height), (20, 20, 22, 255))
    draw = ImageDraw.Draw(img)
    
    center_x, center_y = width // 2, height // 2 - 60
    r = 160
    
    draw.ellipse([center_x - r - 50, center_y - r, center_x + r - 50, center_y + r], outline=(80, 210, 230, 255), width=12)
    draw.ellipse([center_x - r + 50, center_y - r, center_x + r + 50, center_y + r], outline=(250, 110, 120, 255), width=12)
    
    font = get_font(90, is_bold=False)
    text = "VividOrbit"
    
    bbox = draw.textbbox((0, 0), text, font=font)
    text_w = bbox[2] - bbox[0]
    
    draw.text((center_x - text_w // 2, center_y + 200), text, font=font, fill=(245, 245, 250, 255))
    
    img.save("app/src/main/assets/brand/tv_banner_master.png")
    
    banner_tv = img.resize((320, 180), Image.Resampling.LANCZOS)
    banner_tv.save("app/src/main/res/drawable/banner.png")
    banner_tv.save("app/src/main/res/drawable-night/banner.png")
    
    return img

if __name__ == "__main__":
    create_master_icon()
    create_tv_banner()
    print("Minimalistic premium brand assets generated successfully.")

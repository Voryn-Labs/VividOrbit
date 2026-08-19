import os
from PIL import Image

def process():
    input_path = "/Users/naman/.gemini/antigravity/brain/75a18360-6fd5-48e2-b619-605271f6b18d/vividorbit_minimal_premium_logo_1787161929964.jpg"
    if not os.path.exists(input_path):
        print("Image not found")
        return
        
    img = Image.open(input_path).convert("RGBA")
    
    # 1. App Icon (1024x1024)
    # The generated image is already 1:1, just resize
    icon = img.resize((1024, 1024), Image.Resampling.LANCZOS)
    os.makedirs("app/src/main/assets/brand", exist_ok=True)
    icon.save("app/src/main/assets/brand/app_icon.png")
    
    # Export downscaled versions for mipmaps and drawables
    icon.resize((192, 192), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-xxxhdpi/ic_launcher.png")
    icon.resize((144, 144), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-xxhdpi/ic_launcher.png")
    icon.resize((96, 96), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-xhdpi/ic_launcher.png")
    icon.resize((72, 72), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-hdpi/ic_launcher.png")
    icon.resize((48, 48), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-mdpi/ic_launcher.png")
    
    icon.resize((192, 192), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-xxxhdpi/ic_launcher.png")
    icon.resize((144, 144), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-xxhdpi/ic_launcher.png")
    icon.resize((96, 96), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-xhdpi/ic_launcher.png")
    icon.resize((72, 72), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-hdpi/ic_launcher.png")
    icon.resize((48, 48), Image.Resampling.LANCZOS).save("app/src/main/res/mipmap-night-mdpi/ic_launcher.png")
    
    icon.resize((512, 512), Image.Resampling.LANCZOS).save("app/src/main/res/drawable/logo.png")
    icon.resize((512, 512), Image.Resampling.LANCZOS).save("app/src/main/res/drawable-night/logo.png")
    
    # 2. TV Banner (1280x720) 16:9 crop
    # Center crop the 1:1 image to 16:9
    w, h = img.size
    new_h = w * 9 // 16
    top = (h - new_h) // 2
    bottom = top + new_h
    banner = img.crop((0, top, w, bottom))
    banner = banner.resize((1280, 720), Image.Resampling.LANCZOS)
    banner.save("app/src/main/assets/brand/tv_banner_master.png")
    
    banner_tv = banner.resize((320, 180), Image.Resampling.LANCZOS)
    banner_tv.save("app/src/main/res/drawable/banner.png")
    banner_tv.save("app/src/main/res/drawable-night/banner.png")
    
    print("Logo processed successfully.")

if __name__ == "__main__":
    process()

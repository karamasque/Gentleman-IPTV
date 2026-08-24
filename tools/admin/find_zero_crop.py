#!/usr/bin/env python3
# -*- coding: utf-8 -*-
from PIL import Image
from test_mask_clipping import create_masks

fg_img = Image.open(r"D:\Masaüstü\KaynanamTV-IPTV\app\src\main\res\drawable\ic_launcher_foreground_img.png").convert("RGBA")
bbox = fg_img.getbbox()
logo_tight = fg_img.crop(bbox)
tw, th = logo_tight.size
masks = create_masks(512)

for target_dim in range(250, 290, 5):
    scale = target_dim / max(tw, th)
    nw, nh = int(tw * scale), int(th * scale)
    res_logo = logo_tight.resize((nw, nh), Image.Resampling.LANCZOS)
    
    test_canvas = Image.new("RGBA", (512, 512), (0, 0, 0, 0))
    pos_x = (512 - nw) // 2
    pos_y = (512 - nh) // 2
    test_canvas.paste(res_logo, (pos_x, pos_y), res_logo)
    
    circle_clip = 0
    for y in range(512):
        for x in range(512):
            if test_canvas.getpixel((x, y))[3] > 40 and masks["CIRCLE"].getpixel((x, y)) == 0:
                circle_clip += 1
    print(f"Target size: {nw}x{nh} px -> Circle clip: {circle_clip} px")

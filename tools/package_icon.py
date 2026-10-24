"""Render adaptive mask and themed previews without changing the icon artwork."""
from pathlib import Path
from PIL import Image, ImageDraw
root=Path(__file__).resolve().parents[1]
im=Image.open(root/'app/src/main/res/drawable-nodpi/ic_launcher_foreground.png').convert('RGBA')
mono=Image.open(root/'app/src/main/res/drawable-nodpi/ic_launcher_monochrome.png').convert('RGBA')
colors=[('#E7E0F8',None),('#E8DEF8','#494458'),('#D3E8D2','#29432C'),('#F2E0B6','#514628')]
sheet=Image.new('RGB',(768,232),'#FAF8FD')
for i,(bg,fg) in enumerate(colors):
    icon=Image.new('RGBA',(432,432),bg)
    top=im.copy()
    if fg:
        top=Image.new('RGBA',mono.size,fg); top.putalpha(mono.getchannel('A'))
    icon.alpha_composite(top)
    # Android's visible viewport is 72dp in its 108dp layer.
    icon=icon.crop((72,72,360,360)).resize((160,160),Image.Resampling.LANCZOS)
    mask=Image.new('L',(160,160)); draw=ImageDraw.Draw(mask)
    if i==0: draw.rounded_rectangle((0,0,159,159),radius=44,fill=255)
    else: draw.ellipse((0,0,159,159),fill=255)
    sheet.paste(icon,(16+192*i,20),mask)
    label=['Adaptive','Lavender','Green','Sand'][i]
    ImageDraw.Draw(sheet).text((16+192*i,194),label,fill='#393540')
sheet.save(root/'design/icon-preview.png')

"""Build the sample and studio IBL, package the generated icon and fetch Material Symbols."""
from pathlib import Path
import json, math, struct, urllib.request, concurrent.futures, re
from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / 'app/src/main/res'
ASSETS = ROOT / 'app/src/main/assets'

def fetch(url, dest):
    dest = ROOT / dest
    dest.parent.mkdir(parents=True, exist_ok=True)
    if not dest.exists():
        urllib.request.urlretrieve(url, dest)
    print(dest.relative_to(ROOT), dest.stat().st_size, flush=True)

icons = ['folder_open','view_in_ar','settings','arrow_back','more_vert','info','tune',
         'restart_alt','play_arrow','pause','close','add','remove','chevron_left',
         'chevron_right','keyboard_arrow_up','keyboard_arrow_down','light_mode',
         'dark_mode','check','delete','open_in_full','fullscreen_exit','language']

def icon(name):
    url = f'https://raw.githubusercontent.com/google/material-design-icons/master/symbols/android/{name}/materialsymbolsrounded/{name}_24px.xml'
    fetch(url, f'app/src/main/res/drawable/ic_{name}.xml')

with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
    list(pool.map(icon, icons))

# Compose supplies semantic role colors; the upstream Views tint depends on AppCompat.
for p in (RES/'drawable').glob('ic_*.xml'):
    p.write_text(re.sub(r'\s+android:tint="\?attr/colorControlNormal"','',p.read_text(encoding='utf-8')),encoding='utf-8')

nav = RES / 'drawable/ic_chevron_right.xml'
(RES / 'drawable/ic_navigate_next.xml').write_text(nav.read_text(encoding='utf-8').replace('android:autoMirrored="false"','android:autoMirrored="true"'),encoding='utf-8')
for name in ['chevron_left','chevron_right']:
    p=RES/f'drawable/ic_{name}.xml'
    p.write_text(p.read_text(encoding='utf-8').replace('android:autoMirrored="true"','android:autoMirrored="false"'),encoding='utf-8')

# Original prefiltered studio cubemap: broad neutral softboxes, progressively diffused mip levels.
ASSETS.mkdir(parents=True,exist_ok=True)
ktx=bytearray(b'\xABKTX 11\xBB\r\n\x1A\n')
ktx.extend(struct.pack('<13I',0x04030201,0x140B,2,0x1908,0x881A,0x1908,64,64,0,0,6,7,0))
for level in range(7):
    dim=64>>level
    ktx.extend(struct.pack('<I',dim*dim*8))
    for face in range(6):
        for y in range(dim):
            for x in range(dim):
                u=2*(x+.5)/dim-1; v=2*(y+.5)/dim-1
                d=[(1,-v,-u),(-1,-v,u),(u,1,v),(u,-1,-v),(u,-v,1),(-u,-v,-1)][face]
                d=norm(d) if 'norm' in globals() else tuple(c/math.sqrt(sum(k*k for k in d)) for c in d)
                sky=.3+.35*max(d[1],0)
                softbox=sum(max(0,sum(d[k]*light[k] for k in range(3)))**max(1,20/(level+1)**2)*power for light,power in [((.4,.8,.447),3.5),((-.8,.3,-.52),1.8)])
                val=sky+softbox
                ktx.extend(struct.pack('<4e',val*.97,val*.98,val,1))
(ASSETS/'studio_ibl.ktx').write_bytes(ktx)

source = ROOT / 'design/app-icon-gpt-image.png'
out = ROOT / 'design'
out.mkdir(exist_ok=True)
im = Image.open(source).convert('RGBA')
im.save(out / 'app-icon-gpt-image.png')
# Density conversion only: preserve the generated art and its alpha as separate adaptive layers.
dest = RES / 'drawable-nodpi'
dest.mkdir(parents=True, exist_ok=True)
im.resize((432, 432), Image.Resampling.LANCZOS).save(dest / 'ic_launcher_foreground.png')
mono = Image.new('RGBA', im.size, (255,255,255,0))
mono.putalpha(im.getchannel('A'))
mono.resize((432,432), Image.Resampling.LANCZOS).save(dest / 'ic_launcher_monochrome.png')

# Original small PBR sample: a closed (2,3) torus knot with smooth normals and a rotation clip.
def knot(t):
    r = 1.6 + .55 * math.cos(3*t)
    return (r*math.cos(2*t), .55*math.sin(3*t), r*math.sin(2*t))
def norm(a):
    l=math.sqrt(sum(v*v for v in a)); return tuple(v/l for v in a)
def cross(a,b): return (a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0])
pos=[]; normals=[]; indices=[]
N=240; M=24
for i in range(N+1):
    t=2*math.pi*i/N; c=knot(t); c2=knot(t+.0001)
    tangent=norm(tuple(c2[k]-c[k] for k in range(3)))
    n=norm(cross(tangent,(0,1,0))); b=cross(tangent,n)
    for j in range(M+1):
        a=2*math.pi*j/M
        v=tuple(n[k]*math.cos(a)+b[k]*math.sin(a) for k in range(3))
        normals.extend(v); pos.extend(c[k]+.29*v[k] for k in range(3))
for i in range(N):
    for j in range(M):
        a=i*(M+1)+j; b=a+M+1
        indices.extend([a,a+1,b,a+1,b+1,b])
chunks=[]; views=[]; accessors=[]
def accessor(data,fmt,typ,component,count,minimum=None,maximum=None):
    offset=sum(len(c) for c in chunks)
    raw=struct.pack('<'+fmt*len(data),*data); raw+=b'\0'*((-len(raw))%4)
    chunks.append(raw)
    views.append({'buffer':0,'byteOffset':offset,'byteLength':len(raw)})
    acc={'bufferView':len(views)-1,'componentType':component,'count':count,'type':typ}
    if minimum is not None: acc['min']=minimum; acc['max']=maximum
    accessors.append(acc); return len(accessors)-1
p=accessor(pos,'f','VEC3',5126,len(pos)//3,[min(pos[k::3]) for k in range(3)],[max(pos[k::3]) for k in range(3)])
n=accessor(normals,'f','VEC3',5126,len(normals)//3)
ix=accessor(indices,'H','SCALAR',5123,len(indices))
times=[0.,4.,8.,12.,16.]
rot=[]
for a in [0,math.pi/2,math.pi,3*math.pi/2,2*math.pi]: rot.extend([0,math.sin(a/2),0,math.cos(a/2)])
ta=accessor(times,'f','SCALAR',5126,5,[0],[16]); ra=accessor(rot,'f','VEC4',5126,5)
glb={'asset':{'version':'2.0','generator':'ThreeViewer original sample'},
     'scene':0,'scenes':[{'nodes':[0]}],'nodes':[{'mesh':0,'name':'Knot'}],
     'meshes':[{'primitives':[{'attributes':{'POSITION':p,'NORMAL':n},'indices':ix,'material':0}]}],
     'materials':[{'name':'Porcelain','pbrMetallicRoughness':{'baseColorFactor':[.42,.36,.75,1],'metallicFactor':.3,'roughnessFactor':.26}}],
     'animations':[{'name':'Turntable','samplers':[{'input':ta,'output':ra,'interpolation':'LINEAR'}],'channels':[{'sampler':0,'target':{'node':0,'path':'rotation'}}]}],
     'buffers':[{'byteLength':sum(len(c) for c in chunks)}],'bufferViews':views,'accessors':accessors}
j=json.dumps(glb,separators=(',',':')).encode(); j+=b' '*((-len(j))%4); binary=b''.join(chunks)
ASSETS.mkdir(parents=True,exist_ok=True)
(ASSETS/'knot.glb').write_bytes(struct.pack('<III',0x46546c67,2,12+8+len(j)+8+len(binary))+struct.pack('<II',len(j),0x4e4f534a)+j+struct.pack('<II',len(binary),0x004e4942)+binary)
print('Icon layers and knot.glb ready')

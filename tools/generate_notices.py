"""Regenerate offline notices from resolved Maven artifacts and pinned upstream sources.

Run ./gradlew :app:writeRuntimeDependencies first. Generated assets are committed
so opening the license screen and ordinary builds never require network access.
"""
from pathlib import Path
from concurrent.futures import ThreadPoolExecutor
from urllib.request import urlopen
import json, re, zipfile, xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'app/src/main/assets/licenses'
CACHE=Path.home()/'.gradle/caches/modules-2/files-2.1'
FILAMENT='https://raw.githubusercontent.com/google/filament/v1.77.3/'

def get(url):
    with urlopen(url,timeout=40) as response:
        return response.read().decode('utf-8',errors='replace')

def maven_license(coordinate):
    group,name,version=coordinate.split(':')
    directory=CACHE/group/name/version
    poms=list(directory.glob('**/*.pom'))
    if poms: text=poms[0].read_text(encoding='utf-8')
    else:
        base='https://dl.google.com/dl/android/maven2/' if group.startswith('androidx.') else 'https://repo.maven.apache.org/maven2/'
        text=get(base+group.replace('.','/')+f'/{name}/{version}/{name}-{version}.pom')
    pom=ET.fromstring(text)
    licenses=[n.text or '' for n in pom.findall('.//{*}licenses/{*}license/{*}name')]
    # Some POMs inherit their license from their parent. Only known Apache families are allowed.
    assert all('apache' in name.lower() for name in licenses), (coordinate,licenses)
    assert group.startswith(('androidx.','org.jetbrains.kotlin','com.google.android.filament')) or group in ('org.jetbrains','org.jspecify','com.google.guava'), coordinate
    extras=[]
    for artifact in list(directory.glob('**/*.jar'))+list(directory.glob('**/*.aar')):
        with zipfile.ZipFile(artifact) as z:
            for member in z.namelist():
                if re.fullmatch(r'(META-INF/)?(LICENSE|NOTICE|COPYING)(\.txt)?',member,re.I):
                    extras.append((coordinate+' / '+member,z.read(member).decode('utf-8',errors='replace')))
    return coordinate,licenses or ['Apache License 2.0 (upstream family)'],extras

def main():
    OUT.mkdir(parents=True,exist_ok=True)
    apache=(ROOT/'LICENSE').read_text(encoding='utf-8')
    (OUT/'apache-2.0.txt').write_bytes((ROOT/'LICENSE').read_bytes())
    (OUT/'threeviewer.txt').write_text((ROOT/'NOTICE').read_text(encoding='utf-8')+'\n\n'+apache,encoding='utf-8')
    coordinates=[line for line in (ROOT/'app/build/reports/runtime-dependencies.txt').read_text().splitlines() if not line.startswith('ThreeViewer:')]
    rows=list(ThreadPoolExecutor(12).map(maven_license,coordinates))
    groups={
        'androidx':('AndroidX','The Android Open Source Project',lambda c:c.startswith('androidx.')),
        'kotlin':('Kotlin & kotlinx','JetBrains and Kotlin contributors',lambda c:c.startswith('org.jetbrains')),
        'support':('Guava & JSpecify','Google Inc. and The JSpecify Authors',lambda c:c.startswith(('com.google.guava:','org.jspecify:')))
    }
    index=[dict(id='threeviewer',title='ThreeViewer',license='Apache-2.0',file='threeviewer.txt')]
    for key,(title,authors,predicate) in groups.items():
        selected=[row for row in rows if predicate(row[0])]
        sections=[title+'\nCopyright '+authors,'Resolved components:\n'+'\n'.join(row[0] for row in selected),apache]
        seen=set()
        for _,_,extras in selected:
            for label,text in extras:
                if text not in seen: sections.append(label+'\n\n'+text); seen.add(text)
        (OUT/(key+'.txt')).write_text('\n\n'.join(sections),encoding='utf-8')
        index.append(dict(id=key,title=title,license='Apache-2.0',file=key+'.txt'))
    tree_path=ROOT/'.tools/filament-tree.json'
    if tree_path.exists(): tree=json.loads(tree_path.read_text())['tree']
    else: tree=json.loads(get('https://api.github.com/repos/google/filament/git/trees/v1.77.3?recursive=1'))['tree']
    # Preserve upstream notices for Filament's vendored native libraries, including optional backends.
    paths=sorted({entry['path'] for entry in tree if entry['type']=='blob' and (
        re.fullmatch(r'third_party/[^/]+/(LICENSE|COPYING)(\.[a-z0-9]+)?',entry['path'],re.I)
        or entry['path'] in ['third_party/basisu/zstd/LICENSE','filament/src/materials/fsr/license.txt',
            'filament/src/materials/sgsr/LICENSE.txt','filament/backend/src/webgpu/SpdMipmapGenerator/license.txt',
            'third_party/libwebp/AUTHORS','third_party/libwebp/PATENTS'])})
    native=list(ThreadPoolExecutor(12).map(lambda path:(path,get(FILAMENT+path)),paths))
    for header in ['third_party/mikktspace/include/mikktspace/mikktspace.h',
                   'third_party/perfetto/perfetto/perfetto.h']:
        match=re.search(r'/\*.*?\*/',get(FILAMENT+header),re.S)
        assert match, 'Missing native copyright header: '+header
        native.append((header,match.group()))
    sections=['Filament 1.77.3\nCopyright The Android Open Source Project',apache,
        'Resolved components:\n'+'\n'.join(c for c in coordinates if c.startswith('com.google.android.filament:'))]
    for path,text in native: sections.append(FILAMENT+path+'\n\n'+text)
    (OUT/'filament.txt').write_text('\n\n'.join(sections),encoding='utf-8')
    index.append(dict(id='filament',title='Filament',license='Apache-2.0 · BSD · MIT · Zlib',file='filament.txt'))
    (OUT/'symbols.txt').write_text('Material Symbols\nCopyright Google LLC\nhttps://github.com/google/material-design-icons\n\nModified for Compose tint and physical camera direction controls.\n\n'+apache,encoding='utf-8')
    index.append(dict(id='symbols',title='Material Symbols',license='Apache-2.0',file='symbols.txt'))
    (OUT/'index.json').write_text(json.dumps(index,indent=2)+'\n',encoding='utf-8')
    (OUT/'runtime-dependencies.txt').write_text('\n'.join(coordinates)+'\n',encoding='utf-8')
    (OUT/'provenance.json').write_text(json.dumps({'maven':[{ 'coordinate':c,'licenses':l } for c,l,_ in rows],
        'filamentTag':'v1.77.3','filamentSources':[p for p,_ in native]},indent=2)+'\n',encoding='utf-8')
    print(f'Generated {len(index)} offline entries; {len(rows)} resolved Maven components; {len(native)} native notice sources.')

if __name__=='__main__': main()

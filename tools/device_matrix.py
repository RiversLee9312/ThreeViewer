from pathlib import Path
import re,subprocess,sys,time,os
import xml.etree.ElementTree as E
from device import adb, screenshot
ROOT=Path(__file__).resolve().parents[1]
OUT=Path(os.environ.get('THREEVIEWER_VERIFICATION_DIR',str(ROOT/'verification')))
OUT.mkdir(parents=True,exist_ok=True)
RUNNER='dev.threeviewer.test/androidx.test.runner.AndroidJUnitRunner'
BASELINE=adb('shell','settings','get','system','font_scale').decode().strip()
def locale(value=None):
    args=['shell','cmd','locale','set-app-locales','dev.threeviewer']
    if value: args+=['--locales',value]
    adb(*args)
def test(name,method=None):
    args=['shell','am','instrument','-w']
    if method: args+=['-e','class','dev.threeviewer.ViewerAccessibilityTest#'+method]
    result=adb(*args,RUNNER).decode('utf-8',errors='replace').replace('\r','')
    (OUT/(name+'.log')).write_text(result,encoding='utf-8')
    match=re.search(r'OK \(\d+ tests?\)',result)
    print(name,match.group() if match else 'FAILED',flush=True)
    if not match: raise RuntimeError('Device tests failed: '+name)
def launch(): adb('shell','am','start','-W','-n','dev.threeviewer/.MainActivity')
def capture(name):
    adb('shell','uiautomator','dump','/sdcard/window.xml')
    xml=adb('shell','cat','/sdcard/window.xml')
    (OUT/(name+'.xml')).write_bytes(xml)
    (OUT/(name+'.png')).write_bytes(screenshot())
try:
    adb('install','-r',str(ROOT/'app/build/outputs/apk/debug/app-debug.apk'))
    adb('install','-r',str(ROOT/'app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk'))
    locale(); adb('shell','settings','put','system','font_scale','1.0')
    test('tests-standard')
    locale('de'); adb('shell','settings','put','system','font_scale','2.0')
    adb('shell','am','force-stop','dev.threeviewer')
    time.sleep(2) # Allow the system's locale/configuration restart to finish before instrumentation.
    test('tests-de-200','homeAndSettingsHaveAccessibleControls')
    launch(); capture('de-200-home-final')
    subprocess.run([sys.executable,str(ROOT/'tools/interact.py'),'content-desc','Einstellungen'],check=True)
    capture('de-200-settings-final')
    locale('ar'); adb('shell','settings','put','system','font_scale','1.0')
    adb('shell','am','force-stop','dev.threeviewer')
    time.sleep(2)
    test('tests-ar-rtl','homeAndSettingsHaveAccessibleControls')
    launch(); capture('ar-home-final')
finally:
    adb('shell','settings','put','system','font_scale',BASELINE)
    locale()
    adb('shell','am','force-stop','dev.threeviewer')
    print('Restored font scale',BASELINE,'and app language to system default.',flush=True)

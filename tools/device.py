"""Small ADB helper for repeatable on-device screenshots and inspection."""
from pathlib import Path
import subprocess, sys, os, re
ADB = str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe')
SERIAL = os.environ.get('ANDROID_SERIAL')
def adb(*args):
    target=['-s',SERIAL] if SERIAL else ['-d']
    return subprocess.run([ADB,*target,*args],capture_output=True,check=True).stdout

def screenshot():
    # OnePlus can expose a secondary mirror; select the first physical HWC display.
    displays=adb('shell','dumpsys','SurfaceFlinger','--display-id').decode()
    match=re.search(r'Display (\d+) \(HWC display 0\)',displays)
    args=['exec-out','screencap','-p']
    if match: args+=['-d',match.group(1)]
    data=adb(*args)
    return data[data.index(b'\x89PNG\r\n\x1a\n'):]
if __name__ == '__main__':
    action=sys.argv[1]
    if action=='screenshot':
        dest=Path(sys.argv[2]); dest.parent.mkdir(parents=True,exist_ok=True)
        dest.write_bytes(screenshot()); print(dest.resolve())
    elif action=='dump':
        adb('shell','uiautomator','dump','/sdcard/window.xml')
        print(adb('shell','cat','/sdcard/window.xml').decode('utf-8'))
    elif action=='tap': print(adb('shell','input','tap',sys.argv[2],sys.argv[3]).decode())
    else: print(adb(*sys.argv[1:]).decode('utf-8',errors='replace'))

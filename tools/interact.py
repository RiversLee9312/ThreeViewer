from device import adb
import xml.etree.ElementTree as E
import re,sys
adb('shell','uiautomator','dump','/sdcard/window.xml')
root=E.fromstring(adb('shell','cat','/sdcard/window.xml'))
attribute=sys.argv[1]; value=sys.argv[2]
for node in root.iter('node'):
    if node.get(attribute)==value:
        x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
        adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
        print(value,node.get('bounds'));break
else:
    print('No matching UI element:',attribute,value)
    sys.exit(1)

"""Two already-running Android emulators connected to Firebase Emulator Suite."""
import concurrent.futures, json, re, subprocess, sys, time, xml.etree.ElementTree as ET
from pathlib import Path
ADB = '/home/sam/Android/Sdk/platform-tools/adb'
CLOUD = '--cloud' in sys.argv
OUT = Path(__file__).resolve().parents[2] / 'docs' / 'livepulse-evidence' / ('cloud' if CLOUD else 'local')
OUT.mkdir(parents=True, exist_ok=True)
A, B = 'emulator-5554', 'emulator-5556'
def adb(device, *args):
    return subprocess.check_output([ADB, '-s', device, *args], timeout=30)
def tree(device):
    adb(device, 'shell', 'uiautomator', 'dump', '/sdcard/livepulse-test.xml')
    return ET.fromstring(adb(device, 'shell', 'cat', '/sdcard/livepulse-test.xml'))
def texts(root): return [n.get('text','') for n in root.iter('node') if n.get('text')]
def tap(device, node):
    x1,y1,x2,y2=map(int,re.findall(r'\d+',node.get('bounds')))
    adb(device,'shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
def send(device, text):
    node=next(n for n in tree(device).iter('node') if n.get('class')=='android.widget.EditText')
    tap(device,node)
    adb(device,'shell','input','text',text.replace(' ','%s'))
    node=next(n for n in tree(device).iter('node') if n.get('content-desc')=='Send')
    tap(device,node)
def wait_text(device, text):
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        root=tree(device)
        if text in texts(root):return root
    raise AssertionError(f'{device}: message missing: {text}')
for device in [A,B]: adb(device,'shell','am','start','-n','kz.semey.livepulse/.MainActivity')
roots={}
for d in [A,B]:
    deadline=time.monotonic()+30
    while time.monotonic()<deadline:
        root=tree(d)
        if any(' · Connected' in t for t in texts(root)):
            roots[d]=root;break
    assert d in roots, texts(root)
uids={d:next(t.split(' · ')[0] for t in texts(r) if ' · Connected' in t) for d,r in roots.items()}
assert uids[A]!=uids[B], uids
stamp=str(int(time.time()))
first='Hello from emulator A '+stamp
second='Reply from emulator B '+stamp
send(A,first);wait_text(B,first)
send(B,second);wait_text(A,second)
for device in [A,B]:
    root=wait_text(device,second)
    assert 'You' in texts(root)
    (OUT/f'{device}.xml').write_bytes(ET.tostring(root))
    (OUT/f'{device}.png').write_bytes(adb(device,'exec-out','screencap','-p'))
# A process restart must reuse the already stored anonymous session.
adb(A,'shell','am','force-stop','kz.semey.livepulse')
adb(A,'shell','am','start','-n','kz.semey.livepulse/.MainActivity')
root=wait_text(A,first)
assert any(t.startswith(uids[A]+' · ') for t in texts(root))
report={'result':'PASS','devices':[A,B],'uids':uids,'checks':['distinct anonymous sessions','A to B realtime without refresh','B to A realtime without refresh','own message cards on both devices','session and messages restored after process restart'],'messages':[first,second],'backend':'cloud semey-map Firebase Auth and Firestore' if CLOUD else 'local demo-livepulse Firebase Auth and Firestore emulators'}
(OUT/'android-smoke.json').write_text(json.dumps(report,indent=2))
print(json.dumps(report,indent=2))

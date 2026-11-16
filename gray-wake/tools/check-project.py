#!/usr/bin/env python3
"""Offline structural checks. Android lint, APK builds and device tests are separate."""
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
xmls = sorted((ROOT / 'app/src/main').rglob('*.xml'))
for path in xmls:
    ET.parse(path)
manifest = ET.parse(ROOT / 'app/src/main/AndroidManifest.xml').getroot()
permissions = {x.attrib[ANDROID + 'name'] for x in manifest.findall('uses-permission')}
expected = {
    'android.permission.WRITE_SECURE_SETTINGS',
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_SPECIAL_USE',
    'android.permission.RECEIVE_BOOT_COMPLETED',
    'android.permission.POST_NOTIFICATIONS',
}
assert permissions == expected, permissions
application = manifest.find('application')
assert application is not None
assert application.attrib[ANDROID + 'allowBackup'] == 'false'
components = {x.attrib.get(ANDROID + 'name'): x for x in application}
for name, tag in components.items():
    assert (ROOT / 'app/src/main/java/dev/graywake' / (name.lstrip('.') + '.java')).exists(), name
    assert ANDROID + 'exported' in tag.attrib, name
service = components['.MonitorService']
assert service.attrib[ANDROID + 'exported'] == 'false'
assert service.attrib[ANDROID + 'foregroundServiceType'] == 'specialUse'
assert service.attrib[ANDROID + 'directBootAware'] == 'true'
assert service.find('property') is not None
assert components['.GrayTileService'].attrib[ANDROID + 'permission'] == 'android.permission.BIND_QUICK_SETTINGS_TILE'
assert components['.BootReceiver'].attrib[ANDROID + 'exported'] == 'false'
assert 'android.intent.action.SCREEN_ON' not in (ROOT / 'app/src/main/AndroidManifest.xml').read_text()
strings = {x.attrib['name'] for x in ET.parse(ROOT / 'app/src/main/res/values/strings.xml').getroot()}
drawables = {p.stem for p in (ROOT / 'app/src/main/res/drawable').glob('*.xml')}
java_files = sorted((ROOT / 'app/src').rglob('*.java'))
for path in java_files:
    source = path.read_text()
    for name in re.findall(r'R\.string\.(\w+)', source):
        assert name in strings, (path, 'missing string', name)
    for name in re.findall(r'R\.drawable\.(\w+)', source):
        assert name in drawables, (path, 'missing drawable', name)
for path in xmls:
    for name in re.findall(r'@string/(\w+)', path.read_text()):
        assert name in strings, (path, 'missing string', name)
assert 'applicationId \'dev.graywake\'' in (ROOT / 'app/build.gradle').read_text()
assert 'compileSdk 36' in (ROOT / 'app/build.gradle').read_text()
assert 'targetSdk 36' in (ROOT / 'app/build.gradle').read_text()
for shell in [ROOT / 'gradlew', *sorted((ROOT / 'tools').glob('*.sh'))]:
    subprocess.run(['sh', '-n', str(shell)], check=True)
subprocess.run(['java', str(ROOT / 'tools/ParseJava.java'), *map(str, java_files)], check=True)
print(f'PASS: {len(xmls)} XML files; resource references, manifest isolation, permissions and shell syntax')

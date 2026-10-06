#!/usr/bin/env python3
"""Install the signed distributable, launch it and verify coexistence if supplied."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

OUTPUT = Path('screenshots')
OUTPUT.mkdir(exist_ok=True)


def adb(*args, data=None):
    return subprocess.run(['adb', *args], input=data, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True, timeout=60).stdout


def shell(*args, data=None):
    return adb('shell', shlex.join(args), data=data)


def capture(name):
    (OUTPUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))


def digest_entries(apk):
    with zipfile.ZipFile(apk) as archive:
        return {name: hashlib.sha256(archive.read(name)).hexdigest()
                for name in archive.namelist()
                if not name.startswith('META-INF/') and not name.endswith('/')}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('apk', type=Path)
    parser.add_argument('--unsigned-reference', type=Path)
    parser.add_argument('--legacy-apk', type=Path)
    args = parser.parse_args()
    expected = json.loads(Path('signing/release-certificate.json').read_text())
    package = expected['applicationId']
    sdk = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    verification = subprocess.check_output(
        [str(sdk / 'apksigner'), 'verify', '--verbose', '--print-certs',
         '--min-sdk-version', '26', str(args.apk)], text=True)
    cert = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', verification)
    if not cert or cert.group(1) != expected['sha256'].replace(':', '').lower():
        raise AssertionError('Signed APK: wrong release certificate')
    badging = subprocess.check_output(
        [str(sdk / 'aapt'), 'dump', 'badging', str(args.apk)], text=True)
    if not re.search(r"package: name='" + re.escape(package) + "'", badging):
        raise AssertionError('Signed APK: wrong package')
    activity = re.search(r"launchable-activity: name='([^']+)'", badging)
    if not activity:
        raise AssertionError('Signed APK: missing launcher activity')
    if args.unsigned_reference:
        if digest_entries(args.apk) != digest_entries(args.unsigned_reference):
            raise AssertionError('Signed APK does not match the production build contents')
        print('PASS: signed APK contents equal the production build, excluding signing metadata.')
    legacy = 'io.luma.reader'
    marker = None
    if args.legacy_apk:
        if package == legacy:
            raise AssertionError('Parallel installation requires a separate package')
        legacy_badging = subprocess.check_output(
            [str(sdk / 'aapt'), 'dump', 'badging', str(args.legacy_apk)], text=True)
        if "package: name='io.luma.reader'" not in legacy_badging:
            raise AssertionError('Wrong legacy fixture package')
        legacy_verified = subprocess.check_output(
            [str(sdk / 'apksigner'), 'verify', '--print-certs', str(args.legacy_apk)], text=True)
        if 'a25061d76d480e6e68eeb75d2fd823eb3657e6d8c50793ffcd090f6a4e1f8727' not in legacy_verified:
            raise AssertionError('Wrong legacy fixture certificate')
        adb('install', '-r', str(args.legacy_apk))
        shell('am', 'force-stop', legacy)
        shell('run-as', legacy, 'mkdir', '-p', 'files')
        marker = b'legacy-private-data-must-survive-nova-installation'
        shell('run-as', legacy, 'sh', '-c', 'cat > files/nova-coexistence-check.txt', data=marker)
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    for attempt in range(2):
        output = adb('install', '-r', str(args.apk)).decode()
        if 'Success' not in output:
            raise AssertionError('Signed APK install/update did not return Success: ' + output)
        shell('am', 'force-stop', package)
        result = shell('am', 'start', '-W', '-n', package + '/' + activity.group(1)).decode()
        if 'Error:' in result or 'Status: ok' not in result:
            raise AssertionError('Signed APK failed to launch: ' + result)
        deadline = time.monotonic() + 60
        while time.monotonic() < deadline:
            shell('uiautomator', 'dump', '/sdcard/nova-signed-install.xml')
            root = ET.fromstring(adb('exec-out', 'cat', '/sdcard/nova-signed-install.xml'))
            if any(n.get('package') == package and n.get('text') == 'Biblioteca'
                   for n in root.iter('node')):
                break
            time.sleep(0.5)
        else:
            raise AssertionError('Signed APK did not display its library')
        if marker:
            retained = shell('run-as', legacy, 'cat', 'files/nova-coexistence-check.txt')
            if retained != marker or not shell('pm', 'path', legacy).strip():
                raise AssertionError('Legacy application or its private data did not survive')
        print('PASS: signed APK ' + ('installation' if attempt == 0 else 'reinstallation/update') + ' and launch.')
    capture('release-firmado-biblioteca')
    report = {'applicationId': package, 'certificateSha256': cert.group(1),
              'apkSha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'installation': 'passed', 'reinstallation': 'passed', 'launch': 'passed',
              'legacyPrivateData': 'preserved' if marker else 'not checked',
              'productionContents': 'matched' if args.unsigned_reference else 'not checked'}
    (OUTPUT / 'signed-install-result.json').write_text(json.dumps(report, indent=2) + '\n')
    if marker:
        print('PASS: original io.luma.reader remains installed with its private data unchanged.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        capture('release-firmado-fallo')
        raise

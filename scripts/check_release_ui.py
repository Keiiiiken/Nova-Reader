#!/usr/bin/env python3
"""Exercise the normally optimized APK through Android's accessibility UI.

The debug APK is used only to seed an EPUB in the disposable emulator. The
unsigned distribution APK is then signed with that emulator's temporary debug
key and installed over it. No instrumentation runner or test ProGuard rules are
used while exercising the distribution APK.
"""
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from xml.sax.saxutils import escape

PACKAGE = 'io.luma.reader'
OUTPUT = Path('screenshots')
OUTPUT.mkdir(exist_ok=True)


def adb(*args, data=None):
    return subprocess.run(['adb', *args], input=data, stdout=subprocess.PIPE,
                          stderr=subprocess.PIPE, check=True, timeout=45).stdout


def shell(*args, data=None):
    return adb('shell', shlex.join(args), data=data)


def private_file(path, data):
    shell('run-as', PACKAGE, 'sh', '-c', 'cat > ' + shlex.quote(path), data=data)


def capture(name):
    (OUTPUT / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p'))


def window():
    shell('uiautomator', 'dump', '/sdcard/nova-release-smoke.xml')
    xml = adb('exec-out', 'cat', '/sdcard/nova-release-smoke.xml')
    (OUTPUT / 'release-smoke-last-window.xml').write_bytes(xml)
    return ET.fromstring(xml)


def text(node):
    return node.get('text', '') + ' ' + node.get('content-desc', '')


def wait_for(predicate, description):
    deadline = time.monotonic() + 60
    while time.monotonic() < deadline:
        root = window()
        value = predicate(root)
        if value is not None and value is not False:
            return value
        time.sleep(0.5)
    raise AssertionError('Release UI: ' + description)


def find(label):
    return wait_for(lambda root: next((n for n in root.iter('node')
                    if label in text(n)), None), 'missing ' + label)


def tap(node, long=False):
    values = [int(v) for v in re.findall(r'\d+', node.get('bounds', ''))]
    if len(values) != 4:
        raise AssertionError('Release UI: invalid bounds')
    x = str((values[0] + values[2]) // 2)
    y = str((values[1] + values[3]) // 2)
    if long:
        shell('input', 'swipe', x, y, x, y, '1200')
    else:
        shell('input', 'tap', x, y)


def seed():
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk')
    shell('am', 'force-stop', PACKAGE)
    root = shell('run-as', PACKAGE, 'pwd').decode().strip()
    directory = 'files/library/release-smoke'
    shell('run-as', PACKAGE, 'mkdir', '-p', directory + '/OPS/text', 'shared_prefs')
    private_file(directory + '/book.epub', Path('sample-book/El-jardin-de-la-luz.epub').read_bytes())
    for name in ['capitulo-1.xhtml', 'capitulo-2.xhtml']:
        private_file(directory + '/OPS/text/' + name, Path('sample-book/OPS/text/' + name).read_bytes())
    # The preceding instrumentation cleanup normally removed the database. Make
    # that explicit so this check also verifies migration in the distribution APK.
    shell('run-as', PACKAGE, 'rm', '-f', 'databases/nova-library.db',
          'databases/nova-library.db-wal', 'databases/nova-library.db-shm')
    book = dict(id='release-smoke', title='El jardín de la luz', author='Clara Montes',
                epubPath=root + '/' + directory + '/book.epub',
                contentPath=root + '/' + directory + '/OPS',
                chapterIndex=0, chapterProgress=0, progress=0,
                chapters=[dict(title=title, href='text/' + name, mediaType='application/xhtml+xml')
                          for title, name in [('La puerta verde', 'capitulo-1.xhtml'),
                                              ('El jardín', 'capitulo-2.xhtml')]])
    payload = json.dumps(dict(books=[book], collections=[], grid=True), ensure_ascii=False)
    prefs = '<map><string name="library_state_v1">' + escape(payload) + '</string></map>'
    private_file('shared_prefs/luma_library.xml', prefs.encode())


def install_distribution():
    sdk = Path(os.environ['ANDROID_HOME'])
    signer = sdk / 'build-tools/35.0.0/apksigner'
    # Android SDK installations can place the generated debug key outside HOME.
    # Resolve the configured key from AGP instead of assuming a filesystem path.
    report = subprocess.run(['./gradlew', '--no-daemon', '--max-workers=2', ':app:signingReport'],
                            text=True, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
                            check=True, timeout=180).stdout
    block = re.search(r'^Variant: debug\s*\n(.*?)(?=^Variant:|\Z)', report, re.MULTILINE | re.DOTALL)
    match = re.search(r'^Store: (.+)$', block.group(1), re.MULTILINE) if block else None
    if not match or not Path(match.group(1).strip()).is_file():
        raise AssertionError('AGP did not report the generated debug keystore')
    keystore = Path(match.group(1).strip())
    temporary = Path(os.environ.get('RUNNER_TEMP', '/tmp')) / 'nova-release-smoke.apk'
    try:
        subprocess.run([str(signer), 'sign', '--ks', str(keystore),
                        '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android',
                        '--key-pass', 'pass:android', '--out', str(temporary),
                        'app/build/outputs/apk/release/app-release-unsigned.apk'], check=True)
        adb('install', '-r', str(temporary))
    finally:
        temporary.unlink(missing_ok=True)


def main():
    seed()
    install_distribution()
    shell('input', 'keyevent', 'KEYCODE_WAKEUP')
    shell('wm', 'dismiss-keyguard')
    shell('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    book = find('El jardín de la luz')
    capture('release-biblioteca')
    tap(book, long=True)
    find('Cambiar portada')
    capture('release-opciones')
    shell('input', 'keyevent', 'BACK')
    wait_for(lambda root: not any('Cambiar portada' in text(n) for n in root.iter('node')),
             'book options did not close')
    tap(find('El jardín de la luz'))
    wait_for(lambda root: next((node for node in root.iter('node')
             if node.get('class') == 'android.webkit.WebView'
             and any('Emilia' in text(child) for child in node.iter('node'))), None),
             'the distribution WebView did not render EPUB text')
    capture('release-lector')
    # Reader controls hide after five seconds, including while accessibility
    # waits for WebView content. Show them before locating the tools button.
    root = window()
    if not any('Buscar y marcadores' in text(n) for n in root.iter('node')):
        view = next((n for n in root.iter('node')
                     if n.get('class') == 'android.webkit.WebView'), None)
        if view is None:
            raise AssertionError('Release UI: reader WebView disappeared')
        tap(view)
    tap(find('Buscar y marcadores'))
    find('Explorar el libro')
    capture('release-herramientas')
    print('Normal distribution APK: library migration, long press, EPUB rendering and reader tools passed.')


if __name__ == '__main__':
    try:
        main()
    except Exception:
        capture('release-fallo')
        raise

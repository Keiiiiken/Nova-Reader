#!/usr/bin/env python3
"""Sign with the permanent key, locally or via encrypted Actions secret."""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('input_apk', type=Path)
    parser.add_argument('output_apk', type=Path)
    parser.add_argument('--private-config', type=Path)
    args = parser.parse_args()
    certificate = json.loads(Path('signing/release-certificate.json').read_text())
    sdk = Path(os.environ['ANDROID_HOME'])
    signer = sdk / 'build-tools/35.0.0/apksigner'
    if args.private_config:
        credentials = json.loads(args.private_config.read_text())
        key_bytes = (args.private_config.parent / credentials['keystore']).read_bytes()
    else:
        encoded = os.environ.get('NOVA_SIGNING_BUNDLE')
        if not encoded:
            raise SystemExit('Missing NOVA_SIGNING_BUNDLE: APK will not be published unsigned.')
        credentials = json.loads(base64.b64decode(encoded.strip(), validate=True))
        key_bytes = base64.b64decode(credentials['keystoreBase64'], validate=True)
    environment = dict(os.environ,
                       NOVA_STORE_PASSWORD=credentials['storePassword'],
                       NOVA_KEY_PASSWORD=credentials['keyPassword'])
    # Never print passwords or pass them as command-line arguments.
    args.output_apk.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix='nova-signing-') as directory:
        key = Path(directory) / 'release.jks'
        key.write_bytes(key_bytes)
        key.chmod(0o600)
        try:
            subprocess.run([str(signer), 'sign', '--ks', str(key),
                            '--ks-key-alias', credentials['alias'],
                            '--ks-pass', 'env:NOVA_STORE_PASSWORD',
                            '--key-pass', 'env:NOVA_KEY_PASSWORD',
                            '--v1-signing-enabled', 'true',
                            '--v2-signing-enabled', 'true',
                            '--v3-signing-enabled', 'true',
                            '--v4-signing-enabled', 'false',
                            '--out', str(args.output_apk), str(args.input_apk)],
                           env=environment, check=True, capture_output=True)
            verification = subprocess.check_output(
                [str(signer), 'verify', '--verbose', '--print-certs',
                 '--min-sdk-version', '26', str(args.output_apk)], text=True)
            digest = re.search(r'Signer #1 certificate SHA-256 digest: (\w+)', verification)
            expected = certificate['sha256'].replace(':', '').lower()
            if not digest or digest.group(1).lower() != expected:
                raise RuntimeError('The APK does not use the permanent Nova Reader certificate')
            badging = subprocess.check_output(
                [str(sdk / 'build-tools/35.0.0/aapt'), 'dump', 'badging',
                 str(args.output_apk)], text=True)
            package = re.search(r"package: name='([^']+)'", badging)
            if not package or package.group(1) != certificate['applicationId']:
                raise RuntimeError('The APK has the wrong application identifier')
            subprocess.run([str(sdk / 'build-tools/35.0.0/zipalign'), '-c', '-P', '16',
                            '4', str(args.output_apk)], check=True, capture_output=True)
            print(verification)
            print('PASS: permanent certificate, application ID and APK alignment.')
        except Exception:
            args.output_apk.unlink(missing_ok=True)
            raise


if __name__ == '__main__':
    main()

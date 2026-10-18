import argparse
import hashlib
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('variant', type=int, choices=range(1, 6))
options = parser.parse_args()
target = Path('work') / f'WolFox-Source-{options.variant}'
target.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile('ci/wolfox-source-objects.zip') as archive:
    entries = json.loads(archive.read(f'manifests/{options.variant}.json'))
    for name, digest in entries.items():
        destination = target / name
        if not destination.resolve().is_relative_to(target.resolve()):
            raise ValueError('Invalid source path')
        data = archive.read('objects/' + digest)
        if hashlib.sha256(data).hexdigest() != digest:
            raise ValueError('Source checksum mismatch: ' + name)
        destination.parent.mkdir(parents=True, exist_ok=True)
        destination.write_bytes(data)
print(f'Restored variant {options.variant}: {len(entries)} verified files')

from pathlib import Path
import json
root = Path(__file__).resolve().parents[1]
config = json.loads((root / 'edition.json').read_text())
assert config['edition'] in ('gps', 'gps-bluetooth', 'gps-upload-identifier', 'full')
bluetooth = config['edition'] in ('gps-bluetooth', 'full')
media = config['edition'] in ('gps-upload-identifier', 'full')
source = (root / 'app/src/main/java/com/wolfox/gps/EditionConfig.java').read_text()
assert f'ID = "{config["edition"]}"' in source
assert f'HAS_BLUETOOTH = {str(bluetooth).lower()}' in source
assert f'HAS_MEDIA = {str(media).lower()}' in source
assert config['ios_repository'] == 'khalid003600-png/H'
assert config['ios_branch'].endswith('variant-' + config['edition'] + '-20261019')
build = (root / 'app/build.gradle').read_text()
assert f"applicationId '{config['application_id']}'" in build
assert (root / 'app/src/main/assets/wolfox/fonts/Cairo-Variable.ttf').stat().st_size > 10000
assert (root / 'app/src/main/assets/wolfox/fonts/Cairo-OFL.txt').is_file()
print('PASS: edition flags, application identity, iOS pairing and font assets')

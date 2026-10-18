#!/usr/bin/env python3
"""Compare raw Android Lint issues and report test outcomes without dropping warnings."""
from pathlib import Path
from collections import Counter
import argparse
import hashlib
import json
import xml.etree.ElementTree as ET


def read_issues(path):
    if not path.is_file():
        raise FileNotFoundError(path)
    return [
        {
            'id': issue.get('id'),
            'severity': issue.get('severity'),
            'message': issue.get('message'),
            'locations': [dict(location.attrib) for location in issue.findall('location')],
        }
        for issue in ET.parse(path).getroot().findall('issue')
    ]


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--project', type=Path, required=True)
    parser.add_argument('--baseline', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    project = args.project.resolve()
    baseline = read_issues(args.baseline)
    current = read_issues(project / 'app/build/reports/lint-results-debug.xml')
    totals = dict(tests=0, failures=0, errors=0, skipped=0)
    suites = []
    for path in sorted((project / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml')):
        suite = ET.parse(path).getroot()
        row = {'name': suite.get('name'), 'seconds': float(suite.get('time', '0'))}
        for key in totals:
            row[key] = int(suite.get(key, '0'))
            totals[key] += row[key]
        row['failures_detail'] = [
            {'test': test.get('name'), 'reason': failure.get('message'), 'trace': failure.text or ''}
            for test in suite.findall('testcase')
            for failure in list(test.findall('failure')) + list(test.findall('error'))
        ]
        suites.append(row)
    assert totals['tests'] > 0, 'No executed test report found'
    result = {
        'project': str(project),
        'test_totals': totals,
        'suites': suites,
        'baseline_issue_count': len(baseline),
        'baseline_by_id': dict(Counter(row['id'] for row in baseline)),
        'current_issue_count': len(current),
        'current_by_id': dict(Counter(row['id'] for row in current)),
        'current_by_severity': dict(Counter(row['severity'] for row in current)),
        'remaining_issues': current,
        'apk_files': [
            {'path': str(path), 'bytes': path.stat().st_size,
             'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
            for path in sorted((project / 'build/local/apk').glob('*_SIGNED.apk'))
        ],
        'limits': [
            'Robolectric tests are local simulations, not physical-device frame measurements.',
            'Raw warnings are retained; no zero-warning guarantee is inferred from successful tests.',
            'No host APK or production activation server is executed by this report.',
        ],
    }
    args.output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')
    print(json.dumps({key: value for key, value in result.items()
                      if key not in ('suites', 'remaining_issues')}, ensure_ascii=False, indent=2))
    if totals['failures'] or totals['errors']:
        raise SystemExit(1)
    if any(row['severity'] in ('Error', 'Fatal') for row in current):
        raise SystemExit(2)


if __name__ == '__main__':
    main()

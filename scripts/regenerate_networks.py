#!/usr/bin/env python3
"""Regenerate the 45 networks of the study from the design files and compare them with data/runs-llm.

    python scripts/regenerate_networks.py [--out work/runs-llm] [--jar snrm-netbench/target/netbench.jar] [--skip-10000]

Runs `netbench batch` for every design file that was used (the three first-campaign designs, the three
second-campaign designs and the four scale-extension designs), merges the indexes into one index.csv and
checks every regenerated file against data/runs-llm.sha256 (facts.jsonl and network.full.json of the
two 10,000-node networks are not shipped, so they are regenerated but not compared). Generation is
deterministic: every file must match. The two 10,000-node networks take several minutes each.
"""
import argparse, csv, hashlib, os, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DESIGNS = ['design-main.json', 'design-template.json', 'design-noise.json',
           'design-grid2.json', 'design-grid2-main500.json', 'design-grid2-template500.json',
           'design-grid3-1000-main.json', 'design-grid3-1000-adv.json', 'design-grid3-10000-main.json', 'design-grid3-10000-adv.json']
ap = argparse.ArgumentParser()
ap.add_argument('--out', default=os.path.join(ROOT, 'work', 'runs-llm'))
ap.add_argument('--jar', default=None, help='netbench jar (default: snrm-netbench/target/netbench.jar, else snrm-netbench/dist/netbench-0.1.0.jar)')
ap.add_argument('--skip-10000', action='store_true', help='leave out the two 10,000-node designs')
a = ap.parse_args()
if a.jar is None:
    built = os.path.join(ROOT, 'snrm-netbench', 'target', 'netbench.jar')
    a.jar = built if os.path.exists(built) else os.path.join(ROOT, 'snrm-netbench', 'dist', 'netbench-0.1.0.jar')

os.makedirs(a.out, exist_ok=True)
rows, header = [], None
for name in DESIGNS:
    if a.skip_10000 and '10000' in name:
        continue
    tmp = os.path.join(a.out, '_' + name.replace('.json', ''))
    if os.path.exists(tmp):
        shutil.rmtree(tmp)
    r = subprocess.run(['java', '-jar', a.jar, 'batch', '--design', os.path.join(ROOT, 'design', name), '--out-dir', tmp], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout[-1500:], r.stderr[-1500:]); sys.exit(f'batch failed for {name}')
    print(r.stdout.strip().split('\n')[0])
    with open(os.path.join(tmp, 'index.csv'), encoding='utf-8') as f:
        rd = csv.reader(f); h = next(rd); header = header or h
        for row in rd:
            if row[1] == 'generated':
                rows.append(row)
                src = os.path.join(tmp, row[0]); dst = os.path.join(a.out, row[0])
                if os.path.exists(dst):
                    shutil.rmtree(dst)
                shutil.move(src, dst)
    shutil.rmtree(tmp)
with open(os.path.join(a.out, 'index.csv'), 'w', encoding='utf-8', newline='\n') as f:
    w = csv.writer(f, lineterminator='\n'); w.writerow(header); w.writerows(sorted(rows, key=lambda r: r[0]))
print(f'{len(rows)} networks regenerated into {a.out}')

# compare with the shipped checksums
expected = {}
for line in open(os.path.join(ROOT, 'data', 'runs-llm.sha256'), encoding='utf-8'):
    h, p = line.rstrip('\n').split('  ', 1)
    if p.endswith('index.csv'):
        continue                      # the merged index lists the campaigns in a different row order
    expected[p[len('runs-llm/'):]] = h
ok = bad = missing = 0
for rel, h in sorted(expected.items()):
    p = os.path.join(a.out, rel)
    if not os.path.exists(p):
        missing += 1; continue
    if hashlib.sha256(open(p, 'rb').read()).hexdigest() == h:
        ok += 1
    else:
        bad += 1; print('DIFFERS', rel)
print(f'checksums: {ok} identical, {bad} different, {missing} not regenerated')

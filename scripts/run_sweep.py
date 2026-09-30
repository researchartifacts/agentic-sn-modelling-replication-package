#!/usr/bin/env python3
"""Reproducibility sweep of the instrument (Section 3.5 of the paper).

    python scripts/run_sweep.py [--design design/sweep-design.json] [--out work/sweep] [--jar snrm-netbench/target/netbench.jar]

1. Generates the design twice, into two folders A and B.
2. Compares every file of every generated network between A and B byte for byte (SHA-256).
3. Lays B's truths out as candidates of a pseudo-model "rulebased-B" and verifies them against A's truths
   with `netbench verify-batch`, so that every scored metric of the checker is exercised on a perfect
   candidate.
4. Writes sweep-index.csv (A's index), sweep-results.csv (the verify-batch table) and sweep-summary.json.

The study's run of this script over design/sweep-design.json (345 cells: 23 node levels x 5 echelon
counts x 3 seeds) is in data/sweep/: 315 feasible cells, 3,150 identical files, 315 perfect verifications.
The full sweep takes a few minutes; a smaller design file gives a quick check.
"""
import argparse, csv, hashlib, json, os, shutil, subprocess, sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument('--design', default=os.path.join(ROOT, 'design', 'sweep-design.json'))
ap.add_argument('--out', default=os.path.join(ROOT, 'work', 'sweep'))
ap.add_argument('--jar', default=None, help='netbench jar (default: snrm-netbench/target/netbench.jar, else snrm-netbench/dist/netbench-0.1.0.jar)')
a = ap.parse_args()
if a.jar is None:
    built = os.path.join(ROOT, 'snrm-netbench', 'target', 'netbench.jar')
    a.jar = built if os.path.exists(built) else os.path.join(ROOT, 'snrm-netbench', 'dist', 'netbench-0.1.0.jar')

def netbench(*args):
    cmd = ['java', '-jar', a.jar] + list(args)
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode not in (0,):
        print(r.stdout[-2000:], r.stderr[-2000:]); sys.exit(f'netbench exited {r.returncode}: {" ".join(args[:2])}')
    return r.stdout

os.makedirs(a.out, exist_ok=True)
A = os.path.join(a.out, 'A'); B = os.path.join(a.out, 'B')
for d in (A, B):
    if os.path.exists(d):
        shutil.rmtree(d)
    print(netbench('batch', '--design', a.design, '--out-dir', d).strip().split('\n')[0])

# 1. byte comparison
index = list(csv.DictReader(open(os.path.join(A, 'index.csv'), encoding='utf-8')))
generated = [r for r in index if r['status'] == 'generated']
def sha(p):
    return hashlib.sha256(open(p, 'rb').read()).hexdigest()
compared = differing = 0
for r in generated:
    rid = r['run_id']
    for dirpath, _, names in os.walk(os.path.join(A, rid)):
        for name in names:
            pa = os.path.join(dirpath, name); pb = os.path.join(B, os.path.relpath(pa, A))
            compared += 1
            if not os.path.exists(pb) or sha(pa) != sha(pb):
                differing += 1; print('DIFFERS', os.path.relpath(pa, A))
print(f'files compared {compared}, differing {differing}')

# 2. verify B's truth as a candidate against A's truth
resp = os.path.join(a.out, 'responses-B')
if os.path.exists(resp):
    shutil.rmtree(resp)
for r in generated:
    rid = r['run_id']
    shutil.copytree(os.path.join(B, rid, 'truth'), os.path.join(resp, 'rulebased-B', rid, '1', 'candidate'))
results = os.path.join(a.out, 'sweep-results.csv')
netbench('verify-batch', '--runs', A, '--responses', resp, '--out', results)
rows = list(csv.DictReader(open(results, encoding='utf-8')))
perfect = sum(1 for r in rows if r['status'] == 'scored' and float(r['import_valid']) == 1 and float(r['fact_recall']) == 1
              and float(r['fact_precision']) == 1 and float(r['structure_f1']) == 1)
shutil.copy2(os.path.join(A, 'index.csv'), os.path.join(a.out, 'sweep-index.csv'))
summary = {'design': os.path.basename(a.design), 'cells': len(index), 'generated': len(generated), 'skipped_infeasible': len(index) - len(generated),
           'files_compared': compared, 'files_differing': differing, 'verify_rows': len(rows), 'perfect_rows': perfect, 'all_perfect': perfect == len(rows)}
json.dump(summary, open(os.path.join(a.out, 'sweep-summary.json'), 'w'), indent=1)
print(json.dumps(summary, indent=1))

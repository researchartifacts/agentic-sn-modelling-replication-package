#!/usr/bin/env python3
"""Write the soft-wrapped copies of the descriptions that the agents read (data/descriptions/<run-id>/description.md).

    python scripts/prepare_descriptions.py [--width 200]

The generator writes each description as long lines; the agents were given a copy wrapped at 200
characters on word boundaries (the equivalent of `fold -s -w 200`). Wrapping changes only where line
breaks fall inside a paragraph; the text and its tokens are unchanged. The truth folders are not copied,
so this directory can be exposed to an agent while data/runs-llm stays out of reach.
"""
import argparse, os, textwrap

ap = argparse.ArgumentParser()
ap.add_argument('--width', type=int, default=200)
ap.add_argument('--root', default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
a = ap.parse_args()

def fold(text, width):
    """`fold -s -w width`: break each line at the last blank before the width; never join lines."""
    out = []
    for line in text.split('\n'):
        while len(line) > width:
            cut = line.rfind(' ', 0, width)
            if cut <= 0:
                cut = width
            out.append(line[:cut + 1] if line[cut:cut + 1] == ' ' else line[:cut])
            line = line[cut + 1:] if line[cut:cut + 1] == ' ' else line[cut:]
        out.append(line)
    return '\n'.join(out)

runs = os.path.join(a.root, 'data', 'runs-llm'); dst = os.path.join(a.root, 'data', 'descriptions')
n = 0
for rid in sorted(os.listdir(runs)):
    src = os.path.join(runs, rid, 'description', 'description.md')
    if not os.path.exists(src):
        continue
    os.makedirs(os.path.join(dst, rid), exist_ok=True)
    text = open(src, encoding='utf-8').read()
    with open(os.path.join(dst, rid, 'description.md'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(fold(text, a.width))
    n += 1
print(f'wrote {n} wrapped descriptions to {dst}')

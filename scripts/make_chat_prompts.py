#!/usr/bin/env python3
"""Write the ready-to-paste prompts for a chat-interface model (docs/CHAT_INTERFACE_PROTOCOL.md).

    python scripts/make_chat_prompts.py [--out work/chat-prompts]

One file per network listed in protocol/chat-interface-run-list.csv: the instruction file (v1), a
separator, and the network description exactly as generated. The 10,000-node networks are not listed:
their descriptions (5 to 6 MB) exceed the input limit of chat interfaces.
"""
import argparse, csv, os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ap = argparse.ArgumentParser()
ap.add_argument('--out', default=os.path.join(ROOT, 'work', 'chat-prompts'))
a = ap.parse_args()

SEPARATOR = '\n---\n\n# Document\n\nThe document to extract follows. Apply the instructions above to it and return exactly the five CSV blocks.\n\n'
instr = open(os.path.join(ROOT, 'protocol', 'llm-instructions-v1.md'), encoding='utf-8').read()
os.makedirs(a.out, exist_ok=True)
n = 0
for row in csv.DictReader(open(os.path.join(ROOT, 'protocol', 'chat-interface-run-list.csv'), encoding='utf-8')):
    rid = row['run_id']
    desc = open(os.path.join(ROOT, 'data', 'runs-llm', rid, 'description', 'description.md'), encoding='utf-8').read()
    with open(os.path.join(a.out, rid + '.md'), 'w', encoding='utf-8', newline='\n') as f:
        f.write(instr + SEPARATOR + desc)
    n += 1
print(f'wrote {n} prompt files to {a.out}')

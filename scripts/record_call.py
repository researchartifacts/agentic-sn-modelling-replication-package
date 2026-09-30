#!/usr/bin/env python3
"""Merge the harness-side metadata of one agent run into its call.json (docs/AGENT_PROTOCOL.md, step 5).

    python scripts/record_call.py <model-alias> <run-id> <tokens> <duration_ms> <tool_uses> [rep] [--failed "<reason>"]

The agent itself writes model_alias, reported_model, effort, prompt_version, files_read and tools_used
into call.json; this script adds what only the harness knows (tokens, wall-clock duration, tool-use
count, recording time) and lists the candidate files present. For an attempt that ended without any
file, pass --failed with the harness's error message: the record then carries status "failed" and
verify-batch scores the attempt as no_candidate.
"""
import json, os, sys, datetime, argparse

ap = argparse.ArgumentParser()
ap.add_argument('model'); ap.add_argument('run_id'); ap.add_argument('tokens'); ap.add_argument('duration_ms'); ap.add_argument('tool_uses')
ap.add_argument('rep', nargs='?', default='1'); ap.add_argument('--failed', default=None)
ap.add_argument('--root', default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
a = ap.parse_args()

d = os.path.join(a.root, 'data', 'responses', a.model, a.run_id, a.rep)
os.makedirs(d, exist_ok=True)
p = os.path.join(d, 'call.json')
j = {}
if os.path.exists(p):
    try:
        j = json.load(open(p, encoding='utf-8'))
    except Exception:
        j = {'agent_call_json_unparsable': True}
j.update({
    'model_alias': a.model,
    'harness': 'Claude Cowork subagent (Agent tool), file-writing mode, tools limited by instruction to Read/Write',
    'recorded_at': datetime.datetime.now(datetime.timezone.utc).isoformat(timespec='seconds'),
})
if a.failed:
    j.update({'status': 'failed', 'failure': a.failed, 'subagent_tokens': None, 'duration_ms': None, 'tool_uses': None})
else:
    j.update({'subagent_tokens': int(a.tokens), 'duration_ms': int(a.duration_ms), 'tool_uses': int(a.tool_uses)})
cand = os.path.join(d, 'candidate')
j['candidate_files'] = sorted(os.listdir(cand)) if os.path.isdir(cand) else []
json.dump(j, open(p, 'w', encoding='utf-8'), indent=1)
print(a.run_id, a.model, a.rep, j['candidate_files'], a.tokens, a.duration_ms)

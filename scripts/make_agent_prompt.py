#!/usr/bin/env python3
"""Print the prompt that launches one agentic extraction run (docs/AGENT_PROTOCOL.md).

    python scripts/make_agent_prompt.py <model-alias> <run-id> [--rep N] [--version v1|v1.1|v1.2] [--root DIR]

The prompt names two files the agent may read (the instruction file and the network description)
and the directory it must write to (data/responses/<model>/<run-id>/<rep>/). Paths are printed as
absolute paths of the package checkout so that the agent's file tools resolve them.
Three prompt versions were used in the study (Section 3.4 of the paper); v1 is the instruction file
alone, v1.1 adds the one-file-per-tool-call sentence, v1.2 adds the brief, incremental-reasoning sentence.
"""
import argparse, os

RULES = {
    'v1':   "Rules: do not list, search or read any other file or directory; do not run any program, script or shell command; "
            "do not use the web. Use only the Read and Write tools. Follow the instruction file exactly. Do the extraction yourself "
            "from the document text; do not write code to parse it. If the document is long, read it in parts with the Read tool's "
            "offset/limit and write each CSV file once, complete.",
    'v1.1': "Rules: do not list, search or read any other file or directory; do not run any program, script or shell command; "
            "do not use the web. Use only the Read and Write tools. Follow the instruction file exactly. Do the extraction yourself "
            "from the document text; do not write code to parse it. If the document is long, read it in parts with the Read tool's "
            "offset/limit. Write each of the five CSV files in a separate Write tool call, and write each file once, complete; never "
            "split one file across calls and never leave a file for later.",
    'v1.2': "Rules: do not list, search or read any other file or directory; do not run any program, script or shell command; "
            "do not use the web. Use only the Read and Write tools. Follow the instruction file exactly. Do the extraction yourself "
            "from the document text; do not write code to parse it. If the document is long, read it in parts with the Read tool's "
            "offset/limit. Write each of the five CSV files in a separate Write tool call, and write each file once, complete; never "
            "split one file across calls and never leave a file for later. Work incrementally and keep your reasoning short: do not "
            "restate or enumerate the document in your reasoning before writing. Write network_meta.csv and products.csv first, then "
            "nodes.csv, then links.csv, then node_products.csv; before each file, re-read only the part of the document that file needs "
            "(with offset/limit) and write the file straight away.",
}
LABEL = {'v1': None, 'v1.1': 'v1.1 (instruction file v1 + one file per tool call)',
         'v1.2': 'v1.2 (instruction file v1 + one file per tool call + incremental, brief reasoning)'}

ap = argparse.ArgumentParser()
ap.add_argument('model'); ap.add_argument('run_id')
ap.add_argument('--rep', default='1'); ap.add_argument('--version', default='v1.2', choices=list(RULES))
ap.add_argument('--root', default=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
a = ap.parse_args()
root = os.path.abspath(a.root)
instr = os.path.join(root, 'protocol', 'llm-instructions-v1.md')
desc = os.path.join(root, 'data', 'descriptions', a.run_id, 'description.md')      # the soft-wrapped copy (scripts/prepare_descriptions.py)
base = os.path.join(root, 'data', 'responses', a.model, a.run_id, a.rep)
label = f',"prompt_version":"{LABEL[a.version]}"' if LABEL[a.version] else ''
print(f"""Controlled extraction benchmark — run {a.run_id}, model alias {a.model}.

Read exactly two files and nothing else:
1. {instr}  (the task instructions)
2. {desc}  (the document to extract from)

{RULES[a.version]}

Output: instead of replying with fenced blocks, write the five CSV files (exactly the content the instruction file asks for, header row first) to:
{base}/candidate/network_meta.csv
{base}/candidate/nodes.csv
{base}/candidate/links.csv
{base}/candidate/products.csv
{base}/candidate/node_products.csv
Then write {base}/call.json containing:
{{"model_alias":"{a.model}","reported_model":"<the model name or identifier stated in your own system prompt, or 'unknown'>","effort":"default (not configurable by the harness)"{label},"files_read":["<every file path you read>"],"tools_used":["<tool names you used>"]}}

Final reply (short): the number of data rows written to each CSV file, the reported_model value, and the files you read. Do not include CSV content in the reply.""")

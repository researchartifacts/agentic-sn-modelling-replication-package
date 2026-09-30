# Replication package: How Accurately Do Large Language Models Extract Supply Networks from Text? A Controlled Benchmark

This repository holds everything needed to reproduce the study reported in the paper *How Accurately Do Large Language
Models Extract Supply Networks from Text? A Controlled Benchmark* (under double-blind review; author details are withheld
from this repository until the review is complete).

The study asks how accurately current LLMs turn a textual description of a multi-echelon supply network into a
simulation-ready model, and how that accuracy changes with network size, depth, description style and distracting text.
A rule-based generator (`snrm-netbench`) writes a network in the five-file CSV import schema of the open-source Supply
Network Resilience Modelling (SNRM) tool together with a natural-language description of the same object, so that every
fact an LLM extracts can be scored against an exact ground truth. Three Claude models (Haiku 4.5, Sonnet 5, Opus 5.5) were
run as isolated agents on 41 networks of 6 to 500 nodes and four extension networks of 1,000 and 10,000 nodes.

The package contains the instrument (source and a prebuilt jar), the design files, every generated network with its ground
truth, every model response with its harness record and checker report, the scored results, the reproducibility sweep,
and the scripts and protocols that produced them. Everything generated can be regenerated from `design/`, and every
attempt can be re-scored from `data/`. The tables, figures and derived numbers of the paper are not part of the package;
they are computed from `data/results.csv` and the `report.json` files with the definitions in `docs/DATA_DICTIONARY.md`.

## Contents

| Path | What it holds |
|---|---|
| `snrm-netbench/` | The instrument: Java 21 source (`src/`), tests, `pom.xml`, the SNRM sample networks the tests use (`reference/samples/`), three golden example runs (`examples/`), the instruction file (`llm-instructions/v1.md`) and a prebuilt jar (`dist/netbench-0.1.0.jar`). `snrm-netbench/README.md` documents every command. |
| `design/` | The `netbench batch` design files that generated the networks: first campaign (`design-main.json`, `design-template.json`, `design-noise.json`), second campaign (`design-grid2*.json`), scale extension (`design-grid3-*.json`) and the reproducibility sweep (`sweep-design.json`). |
| `protocol/` | `llm-instructions-v1.md`, the instruction file every model received (identical to `snrm-netbench/llm-instructions/v1.md`), and `chat-interface-run-list.csv` for the chat-interface protocol. |
| `data/runs-llm/` | The 45 generated networks: `description/description.md` (A, what the model reads), `truth/*.csv` (B, the five SNRM files), `facts.jsonl` (every scored fact), `decoys.jsonl` (every adversarial sentence), `network.full.json`, `manifest.json`; `index.csv` lists them with their parameters and counts. The two 10,000-node networks are shipped without `facts.jsonl` and `network.full.json` (21 MB and 10 MB each); `scripts/regenerate_networks.py` recreates them. |
| `data/responses/` | Every model attempt: `<model>/<run-id>/<rep>/candidate/*.csv` (C, the files the agent wrote), `call.json` (harness record: tokens, duration, tool uses, prompt version, self-reported model, deviations), `report.json` (the checker's report). `regex-extract/` is the rule-based baseline on the seven template networks. Failed attempts have a `call.json` with `"status": "failed"` and no candidate. |
| `data/results.csv` | The `netbench verify-batch` table: one row per attempt (153 rows: 134 completed runs, 12 failed attempts, 7 baseline runs), every design parameter, every scalar metric, the L0 error counts by code and the `call_*` fields. |
| `data/sweep/` | The reproducibility sweep of Section 3.5 (345 cells generated twice; 3,150 identical files; 315 perfect verifications). |
| `data/runs-llm.sha256` | SHA-256 of every shipped network file, for the regeneration check. |
| `scripts/` | Python scripts (standard library only) for every step below. |
| `docs/` | `AGENT_PROTOCOL.md` (how each LLM run was executed and recorded), `CHAT_INTERFACE_PROTOCOL.md` (adding a chat-interface model), `RUN_LOG.md` (what was actually done, every deviation), `DATA_DICTIONARY.md` (file formats and columns), `ORIGINAL_PLAN_2026-09-26.md` (the pre-study plan). |

## Requirements

- Java 21 (to run the jar) and Maven 3.9 (to rebuild it from source; not needed with the prebuilt jar).
- Python 3.10 or later (the scripts use the standard library only).
- To repeat the LLM runs: access to the models through an agentic harness with file tools (Section C below).

## Quick start (no LLM calls)

```bash
python scripts/regenerate_networks.py --skip-10000     # regenerates the 43 networks up to 1,000 nodes into work/runs-llm/
snrm-netbench/netbench verify-batch --runs data/runs-llm --responses data/responses --out work/results.csv
```

The first command takes about 20 seconds and must end with `checksums: 430 identical, 0 different, 16 not regenerated`
(the 16 are the files of the two 10,000-node networks, left out by `--skip-10000`); the second re-scores every shipped
attempt and must reproduce `data/results.csv` (see step D).

## Replicating the study step by step

### A. Build and check the instrument

```bash
cd snrm-netbench
mvn -q verify                     # compiles, runs the test suite, writes target/netbench.jar
./netbench --help                 # netbench.cmd on Windows; or: java -jar dist/netbench-0.1.0.jar --help
cd ..
```

`dist/netbench-0.1.0.jar` is the build used in the study; the scripts use `target/netbench.jar` when it exists and the
prebuilt jar otherwise.

Reproducibility sweep (Section 3.5): generates the 345-cell design twice, compares every file byte for byte, and verifies
each second-folder truth against the first (all 315 feasible cells must be identical and perfect):

```bash
python scripts/run_sweep.py                       # -> work/sweep/sweep-index.csv, sweep-results.csv, sweep-summary.json
```

### B. Regenerate the networks

```bash
python scripts/regenerate_networks.py             # all 45 networks -> work/runs-llm/, compared with data/runs-llm.sha256
python scripts/regenerate_networks.py --skip-10000   # the 43 networks up to 1,000 nodes (about 20 seconds)
```

Generation is deterministic: every regenerated file must match the shipped checksum. The same networks are obtained with
the tool directly, for example `snrm-netbench/netbench batch --design design/design-main.json --out-dir work/runs-llm`.

The agents read a copy of each description soft-wrapped at 200 characters (word boundaries only; the text is unchanged):

```bash
python scripts/prepare_descriptions.py            # -> data/descriptions/<run-id>/description.md
```

### C. Run the LLM extraction (the part that costs money)

The protocol is in `docs/AGENT_PROTOCOL.md`; in short, for every model × network cell:

1. Print the prompt: `python scripts/make_agent_prompt.py <model-alias> <run-id> --version v1.2` (the study used v1 for
   the first 22 networks, v1.1 for the remaining 19 and for retries, v1.2 for the Sonnet 5 500-node runs, the third
   attempts and the scale extension; see `docs/RUN_LOG.md`).
2. Launch one fresh agent with that prompt, with the Read and Write file tools only, no code execution, no web access,
   and the ground truth (`data/runs-llm/*/truth`, `facts.jsonl`, `decoys.jsonl`, `manifest.json`) held outside any path the
   prompt names. The agent reads the instruction file and the wrapped description and writes the five CSV files and
   `call.json` into `data/responses/<model>/<run-id>/<rep>/`.
3. Record what only the harness knows: `python scripts/record_call.py <model> <run-id> <tokens> <duration_ms> <tool_uses> [rep]`;
   for an attempt the harness terminated before any file was written, `... --failed "<error message>"`.
4. Never correct, re-prompt or help the agent. A retry is a new `<rep>` directory and is reported as a deviation.

A model that can only be used through a chat interface is added with `docs/CHAT_INTERFACE_PROTOCOL.md`
(`python scripts/make_chat_prompts.py` writes the 43 ready-to-paste prompts).

### D. Score

```bash
snrm-netbench/netbench verify-batch --runs data/runs-llm --responses data/responses --out work/results.csv
```

writes `report.json` beside every response and one row per attempt into the results table. The rule-based baseline is
`snrm-netbench/netbench extract --description data/runs-llm/<run-id>/description/description.md --out data/responses/regex-extract/<run-id>/1/candidate`
for each template network, scored by the same command. Re-scoring the shipped responses reproduces `data/results.csv`
(the `call_*` columns come from each `call.json`), except the `status` of one failed attempt (see "Notes on the data").
Re-scoring also rewrites the shipped `report.json` files; the six 10,000-node reports come back in full (33–40 MB each)
instead of the trimmed copies described under "Notes on the data".

### E. Derive the reported numbers

The paper's tables and figures are computed from `data/results.csv` (one row per attempt) and, for the network-name
adjustment, from the `l2.mismatches` of each `report.json`; `docs/DATA_DICTIONARY.md` defines every column, which rows
count as completed runs, and name-adjusted recall. No analysis code is included.

## What to expect

The instrument, the networks, the sweep and the scoring are deterministic: regeneration reproduces every shipped network
file byte for byte, the sweep produces 315 identical and perfect cells, and re-scoring reproduces `data/results.csv` (bar
the one `status` value noted below). Repeating the LLM runs will not reproduce the shipped responses: the models are
sampled at the harness's default settings and the harness (the Claude Cowork subagent runtime, September 2026) sets the
64,000-token per-response limit and the context compaction that shaped the failures at 200 and 10,000 nodes.

## Notes on the data

- Model identifiers are as the models reported them (`claude-haiku-4-5-20251001`, `claude-sonnet-5`, `claude-opus-5-5`);
  the harness did not expose dated version strings, temperature or effort settings.
- `data/responses/*/s1-n10000-*/1/report.json` list only the non-omitted mismatches; the count of omitted facts is in
  `l2.mismatches_omitted` and the per-field totals in `l2.per_field`. `netbench verify` regenerates the full report.
- Absolute paths inside `call.json`, `report.json` and `manifest.json` were rewritten to package-relative paths.
- The failed Sonnet 5 attempt `s1-n200-e5-narrative-typed-uniform-none0-0fae8826/1` left an empty `candidate/` directory,
  so its row in `data/results.csv` has `status` = "scored" with five `FILE_MISSING` errors; git keeps no empty
  directories, so re-scoring the package gives `status` = "no_candidate" for that row. Either way it is a failed attempt
  (`call_status` = "failed") and not a completed run.
- "Importable" means the checker's re-implementation of SNRM's import rules raised no error; SNRM's live importer was not
  called (the `*_snrm` and `l3_*` columns are empty). `snrm-netbench/README.md` lists the assumptions the re-implementation makes.

## Citation and licence

The paper is under review; citation details and a `CITATION.cff` will be added on acceptance. The licence of the code and
data is stated in `LICENSE.md`.

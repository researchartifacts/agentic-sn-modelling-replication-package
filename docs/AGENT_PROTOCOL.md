# Protocol for the agentic extraction runs

This is the procedure that produced every row of `data/results.csv` for the three Claude models. It is written so that
the same runs can be repeated with the same or other models in any agentic harness that gives a model file tools.

## 1. Harness

- Runtime used in the study: the Claude Cowork subagent runtime (September 2026). Each run was one freshly started
  subagent with no memory of any other run, given the prompt of Section 3 as its whole task.
- Tools: by instruction, only the file-reading tool (Read, with offset/limit for long files) and the file-writing tool
  (Write). Code execution, shell, search and web access were forbidden in the prompt. The harness could not technically
  remove those tools, so compliance rests on the agent's self-report (`tools_used` in `call.json`) and the harness's
  tool-use count; the two Sonnet 5 runs that disclosed an accidental Bash call and the one that used Edit are reported as
  deviations (`docs/RUN_LOG.md`).
- Sampling and effort: the harness's defaults, which it does not expose. Record `"effort": "default"`.
- Output limit: 64,000 tokens per model response, a property of the harness that caused the Sonnet 5 failures at 100 to
  200 nodes and, with automatic context compaction, ended the 10,000-node runs.

## 2. Isolation of the ground truth

The agent must be able to reach exactly two files: the instruction file and the wrapped description. In the study the
truth folders, `facts.jsonl`, `decoys.jsonl`, `manifest.json` and the generator jar were moved to a directory that no
prompt referenced for the duration of the runs and restored afterwards. Isolation therefore rests on three records:

1. the prompt names only the two files and forbids listing, searching or reading anything else;
2. the agent's `files_read` self-report, which listed exactly the two files in every completed run that wrote one;
3. the harness's tool-use count per run (9 to 24 in the grid, consistent with a few reads and five or six writes).

A stricter harness that limits the file tools to the two files is preferable; describe whatever isolation is used.

## 3. Prompt

`scripts/make_agent_prompt.py <model> <run-id> [--rep N] [--version v1|v1.1|v1.2]` prints the prompt. Its parts:

1. the two files to read: `protocol/llm-instructions-v1.md` and `data/descriptions/<run-id>/description.md`
   (the copy wrapped at 200 characters by `scripts/prepare_descriptions.py`);
2. the rules paragraph, which differs between the three versions used in the study:
   - **v1**: read only the two files; no programs, scripts, shell or web; Read and Write tools only; follow the instruction
     file; extract by reading, not by writing a parser; read long documents in parts; write each CSV file once, complete;
   - **v1.1**: v1 plus "write each of the five CSV files in a separate Write tool call, and write each file once, complete;
     never split one file across calls and never leave a file for later" (added after a Sonnet 5 response holding all five
     files exceeded the output limit);
   - **v1.2**: v1.1 plus "work incrementally and keep your reasoning short ... write network_meta.csv and products.csv first,
     then nodes.csv, then links.csv, then node_products.csv; before each file, re-read only the part of the document that
     file needs and write the file straight away" (added when Sonnet 5 exceeded the limit under v1.1 as well);
3. the five output paths under `data/responses/<model>/<run-id>/<rep>/candidate/`;
4. the `call.json` the agent must write: `model_alias`, `reported_model` (the identifier the model finds in its own
   system prompt, or "unknown"), `effort`, `prompt_version` (v1.1 and v1.2 only), `files_read`, `tools_used`;
5. the final reply: row counts per file, the reported model, the files read; no CSV content.

The instruction file (`protocol/llm-instructions-v1.md`) was frozen before the first run and never changed; the prompt
versions differ only in the rules paragraph. A new instruction file must be published as a new version (`v2.md`), and the
schema block it embeds must equal the output of `netbench schema --format markdown` (a test in `snrm-netbench` checks this).

## 4. Which version was used where

| Runs | Prompt |
|---|---|
| First campaign, 22 networks, all three models, first attempts | v1 |
| Second campaign, 19 networks, Haiku 4.5 and Opus 5.5 | v1.1 |
| Second campaign, Sonnet 5 first attempts below 500 nodes; the second attempt at 200 nodes / five echelons | v1.1 |
| Sonnet 5 third attempts (five networks) and its six 500-node runs | v1.2 |
| Scale extension (1,000 and 10,000 nodes), all three models | v1.2 |

## 5. Recording a run

After the agent finishes, merge what only the harness knows into its `call.json`:

```bash
python scripts/record_call.py <model> <run-id> <subagent_tokens> <duration_ms> <tool_uses> [rep]
python scripts/record_call.py <model> <run-id> 0 0 0 [rep] --failed "<the harness's error message>"   # nothing written
```

`subagent_tokens` is the token total the harness reports for the agent, `duration_ms` the wall-clock time from launch to
final reply, `tool_uses` the number of tool calls. For a terminated attempt the harness returns none of these; the record
carries `"status": "failed"` and the error text, and `verify-batch` scores the attempt as `no_candidate` (or scores
whatever partial candidate exists). Note in `operator_note` anything the operator had to do to the record (for example a
`call.json` written to the wrong path and moved) and anything the agent disclosed in its final reply that the record does
not carry (an extra tool call, a rewritten file, a compacted context). Never touch a candidate CSV file.

## 6. Attempts, retries and what counts

- One attempt per model × network is the design. The first completed attempt is the result.
- If the harness terminates the agent before any file is written, keep the failed record as `<rep>` 1 and run the next
  attempt as `<rep>` 2 (a plain retry) or 3; state the prompt version of each. Every retry is a protocol deviation and is
  reported as such (Section 4 of the paper).
- Never correct, re-prompt or continue an agent, and never inspect or edit its files before scoring.
- An agent refused or aborted by the platform before it produced any output is relaunched unchanged and not counted as a
  retry (one Haiku 4.5 launch in the study).

## 7. Scoring

```bash
snrm-netbench/netbench verify-batch --runs data/runs-llm --responses data/responses --out data/results.csv
```

`verify-batch` never reads `call.json` for scoring; it copies its top-level scalar fields into `results.csv` as
`call_<field>` columns so that tokens, durations, prompt versions and failure texts travel with the scores.

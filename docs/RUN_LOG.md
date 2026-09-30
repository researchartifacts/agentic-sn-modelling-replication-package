# Run log: what was actually done, and every deviation

Dates are UTC as recorded in each `call.json` (`recorded_at`, the time the harness record was merged, minutes after the
run ended). All runs were executed on the Claude Cowork subagent runtime between 2026-09-26 22:33 and 2026-09-28 07:56 UTC.

## 1. Networks

| Campaign | Design files | Networks | Notes |
|---|---|---|---|
| First (26–27 September) | `design-main.json` (narrative, E 3/5/7, 6–200 nodes), `design-template.json` (template, E5), `design-noise.json` (topical and adversarial 30 %, E5) | 29 generated, 22 run | Generated 29; 7 (200-E3, 200-E7, template at 6/25/200, both 200-node noise variants) were held back for budget and run in the second campaign. |
| Second (27–28 September) | the 7 held-back networks plus `design-grid2.json` (noise at 6, 25, 100, 500), `design-grid2-main500.json` (500 nodes, E 3/5/7), `design-grid2-template500.json` | 19 run | Completes the full grid: 7 sizes {6, 12, 25, 50, 100, 200, 500} × 6 conditions, minus 6 nodes at seven echelons = 41 networks. |
| Scale extension (28 September) | `design-grid3-1000-main.json`, `design-grid3-1000-adv.json`, `design-grid3-10000-main.json`, `design-grid3-10000-adv.json` | 4 run | Seven echelons, narrative, with and without adversarial noise, at 1,000 and 10,000 nodes. One design file per noise condition because a design that lists `noise-type [none, adversarial]` with `noise-ratio 0.3` skips the `none` cell. |

All networks use seed 1, product "Widget", typed names, uniform DAY units, a 90-period horizon and, for noise, a 30 %
sentence ratio. The generator's self-checks passed for every network. `data/runs-llm/index.csv` lists all 45.

## 2. Runs and prompt versions

- 41 grid networks × 3 models = 123 completed runs; 4 extension networks × 3 models = 11 completed runs and 1 failed attempt.
- Prompt v1 (instruction file alone) for the first campaign; v1.1 (one file per tool call) for the second campaign; v1.2
  (brief, incremental reasoning) for the Sonnet 5 third attempts, its 500-node runs and the extension (`docs/AGENT_PROTOCOL.md`, Section 4).
- Haiku 4.5 and Opus 5.5 completed every first attempt in the grid.
- Sonnet 5 failed its first attempt on 6 of the 35 grid networks it was given under v1 or v1.1 (100 topical, 100
  adversarial, 200 E3, 200 E5, 200 topical, 200 E7): three exceeded the 64,000-token output limit, three timed out. The
  200-E5 network (first campaign, v1) completed on a second attempt that carried the v1.1 sentence. Plain retries of the
  other five (v1.1) failed again on the output limit; third attempts with v1.2 completed all five. The six Sonnet 5
  500-node runs used v1.2 from the start and completed. Total failed attempts in the grid: 11, all Sonnet 5, none with
  any file written.
- Completed prompt versions: Haiku 4.5 and Opus 5.5 v1 on 22 networks and v1.1 on 19; Sonnet 5 v1 on 21, v1.1 on 9 and
  v1.2 on 11.
- Extension: the Sonnet 5 attempt on the 10,000-node adversarial network was terminated by the harness while it was
  reading the description ("Autocompact is thrashing: the context refilled to the limit within 3 turns of the previous
  compact, 3 times in a row"); `network_meta.csv` and `products.csv` had been written and are scored as a partial
  candidate. The other eleven extension runs completed, all with deliberately or involuntarily partial output at 10,000
  nodes (the descriptions of 0.9–1.1 million words exceed the context window, and a 10,000-row file exceeds the
  per-response output limit).

## 3. Isolation of the truth

The plan was to delete the truth from the machine during the runs. The environment refused irreversible deletion, so the
truth folders, ledgers, manifests and the generator jar were moved to a directory no prompt referenced and restored
afterwards. Isolation rests on the prompt, the agents' `files_read` self-reports (exactly the instruction file and the
description in every one of the 133 completed runs that wrote a report) and the harness's tool-use counts (9 to 24 per
grid run). No full tool-call log exists.

## 4. Deviations disclosed by the agents or made by the operator (all recorded in the `call.json` files)

- Descriptions were soft-wrapped at 200 characters so that the file tool could display them; whitespace only.
- Haiku 4.5, 500 nodes / three echelons: the agent wrote its self-report to `candidate/call.json`; the operator moved it to
  `call.agent-misplaced.json` and merged it into `call.json` (no CSV touched). Its `reported_model` is the alias "haiku".
- Haiku 4.5, 500 nodes / template: the agent stopped after `network_meta.csv` and `nodes.csv` and wrote no self-report.
- Haiku 4.5, 1,000 nodes / adversarial: self-report again written to `candidate/call.json` and merged by the operator.
  Both Haiku 4.5 1,000-node agents abbreviated `prompt_version` to "v1.2"; the operator expanded it to the label of the
  prompt issued (noted in `operator_note`); the same was done for the Haiku 4.5 records at 500 nodes with three and five
  echelons.
- Haiku 4.5, 50 nodes / seven echelons: the platform refused the launch once with a transient message; relaunched
  unchanged, not counted as a retry.
- Opus 5.5, both 1,000-node runs: `products.csv` rewritten once (first write incomplete), a deviation from the
  write-once rule; the narrative run also reported that it did not keep its reasoning brief.
- Sonnet 5, 1,000 nodes / narrative: one accidental Bash call (before a context-compaction boundary); Sonnet 5, 1,000
  nodes / adversarial: used the Edit tool; Sonnet 5, 10,000 nodes / narrative: one accidental Bash call (a no-op echo)
  and `nodes.csv` rewritten more than once.
- Context compaction by the harness was disclosed for both Sonnet 5 runs at 1,000 nodes, the Sonnet 5 narrative run at
  10,000 nodes and the Opus 5.5 adversarial run at 10,000 nodes; the `subagent_tokens` of those runs are not comparable
  with the rest and are drawn hollow or excluded in Figure 10.
- The Sonnet 5 200-E5 second attempt (first campaign) has no `prompt_version` label; its harness note records the added
  one-file-per-call sentence (v1.1).
- SNRM's live importer was not run on any candidate: the `import_valid_snrm`, `disagreement_count` and `l3_status`
  columns are empty, and "importable" means the checker's re-implementation of the import rules raised no error.
- Each cell was run once per model (budget); confidence intervals are Clopper–Pearson.
- The network-name ambiguity (title "Supply network description: Widget" against the sentence "the Widget supply
  network") is a defect of the generator's narrative renderer; it produced the only Opus 5.5 mismatches and the
  name-adjusted recall reported in the paper. A later generator version should state the name once.

## 5. Scoring and analysis

`netbench verify-batch --runs data/runs-llm --responses data/responses --out data/results.csv` scored every attempt
(153 rows). The analysis treats a row as a completed run when `call_status` is not "failed" and `status` is "scored"
(`docs/DATA_DICTIONARY.md`). Every number in the paper was recomputed independently by spreadsheet formula from
`data/results.csv` and the `report.json` files; three slips found by that check were corrected in the submitted version
(the run dates, the count of decoy statements naming a non-existent entity, and the self-reported identifier of one
Haiku 4.5 run). The analysis tables, the figures and the verification workbook belong to the manuscript and are not
part of this package.

## 6. Resource use

The 123 grid runs consumed 16.0 million agent tokens and 921 minutes of agent wall-clock time; the 1,000-node runs took
26 to 100 minutes each. Generating the 10,000-node adversarial network took longer than ten minutes.

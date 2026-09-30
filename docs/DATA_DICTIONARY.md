# Data dictionary

## `data/runs-llm/<run-id>/` — one generated network

| File | Content |
|---|---|
| `description/description.md` | A: the natural-language description the model extracts from, exactly as generated (long lines). The agents read a copy wrapped at 200 characters (`scripts/prepare_descriptions.py`). |
| `truth/network_meta.csv`, `nodes.csv`, `links.csv`, `products.csv`, `node_products.csv` | B: the five SNRM import files, the ground truth. |
| `facts.jsonl` | One line per atomic fact of B: `id`, `kind` (`meta`, `product`, `node`, `link`, `node_product`), `entity` (a name, `source->target`, `node\|product` or `network_meta`), `field` (the column stem, `geo`, or `exists`), `value` (B's cell text), `unit`, `stated` (whether A states it), `relative` (stated relative to another node), `sentence` (1-based sentence index in A). Stated facts are the denominator of fact recall. |
| `decoys.jsonl` | One line per adversarial sentence (empty for other noise types): `id`, `marker` (the out-of-scope phrase that opens the sentence), `kind`, `entity`, `field`, `value`, `unit`, `decoy_entity` (true when the entity does not exist in B), `sentence`. |
| `network.full.json` | The full network object before any attribute blanking (identical to B at attribute coverage 1). |
| `manifest.json` | `tool_version`, `run_id`, `seed`, `params` (every effective generator parameter), `tiers` (index, role, SNRM type and node names of each tier), `counts` (nodes, links, products, node-products, facts total and stated, header/fact/noise sentences, actual noise ratio, words, token estimate = words × 1.3), `expected_import` (0 errors and the exact warning counts B raises), `decoy_entities`, `notes`, `files` and the SHA-256 of every file. |

Run ids are `s{seed}-n{nodes}-e{echelons}-{style}-{naming}-{units}-{noise}{ratio}-{hash8}` (hash of every effective parameter).
`index.csv` lists every network with `status`, every generator parameter and the manifest counts (`counts_*`).

## `data/responses/<model>/<run-id>/<rep>/` — one model attempt

| File | Content |
|---|---|
| `candidate/*.csv` | C: the five files the agent wrote (fewer when it stopped early; none for a terminated attempt). |
| `call.json` | Written by the agent (`model_alias`, `reported_model`, `effort`, `prompt_version` for v1.1/v1.2, `files_read`, `tools_used`) and completed by `scripts/record_call.py` (`harness`, `subagent_tokens`, `duration_ms`, `tool_uses`, `recorded_at`, `candidate_files`). A terminated attempt has `status: "failed"` and `failure` (the harness's message). `operator_note`, `note`, `notes`, `attempt` and `scenario` hold disclosures and interventions (`docs/RUN_LOG.md`). |
| `report.json` | The checker's report: `envelope` (files found and missing), `l0` (import validity, error and warning counts, every finding with its SNRM code), `l1` (per-sheet precision, recall, F1; missing, extra and reversed entities), `l2` (`fact_recall`, `fact_precision`, `per_field` counts per class, `fabricated`, `convention_violations`, `mismatches`), `decoys` (total, entities and values absorbed), `l3` (null: not run), `composite` (`ncs`). The six 10,000-node reports omit the `omitted`-class mismatches (`l2.mismatches_omitted` gives their count). |

Models: `haiku` = Claude Haiku 4.5, `sonnet` = Claude Sonnet 5, `opus` = Claude Opus 5.5, `regex-extract` = the rule-based extractor on the template descriptions (baseline).

## `data/results.csv` — one row per attempt (`netbench verify-batch`)

| Columns | Meaning |
|---|---|
| `model`, `run_id`, `rep`, `status` | The attempt; `status` is `scored`, `missing_truth`, `truth_invalid` or `no_candidate`. |
| `seed` … `locale` | The generator parameters of the network (from its manifest): `nodes`, `echelons`, `style` (template/narrative), `noise-type` (none/topical/adversarial), `noise-ratio`, … |
| `import_valid`, `error_count`, `warning_count` | L0: 1 when no import error; counts of L0 findings. `import_valid_snrm`, `disagreement_count` are empty (SNRM not called). |
| `nodes_truth`, `nodes_candidate`, `nodes_matched`, `nodes_precision`, `nodes_recall`, `nodes_f1` (also for `links_`, `products_`, `node_products_`), `links_recall_conditional`, `node_type_accuracy`, `structure_f1` | L1 structural scores per sheet. |
| `fact_recall`, `fact_precision`, `facts_stated` | L2: `fact_recall` = correct ÷ stated facts (the paper's fact recall); `fact_precision` = correct ÷ (correct + wrong of every kind + filled_unstated + facts of fabricated entities). |
| `class_correct`, `class_correct_blank`, `class_convention_ok`, `class_omitted`, `class_wrong`, `class_wrong_unit`, `class_wrong_type`, `class_wrong_direction`, `class_filled_unstated`, `class_convention_violation`, `class_decoy_absorbed` | Number of scored fields in each L2 class. |
| `fabricated_nodes`, `fabricated_links`, `fabricated_products`, `fabricated_node_products`, `fabricated_total`, `fabricated_facts`, `convention_violations` | Entities in C that match nothing in B, and the facts they carry. |
| `decoy_total`, `decoy_entities_absorbed`, `decoy_values_absorbed` | Adversarial decoys offered; decoy entities present in C; decoy values C used for a real entity. |
| `l3_status`, `behavioural_equivalence`, `sim_rel_*` | Empty: the behavioural layer was not run. |
| `ncs` | Composite 0.2 × import_valid + 0.3 × structure_f1 + 0.5 × fact_recall (ranking only; not used in the paper). |
| `l0_<CODE>` | Number of L0 findings of each SNRM validation code (`CUSTOMER_UNREACHABLE`, `LATERAL_LINK`, `NOT_A_NUMBER`, `UNKNOWN_TIME_UNIT`, `ROW_WIDTH_MISMATCH`, `FILE_MISSING`, …). |
| `call_<field>` | The top-level scalar fields of the attempt's `call.json` (`call_status` = "failed" for terminated attempts, `call_subagent_tokens`, `call_duration_ms`, `call_tool_uses`, `call_prompt_version`, `call_reported_model`, `call_failure`, `call_operator_note`, …). |

A completed run is a row with `call_status` not "failed" and `status` = "scored" (12 rows are failed attempts, 7 are the baseline).

Name-adjusted recall, as reported in the paper, is (facts correct + network-name mismatches) ÷ `facts_stated`, where facts
correct = round(`fact_recall` × `facts_stated`) and the network-name mismatches of an attempt are the entries of
`l2.mismatches` in its `report.json` with `kind` = "meta", `field` = "name" and `class` = "wrong" (0 for a failed
attempt, which has no report). Tokens and minutes per run are `call_subagent_tokens` ÷ 1,000 and `call_duration_ms` ÷ 60,000.

## `data/sweep/` — reproducibility sweep (`scripts/run_sweep.py`)

`sweep-index.csv` (345 cells, 315 generated, 30 skipped because nodes < echelons), `sweep-results.csv` (the second
folder's truth verified against the first: all 315 perfect) and `sweep-summary.json`.

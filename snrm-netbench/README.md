# snrm-netbench

A Java 21 command-line tool: it **generates** a supply network as SNRM import files (**B**, the ground
truth) together with a natural-language description of it (**A**), and it **verifies** a network an LLM
extracted from A (**C**) against B, layer by layer. It is the instrument of the replication package this
folder belongs to (see the package README for how it was used).

- [Purpose](#purpose) · [Install](#install) · [`generate`](#generate) · [`verify`](#verify) ·
 [`extract`](#extract) · [`batch`](#batch) · [`verify-batch`](#verify-batch) · [`schema`](#schema) ·
 [LLM instruction file](#llm-instruction-file) · [SNRM integration](#snrm-integration) ·
 [Output layout](#output-layout) ·
 [Report schema](#report-schema) · [Assumptions about the SNRM importer](#assumptions-about-the-snrm-importer) ·
 [Design decisions](#design-decisions)

## Purpose

| Command | What it does |
|---|---|
| `generate` | From a seed and parameters: B (five canonical CSV files), A (`description.md`), the fact ledger, the decoy log, the full network and a manifest — byte-identical on any machine. |
| `verify` | Scores a candidate C (a directory of CSV files, or one LLM response file) against B: L0 syntactic, L1 structural, L2 attribute, decoy absorption, composite. Writes `report.json`. |
| `extract` | The rule-based parser of `template` descriptions: the non-LLM baseline and the A ⇔ B round-trip check. |
| `validate` | SNRM's import rules over any directory, as JSON. |
| `batch` | Generates the full factorial of a design file (fixed parameters × factor levels × seeds) and writes `index.csv`; invalid cells are skipped with a reason, never fatal. |
| `verify-batch` | Verifies every `responses/<model>/<run-id>/<rep>/` response against its run, writes `report.json` beside each response and one flat `results.csv`. |
| `schema` | Prints the canonical CSV contract (files, columns, enums, unit tokens, unit rules, conventions) as Markdown or JSON; the LLM instruction file embeds it verbatim. |

It never calls an LLM, and it does not reimplement SNRM's simulation engine.

## Install

Requires Java 21 and Maven 3.9.

```bash
mvn -q verify # compile, run every test, build target/netbench.jar
./netbench --help # wrapper for java -jar target/netbench.jar (netbench.cmd on Windows)
```

`netbench` must be executable after a checkout (`git update-index --chmod=+x netbench` when committing
from Windows). Every command writes its standard output as UTF-8, whatever the console encoding.

## `generate`

```text
netbench generate --seed <long> --nodes <int> --echelons <3..7> --final-product-name <text> --out-dir <dir>
 [--products <int>=1] [--naming typed|neutral|realistic=typed]
 [--style template|paraphrased|narrative=template] [--order by-tier|by-node|shuffled=by-tier]
 [--units uniform|mixed=uniform] [--period-unit HOUR|DAY|WEEK=DAY] [--period-value <int>=1] [--horizon <int>=90]
 [--inbound-links <min>,<max>=1,2] [--lateral-links <int>=0] [--attribute-coverage <0..1>=1.0]
 [--noise-type none|boilerplate|topical|adversarial=none] [--noise-ratio <0..0.8>=0]
 [--geo] [--distance-mode none|km=none] [--locale plain|en|de=plain]
 [--run-id <text>] [--overwrite]
 [--snrm-url <base> (--snrm-token <jwt> | --snrm-user <u> --snrm-pass <p>)] [--snrm-project-id <id>]
```

Example:

```bash
./netbench generate --seed 1 --nodes 12 --echelons 4 --final-product-name Widget --out-dir runs
# -> runs/s1-n12-e4-template-typed-uniform-none0-301ecdc0
```

The default run id is `s{seed}-n{nodes}-e{echelons}-{style}-{naming}-{units}-{noise}{ratio}-{hash8}`, where
`hash8` is the first 8 hex digits of SHA-256 over the sorted-key JSON of every effective parameter (all
of them except `--out-dir`, `--overwrite`, `--run-id`). Before anything is written, `generate` runs the
self-checks (B imports with exactly the predicted warnings; the network is demand-feasible by
max-flow; a template description extracts back to B byte for byte and verifies perfectly; every stated
fact's sentence contains its value). If one fails it writes nothing and exits 3. Exit codes: 0 written ·
2 bad arguments (one-line message), or the run directory exists without `--overwrite` · 3 self-check
failed (self-check 5 is the optional SNRM validation) · 5 SNRM could not be reached.

`examples/` holds the committed output for seeds 1, 2 and 3 at `--nodes 12 --echelons 4`; a test
regenerates them and requires identical bytes, and another requires `verify(B, B)` to be perfect on
each.

Three options change only how A is written (B keeps its plain values):

- `--locale en|de` writes every number of A with thousands separators and the locale's decimal mark
 (`1,200` and `1.80` in `en`; `1.200` and `1,80` in `de`) — header counts, values, coordinates and
 distances alike; years in noise stay plain.
- `--distance-mode km` states each lane's length instead of its lead time — "SUP-01 ships to PLT-02 with
 a road distance of 960 km, …" or "an ocean distance of …" — and the header adds "Road lanes cover 480
 km per day and ocean lanes 600 km per day." A lane from an overseas supplier (APAC, AMER) to a plant is
 an ocean lane, every other lane a road lane; km = lead days × the mode's speed, exactly (both speeds
 are multiples of 24, so a lead time of whole hours is whole kilometres). The reader must derive the
 lead time.
- `--geo` adds `lat`/`lng` to B and a sentence "X is located at latitude … and longitude …" to A; the
 pair is one fact (`field: "geo"`).

A localized description declares its format in the header ("Numbers use a point as the thousands
separator and a comma as the decimal mark."), so `1.500` is never ambiguous; `llm-instructions/v1.md`
tells the model to write numbers plain, and to derive a lane's lead time in hours (distance ÷ speed per
day × 24, always a whole number) when the document gives distances.

## `verify`

```text
netbench verify --truth <dir> --candidate <dir|file> --out <report.json>
 [--facts <facts.jsonl>] [--decoys <decoys.jsonl>] [--manifest <manifest.json>]
 [--tolerance <rel>=1e-6] [--split-dir <dir>] [--lenient-numbers]
 [--snrm-url <base> (--snrm-token <jwt> | --snrm-user <u> --snrm-pass <p>)] [--snrm-project-id <id>] [--l3] [--keep]
```

`--lenient-numbers` scores candidate numbers written with thousands separators or a decimal comma
(`1,200`, `1.200.000`, `1 200`, `0,05`, `1.234,56`) by value at L1 and L2;
L0 keeps SNRM's strict reading, so such cells are still `NOT_A_NUMBER` there, and a cell the strict
grammar reads (`6.000` = 6) keeps that reading. The `--snrm-*`, `--l3` and `--keep` flags are described
under [SNRM integration](#snrm-integration).

- `--candidate` is a directory of CSV files (the five canonical names, matched case-insensitively), or
 any file — an LLM response — which is split into files by its envelope: a heading naming the file
 followed by a fenced block (`### nodes.csv`), a marker section (`=== FILE: nodes.csv ===`), or, as a
 fallback, a fenced block whose first line is a canonical header row. The split files are written to
 `--split-dir` (default `<candidate>.split/`) for inspection.
- `--facts`, `--decoys` and `--manifest` default to `facts.jsonl`, `decoys.jsonl` and `manifest.json`
 beside the truth directory when they exist. Without a ledger every non-blank truth cell counts as a
 stated fact.
- Exit codes: 0 report written (for any candidate, however broken) · 2 bad arguments (a missing
 candidate, a truth that is not a directory, a malformed ledger, a negative tolerance) · 3 the truth
 fails SNRM's import rules (the findings are printed) · 5 SNRM could not be reached when `--snrm-url`
 was given.

Scoring in brief (details under [Report schema](#report-schema)):

- **L0** runs SNRM's import rules over C and adds the envelope findings (`FILE_MISSING` — an error for
 nodes.csv and links.csv, a warning otherwise; `NETWORK_META_DEFAULTED`; `ENVELOPE_PARSE_ERROR`).
 `import_valid` = no error.
- **L1** matches nodes and products by normalised name (trim, strip one pair of quotes, collapse spaces,
 fold case), links by the normalised ordered pair, node-product rows by the normalised pair, and
 reports precision, recall and F1 per sheet, `recall_conditional` for links, `node_type_accuracy` and
 `structure_f1`.
- **L2** classifies every scored field (`correct`, `correct_blank`, `convention_ok`, `omitted`, `wrong`,
 `wrong_unit`, `wrong_type`, `wrong_direction`, `filled_unstated`, `convention_violation`,
 `fabricated`, `decoy_absorbed`). Durations compare in seconds and rates per second, so `3 WEEK` equals
 `21 DAY`, a blank unit means that network's own period unit, and a wrong period is one wrong meta fact
 rather than a cascade over every rate. `fact_recall` = correct ÷ stated facts; `fact_precision` =
 correct ÷ (correct + wrong of every kind + filled_unstated + facts of fabricated entities).
- **Decoys**: decoy entities C contains and decoy values C used for a real entity.
- **Composite**: `ncs = 0.2 × import_valid + 0.3 × structure_f1 + 0.5 × fact_recall` (ranking only).

## `extract`

```text
netbench extract --description <file> --out <dir> [--strict] [--locale plain|en|de=plain]
```

`--locale` tells the extractor how the description writes numbers; in distance mode it derives each
lead time from the stated distance and the header's speeds, in the period unit.

Parses a `template`-style description with patterns compiled from the very constants the renderer
fills (`describe/Templates.java`), and writes the five canonical CSV files plus `extract-log.json`
(`matched`, `skipped` — sentences opened by an adversarial marker —, `ignored`, the ignored sentences,
and problems such as a link to a node never introduced or a value that does not fit its column, which is
left blank). `--strict` exits 4 when any sentence matched nothing or any problem was recorded. On a
template description its output is byte-identical to B, with or without noise.

## `batch`

```text
netbench batch --design <design.json> --out-dir <dir> [--overwrite]
```

The design lists fixed parameters, factor levels and seeds. Parameter names are the `generate` flags
without `--`; values are numbers, strings or booleans (`"inbound-links": "1,2"`, `"geo": true`); every
parameter left out takes `generate`'s default.

```json
{ "name":"main-a", "fixed": {"final-product-name":"Widget", "period-unit":"DAY", "horizon":90},
 "factors": {"nodes":[6,12,25,50,100], "echelons":[3,4,5], "style":["template","narrative"]},
 "seeds":[1,2,3,4,5] }
```

Cells are the full factorial in the order the factors are listed (first factor outermost), times the
seeds (innermost). Each cell is parsed and validated exactly as `generate` would, then written to
`<out-dir>/<run-id>`. A cell is `skipped`, with its reason, when its arguments are invalid (e.g.
`nodes < echelons`), when its run directory exists and `--overwrite` is not given, when an earlier cell
had the same parameters, or when generation fails. Skipped cells never stop the batch; the summary
lists them.

`<out-dir>/index.csv` has one row per cell × seed: `run_id`, `status` (`generated` | `skipped`),
`reason`, every `generate` parameter (in flag order: `seed`, `nodes`, `echelons`, …, `locale`), then the
manifest counts as `counts_nodes`, `counts_links`, …, `counts_token_estimate` (blank for skipped
cells). Exit codes: 0 the design parsed and the index was written (whatever was skipped) · 2 bad
arguments or a bad design file (not JSON, an unknown key or parameter, `seed` outside `seeds`, a
parameter both fixed and a factor, an empty factor, missing or non-integer seeds).

## `verify-batch`

```text
netbench verify-batch --runs <dir> --responses <dir> --out <results.csv> [--tolerance <rel>=1e-6]
 [--lenient-numbers] [--snrm-url … (as for verify)] [--l3] [--keep]
```

Walks `<responses>/<model>/<run-id>/<rep>/` in name order. The candidate of a rep directory is
`response.md`, else `response.txt`, else its only `*.md` file, else its `candidate/` directory. The truth
is `<runs>/<run-id>/truth`, with `facts.jsonl`, `decoys.jsonl` and `manifest.json` beside it; scoring is
exactly `verify`'s. `report.json` is written into the rep directory, and a response file's split CSV
files into `<response>.split/` beside it.

`results.csv` has one row per rep directory: `model`, `run_id`, `rep`, `status` (`scored` |
`missing_truth` | `truth_invalid` | `no_candidate`), every `generate` parameter from the run's manifest
(same order as `index.csv`), every scalar metric of the report (`import_valid`, `error_count`,
`warning_count`, the L1 counts and ratios per sheet, `fact_recall`, `fact_precision`, the per-class
totals `class_*`, the `fabricated_*` counts, the decoy counts, `ncs`), one `l0_<CODE>` column per
validation code with the number of L0 findings of that code, and, when a rep directory holds a
`call.json` (the harness's call metadata), its top-level scalar fields as `call_<key>` (the union over
all rows, sorted). Every row has every column; unavailable values are empty cells; booleans are `1`/`0`
so they stay numeric where unscored rows leave cells empty; numbers never use exponents; UTF-8, `\n`, no
embedded newlines — it loads in pandas with numeric dtypes as is. Exit codes: 0 written · 2 bad
arguments.

## `schema`

```text
netbench schema [--format markdown|json]
```

Prints the canonical CSV contract, derived entirely from `Schema`, `Field`, `Conventions`, `UnitTokens`
and the model enums: the five files and their columns in order (`*` = required, with each column's
note), the node types with their echelon ranks, the time units with their length in seconds (MONTH = 30
days, YEAR = 365 days) and accepted tokens, the rounding policies with their description phrases and
worked examples (NEAREST rounds 1.5 periods to 2), the unit rules (durations vs rates, a missing or
blank `*_unit` means the period unit, a blank `capacity_value` means uncapped) and the conventions
table. The output is ASCII with `\n` line ends and is pinned by golden files
(`src/test/resources/golden/schema.md`, `schema.json`).

## LLM instruction file

`llm-instructions/v1.md` is the extraction prompt, stamped `version: v1` in its front matter: the
task, the output of `netbench schema --format markdown` between
`<!-- schema: generated by netbench schema --format markdown; do not edit -->` and `<!-- /schema -->`,
the role → type mapping, the rows and conventions, the unit rules, verbatim names, the scope rule (only
the current network), and the response envelope (five fenced `csv` blocks under level-3 file-name
headings, in sheet order, nothing else). A test requires the block between the markers to equal the
current schema output byte for byte, so the prompt and the checker cannot drift. After a schema change,
paste the new output between the markers, and publish a new version (`v2.md`) instead of editing a
version that responses already used.

## SNRM integration

With `--snrm-url`, `generate`, `verify` and `verify-batch` also ask a running SNRM. All network access is
in `verify/SnrmClient`; every endpoint path and field name it assumes is in `verify/SnrmApi.java` (see
[Assumptions about the SNRM importer](#assumptions-about-the-snrm-importer), rows 2, 3 and 6). **This
integration has been exercised only against the mock server of the test suite**, not against a running
SNRM; the study did not use it.

```text
--snrm-url <base> (--snrm-token <jwt> | --snrm-user <u> --snrm-pass <p>) [--snrm-project-id <id>]
verify, verify-batch also: [--l3] [--keep]
```

- `<base>` is the server root (`http://localhost:8080`); `/api/v1/…` is appended. A token is sent as
 `Authorization: Bearer <jwt>`; otherwise the tool logs in once (`POST /api/v1/auth/login`) and reuses the
 token — `verify-batch` logs in once for the whole batch. Timeouts: connect 10 s, request 120 s.
- Exit 2 for: a `--snrm-*` flag without `--snrm-url`; `--snrm-url` without a token or without both user
 and password, or with both; a URL that is not http(s); `--l3` without `--snrm-url`; `--keep` without
 `--l3`.
- **Exit 5** when SNRM cannot be reached at all (connection refused, unknown host, connect or request
 timeout): `generate` writes nothing, `verify` writes no report, `verify-batch` writes no `results.csv`
 (reports already written stay). An HTTP error answer is not exit 5: see below.

**`generate`** — after self-checks 1–4 have passed in memory, B's five files are POSTed to
`/api/v1/networks/import` with `validateOnly=true` (network name = run id; a project only with
`--snrm-project-id`, so a validation creates nothing). The report must say `valid: true`; otherwise — or
when SNRM answers with an HTTP error — self-check 5 fails: exit 3, SNRM's `errors[]` on stderr, nothing
written. The report is written as `snrm-validation.json` in the run directory; it is not in the
manifest's `sha256` table, and the `--snrm-*` flags enter neither `params` nor the run-id hash.

**`verify` — L0 cross-check.** The candidate's files as loaded (a directory: every `.csv` in name
order; a response file: its split files) are POSTed with `validateOnly=true`. `l0.snrm` is SNRM's report
verbatim, `l0.import_valid_snrm` its `valid`, and `l0.disagreements` lists `{code, ours, snrm}` for every
code whose count differs between this tool's L0 findings (SNRM's codes only; the three envelope codes are
this tool's own) and SNRM's `errors[]` plus `warnings[]`. If SNRM answers with an HTTP error, `l0.snrm` is
`{"error": "<message>"}`, `import_valid_snrm` is null and the report is still written.

**`verify --l3` — behavioural comparison.**

1. Project: `--snrm-project-id`, else a scratch project `netbench-l3-<label>` (label = the manifest's
 run id, else `verified_at`).
2. **Deterministic twins**, built in a temp directory: B and C rewritten from their parsed tables with
 the same file names and headers, every `failure_prob` cell (blank ones included) set to `0`, nothing
 else changed. A sheet without a `failure_prob` column gets none, so it relies on SNRM's default
 (assumption 3 below).
3. B's twin is imported first, then C's (`validateOnly=false`). If C's twin is rejected,
 `l3 = {"status": "candidate_not_importable", "snrm": <its report>}` and nothing else is computed —
 never zeros.
4. Topological metrics of both: `DENSITY`, `SPOF_NODE_COUNT`, `SPOF_ARC_COUNT`, `SPOF_COUNT`, `AVG_PATH`,
 `CLUSTERING`, `ROBUSTNESS_RANDOM`, `ROBUSTNESS_TARGETED`, and `NODE_CRITICALITY` for every node both
 networks have (matched by normalised name).
5. One **baseline run** of each (no scenario) with `params` `{seed: 1, replications: 1, demandNoiseCv: 0,
 timingJitterPeriods: 0, recordElementTimeseries: false}` — `safetyStockPriority` is deliberately not
 sent, so SNRM's default applies. The job is polled (100 ms doubling to 2 s, at most 10 min) until
 `DONE`, and `FILL_RATE`, `SERVICE_LEVEL`, `MIN_FILL_RATE`, `TOTAL_COST`, `CVAR_COST`, `AVG_INVENTORY`,
 `AVG_PIPELINE` are compared.
6. Every comparison is `{truth, candidate, abs_diff, rel_diff, missing}` with `rel_diff = |C − B| / |B|`
 (0 when both are 0, null when only B is 0); a value SNRM did not give is `missing`, never 0.
 **`behavioural_equivalence`** = all seven present on both sides and each within 1% of B's value.
7. **Cleanup**, unless `--keep`: both runs, then both networks, then a scratch project the tool created
 (never a `--snrm-project-id`). `cleaned_up` reports whether every deletion succeeded; a failed
 deletion never changes the exit code.

An HTTP error or a malformed answer during L3 gives `l3 = {"status": "error", "message": …}`; what was
created is still deleted and the report is written (exit 0).

**`verify-batch`** takes the same flags and does exactly what `verify` does for every scored response
(one login, one scratch project per response). `results.csv` gains `import_valid_snrm`,
`disagreement_count`, `l3_status`, `behavioural_equivalence` and `sim_rel_<CODE>` for the seven
simulation metrics — empty cells when not computed.

**Outside the determinism guarantee:** `snrm-validation.json`, `l0.snrm`, `import_valid_snrm`,
`disagreements` and everything under `l3` depend on the SNRM build and its database. Everything else in
a run directory and a report is unchanged by the `--snrm-*` flags.

## Output layout

```text
<out-dir>/<run-id>/
 description/description.md # A
 truth/network_meta.csv nodes.csv links.csv products.csv node_products.csv # B
 facts.jsonl # one atomic fact per line, stated and unstated
 decoys.jsonl # adversarial decoys (empty file when none)
 network.full.json # the full network before coverage blanking
 manifest.json
```

`facts.jsonl` lines look like
`{"id":17,"kind":"node","entity":"PLT-01","field":"capacity","value":"1200","unit":"DAY","stated":true,"relative":false,"sentence":23}`:
`kind ∈ {meta, product, node, link, node_product}`; `entity` is a name, `source->target`,
`node|product`, or `network_meta`; `field` is the column stem, `geo`, or `exists` (a node's type, else
`true`); `value` is exactly B's cell text; `relative` marks a fact the narrative style stated relative to
another node ("the same fixed cost as PLT-01"); `sentence` is the 1-based sentence index in A (null when
unstated). Stated facts come first in description order, then unstated ones by id; ids are assigned in
canonical entity order and do not change with style, order or noise.

`decoys.jsonl` lines look like
`{"id":3,"marker":"Before the 2021 restructuring,","kind":"node","entity":"SUP-04","field":"exists","value":"SUPPLIER","unit":null,"decoy_entity":true,"sentence":9}`.

`manifest.json` holds `tool_version`, `generated_at` (null unless `SOURCE_DATE_EPOCH` is set), `run_id`,
`seed`, `params` (every effective parameter), `tiers` (index, role, type, node names), `counts` (nodes,
links, products, node-products, facts total and stated, header/fact/noise sentences, actual noise ratio,
words, token estimate = words × 1.3), `expected_import` (0 errors and the exact warning counts),
`decoy_entities`, `notes` (e.g. lateral links that did not fit), `files` and the SHA-256 of every other
file.

## Report schema

`report.json`, in key order:

| Key | Content |
|---|---|
| `tool_version`, `verified_at`, `truth`, `candidate`, `run_id` | Provenance. `verified_at` is ISO-8601 UTC to the second (`SOURCE_DATE_EPOCH` when set). `run_id` comes from the manifest. |
| `manifest` | `{seed, params, counts}` from manifest.json, or null. |
| `envelope` | `source` (`file` / `directory`), `files_found`, `files_missing`, `findings` (envelope findings only). |
| `l0` | `import_valid`, `error_count`, `warning_count`, `findings` (each `{code, severity, file, line, column, element, message}`, absent keys omitted), `snrm` (SNRM's `validateOnly` report verbatim, `{"error": …}` on an HTTP error, null without `--snrm-url`), `import_valid_snrm` (its `valid`, or null), `disagreements` (`[{code, ours, snrm}]`, `[]` without `--snrm-url`). |
| `l1` | `nodes`, `links`, `products`, `node_products`: each `{truth, candidate, matched, precision, recall, f1}`, links also `recall_conditional`; `node_type_accuracy`, `structure_f1`, `missing_nodes`, `extra_nodes`, `missing_links`, `extra_links`, + `reversed_links` (truth links C has the wrong way round). |
| `l2` | `fact_recall`, `fact_precision`, `per_field` (per `sheet.field`, e.g. `nodes.capacity`: `stated` and one count per class), `fabricated` (per sheet: entity keys), `convention_violations` (mismatch records), `mismatches` (every non-correct classification: `{kind, entity, field, class, truth, truth_unit+, candidate, candidate_unit+, sentence}`), + `settings` (`{tolerance, lenient_numbers}` the scores were computed with). |
| `decoys` | `decoy_total`, `decoy_entities_absorbed` (names and keys), `decoy_values_absorbed` (`{entity, field, value, unit, sentence}`). |
| `l3` | null without `--l3`; else `{status: "ok", project_id, truth_network_id, candidate_network_id, topological, node_criticality, simulation, behavioural_equivalence, cleaned_up}`, `{status: "candidate_not_importable", snrm}` or `{status: "error", message}` (see [SNRM integration](#snrm-integration)). |
| `composite` | `{ncs}`. |

Ratios are written at full double precision; an undefined ratio (a denominator of 0) is `0.0`.

## Assumptions about the SNRM importer

The tool re-implements SNRM's import rules from SNRM's design document and its sample networks. Where
those sources leave a detail open, the tool makes the assumption listed here; each is one place to check
against the SNRM source.

| # | Item | Assumption |
|---|---|---|
| 1 | Unit-token table | `s sec secs second seconds · min mins minute minutes · h hr hrs hour hours · d day days · wk wks week weeks · mo mon month months · y yr yrs year years`, case-insensitive, trimmed |
| 2 | `POST /api/v1/networks/import` fields and report JSON | multipart/form-data: one part `files` per CSV file (`filename="<file name>"`, `text/csv; charset=UTF-8`), then text parts `name`, `projectId` (only when a project is given) and `validateOnly` (`true`/`false`); `periodLengthValue`, `periodLengthUnit`, `mapping`, `baseNetworkId` are never sent. Read from the report: `valid`, the `code` of each item of `errors[]` and `warnings[]`, and `networkId` of a committed import; the rest is stored verbatim. Also assumed : `POST /api/v1/auth/login` `{username, password}` → `{token}`; `POST /api/v1/projects` `{name}` → `{id}`; `GET /api/v1/networks/{id}/metrics/topological` → `[{code, value, scopeId}]`; `GET /api/v1/networks/{id}/nodes` → `[{id, name}]`; `DELETE` of projects, networks and runs. All in `verify/SnrmApi.java`. |
| 3 | Defaults for omitted optional columns | L3's twins zero every `failure_prob` cell of a present column and add no column, so a candidate without the column relies on SNRM's default being 0; other blank cells take SNRM's defaults in both twins alike |
| 4 | `CUSTOMER_UNREACHABLE` | no path from any SUPPLIER or PLANT node (not merely "no inbound link") |
| 5 | `ORPHAN_NODE` with only outbound links | not an orphan (orphan = no links at all) |
| 6 | Simulation `params` names and defaults | `POST /api/v1/simulations` `{networkId, params: {seed: 1, replications: 1, demandNoiseCv: 0, timingJitterPeriods: 0, recordElementTimeseries: false}}` (no `scenarioId` = baseline run; `safetyStockPriority` never sent) → `{jobId, runId}`; `GET /api/v1/jobs/{jobId}` → `{status ∈ QUEUED, RUNNING, DONE, FAILED, CANCELLED}`; `GET /api/v1/simulations/{runId}/results` → `[{code, value}]` |
| 7 | `node_products` row required per node; products referenced without `products.csv` | not required; without `products.csv` product references are not checked and `PRODUCT_CREATED_IMPLICITLY` is raised |
| 8 | Case/whitespace folding for `DUPLICATE_KEY` and references | exact after trimming |
| 9 | Present-but-blank `*_unit` cell | read as the period unit, no `UNKNOWN_TIME_UNIT` |
| 10 | Node types counting for `NO_SUPPLY_NODE` | SUPPLIER and PLANT (not DC) |
| 11 | SUPPLIER→SUPPLIER and PLANT→PLANT links | `LATERAL_LINK` warning (same type both ends); E ∈ {6, 7} should not be used in a study until confirmed |
| 12 | Scope of `OUT_OF_RANGE` | negative capacity, cost, demand, inventory, safety stock, lead time, processing time, `unit_value`; `horizon_periods < 1`; `period_length_value ≤ 0` |

The SNRM samples README states 17 errors for `broken-6-node`, but its own error table lists 16 findings;
this tool's reconstruction of that file set gives the 16 listed errors and the 11 warnings exactly
(`validate/Broken6NodeTest`).

## Design decisions

Choices the design left open, and how they were made:

**Determinism and layout**

- `reference/samples/` holds read-only copies of the SNRM sample networks and their README, which the tests use.
- `manifest.generated_at` is `null` unless `SOURCE_DATE_EPOCH` is set: a wall-clock stamp would make two
 identical runs differ.
- The manifest's `params` (and the run-id hash) contain every effective parameter except the output
 location, `--overwrite`, `--run-id` and credentials, so the manifest is identical on every machine.
- JSON is written with `\n` line endings and two-space indentation regardless of platform;
 `.gitattributes` pins LF and marks `examples/`, `reference/` and test resources `-text` so a Windows
 checkout with `core.autocrlf=true` keeps them byte-exact. Directory listings are ordered by the file
 name's ordinal string order, which is the same on every OS.
- "Changing `--seed` changes every file" holds for every file that depends on the seed;
 `truth/network_meta.csv` states only parameters and `decoys.jsonl` is empty without adversarial noise,
 so those two are identical across seeds.
- The design says changing `--naming` changes only name cells, "never a numeric cell". Rows are ordered by
 name, which the byte-exact extract round trip depends on, so another naming scheme reorders
 rows; what holds — and is tested on the written files — is that every row, keyed by its renamed
 entity, keeps every non-name cell.

**Structure, attributes and names**

- All draws iterate nodes and links in canonical order (tier, position within the tier), never in name
 order; attributes are drawn before names exist, so `--naming` cannot influence them.
- Capacity slack is drawn in thousandths (1.100–1.600) so capacities are computed exactly.
- Durations are whole periods: a lead time of k periods is written as `k × period-value` in the period
 unit. Rates (capacity, demand, holding cost) are per period *unit* — with `--period-value 1` (the
 default) that is the same as per period.
- `--lateral-links` uses each unordered DC pair at most once (no two-cycles); when fewer links fit than
 requested, the shortfall is recorded in `manifest.notes`.
- `--units mixed`: a row whose target unit is the period unit itself is skipped; otherwise one draw is
 consumed per candidate quantity and the re-expression applies only when exact.
- `--geo` coordinates are drawn last in the attributes stage, so switching `--geo` changes nothing else.
- `realistic` naming gives suppliers company names and every other node the name of a city in its own
 region (`names/cities.txt` is grouped into `# region:` sections; a plant in EU-North is a Nordic
 city). A list that runs out is reused with Roman numerals (`Rotterdam II`).
- More than 26 products are named `-A … -Z, -AA, -AB, …`.
- `--final-product-name` must be printable ASCII without commas and without surrounding whitespace.
- `--run-id` must be a plain file name (letters, digits, `.`, `_`, `-`), so `--overwrite` can never
 delete anything but a run directory.

**Description**

- A *sentence* (the ledger's `sentence`, the unit of `--order shuffled` and of noise insertion) is a
 statement unit. A template node statement is two grammatical sentences — "X is a plant in the
 EU-West region. It has a capacity of …" — kept together because the second refers to the first.
 Sentences are numbered from 1, the four header sentences first.
- Counts and unit words are singular for exactly one ("1 supplier", "1 day", "1 product").
- An inventory sentence whose initial-inventory and safety-stock clauses are both unstated
 (`--attribute-coverage` below 1) opens with "stocks {product}", so it still names its product.
- `manifest.counts.sentences_fact` counts fact statements after the header (the ones noise is
 interleaved with); `sentences_header` is reported separately.
- Self-check 3 is stricter than asks: on a template description the extractor must reproduce B
 byte for byte.
- A failed self-check writes nothing: runs are built in memory, checked, then written through a staging
 directory.
- The header paragraph is identical in every style; only the body is paraphrased or narrated.
- `paraphrased`: every phrase (node head, region phrase, each clause, link frame, …) is drawn from its
 own pool in `resources/paraphrases/`, so clauses dropped by `--attribute-coverage` still compose.
 Links of one source are merged into one group sentence when every link attribute is identical —
 equal value and unit where stated, and unstated alike. The group sentence carries all their facts.
- `narrative`: sections *Overview* (one group sentence per tier naming its members — this is where
 every node's existence and type is stated), *Facilities* (per supply-side tier, one unit per node;
 per customer tier, customers grouped by region), *Logistics lanes*, *Demand and inventory* (customers
 with identical demand grouped). A node's sentences — its name sentence, then sentences that refer
 to it by one coreference noun ("The plant", "This hub", drawn once per node) — form one statement
 unit, so noise can never separate a noun from its referent; right after a relative statement, which
 names another node of the same type, the node's name is repeated instead of the noun, so the noun can
 never be read as that other node. A value equal to that of an earlier node of the same tier (and
 product, for inventory) is stated relatively ("… has the same fixed cost as PLT-01") and its ledger
 fact is `relative:true`; the referent's absolute statement always comes first. The section structure is fixed, so `--order`
 only orders entities inside each paragraph: `shuffled` shuffles them, `by-tier` and `by-node` keep
 name order.

**Resources (names, noise, paraphrase and narrative pools)**

- `src/main/resources/` holds, one entry per line and ASCII only: `names/companies.txt` (260 fictional
 industrial supplier names), `names/cities.txt` (real cities in seven region sections),
 `noise/boilerplate.txt` (95 generic corporate sentences), `noise/topical.txt` (74 templates about the
 company and product), 22 `paraphrases/<id>.txt` pools and 12 `narrative/<id>.txt` pools.
 `describe/ResourcesTest` enforces the whole contract.
- Line 1 of every paraphrase pool is checked against the `Templates` constant, so the pool always contains
 the template sentence and cannot drift from it. Every line of a pool uses exactly the pool's
 placeholders, each once. The pieces are joined by code, so their shape is tested (leading spaces, no
 final full stop in clauses, one sentence per sentence pool, a unit word always next to its number,
 "each"/"same" in group and relative statements).
- Names are ASCII letters, digits, single spaces and hyphens, unique within and across both lists after
 normalisation, never end in a Roman numeral, and contain neither a fact-template phrase nor `and`
 (lists read "A, B and C") nor a word forbidden in noise (names are filled into topical noise). City
 names use English spellings where they exist and drop diacritics otherwise; cities that are ordinary
 English words are left out.
- Noise is stricter than : forbidden words are matched as words with their endings; other words for
 network things are avoided too (shipping, supply, cost, route, network, units, plant, hub, customer,
 …); no topical line names a node together with a region or a product. A test appends every noise line to
 a generated template description and requires the extractor to ignore each one.

**Locale, distance and geo**

- `--locale` localizes every number A writes and nothing in B; the fact ledger keeps B's plain value.
 The extractor needs the same `--locale`: a German description read as plain fails (`--strict`
 exits 4), which the tests check.
- The distance clause carries the lane mode with its article (`{mode}` = "a road" / "an ocean"), so a
 lane's speed never depends on the reader knowing its supplier's region, which `--attribute-coverage`
 may leave unstated.
- In distance mode the extractor writes the derived lead time in the period unit. B states some lead
 times in weeks or hours under `--units mixed`; the values are equal and verify perfectly, but the
 bytes differ, so self-check 3 skips its byte comparison (not its verification) in that one case.
- In distance mode, adversarial decoys about lead times state a distance too (at the lane's speed), so
 no sentence of A gives a lead time directly; `decoys.jsonl` still records the false lead time.

**Noise and decoys**

- Noise sentences are placed at uniformly random slots among the fact statements (never in the
 header) and join the paragraph of the statement that follows them, so they stay inside a section.
- Boilerplate and topical corpora are drawn without replacement until exhausted, then with
 replacement. Topical placeholders take the network's own products and node names, one of the seven
 regions, a year in 2012–2025 and a percentage in 2–45.
- Each adversarial sentence is a template sentence (template wording in every style) opened by a
 marker, stating exactly one false fact, which `decoys.jsonl` records. Marker years lie in 2008–2019:
 A carries no date, and a recent year ("Until 2024, …") could be read as still current by a model that
 assumes an earlier present.
 - Half the time it is a real entity with a false value. A numeric value moves up by 25–100% or down
 by 25–70%, and always by at least 20% after rounding; a zero becomes a small positive value. A
 region becomes another region.
 - Otherwise it is a decoy entity: a node named in the run's scheme that B does not have (the tier's
 next typed index, the next neutral number, an unused company or city of the stated region), a link
 between real nodes that B does not have, or a demand or inventory row of a decoy node.
- A decoy entity's ledger `field` is `exists` (value = its type) for a node, and otherwise the field
 its sentence states (`lead_time`, `demand`, `initial_inventory`).

**Validation (`SnrmRules`)**

- Reference integrity (`UNKNOWN_NODE_REFERENCE`, `UNKNOWN_PRODUCT_REFERENCE`) is checked against every
 name a row declares, so a node dropped for another error does not cascade into its links; its links
 then fall out of the network stage, which `NETWORK_CHECKS_ON_PARTIAL_GRAPH` flags. Every network-level
 check — the duration checks and `NO_DEMAND_DECLARED` included — sees only that graph.
- `DUPLICATE_KEY` is raised on the later row; both occurrences count as declared.
- An absent `network_meta.csv` is SNRM's default (1 DAY / NEAREST / 52 periods) and raises nothing in
 `validate`; `verify` adds its own `NETWORK_META_DEFAULTED`. Only the first data row of
 `network_meta.csv` is read.
- A sheet missing a required column reports `MISSING_REQUIRED_COLUMN` once per column and drops all its
 rows without row-level checks (for `network_meta` the fallback values are used instead).
- `PERIOD_TOO_FINE` is one finding, on the longest duration.

**Verification**

- L1 and L2 score every candidate row with a non-blank key, including rows L0 drops: a node typed
 `WAREHOUSE` is matched and its type is `wrong_type`; `n/a` in a stated field is `wrong`. A key that
 repeats after normalisation keeps its first row.
- Numbers compare exactly after unit normalisation first; two integers that are not exactly equal are
 different ("integers exact"); otherwise the relative tolerance applies. `wrong_unit` = the same
 number in a non-equivalent unit. An unknown unit token makes a present value `wrong`.
- A blank `period_length_unit` in C is not resolved through the period itself: the period-length fact is
 `wrong` (a blank value is `omitted`), where every other blank unit resolves to the candidate's period.
- **A reversed link counts as one error.** When a truth link a→b is absent from C and C has b→a (and B
 has no b→a), its existence fact is `wrong_direction` and its other facts are compared against the
 reversed candidate link, so a model that got every attribute right but the direction wrong loses one
 fact, not five; the reversed link is not fabricated at L2. At L1 the raw view is kept (a→b missing,
 b→a extra, both listed in `reversed_links`). The stricter reading — every attribute of the reversed
 link `omitted` — is a one-line change in `Verifier.scoreTruthEntity` if the study prefers it.
- Convention fields are decided by the truth node's type and score only `convention_ok` /
 `convention_violation`; a blank capacity is the only accepted value for a customer's capacity.
- With a ledger, a fact is stated iff the ledger says so; a fact the ledger does not list falls back to
 the no-ledger rule (stated iff its truth cell is non-blank).
- A fabricated entity contributes 1 + one per non-blank scored field to `fact_precision`'s denominator.
- A decoy entity in C is `fabricated` at L2, its mismatch carrying the decoy's sentence index,
 and is counted in `decoys.decoy_entities_absorbed` — wherever C uses it (as a node, a link endpoint or
 a node-product node), including decoy nodes that only a link or row decoy introduced (from
 `manifest.decoy_entities` and the decoy keys). The class `decoy_absorbed` is kept for decoy *values*:
 a real entity's field whose value equals a decoy's, which would otherwise be `wrong`, `wrong_unit`,
 `wrong_type` or `filled_unstated`. So `class_decoy_absorbed` in `results.csv` counts value
 absorptions and `decoy_entities_absorbed` entity absorptions.
- `--lenient-numbers` re-reads only cells the strict grammar rejects: on a `--locale de` run a single-group
 thousands value such as `14.500` keeps SNRM's reading, 14.5, while `1.200.000`, `2,71` and `1 200` are
 read by value. A leading group of 0 is never grouping (`0,050` = 0.05).
- Envelope: for each file the most preferred recognition form wins wherever it appears (heading, then
 marker, then header-row fallback); a less preferred block is ignored silently, and two blocks of the
 same form are an `ENVELOPE_PARSE_ERROR` (first wins). A marker line naming no file (`=== END ===`,
 `--- Answer ---`) only ends the previous section. The patterns scan in linear time, so a degenerate
 line cannot stall a batch. A file that is not valid CSV (an unterminated quote) is an
 `ENVELOPE_PARSE_ERROR` and is ignored, so the report is always written.

**SNRM integration**

- Every SNRM path and field name is a constant in `verify/SnrmApi.java`, grouped by the assumption it
 depends on, so correcting them against the SNRM source is a one-file change. `verify/SnrmClient` is the only
 class that opens a connection (`java.net.http`, JSON through Jackson, multipart built by hand with a
 boundary derived from the content's hash — no randomness).
- Exit 5 means "could not connect" and nothing else; an HTTP error answer is recorded (a failed
 self-check 5 in `generate`, `l0.snrm` or `l3` in `verify`).
- `l0.disagreements` compares counts per code, not individual findings: only the `code` field of SNRM's
 report items is relied on.
- The twins zero every `failure_prob` cell and change nothing else, so a candidate whose only defect was
 an out-of-range probability can be importable at L3; `candidate_not_importable` is judged on the twin,
 `import_valid_snrm` on C as it is.
- B's twin is imported before C's: products are project-scoped and the first import's product wins
 (samples README), so the truth defines them; the price is that a wrong `unit_value` in C does not show
 at L3 (L2 scores it). The default scratch project — one per verification — keeps products from leaking
 between runs.
- A missing metric is reported as missing, never 0; runs are deleted before networks (SNRM refuses to
 delete a network a run references), and a job still running after 10 minutes is an L3 error whose run
 must be deleted by hand.

**Batch, schema and the instruction file**

- `batch` parses every cell with `generate`'s own command line, so spellings, parsing and defaults cannot
 diverge from `generate`. Structural problems with the design file exit 2; problems with a value are
 skipped cells with a reason, so a long batch never dies on one bad cell. A skipped cell still lists its
 parameters and, when they are valid, its `run_id`.
- `index.csv` prefixes the manifest counts with `counts_` because `nodes` and `products` are both a
 parameter and a count; duplicate headers would not load cleanly in pandas.
- `verify-batch` sorts directories by plain string order (`10` before `2`) so the order is identical on
 every platform. Its scalar columns come from the report's own `scalars()` key list, so the header is
 the same whether or not any response was scored. Rows that were not scored have empty metric cells,
 and no `report.json` is written for them.
- `netbench schema` lists every column, not only the ones B writes, and tells the extractor to write
 a header with all of them, so every response's headers are the same. It states the reader's number
 grammar and that the unit cell next to a convention value holds the period unit.
- The instruction file says a value stated relative to another ("the same safety stock as X") counts as
 stated, so narrative-style descriptions do not conflict with "never invent a value".

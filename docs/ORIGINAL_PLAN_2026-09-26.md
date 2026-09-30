# Experiment Methodology — LLM Generation of SNRM-Compatible Supply Networks from Text

- **Working title:** From document to model: benchmarking LLMs on extracting simulation-ready supply networks from natural-language descriptions
- **Version:** 0.1 (draft) · 26 September 2026
- **Target tool:** SNRM — Supply Network Resilience Modelling tool (design document v0.17, 28 August 2026)

---

> **Status.** This is the plan written before the study (26 September 2026), kept for transparency. The executed
> design differs: three Claude models were run as file-writing agents (one attempt per cell) on a full grid of 41 networks
> from 6 to 500 nodes with a scale extension to 1,000 and 10,000 nodes; the self-check and tool-loop conditions, the
> six-model roster, the repeated seeds and the mixed-effects analysis were not run; SNRM's live importer and the
> behavioural layer (L3) were not used. What was done is in `RUN_LOG.md`, `AGENT_PROTOCOL.md` and the paper.

## 1. Purpose and research questions

SNRM imports a complete supply network from a set of CSV files against a canonical schema (SDD §5.4). Building such a file set by hand from corporate documents is the slow, error-prone step between "we have documents" and "we have a model". This experiment measures how well large language models (LLMs) can do that step, under controlled and reproducible conditions.

The core idea is that **the ground truth is generated, not annotated**. A rule-based generator produces a network **B** (SNRM import files) and, from the same in-memory object, a natural-language description **A**. An LLM is given A plus a fixed instruction file and asked to produce **C**, an SNRM-importable network. A rule-based checker scores C against B exactly. Because every parameter of B and A is under our control, we can vary one thing at a time and measure how each model degrades.

Research questions:

| ID | Question | Primary evidence |
|---|---|---|
| RQ1 | How accurately do current LLMs turn a textual supply-network description into an SNRM-importable network? | L0–L2 scores at the baseline condition |
| RQ2 | How does accuracy change with network size (nodes), depth (echelons), linguistic form of the description, naming scheme and the presence of distracting text? | Degradation curves over the factor levels; mixed-effects model |
| RQ3 | Do extraction errors matter for the model's *behaviour* — do B and C produce the same structural metrics and the same simulated resilience figures? | L3 behavioural equivalence |
| RQ4 | Does giving the model access to SNRM's own validator (an agentic retry loop) close the gap between models, and at what cost? | Agentic condition vs single-shot; tokens and latency per correct network |

Out of scope: extraction from real corporate documents (no ground truth), OCR/PDF layout, multi-document reconciliation, and any change to SNRM itself.

---

## 2. Pipeline overview

```text
 seed + parameters
        │
        ▼
 ┌─────────────────────┐        ┌────────────────────────────────────┐
 │  netbench generate  │──────▶ │ B: truth/  (5 canonical CSV files)  │  ground truth
 │  (rule-based, Java) │        └────────────────────────────────────┘
 │                     │──────▶  A: description/description.md         natural-language description
 │                     │──────▶  facts.jsonl, decoys.jsonl, manifest.json
 └─────────────────────┘
        │  A + llm-instructions/vN.md
        ▼
 ┌─────────────────────┐
 │  LLM under test     │──────▶  C: candidate/  (5 CSV files, or one response file)
 └─────────────────────┘
        │  B, C
        ▼
 ┌─────────────────────┐
 │  netbench verify    │──────▶  report.json  (L0 syntactic · L1 structural · L2 attribute · L3 behavioural)
 └─────────────────────┘
```

Artefact definitions:

- **A — description.** A Markdown document that states every fact of B in prose (and, when enabled, distracting sentences that state nothing true about B). A is rendered from the same object as B, so A is complete with respect to everything the checker scores.
- **B — truth.** The five canonical SNRM CSV files. B is *exactly what a perfect extractor would produce from A*: every fact stated in A appears in B, and nothing else. Attributes deliberately left unstated in A (see `--attribute-coverage`) are blank in B.
- **C — candidate.** Whatever the LLM produced, either as a directory of CSV files or as a single response file that the checker splits by the response envelope (§6.3).
- **facts.jsonl** — the atomic fact ledger: one record per (entity, field, value, unit, stated?, sentence index). This is the machine-readable form of A and what fact-level recall is computed against.
- **decoys.jsonl** — in adversarial-noise runs, the false facts that were injected, so the checker can measure whether the model absorbed them.
- **manifest.json** — all parameters, the seed, counts (nodes, links, facts, sentences, words, token estimate) and the expected SNRM import outcome for B (0 errors; the exact expected warning codes and counts).

Generation is deterministic: the same seed and parameters produce byte-identical A and B on any machine. Separate locations are used for A and B so a harness can hand a model the description without any risk of leaking the truth.

---

## 3. Target format: the SNRM canonical CSV schema

CSV is the target format (decided; the XML interchange format of SDD §5.6 is not used). A network is one directory of files named after the five canonical sheets, and the whole directory is what `POST /api/v1/networks/import` (multipart) accepts. Column headers are case-insensitive on import; `*` marks required columns.

| File | Columns (SDD §5.4) | Notes relevant to the experiment |
|---|---|---|
| `network_meta.csv` | `period_length_value*, period_length_unit*, horizon_periods*, rounding_policy, name` | Optional sheet in SNRM (defaults 1 DAY / NEAREST); **always written in B** and always stated in A. |
| `nodes.csv` | `name*, type*, capacity_value, capacity_time_unit, processing_time_value, processing_time_unit, fixed_cost, var_cost, failure_prob, region, lat, lng, pos_x, pos_y, caption, caption_visible` | `type ∈ {SUPPLIER, PLANT, DC, CUSTOMER}`; `name` unique. `pos_x/pos_y/caption/caption_visible` are never generated or scored; `lat/lng` only when `--geo` is on. |
| `links.csv` | `source*, target*, lead_time_value, lead_time_unit, capacity_value, capacity_time_unit, unit_cost, failure_prob, caption, caption_visible` | `source/target` reference `nodes.name`; duplicate pairs and self-loops are rejected by SNRM. |
| `products.csv` | `name*, unit_value` | Optional in SNRM (a default product is created if absent, with a `PRODUCT_CREATED_IMPLICITLY` warning); always written in B. |
| `node_products.csv` | `node*, product*, demand_value, demand_time_unit, initial_inventory, safety_stock, holding_cost_value, holding_cost_time_unit` | `demand_value` applies to CUSTOMER nodes; inventory fields to supply-side nodes. |

Unit semantics that the checker must reproduce (SDD §5.5):

- `TIME_UNIT ∈ {SECOND, MINUTE, HOUR, DAY, WEEK, MONTH, YEAR}`; `MONTH = 30 days`, `YEAR = 365 days` (fixed-length approximations).
- **Durations** (period length, link `lead_time`, node `processing_time`) convert to periods by `round(duration_seconds / periodSeconds, rounding_policy)`; a non-zero duration that converts to **0 periods is an import error** (`DURATION_ROUNDS_TO_ZERO`; a stated 0 is legal), a relative rounding error above 10% is a warning (`DURATION_ROUNDING_ERROR`), and a period more than 1000× finer than the longest duration is a warning (`PERIOD_TOO_FINE`).
- **Rates** (node and link `capacity`, `demand`, `holding_cost`) carry their denominator unit and rescale exactly; they never warn.
- Any `*_unit` column may be omitted, in which case the value is read in the network's period unit. Unit tokens are case-insensitive and accept singular, plural and common abbreviations (`h, hr, hrs, hour, hours`, `d`, `wk`, …).
- An empty `capacity_value` is how an **uncapped** node is stored; customers are uncapped by convention in every SNRM sample.
- `rounding_policy ∈ {NEAREST, UP, DOWN}`, default NEAREST.

Echelon semantics: SNRM knows four node types, not echelons. Its echelon order is `SUPPLIER → PLANT → DC → CUSTOMER`. A link between two nodes of the same type is legal but flagged `LATERAL_LINK`; a link running upstream is flagged `ECHELON_DIRECTION`. Therefore any network with more than four echelons (e.g. central and regional DC tiers) *necessarily* imports with `LATERAL_LINK` warnings — this is expected and recorded in the manifest, not an error. (Confirmed for DC→DC by the 50-node sample; how the importer treats PLANT→PLANT and SUPPLIER→SUPPLIER is §10 item 7.)

---

## 4. Generator design (A and B)

### 4.1 Parameters

| Flag | Meaning | Default | Levels used in the study |
|---|---|---|---|
| `--seed` | RNG seed; determines everything else | required | 5 seeds per cell |
| `--nodes` | total node count N | required | 6, 12, 25, 50, 100 |
| `--echelons` | tier count E (3–7); tier→type mapping in §4.2 | required | 3, 4, 5 |
| `--final-product-name` | name of the primary product | required | fixed per study |
| `--products` | number of products (variants of the final product) | 1 | 1 (2 in a sub-study) |
| `--naming` | `typed` (SUP-01, PLT-01 …), `neutral` (N07), `realistic` (company/city names) | typed | typed, realistic |
| `--style` | `template`, `paraphrased`, `narrative` | template | all three |
| `--order` | fact order: `by-tier`, `by-node`, `shuffled` | by-tier | by-tier, shuffled |
| `--units` | `uniform` (all values in the period unit) or `mixed` (days/weeks/hours where conversion is exact) | uniform | uniform, mixed |
| `--period-unit` / `--period-value` / `--horizon` | network time base | DAY / 1 / 90 | fixed |
| `--inbound-links` | min,max parents per non-supplier node (redundancy) | 1,2 | fixed |
| `--lateral-links` | extra DC→DC links within a tier | 0 | 0 (2 in a sub-study) |
| `--attribute-coverage` | fraction of optional attributes stated in A | 1.0 | 1.0, 0.6 |
| `--noise-type` | `none`, `boilerplate`, `topical`, `adversarial` | none | none, topical, adversarial |
| `--noise-ratio` | share of sentences that are noise | 0 | 0, 0.3, 0.6 |
| `--geo` | write `lat/lng` and state them in A | off | off |
| `--distance-mode` | `none` (state lead time) or `km` (state distance + a speed rule; the model must derive lead time) | none | none (Phase 3) |
| `--locale` | number formatting in A: `plain`, `en` (1,200), `de` (1.200) | plain | plain (Phase 3) |
| `--out-dir` | output root; A and B go to separate sub-directories | required | — |

### 4.2 Structure rules

- **Tier → type mapping** (the description uses the tier role words; the LLM instruction file states the mapping to SNRM types):

| E | Tiers (role → SNRM type) |
|---|---|
| 3 | supplier→SUPPLIER, plant→PLANT, customer→CUSTOMER |
| 4 | supplier→SUPPLIER, plant→PLANT, distribution centre→DC, customer→CUSTOMER |
| 5 | supplier, plant, **central DC→DC**, **regional DC→DC**, customer |
| 6 | supplier, **component plant→PLANT**, **assembly plant→PLANT**, central DC, regional DC, customer |
| 7 | **tier-2 supplier→SUPPLIER**, **tier-1 supplier→SUPPLIER**, component plant, assembly plant, central DC, regional DC, customer |

- **Node allocation:** one node per tier first; of the remaining N−E nodes, half go to the customer tier and half are spread over the E−1 upstream tiers (equal shares, remainder assigned from the supplier tier downwards). Works for any N ≥ E; customers approach half of N as N grows (N = 25, E = 5 → 4, 4, 3, 3, 11).
- **Links:** every node in tier t+1 draws its number of parents from `--inbound-links` (capped at the size of tier t) and chooses them at random; a first pass guarantees every node in tier t has at least one child. Result: every customer is reachable from a supplier, every supply-side node lies on a supplier→customer path, no orphans, no self-loops, no duplicate pairs, no upstream links.
- **Lateral links** (optional) are added only between DC nodes of the same tier.

### 4.3 Attribute rules and invariants

All values are drawn from role-specific ranges (tabulated in the build specification). Three invariants make B a *valid and useful* ground truth:

1. **B imports with zero errors.** The generator runs the checker's own SNRM-rule validator over B before writing it, and the manifest records the expected warning codes (`LATERAL_LINK` for E ≥ 5 or lateral links; nothing else).
2. **Every duration is an exact whole number of periods.** No rounding ever occurs, so unit normalisation in the checker is exact and B raises no resolution warnings. In `--units mixed`, alternative units are used only where the conversion is exact (e.g. 21 days ↔ 3 weeks; 2000/week for a capacity).
3. **Demand is feasible.** Each node's capacity is at least `slack × (total per-period demand of all customers reachable downstream from it)`, and each link's capacity at least `slack × (demand reachable downstream from its target)`, with `slack ∈ [1.1, 1.6]`. Routing each customer's demand along any one path then never exceeds any capacity, so the baseline simulation is demand-limited rather than a bottleneck puzzle and behavioural differences between B and C (L3) are attributable to extraction errors.

Customer conventions follow the SNRM samples: empty capacity (uncapped), zero costs, zero failure probability, demand rows only. These conventions are *not* stated in A; the LLM instruction file states them, and the checker accepts blank or 0 for the cost, failure-probability and inventory fields, and blank only for capacity (a 0 capacity would cap the customer at nothing).

### 4.4 Naming schemes

`typed` names leak the tier (`SUP-01`, `PLT-01`, `DCC-01`, `DCR-01`, `CUS-01`) and are the easy level. `neutral` names (`N01…N50`, assigned in shuffled order) leak nothing. `realistic` names are drawn from bundled word lists — company names for suppliers, city-based names for plants, DCs and customers — and are closest to corporate documents. All names are ASCII, unique, and free of commas. The checker matches names after normalisation (trim, case-fold, collapse internal whitespace, strip quotes) and never fuzzily; the LLM instruction file therefore requires names to be copied verbatim.

### 4.5 Description styles

- **`template`** — one fact per sentence from a fixed template per fact type, grouped per node then per link. This is the ceiling condition. It is also fully parseable by a rule-based extractor (`netbench extract`), which gives (a) a self-consistency check that A round-trips to B and (b) the non-LLM baseline.
- **`paraphrased`** — each sentence is drawn from a pool of at least four paraphrases per fact type; links with identical attributes from one source are merged into group statements ("PLT-01 supplies DCC-01, DCC-02 and DCC-03, each with a lead time of 2 days and a capacity of 700 units per day").
- **`narrative`** — a document with sections (Overview, Facilities, Logistics lanes, Demand and inventory), coreference ("the plant", "this hub"), relative statements ("DCR-02 holds the same safety stock as DCR-01") and group statements. Closest to a real document; hardest.

The header paragraph always states the product, the echelon and node counts per tier, and the time base (period, horizon, rounding), because `network_meta` is scored.

### 4.6 Noise tiers (mimicking corporate documents)

Noise sentences are interleaved at seeded random positions (never inside the header paragraph) until they make up `--noise-ratio` of all sentences.

| Tier | What it is | What it tests |
|---|---|---|
| `boilerplate` | Generic corporate prose unrelated to the network | Attention under length |
| `topical` | Same company and product, non-network content (sales, ESG, headcount); may name nodes but never states a scored attribute or a link | Topic-level discrimination |
| `adversarial` | Sentences built from the fact templates but marked historical, hypothetical, or foreign ("Until 2021…", "Under the proposed expansion…", "In the competitor's network…"), carrying values that differ from B or entities that do not exist in B | Temporal/modal reasoning; hallucination pressure |

Every adversarial sentence is logged in `decoys.jsonl`. The checker reports **decoy absorption**: decoy entities that appear in C and decoy values that C uses for a scored field.

---

## 5. LLM task protocol

### 5.1 Inputs to the model

1. The **instruction file** `llm-instructions/vN.md` — frozen and versioned; any change creates a new version and a new run. It contains: the task statement; the exact canonical schema table (generated from the tool's schema constants with `netbench schema`, so it cannot drift from the checker); the tier→type mapping; the customer conventions; the unit rules; the requirement to copy names verbatim and to include unit columns; and the response envelope.
2. The **description** `description.md` (A).

Nothing else: no examples of B, no SNRM documentation. A one-shot variant (one worked example of a tiny network) is a separate factor if used.

### 5.2 Decoding and logging

Fixed across all models where the API allows: temperature 0, a maximum output length large enough for the largest network (≥ 16k tokens), no tool use (single-shot condition), structured-output/JSON modes off. For each call the harness records: model identifier and version string, date, request and response token counts, latency, finish reason (a truncated response is a *completeness* failure and is analysed separately from correctness), and the raw response text.

### 5.3 Response envelope

The model returns the five files as fenced blocks under headings that name the file (`### nodes.csv` followed by a ```` ```csv ```` block). The checker's splitter tolerates `=== FILE: nodes.csv ===` markers, missing language tags and surrounding prose, and records any file it could not find. Envelope failures are counted at L0 (`ENVELOPE_PARSE_ERROR`, `FILE_MISSING`; either sets `import_valid = false`), not as content errors.

### 5.4 Conditions

| Condition | Description |
|---|---|
| **single-shot** | One request, one response. The primary condition for RQ1–RQ3. |
| **self-check** | The model is asked, in a second turn, to review its own output against the description and correct it, without any tool. Controls for "more tokens" versus "better feedback". |
| **tool-loop (agentic)** | The model may submit its files to `POST /api/v1/networks/import` with `validateOnly` set (nothing is created), read the two-stage validation report, and resubmit, up to k = 3 attempts. RQ4. |

### 5.5 Model roster

At least six models spanning vendors and sizes, including at least one small model and at least one open-weight model, each pinned to a dated version string. The roster is fixed before the main study; models added later are run on the identical artefacts and reported as additions.

---

## 6. Scoring (the checker)

### 6.1 Matching rules

- Nodes match on normalised name (§4.4). Node type is compared on matched nodes only.
- Links match on the normalised (source, target) pair; direction matters.
- Products match on normalised name; node-product rows on the normalised (node, product) pair.
- Numeric comparison: durations are normalised to seconds, rates to per-period values, before comparison; a missing `*_unit` cell is read as the period unit exactly as SNRM does. Equality is exact for integers and within a relative tolerance of 1e-6 for converted rates and decimals.
- Each field of a matched entity is classified as **correct**, **wrong**, **omitted** (blank in C, stated in A), or **filled-unstated** (value in C where A stated nothing). Fields of unmatched truth entities count as omitted; entities in C that are not in B are **fabricated**.

### 6.2 Layers

| Layer | Question | Metrics |
|---|---|---|
| **L0 syntactic** | Would SNRM accept C? | Error and warning counts by code, from the checker's re-implementation of SNRM's row-level and network-level rules (same codes: `MISSING_REQUIRED_COLUMN`, `REQUIRED_VALUE_MISSING`, `UNKNOWN_ENUM_VALUE`, `NOT_A_NUMBER`, `UNKNOWN_TIME_UNIT`, `PROBABILITY_OUT_OF_RANGE`, `OUT_OF_RANGE`, `DUPLICATE_KEY`, `SELF_LOOP`, `UNKNOWN_NODE_REFERENCE`, `UNKNOWN_PRODUCT_REFERENCE`, `NO_SUPPLY_NODE`, `NO_CUSTOMER_NODE`, `NO_DEMAND_DECLARED`, `CUSTOMER_UNREACHABLE`, `DURATION_ROUNDS_TO_ZERO`; warnings `UNMAPPED_COLUMN`, `UNRECOGNISED_SHEET`, `ROW_WIDTH_MISMATCH`, `LATERAL_LINK`, `ECHELON_DIRECTION`, `ORPHAN_NODE`, `DEMAND_ON_NON_CUSTOMER`, `DURATION_ROUNDING_ERROR`, `PERIOD_TOO_FINE`, `NETWORK_CHECKS_ON_PARTIAL_GRAPH`, `PRODUCT_CREATED_IMPLICITLY`; plus the checker's own envelope codes of §5.3). `import_valid` = zero errors. When an SNRM instance is available, the authoritative report comes from `validateOnly=true` and is stored beside the checker's. |
| **L1 structural** | Is the graph right? | Node precision/recall/F1; link P/R/F1 (raw) and link recall conditional on both endpoints matched; product and node-product P/R/F1; node-type accuracy; `structure_f1` = harmonic mean of node F1 and link F1. |
| **L2 attribute** | Are the numbers right? | Per-field correct/wrong/omitted/filled-unstated counts; **fact recall** = correct stated facts ÷ stated facts in B (entity-existence facts included); **fact precision** = correct ÷ (facts C asserts); fabrication and convention-violation counts. Decoy absorption (§4.6) is reported in its own block beside L2. |
| **L3 behavioural** | Does the error matter? | Both B and C are imported into a scratch SNRM project as *deterministic twins* (failure probabilities zeroed; demand noise and timing jitter 0; one replication; fixed seed; element time series off; safety-stock priority left at SNRM's default so that a wrongly extracted safety stock still shows) and compared on the topological suite (`DENSITY`, `SPOF_NODE_COUNT`, `SPOF_ARC_COUNT`, `SPOF_COUNT`, `AVG_PATH`, `CLUSTERING`, `ROBUSTNESS_RANDOM`, `ROBUSTNESS_TARGETED`; `NODE_CRITICALITY` per matched node) and on the seven metrics a baseline run yields (`FILL_RATE`, `SERVICE_LEVEL`, `MIN_FILL_RATE`, `TOTAL_COST`, `CVAR_COST`, `AVG_INVENTORY`, `AVG_PIPELINE`). `behavioural_equivalence` = every simulated metric within 1% relative of B's. Not computable when C fails import — reported as such, never as zero. |

### 6.3 Composite (ranking only)

`NCS = 0.2 · import_valid + 0.3 · structure_f1 + 0.5 · fact_recall`

All components are always reported; NCS exists to order models in one table and is never analysed on its own.

### 6.4 Error taxonomy

Every mismatch in every report carries one of the checker's classes — `omitted`, `wrong`, `wrong_unit`, `wrong_type`, `wrong_direction`, `filled_unstated`, `convention_violation`, `fabricated`, `decoy_absorbed` — and the sentence index of the fact in A, so that "lost-in-the-middle" position effects and per-attribute weaknesses can be reported qualitatively. Two further kinds come from outside the checker's classes: *format violations* are the L0 findings, and *truncation* is the harness's finish reason (§5.2).

---

## 7. Experimental design

### 7.1 Baselines

- **Regex extractor** (`netbench extract`) on `template` style — the deterministic ceiling; also proves A ⇔ B consistency for every generated instance.
- **Empty candidate** — defines the floor (all recall 0, `import_valid` 0).

### 7.2 Phases and budget

| Phase | Factors | Cells | Seeds × reps | Calls per model |
|---|---|---|---|---|
| Pilot (calibration, prompt freeze) | nodes {6, 12, 25, 50, 100} × style {template, narrative}; E = 4, typed, no noise | 10 | 3 × 2 | 60 |
| Main A — scale and structure | nodes {6, 12, 25, 50, 100} × echelons {3, 4, 5} × style {template, narrative}; typed, no noise | 30 | 5 × 3 | 450 |
| Main B — document realism | at nodes {12, 50}, E = 4: style {paraphrased, narrative} × noise {none, topical 0.3, adversarial 0.3, adversarial 0.6} × naming {typed, realistic} | 32 | 5 × 3 | 480 |
| Agentic (RQ4) | nodes {25, 50}, narrative, adversarial 0.3, realistic × condition {single-shot, self-check, tool-loop} | 6 | 5 × 3 | 90 |
| **Total** | | | | **≈ 1,080** |

Sub-studies if budget allows: `--units mixed`, `--products 2`, `--attribute-coverage 0.6`, `--order shuffled`, E ∈ {6, 7} (only once §10 item 7 is confirmed).

Every cell uses the *same* generated instances for every model (seeds are shared), so model comparisons are paired.

### 7.3 Analysis plan

1. **Degradation curves** — mean fact recall and structure F1 against nodes, one line per model, faceted by style; 95% CIs over seeds × reps.
2. **Breaking point** per model — the largest N at which mean fact recall ≥ 0.95.
3. **Mixed-effects model** — fact recall (or NCS) ~ model × nodes + echelons + style + noise + naming, with seed as a random intercept; report effect sizes, not only p-values. Input length (words) and output length (tokens) enter as covariates to separate "long document" from "large network".
4. **Completeness vs correctness** — truncated responses analysed separately; report the share of truncations per model and N.
5. **Behavioural relevance** — share of instances where `import_valid` and `behavioural_equivalence` disagree with a "good" L2 score, i.e. small extraction errors with large simulated consequences, and vice versa.
6. **Agentic gain** — Δ fact recall and Δ import_valid from single-shot to tool-loop, against tokens and latency per correct network.
7. **Error taxonomy** — frequency of each error kind per model and per attribute; position effect by sentence index.

---

## 8. Threats to validity and mitigations

| Threat | Mitigation |
|---|---|
| Template triviality (the task reduces to pattern matching) | Three description styles; `template` retained only as the ceiling; the regex baseline makes the point explicit |
| Name leakage (typed names reveal type/tier) | `naming` is a factor; `realistic` is the reported headline condition for RQ2 |
| Size confounded with length | Words and tokens recorded as covariates; completeness scored separately |
| Prompt sensitivity | One frozen, versioned instruction file generated from the schema constants; reported verbatim in the appendix |
| Non-deterministic decoding | ≥ 3 repetitions per (model, instance) in the main phases (2 in the pilot); temperature 0 where available; variance reported |
| Invalid ground truth | Invariants of §4.3 enforced by the generator; every B validated by the checker and, when available, by SNRM `validateOnly`; template round-trip proves A ⇔ B |
| Checker not equal to SNRM | L0 re-implementation is a convenience; authoritative L0 and all of L3 come from a pinned SNRM build; disagreements are logged |
| Ambiguity in A | A states each scored fact exactly once, with an explicit unit; "distance" is never used unless `--distance-mode km` with a stated speed rule |
| Contamination | All artefacts are novel and seeded; releasing them after publication is the accepted cost |
| Cost asymmetry between models | Tokens, latency and price recorded per call; cost per correct network reported |

---

## 9. Reproducibility package

- `snrm-netbench` (generator, extractor, checker; Java 21) with a pinned version and the SNRM build it was verified against.
- `llm-instructions/` — every instruction file version used.
- `runs/<design>/<run-id>/` — A (`description/`), B (`truth/`), `facts.jsonl`, `decoys.jsonl`, `network.full.json`, `manifest.json`, written by `netbench batch` from `<design>.json`; `runs/<design>/index.csv` lists every cell × seed with its parameters and counts.
- `responses/<model>/<run-id>/<rep>/` — `response.md` (raw), the split candidate files, `report.json`, and the call metadata (`call.json`: model version string, date, tokens, latency, finish reason, instruction-file version).
- `results.csv` — one row per response with all factor levels and every scalar metric, written by `netbench verify-batch`.
- Analysis notebooks and the exact model identifiers and dates.

---

## 10. Items to confirm against the SNRM source before the pilot

These are places where this document relies on SNRM's design document and sample README rather than on SNRM's code.

1. The exact unit-token table accepted by the importer (the checker must accept the same set — `h, hr, hrs, hour(s), d, day(s), wk, week(s), min, minute(s), s, sec, second(s), mo, month(s), y, yr, year(s)` are assumed).
2. The multipart field names of `POST /api/v1/networks/import` (files, `name`, project selector, `validateOnly`, `periodLengthValue`, `periodLengthUnit`, `mapping`, `baseNetworkId`) and the JSON shape of the validation report (`valid`, `committed`, `graphComplete`, `errors[]`, `warnings[]`, counts, `suggestedPeriod`).
3. Defaults applied to omitted optional columns (`failure_prob`, costs, `processing_time`, inventory fields) — they decide what a blank cell means at L3.
4. The exact definition of `CUSTOMER_UNREACHABLE` (no inbound link at all, or no path from a SUPPLIER).
5. Simulation parameter names and defaults in `POST /api/v1/simulations` (`params.seed`, `replications`, `demandNoiseCv`, `timingJitterPeriods`, `safetyStockPriority`, `recordElementTimeseries`) for the L3 deterministic twins.
6. Which node types satisfy the "at least one supply-side node" check (`NO_SUPPLY_NODE`): the samples README provokes it by deleting the supplier *and* the plant while the DCs remain.
7. How the importer treats SUPPLIER→SUPPLIER and PLANT→PLANT links (the editor blocks a link *into* a SUPPLIER): this decides whether 6- and 7-echelon networks are usable.
8. Whether name comparison for uniqueness and references is case-sensitive, and whether a present-but-blank unit cell is read as the period unit.

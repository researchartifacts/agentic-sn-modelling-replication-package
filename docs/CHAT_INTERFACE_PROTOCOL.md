# Protocol for chat-interface models (ChatGPT and others)

This protocol adds a model that cannot be run through the agentic harness to the same 41 networks (the
full grid, 6 to 500 nodes) that the three Claude models extracted, so that its results land in the same `results.csv` and can be
compared directly. It is the protocol for chat-interface models referred to in Section 4 of the paper.

Everything below is manual by design: one fresh chat per network, one attempt per network, the
response saved verbatim, and scoring left entirely to `netbench verify-batch`. The person running it
never opens a `truth/` folder while a chat is open.

## 1. What you need

| Item | Where |
|---|---|
| 43 ready-to-paste prompts, one per network (41 grid + 2 extension) | `python scripts/make_chat_prompts.py` writes them to `work/chat-prompts/<run-id>.md` (instruction file v1 + separator + description, concatenated). The prompt versions v1.1 and v1.2 used for some Claude runs only add sentences about the harness's file-writing tools and reasoning length; they do not apply to a chat interface, so every prompt here is v1. |
| The instruction file on its own | `protocol/llm-instructions-v1.md` (identical to the copy embedded in every prompt) |
| The design networks with their truth | `data/runs-llm/` (the 41 networks listed below and the four scale-extension networks of Section 3a) |
| The checker | `snrm-netbench/target/netbench.jar` after `mvn -q package` (`./netbench` wrapper) |
| The Claude results to compare against | `data/results.csv` (153 rows: 134 completed runs, 12 failed attempts, 7 rule-based baseline runs; 12 of the rows are the scale-extension runs of Section 3a) |

## 2. Settings to fix before the first network

Record these once in `data/responses/<model>/README.md` and repeat the relevant ones in every
`call.json` (Section 4):

1. **Model and reasoning setting.** Use one model and one reasoning/effort setting for all 41 networks
   (for example the ones used in the 3-node pilot). Write the exact names as the interface shows them.
2. **Fresh conversation per network.** Start a new chat for every network. Do not reuse a chat, and do
   not paste two networks into one chat.
3. **No carry-over features.** Turn off memory, custom instructions and any "project" or system prompt
   for these chats. Turn off web browsing and code execution if the interface allows it; if it does not,
   record that they were on.
4. **Paste, do not upload, when possible.** Paste the whole prompt file as one message. If the
   interface refuses the paste because of length (the 200-node prompts are about 20,000 words and the
   500-node prompts 43,000 to 55,000 words), upload the
   prompt file as an attachment with the message "Apply the instructions in the attached file to the
   document it contains" and record `"input_mode": "upload"` in `call.json`.
5. **One attempt.** The first complete response is the result. If the response is cut off, refuses, or
   returns fewer than five CSV blocks, save it anyway as rep 1 with `"status": "failed"` and the reason;
   you may then run a second attempt as rep 2 (Section 4) and it will be reported as a retry, exactly as
   the Sonnet 5 retries are reported in Section 4 of the paper.
6. **No help.** Do not correct, re-prompt, or ask the model to fix anything after its first answer.
   "Continue" is allowed only if the interface itself truncated the output mid-block; record it.

## 3. The 41 networks (same run-ids as the Claude runs)

Do them in this order (smallest first), so that any interface limit shows up before the long ones.

| # | run-id | Nodes | Echelons | Style | Noise | Words |
|---|---|---|---|---|---|---|
| 1 | s1-n6-e3-narrative-typed-uniform-none0-f84ef3a2 | 6 | 3 | narrative | none | 635 |
| 2 | s1-n6-e5-narrative-typed-uniform-none0-1fe66829 | 6 | 5 | narrative | none | 690 |
| 3 | s1-n6-e5-narrative-typed-uniform-topical0.3-0fb1cf0b | 6 | 5 | narrative | topical 30% | 796 |
| 4 | s1-n6-e5-narrative-typed-uniform-adversarial0.3-17412e63 | 6 | 5 | narrative | adversarial 30% | 840 |
| 5 | s1-n6-e5-template-typed-uniform-none0-5ebabd5a | 6 | 5 | template | none | 613 |
| 6 | s1-n12-e3-narrative-typed-uniform-none0-db696bc3 | 12 | 3 | narrative | none | 1,197 |
| 7 | s1-n12-e5-narrative-typed-uniform-none0-4db1fe32 | 12 | 5 | narrative | none | 1,280 |
| 8 | s1-n12-e7-narrative-typed-uniform-none0-3489af73 | 12 | 7 | narrative | none | 1,323 |
| 9 | s1-n12-e5-narrative-typed-uniform-topical0.3-c06df827 | 12 | 5 | narrative | topical 30% | 1,478 |
| 10 | s1-n12-e5-narrative-typed-uniform-adversarial0.3-82496d1c | 12 | 5 | narrative | adversarial 30% | 1,559 |
| 11 | s1-n12-e5-template-typed-uniform-none0-cb284b12 | 12 | 5 | template | none | 1,181 |
| 12 | s1-n25-e3-narrative-typed-uniform-none0-d1783a93 | 25 | 3 | narrative | none | 2,246 |
| 13 | s1-n25-e5-narrative-typed-uniform-none0-9a9a7f5f | 25 | 5 | narrative | none | 2,413 |
| 14 | s1-n25-e7-narrative-typed-uniform-none0-3d0aa8a7 | 25 | 7 | narrative | none | 2,589 |
| 15 | s1-n25-e5-narrative-typed-uniform-topical0.3-77b71d8d | 25 | 5 | narrative | topical 30% | 2,812 |
| 16 | s1-n25-e5-narrative-typed-uniform-adversarial0.3-7d791dc9 | 25 | 5 | narrative | adversarial 30% | 2,910 |
| 17 | s1-n25-e5-template-typed-uniform-none0-0f93eb0d | 25 | 5 | template | none | 2,296 |
| 18 | s1-n50-e3-narrative-typed-uniform-none0-a7bacd9e | 50 | 3 | narrative | none | 4,422 |
| 19 | s1-n50-e5-narrative-typed-uniform-none0-9395e03e | 50 | 5 | narrative | none | 4,743 |
| 20 | s1-n50-e7-narrative-typed-uniform-none0-344d5fb7 | 50 | 7 | narrative | none | 4,816 |
| 21 | s1-n50-e5-narrative-typed-uniform-topical0.3-20cc50fc | 50 | 5 | narrative | topical 30% | 5,483 |
| 22 | s1-n50-e5-narrative-typed-uniform-adversarial0.3-fbabfcfe | 50 | 5 | narrative | adversarial 30% | 5,681 |
| 23 | s1-n50-e5-template-typed-uniform-none0-7cf91e25 | 50 | 5 | template | none | 4,540 |
| 24 | s1-n100-e3-narrative-typed-uniform-none0-a86ac33e | 100 | 3 | narrative | none | 8,701 |
| 25 | s1-n100-e5-narrative-typed-uniform-none0-b6562a2f | 100 | 5 | narrative | none | 9,250 |
| 26 | s1-n100-e7-narrative-typed-uniform-none0-e87d2ad6 | 100 | 7 | narrative | none | 9,547 |
| 27 | s1-n100-e5-narrative-typed-uniform-topical0.3-d26f808c | 100 | 5 | narrative | topical 30% | 10,679 |
| 28 | s1-n100-e5-narrative-typed-uniform-adversarial0.3-1ef3e0d9 | 100 | 5 | narrative | adversarial 30% | 11,024 |
| 29 | s1-n100-e5-template-typed-uniform-none0-13f737a9 | 100 | 5 | template | none | 8,870 |
| 30 | s1-n200-e3-narrative-typed-uniform-none0-752a18b3 | 200 | 3 | narrative | none | 17,087 |
| 31 | s1-n200-e5-narrative-typed-uniform-none0-0fae8826 | 200 | 5 | narrative | none | 18,427 |
| 32 | s1-n200-e7-narrative-typed-uniform-none0-aa56d562 | 200 | 7 | narrative | none | 18,867 |
| 33 | s1-n200-e5-narrative-typed-uniform-topical0.3-7037ac06 | 200 | 5 | narrative | topical 30% | 21,027 |
| 34 | s1-n200-e5-narrative-typed-uniform-adversarial0.3-dad255f4 | 200 | 5 | narrative | adversarial 30% | 21,800 |
| 35 | s1-n200-e5-template-typed-uniform-none0-4a8424d4 | 200 | 5 | template | none | 17,889 |
| 36 | s1-n500-e3-narrative-typed-uniform-none0-228c9248 | 500 | 3 | narrative | none | 41,659 |
| 37 | s1-n500-e5-narrative-typed-uniform-none0-6ee0683f | 500 | 5 | narrative | none | 45,184 |
| 38 | s1-n500-e7-narrative-typed-uniform-none0-e8d5ce0a | 500 | 7 | narrative | none | 45,949 |
| 39 | s1-n500-e5-narrative-typed-uniform-topical0.3-b6bf4d5b | 500 | 5 | narrative | topical 30% | 51,423 |
| 40 | s1-n500-e5-narrative-typed-uniform-adversarial0.3-9ee05811 | 500 | 5 | narrative | adversarial 30% | 53,002 |
| 41 | s1-n500-e5-template-typed-uniform-none0-c5b16bb3 | 500 | 5 | template | none | 44,393 |

All 41 networks of the grid are in this list; the design is the full grid of seven sizes by six
conditions (6 nodes cannot host seven echelons).

### 3a. Scale extension (optional, after the 41)

The paper adds four seven-echelon networks at 1,000 and 10,000 nodes (in `data/runs-llm/`),
each extracted once by each Claude model. Two of them have a prompt file and can be attempted in a chat interface
if it accepts the input (about 0.5 to 0.6 MB, 92 to 108 thousand words); save and score them exactly as the grid
networks, and report them separately as the extension (Table 3 and Figure 9 of the paper).

| # | run-id | Nodes | Echelons | Style | Noise | Words |
|---|---|---|---|---|---|---|
| 42 | s1-n1000-e7-narrative-typed-uniform-none0-de118b63 | 1,000 | 7 | narrative | none | 91,683 |
| 43 | s1-n1000-e7-narrative-typed-uniform-adversarial0.3-67c4defd | 1,000 | 7 | narrative | adversarial 30% | 107,503 |

The two 10,000-node networks (`s1-n10000-e7-narrative-typed-uniform-none0-5c95bcf3`, 910,743 words, and
`s1-n10000-e7-narrative-typed-uniform-adversarial0.3-a56c684f`, 1,065,362 words) have no prompt file: their
descriptions (5.1 and 6.0 MB) exceed the input limit of every chat interface we know of and exceeded the Claude
models' context window in the harness, where no model returned an importable file set. If an interface accepts
them, the prompt is assembled the same way (instruction file v1, the separator, then `description/description.md`).

## 4. Saving each response

Use the model's name as the folder (lower-case, no spaces, for example `chatgpt-5-2-thinking-medium`):

```
data/responses/<model>/<run-id>/1/response.md    ← the assistant's whole reply, verbatim
data/responses/<model>/<run-id>/1/call.json      ← metadata for that attempt
```

`response.md` is a copy-paste of the entire reply, including the `### network_meta.csv` headings and
the five fenced ```` ```csv ```` blocks. Do not edit, reformat or "fix" it; `verify-batch` splits the blocks
itself and writes them to `response.md.split/`.

`call.json` (one per attempt):

```json
{
  "model_alias": "chatgpt",
  "reported_model": "<exact model name shown in the interface>",
  "reasoning": "<effort or mode shown in the interface, e.g. medium>",
  "interface": "chat.openai.com web",
  "input_mode": "paste",
  "attempt": 1,
  "status": "completed",
  "recorded_at": "2026-10-03T10:15:00+11:00",
  "duration_seconds": 95,
  "browsing": "off",
  "code_execution": "off",
  "memory": "off",
  "note": ""
}
```

`duration_seconds` is the time from sending the message to the end of the reply (a stopwatch is
accurate enough). Leave `reported_model` as the interface names it; do not guess a dated snapshot. A
failed attempt keeps `"status": "failed"` and a `"note"` saying what happened; a retry goes in
`.../<run-id>/2/` with `"attempt": 2` and a note saying what changed (normally nothing).

## 5. Scoring

From the repository root:

```bash
# one network, while you are checking the first paste worked
snrm-netbench/netbench verify --truth data/runs-llm/<run-id>/truth \
                  --candidate data/responses/<model>/<run-id>/1/response.md --out data/responses/<model>/<run-id>/1/report.json

# everything under data/responses/, one row per rep directory
snrm-netbench/netbench verify-batch --runs data/runs-llm --responses data/responses \
                        --out data/results.csv
```

`verify-batch` writes `report.json` beside every `response.md` and one flat CSV with the same columns
as the shipped `data/results.csv`; because the Claude responses are in the same `data/responses/` folder, the
output CSV contains every model in one table. The `call_*` columns are the top-level scalar fields of each
`call.json`.

## 6. Reading the result

The columns that the paper reports are: `import_valid`, `error_count`, `structure_f1`,
`fact_recall`, `fact_precision`, `facts_stated`, `fabricated_total`, `decoy_entities_absorbed`,
`decoy_values_absorbed` and the `l0_<CODE>` counts. Name-adjusted recall (the network-name ambiguity
described in Section 5.1 of the paper) is (facts correct + network-name mismatches) ÷ `facts_stated`, with the
name mismatches read from `l2.mismatches` of the `report.json` (`docs/DATA_DICTIONARY.md`); it is computed the same
way for every model name that appears in the `model` column.

Things to look for first, because they were the failure modes seen so far: (1) customer rows or
inventory rows with a misplaced cell (`UNKNOWN_TIME_UNIT` / `NOT_A_NUMBER` errors), (2) the network
name written as `Widget` instead of `Widget supply network` (a single `meta.name: wrong` mismatch,
reported as name-adjusted recall), (3) at 500 nodes, omitted or invented links (`links_recall`,
`fabricated_links`, `CUSTOMER_UNREACHABLE`) and responses that stop before the fifth file
(`FILE_MISSING`), and (4) wrong regions or demands for customers named in grouped sentences.

## 7. Reporting it

Add one row to Table 2, one column to Table 3, one or two columns to Table 4 and a line to Figure 9, and state in the methods the model name, reasoning setting, interface, dates and the
settings in Section 2 above. Any retry is a protocol deviation and is reported the way the Sonnet 5
retries are reported in Section 4 of the paper.

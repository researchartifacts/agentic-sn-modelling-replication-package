## Files and columns

Five CSV files, comma-separated. Each starts with a header row naming every column of the file below, lower-case, in the order shown; every further row is one record. `*` marks a column SNRM requires; any other cell may be left blank. A number is plain digits with an optional `.` fraction (`1200`, `0.05`): no thousands separators, and no units or other text in the cell.

### network_meta.csv

| Column | Required | Kind | Meaning |
|---|---|---|---|
| `period_length_value` | * | number | length of one simulation period, in period_length_unit |
| `period_length_unit` | * | time unit | unit of the period length |
| `horizon_periods` | * | number | number of periods simulated |
| `rounding_policy` |  | rounding policy | how durations round to whole periods; default NEAREST |
| `name` |  | text | the network's own name |

### nodes.csv

| Column | Required | Kind | Meaning |
|---|---|---|---|
| `name` | * | text | unique node name |
| `type` | * | node type | SUPPLIER, PLANT, DC or CUSTOMER |
| `capacity_value` |  | number | rate: units per capacity_time_unit; blank = uncapped |
| `capacity_time_unit` |  | time unit | denominator of capacity_value |
| `processing_time_value` |  | number | duration in processing_time_unit |
| `processing_time_unit` |  | time unit | unit of processing_time_value |
| `fixed_cost` |  | number | cost per period |
| `var_cost` |  | number | cost per unit |
| `failure_prob` |  | number | probability in [0, 1] |
| `region` |  | text | region tag |
| `lat` |  | number | latitude in degrees |
| `lng` |  | number | longitude in degrees |
| `pos_x` |  | number | editor canvas x (not geography) |
| `pos_y` |  | number | editor canvas y (not geography) |
| `caption` |  | text | annotation drawn under the name |
| `caption_visible` |  | true/false | true/false; blank = visible |

### links.csv

| Column | Required | Kind | Meaning |
|---|---|---|---|
| `source` | * | text | name of the node the link leaves |
| `target` | * | text | name of the node the link enters |
| `lead_time_value` |  | number | duration in lead_time_unit |
| `lead_time_unit` |  | time unit | unit of lead_time_value |
| `capacity_value` |  | number | rate: units per capacity_time_unit; blank = uncapped |
| `capacity_time_unit` |  | time unit | denominator of capacity_value |
| `unit_cost` |  | number | transport cost per unit |
| `failure_prob` |  | number | probability in [0, 1] |
| `caption` |  | text | annotation drawn under the lead time |
| `caption_visible` |  | true/false | true/false; blank = visible |

### products.csv

| Column | Required | Kind | Meaning |
|---|---|---|---|
| `name` | * | text | unique product name |
| `unit_value` |  | number | value of one unit (the cost of a unit of unmet demand) |

### node_products.csv

| Column | Required | Kind | Meaning |
|---|---|---|---|
| `node` | * | text | node name |
| `product` | * | text | product name |
| `demand_value` |  | number | rate: units per demand_time_unit (customers only) |
| `demand_time_unit` |  | time unit | denominator of demand_value |
| `initial_inventory` |  | number | units on hand at period 0 |
| `safety_stock` |  | number | units |
| `holding_cost_value` |  | number | rate: cost per unit per holding_cost_time_unit |
| `holding_cost_time_unit` |  | time unit | denominator of holding_cost_value |

### Node types

`type` is one of:

| Value | Echelon rank |
|---|---|
| `SUPPLIER` | 0 |
| `PLANT` | 1 |
| `DC` | 2 |
| `CUSTOMER` | 3 |

### Time units

A `*_unit` cell holds one of these units, written as its name or as any of its tokens (case-insensitive):

| Unit | Seconds | Tokens |
|---|---|---|
| `SECOND` | 1 | `s`, `sec`, `secs`, `second`, `seconds` |
| `MINUTE` | 60 | `min`, `mins`, `minute`, `minutes` |
| `HOUR` | 3600 | `h`, `hr`, `hrs`, `hour`, `hours` |
| `DAY` | 86400 | `d`, `day`, `days` |
| `WEEK` | 604800 (7 days) | `wk`, `wks`, `week`, `weeks` |
| `MONTH` | 2592000 (30 days) | `mo`, `mon`, `month`, `months` |
| `YEAR` | 31536000 (365 days) | `y`, `yr`, `yrs`, `year`, `years` |

### Rounding policies

`rounding_policy` is one of:

| Value | Durations are rounded | 1.25 periods become | 1.5 periods become |
|---|---|---|---|
| `NEAREST` | to the nearest period | 1 | 2 |
| `UP` | up to the next whole period | 2 | 2 |
| `DOWN` | down to the previous whole period | 1 | 1 |

### Units

- Durations (the value is a length of time, measured in its unit):
  - network_meta.csv: `period_length_value` in `period_length_unit`
  - nodes.csv: `processing_time_value` in `processing_time_unit`
  - links.csv: `lead_time_value` in `lead_time_unit`
- Rates (the unit is the time denominator: so many per one unit of time):
  - nodes.csv: `capacity_value` per `capacity_time_unit`
  - links.csv: `capacity_value` per `capacity_time_unit`
  - node_products.csv: `demand_value` per `demand_time_unit`
  - node_products.csv: `holding_cost_value` per `holding_cost_time_unit`
- A missing or blank `*_unit` column or cell means the period unit (`period_length_unit`).
- A blank `capacity_value` (nodes.csv, links.csv) means uncapped.

### Conventions

Cells fixed by rule for the type of the row's node; a description never states them:

| Node type | File | Column | Value |
|---|---|---|---|
| `CUSTOMER` | nodes.csv | `capacity_value` | blank |
| `CUSTOMER` | nodes.csv | `processing_time_value` | `0` |
| `CUSTOMER` | nodes.csv | `fixed_cost` | `0` |
| `CUSTOMER` | nodes.csv | `var_cost` | `0` |
| `CUSTOMER` | nodes.csv | `failure_prob` | `0` |
| `SUPPLIER`, `PLANT`, `DC` | node_products.csv | `demand_value` | `0` |
| `CUSTOMER` | node_products.csv | `initial_inventory` | `0` |
| `CUSTOMER` | node_products.csv | `safety_stock` | `0` |
| `CUSTOMER` | node_products.csv | `holding_cost_value` | `0` |

`0` may also be left blank. The unit cell next to a convention value holds the period unit (`period_length_unit`).

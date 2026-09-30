# Supply network description: Widget

This document describes the "Widget supply network". The network has 7 echelons and 12 nodes: 2 tier-2 suppliers, 2 tier-1 suppliers, 2 component plants, 1 assembly plant, 1 central distribution centre, 1 regional distribution centre and 3 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 69).

## Overview

The 2 tier-2 suppliers are SP2-01 and SP2-02. The 2 tier-1 suppliers are SP1-01 and SP1-02. The 2 component plants are PLC-01 and PLC-02. PLA-01 is the network's sole assembly plant. There is just one central distribution centre in the network, DCC-01. There is just one regional distribution centre in the network, DCR-01. The network has 3 customers: CUS-01, CUS-02 and CUS-03.

## Facilities

SP2-01 is located in the AMER region. This supplier is configured with a throughput capacity of 90 units each day, a processing time of 0 days, a fixed operating cost of 700 per period, a variable cost of 2.21 per unit and a failure likelihood of 0.04. In terms of location, SP2-02 falls within the AMER region. This vendor is configured with a fixed operating cost of 650 per period, a variable charge of 1.93 per unit and a 0.03 probability of failure. This vendor has the same capacity as SP2-01. SP2-02 and SP2-01 have the same processing time.

SP1-01 sits in the APAC region. The supplier is characterised by the capacity to handle 110 units per day, a processing time of 0 days, a fixed charge of 2100 per period, a variable cost of 1.94 per unit and a likelihood of failure of 0.01. SP1-02 sits in the EU-South region. This supplier is modelled with a capacity of 120 units every day, fixed costs of 500 per period, a variable cost amounting to 1.61 per unit and a failure probability of 0.06. This supplier has exactly the same processing time as SP1-01.

PLC-01 is located in the EU-South region. This factory has a capacity of 100 units every day, a standard processing time of 3 days, a fixed charge of 5500 per period, a variable cost amounting to 5.53 per unit and a failure likelihood of 0.01. In terms of location, PLC-02 falls within the EU-South region. This plant has a capacity of 90 units every day, a processing time of 2 days, a fixed cost of 5100 per period, a variable cost of 7.00 per unit and a failure likelihood of 0.02.

PLA-01 sits in the EU-North region. This plant has a maximum throughput of 90 units per day, a processing time of 2 days, a fixed operating cost of 9900 per period, a variable charge of 6.14 per unit and a probability of failure of 0.01.

DCC-01 operates in the EU-East region. The warehouse operates with a capacity of 100 units every day, 0 days of processing time, a fixed cost of 4900 per period, variable costs of 1.47 per unit and a failure probability of 0.03.

In terms of location, DCR-01 falls within the EU-Central region. This distribution centre is modelled with a capacity of 90 units every day, an internal processing time of 0 days, a fixed operating cost of 2200 per period, variable costs of 1.00 per unit and a probability of failure of 0.01.

CUS-01 can be found in the EU-South region. CUS-02 is located in the EU-Central region. CUS-03 sits in the EU-East region.

## Logistics lanes

The lane from SP2-01 to SP1-02 has 4 days of lead time, a link capacity of 100 units every day, a transport cost of 0.27 per unit and a 0.05 probability of link failure. The link from SP2-02 to SP1-01 has a lead time of 5 days, a lane capacity of 90 units per day, a shipping cost of 0.27 per unit and a lane failure probability of 0.02. SP2-02 delivers to SP1-02 with 5 days of lead time, a transport capacity of 100 units per day, a freight cost of 0.25 per unit and a link failure likelihood of 0.04.

SP1-01 delivers to PLC-01 with a transit time of 21 days, a link capacity of 100 units every day, transport costs of 2.48 per unit and a link failure likelihood of 0.05. SP1-02 delivers to PLC-01 with a delivery time of 4 days, a link capacity of 100 units per day, a transport cost amounting to 0.39 per unit and a link failure likelihood of 0.05. SP1-02 sends goods to PLC-02 with a delivery time of 4 days, a lane capacity of 120 units per day, a transport cost amounting to 0.54 per unit and a failure probability of 0.06.

PLC-01 ships to PLA-01 with a replenishment lead time of 1 day, a link capacity of 110 units every day, a shipping cost of 0.36 per unit and a link failure probability of 0.03. Shipments from PLC-02 to PLA-01 have a lead time of 3 days, a lane capacity of 90 units per day, transport costs of 0.36 per unit and a lane failure probability of 0.05.

PLA-01 ships to DCC-01 with a delivery time of 1 day, a link capacity of 100 units per day, a transport cost of 0.32 per unit and a failure probability of 0.01.

DCC-01 delivers to DCR-01 with 2 days of lead time, a lane capacity of 90 units per day, a transport cost amounting to 0.32 per unit and a lane failure probability of 0.02.

DCR-01 ships to CUS-01 with a transit time of 1 day, a lane capacity of 20 units per day, transport costs of 0.25 per unit and a link failure probability of 0.06. The link from DCR-01 to CUS-02 has 1 day of lead time, a link capacity of 40 units every day, transport costs of 0.19 per unit and a link failure probability of 0.06. DCR-01 ships to CUS-03 with a replenishment lead time of 3 days, a transport capacity of 60 units per day, a transport cost of 0.24 per unit and a link failure probability of 0.05.

## Demand and inventory

On inventory, SP2-01 starts with 288 units of Widget in stock, targets a safety stock of 144 units of Widget and incurs a holding cost of 0.06 per unit per day. SP2-02 currently holds an initial inventory of 216 units of Widget, targets a safety stock of 108 units of Widget and incurs a holding cost of 0.44 per unit per day.

On inventory, SP1-01 has an opening inventory of 288 units of Widget, holds a safety stock of 144 units of Widget and incurs holding costs of 0.16 per unit per day. On inventory, SP1-02 starts with an initial inventory of 216 units of Widget, keeps a safety stock of 108 units of Widget and incurs a holding cost of 0.36 per unit per day.

PLC-01 also holds an initial inventory of 288 units of Widget, keeps 144 units of Widget as safety stock and incurs a holding cost of 0.36 per unit per day. On inventory, PLC-02 holds an initial inventory of 216 units of Widget, holds a safety stock of 108 units of Widget and pays a holding cost of 0.23 per unit per day.

PLA-01 currently begins the horizon with 144 units of Widget on hand, maintains a safety stock of 72 units of Widget and pays a holding cost of 0.04 per unit per day.

DCC-01 starts with 360 units of Widget in stock, keeps 180 units of Widget as safety stock and has a holding cost of 0.46 per unit per day.

On inventory, DCR-01 begins the horizon with 216 units of Widget on hand, keeps a safety stock of 108 units of Widget and incurs a holding cost of 0.31 per unit per day.

CUS-01 requires 11 units of Widget per day. CUS-02 demands 22 units of Widget per day. CUS-03 orders 39 units of Widget every day.

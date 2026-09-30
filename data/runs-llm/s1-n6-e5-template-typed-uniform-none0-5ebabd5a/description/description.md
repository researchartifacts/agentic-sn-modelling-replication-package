# Supply network description: Widget

This document describes the "Widget supply network". The network has 5 echelons and 6 nodes: 2 suppliers, 1 plant, 1 central distribution centre, 1 regional distribution centre and 1 customer. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 78).

SUP-01 is a supplier in the AMER region. It has a capacity of 70 units per day, a processing time of 0 days, a fixed cost of 1750 per period, a variable cost of 2.88 per unit and a failure probability of 0.04.
SUP-01 holds an initial inventory of 98 units of Widget, keeps a safety stock of 49 units of Widget and has a holding cost of 0.29 per unit per day.
SUP-02 is a supplier in the AMER region. It has a capacity of 80 units per day, a processing time of 0 days, a fixed cost of 2200 per period, a variable cost of 2.36 per unit and a failure probability of 0.07.
SUP-02 holds an initial inventory of 196 units of Widget, keeps a safety stock of 98 units of Widget and has a holding cost of 0.26 per unit per day.

PLT-01 is a plant in the EU-South region. It has a capacity of 70 units per day, a processing time of 2 days, a fixed cost of 8100 per period, a variable cost of 5.96 per unit and a failure probability of 0.02.
PLT-01 holds an initial inventory of 245 units of Widget, keeps a safety stock of 123 units of Widget and has a holding cost of 0.35 per unit per day.

DCC-01 is a central distribution centre in the EU-North region. It has a capacity of 60 units per day, a processing time of 0 days, a fixed cost of 2000 per period, a variable cost of 0.88 per unit and a failure probability of 0.01.
DCC-01 holds an initial inventory of 147 units of Widget, keeps a safety stock of 74 units of Widget and has a holding cost of 0.28 per unit per day.

DCR-01 is a regional distribution centre in the EU-North region. It has a capacity of 60 units per day, a processing time of 1 day, a fixed cost of 3700 per period, a variable cost of 0.92 per unit and a failure probability of 0.02.
DCR-01 holds an initial inventory of 245 units of Widget, keeps a safety stock of 123 units of Widget and has a holding cost of 0.04 per unit per day.

CUS-01 is a customer in the EU-West region.
CUS-01 demands 49 units of Widget per day.

SUP-01 ships to PLT-01 with a lead time of 14 days, a link capacity of 80 units per day, a transport cost of 2.09 per unit and a link failure probability of 0.06.
SUP-02 ships to PLT-01 with a lead time of 28 days, a link capacity of 70 units per day, a transport cost of 2.04 per unit and a link failure probability of 0.05.

PLT-01 ships to DCC-01 with a lead time of 1 day, a link capacity of 70 units per day, a transport cost of 0.28 per unit and a link failure probability of 0.01.

DCC-01 ships to DCR-01 with a lead time of 4 days, a link capacity of 70 units per day, a transport cost of 0.42 per unit and a link failure probability of 0.03.

DCR-01 ships to CUS-01 with a lead time of 1 day, a link capacity of 80 units per day, a transport cost of 0.21 per unit and a link failure probability of 0.04.

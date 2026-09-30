# Supply network description: Widget

This document describes the "Widget supply network". The network has 5 echelons and 12 nodes: 2 suppliers, 2 plants, 2 central distribution centres, 2 regional distribution centres and 4 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 75).

SUP-01 is a supplier in the AMER region. It has a capacity of 180 units per day, a processing time of 0 days, a fixed cost of 550 per period, a variable cost of 2.07 per unit and a failure probability of 0.07.
SUP-01 holds an initial inventory of 570 units of Widget, keeps a safety stock of 285 units of Widget and has a holding cost of 0.06 per unit per day.
SUP-02 is a supplier in the AMER region. It has a capacity of 130 units per day, a processing time of 0 days, a fixed cost of 900 per period, a variable cost of 2.26 per unit and a failure probability of 0.04.
SUP-02 holds an initial inventory of 456 units of Widget, keeps a safety stock of 228 units of Widget and has a holding cost of 0.06 per unit per day.

PLT-01 is a plant in the EU-South region. It has a capacity of 150 units per day, a processing time of 1 day, a fixed cost of 14700 per period, a variable cost of 6.31 per unit and a failure probability of 0.01.
PLT-01 holds an initial inventory of 306 units of Widget, keeps a safety stock of 153 units of Widget and has a holding cost of 0.44 per unit per day.
PLT-02 is a plant in the EU-North region. It has a capacity of 150 units per day, a processing time of 3 days, a fixed cost of 13500 per period, a variable cost of 6.07 per unit and a failure probability of 0.03.
PLT-02 holds an initial inventory of 456 units of Widget, keeps a safety stock of 228 units of Widget and has a holding cost of 0.16 per unit per day.

DCC-01 is a central distribution centre in the EU-North region. It has a capacity of 130 units per day, a processing time of 1 day, a fixed cost of 4000 per period, a variable cost of 1.37 per unit and a failure probability of 0.03.
DCC-01 holds an initial inventory of 306 units of Widget, keeps a safety stock of 153 units of Widget and has a holding cost of 0.36 per unit per day.
DCC-02 is a central distribution centre in the EU-West region. It has a capacity of 140 units per day, a processing time of 1 day, a fixed cost of 1850 per period, a variable cost of 0.92 per unit and a failure probability of 0.03.
DCC-02 holds an initial inventory of 396 units of Widget, keeps a safety stock of 198 units of Widget and has a holding cost of 0.36 per unit per day.

DCR-01 is a regional distribution centre in the EU-West region. It has a capacity of 160 units per day, a processing time of 0 days, a fixed cost of 3250 per period, a variable cost of 0.99 per unit and a failure probability of 0.01.
DCR-01 holds an initial inventory of 306 units of Widget, keeps a safety stock of 153 units of Widget and has a holding cost of 0.23 per unit per day.
DCR-02 is a regional distribution centre in the EU-South region. It has a capacity of 140 units per day, a processing time of 0 days, a fixed cost of 2100 per period, a variable cost of 1.32 per unit and a failure probability of 0.02.
DCR-02 holds an initial inventory of 198 units of Widget, keeps a safety stock of 99 units of Widget and has a holding cost of 0.04 per unit per day.

CUS-01 is a customer in the EU-South region.
CUS-01 demands 53 units of Widget per day.
CUS-02 is a customer in the EU-South region.
CUS-02 demands 34 units of Widget per day.
CUS-03 is a customer in the EU-North region.
CUS-03 demands 15 units of Widget per day.
CUS-04 is a customer in the EU-East region.
CUS-04 demands 12 units of Widget per day.

SUP-01 ships to PLT-02 with a lead time of 14 days, a link capacity of 140 units per day, a transport cost of 1.79 per unit and a link failure probability of 0.01.
SUP-02 ships to PLT-01 with a lead time of 14 days, a link capacity of 120 units per day, a transport cost of 2.08 per unit and a link failure probability of 0.06.
SUP-02 ships to PLT-02 with a lead time of 21 days, a link capacity of 160 units per day, a transport cost of 2.61 per unit and a link failure probability of 0.04.

PLT-01 ships to DCC-01 with a lead time of 3 days, a link capacity of 120 units per day, a transport cost of 0.27 per unit and a link failure probability of 0.05.
PLT-02 ships to DCC-01 with a lead time of 2 days, a link capacity of 140 units per day, a transport cost of 0.27 per unit and a link failure probability of 0.02.
PLT-02 ships to DCC-02 with a lead time of 1 day, a link capacity of 120 units per day, a transport cost of 0.25 per unit and a link failure probability of 0.04.

DCC-01 ships to DCR-01 with a lead time of 2 days, a link capacity of 130 units per day, a transport cost of 0.44 per unit and a link failure probability of 0.05.
DCC-02 ships to DCR-02 with a lead time of 3 days, a link capacity of 140 units per day, a transport cost of 0.39 per unit and a link failure probability of 0.05.

DCR-01 ships to CUS-01 with a lead time of 2 days, a link capacity of 80 units per day, a transport cost of 0.34 per unit and a link failure probability of 0.06.
DCR-01 ships to CUS-02 with a lead time of 1 day, a link capacity of 60 units per day, a transport cost of 0.11 per unit and a link failure probability of 0.03.
DCR-01 ships to CUS-03 with a lead time of 3 days, a link capacity of 30 units per day, a transport cost of 0.31 per unit and a link failure probability of 0.05.
DCR-02 ships to CUS-01 with a lead time of 1 day, a link capacity of 70 units per day, a transport cost of 0.22 per unit and a link failure probability of 0.01.
DCR-02 ships to CUS-02 with a lead time of 3 days, a link capacity of 50 units per day, a transport cost of 0.15 per unit and a link failure probability of 0.02.
DCR-02 ships to CUS-04 with a lead time of 1 day, a link capacity of 20 units per day, a transport cost of 0.25 per unit and a link failure probability of 0.06.

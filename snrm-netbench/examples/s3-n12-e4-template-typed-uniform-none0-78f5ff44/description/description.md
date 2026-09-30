# Supply network description: Widget

This document describes the "Widget supply network". The network has 4 echelons and 12 nodes: 3 suppliers, 2 plants, 2 distribution centres and 5 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 92).

SUP-01 is a supplier in the EU-West region. It has a capacity of 270 units per day, a processing time of 0 days, a fixed cost of 700 per period, a variable cost of 2.26 per unit and a failure probability of 0.05.
SUP-01 holds an initial inventory of 368 units of Widget, keeps a safety stock of 184 units of Widget and has a holding cost of 0.13 per unit per day.
SUP-02 is a supplier in the EU-West region. It has a capacity of 140 units per day, a processing time of 0 days, a fixed cost of 500 per period, a variable cost of 2.26 per unit and a failure probability of 0.03.
SUP-02 holds an initial inventory of 465 units of Widget, keeps a safety stock of 233 units of Widget and has a holding cost of 0.09 per unit per day.
SUP-03 is a supplier in the EU-East region. It has a capacity of 240 units per day, a processing time of 0 days, a fixed cost of 1100 per period, a variable cost of 2.55 per unit and a failure probability of 0.05.
SUP-03 holds an initial inventory of 552 units of Widget, keeps a safety stock of 276 units of Widget and has a holding cost of 0.42 per unit per day.

PLT-01 is a plant in the EU-Central region. It has a capacity of 120 units per day, a processing time of 3 days, a fixed cost of 7400 per period, a variable cost of 6.18 per unit and a failure probability of 0.01.
PLT-01 holds an initial inventory of 465 units of Widget, keeps a safety stock of 233 units of Widget and has a holding cost of 0.07 per unit per day.
PLT-02 is a plant in the EU-South region. It has a capacity of 220 units per day, a processing time of 3 days, a fixed cost of 13600 per period, a variable cost of 6.86 per unit and a failure probability of 0.01.
PLT-02 holds an initial inventory of 552 units of Widget, keeps a safety stock of 276 units of Widget and has a holding cost of 0.24 per unit per day.

DC-01 is a distribution centre in the EU-North region. It has a capacity of 240 units per day, a processing time of 1 day, a fixed cost of 4900 per period, a variable cost of 1.42 per unit and a failure probability of 0.01.
DC-01 holds an initial inventory of 516 units of Widget, keeps a safety stock of 258 units of Widget and has a holding cost of 0.24 per unit per day.
DC-02 is a distribution centre in the EU-West region. It has a capacity of 130 units per day, a processing time of 1 day, a fixed cost of 4350 per period, a variable cost of 0.82 per unit and a failure probability of 0.01.
DC-02 holds an initial inventory of 372 units of Widget, keeps a safety stock of 186 units of Widget and has a holding cost of 0.35 per unit per day.

CUS-01 is a customer in the EU-North region.
CUS-01 demands 33 units of Widget per day.
CUS-02 is a customer in the EU-South region.
CUS-02 demands 58 units of Widget per day.
CUS-03 is a customer in the EU-Central region.
CUS-03 demands 59 units of Widget per day.
CUS-04 is a customer in the EU-West region.
CUS-04 demands 22 units of Widget per day.
CUS-05 is a customer in the EU-West region.
CUS-05 demands 12 units of Widget per day.

SUP-01 ships to PLT-02 with a lead time of 5 days, a link capacity of 270 units per day, a transport cost of 0.43 per unit and a link failure probability of 0.05.
SUP-02 ships to PLT-01 with a lead time of 5 days, a link capacity of 150 units per day, a transport cost of 0.49 per unit and a link failure probability of 0.06.
SUP-03 ships to PLT-01 with a lead time of 4 days, a link capacity of 140 units per day, a transport cost of 0.50 per unit and a link failure probability of 0.05.
SUP-03 ships to PLT-02 with a lead time of 2 days, a link capacity of 220 units per day, a transport cost of 0.31 per unit and a link failure probability of 0.03.

PLT-01 ships to DC-02 with a lead time of 3 days, a link capacity of 140 units per day, a transport cost of 0.50 per unit and a link failure probability of 0.02.
PLT-02 ships to DC-01 with a lead time of 2 days, a link capacity of 220 units per day, a transport cost of 0.37 per unit and a link failure probability of 0.04.
PLT-02 ships to DC-02 with a lead time of 1 day, a link capacity of 150 units per day, a transport cost of 0.35 per unit and a link failure probability of 0.03.

DC-01 ships to CUS-01 with a lead time of 1 day, a link capacity of 50 units per day, a transport cost of 0.23 per unit and a link failure probability of 0.02.
DC-01 ships to CUS-02 with a lead time of 3 days, a link capacity of 100 units per day, a transport cost of 0.29 per unit and a link failure probability of 0.01.
DC-01 ships to CUS-03 with a lead time of 1 day, a link capacity of 90 units per day, a transport cost of 0.20 per unit and a link failure probability of 0.06.
DC-01 ships to CUS-04 with a lead time of 3 days, a link capacity of 40 units per day, a transport cost of 0.13 per unit and a link failure probability of 0.02.
DC-02 ships to CUS-03 with a lead time of 1 day, a link capacity of 80 units per day, a transport cost of 0.19 per unit and a link failure probability of 0.01.
DC-02 ships to CUS-04 with a lead time of 1 day, a link capacity of 30 units per day, a transport cost of 0.23 per unit and a link failure probability of 0.05.
DC-02 ships to CUS-05 with a lead time of 2 days, a link capacity of 20 units per day, a transport cost of 0.27 per unit and a link failure probability of 0.03.

# Supply network description: Widget

This document describes the "Widget supply network". The network has 4 echelons and 12 nodes: 3 suppliers, 2 plants, 2 distribution centres and 5 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 41).

SUP-01 is a supplier in the EU-East region. It has a capacity of 190 units per day, a processing time of 0 days, a fixed cost of 1850 per period, a variable cost of 2.45 per unit and a failure probability of 0.06.
SUP-01 holds an initial inventory of 552 units of Widget, keeps a safety stock of 276 units of Widget and has a holding cost of 0.24 per unit per day.
SUP-02 is a supplier in the EU-East region. It has a capacity of 310 units per day, a processing time of 0 days, a fixed cost of 850 per period, a variable cost of 2.42 per unit and a failure probability of 0.02.
SUP-02 holds an initial inventory of 1188 units of Widget, keeps a safety stock of 594 units of Widget and has a holding cost of 0.26 per unit per day.
SUP-03 is a supplier in the EU-West region. It has a capacity of 180 units per day, a processing time of 0 days, a fixed cost of 1400 per period, a variable cost of 1.64 per unit and a failure probability of 0.05.
SUP-03 holds an initial inventory of 690 units of Widget, keeps a safety stock of 345 units of Widget and has a holding cost of 0.31 per unit per day.

PLT-01 is a plant in the EU-West region. It has a capacity of 280 units per day, a processing time of 1 day, a fixed cost of 5300 per period, a variable cost of 6.32 per unit and a failure probability of 0.01.
PLT-01 holds an initial inventory of 990 units of Widget, keeps a safety stock of 495 units of Widget and has a holding cost of 0.05 per unit per day.
PLT-02 is a plant in the EU-East region. It has a capacity of 160 units per day, a processing time of 3 days, a fixed cost of 10400 per period, a variable cost of 5.32 per unit and a failure probability of 0.03.
PLT-02 holds an initial inventory of 414 units of Widget, keeps a safety stock of 207 units of Widget and has a holding cost of 0.16 per unit per day.

DC-01 is a distribution centre in the EU-South region. It has a capacity of 210 units per day, a processing time of 1 day, a fixed cost of 3050 per period, a variable cost of 0.80 per unit and a failure probability of 0.02.
DC-01 holds an initial inventory of 276 units of Widget, keeps a safety stock of 138 units of Widget and has a holding cost of 0.25 per unit per day.
DC-02 is a distribution centre in the EU-East region. It has a capacity of 120 units per day, a processing time of 1 day, a fixed cost of 2800 per period, a variable cost of 1.22 per unit and a failure probability of 0.01.
DC-02 holds an initial inventory of 415 units of Widget, keeps a safety stock of 208 units of Widget and has a holding cost of 0.07 per unit per day.

CUS-01 is a customer in the EU-East region.
CUS-01 demands 55 units of Widget per day.
CUS-02 is a customer in the EU-West region.
CUS-02 demands 31 units of Widget per day.
CUS-03 is a customer in the EU-East region.
CUS-03 demands 23 units of Widget per day.
CUS-04 is a customer in the EU-East region.
CUS-04 demands 29 units of Widget per day.
CUS-05 is a customer in the EU-East region.
CUS-05 demands 60 units of Widget per day.

SUP-01 ships to PLT-02 with a lead time of 4 days, a link capacity of 170 units per day, a transport cost of 0.56 per unit and a link failure probability of 0.06.
SUP-02 ships to PLT-01 with a lead time of 3 days, a link capacity of 290 units per day, a transport cost of 0.59 per unit and a link failure probability of 0.06.
SUP-03 ships to PLT-02 with a lead time of 2 days, a link capacity of 200 units per day, a transport cost of 0.43 per unit and a link failure probability of 0.01.

PLT-01 ships to DC-01 with a lead time of 3 days, a link capacity of 220 units per day, a transport cost of 0.50 per unit and a link failure probability of 0.01.
PLT-01 ships to DC-02 with a lead time of 2 days, a link capacity of 130 units per day, a transport cost of 0.50 per unit and a link failure probability of 0.04.
PLT-02 ships to DC-01 with a lead time of 1 day, a link capacity of 200 units per day, a transport cost of 0.41 per unit and a link failure probability of 0.04.

DC-01 ships to CUS-01 with a lead time of 1 day, a link capacity of 70 units per day, a transport cost of 0.27 per unit and a link failure probability of 0.04.
DC-01 ships to CUS-02 with a lead time of 1 day, a link capacity of 50 units per day, a transport cost of 0.20 per unit and a link failure probability of 0.01.
DC-01 ships to CUS-03 with a lead time of 3 days, a link capacity of 40 units per day, a transport cost of 0.38 per unit and a link failure probability of 0.05.
DC-01 ships to CUS-04 with a lead time of 2 days, a link capacity of 40 units per day, a transport cost of 0.18 per unit and a link failure probability of 0.04.
DC-02 ships to CUS-03 with a lead time of 3 days, a link capacity of 30 units per day, a transport cost of 0.23 per unit and a link failure probability of 0.02.
DC-02 ships to CUS-05 with a lead time of 3 days, a link capacity of 90 units per day, a transport cost of 0.28 per unit and a link failure probability of 0.01.

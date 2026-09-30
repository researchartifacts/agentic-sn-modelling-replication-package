# Supply network description: Widget

This document describes the "Widget supply network". The network has 4 echelons and 12 nodes: 3 suppliers, 2 plants, 2 distribution centres and 5 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 58).

SUP-01 is a supplier in the AMER region. It has a capacity of 80 units per day, a processing time of 0 days, a fixed cost of 550 per period, a variable cost of 2.07 per unit and a failure probability of 0.07.
SUP-01 holds an initial inventory of 180 units of Widget, keeps a safety stock of 90 units of Widget and has a holding cost of 0.09 per unit per day.
SUP-02 is a supplier in the AMER region. It has a capacity of 120 units per day, a processing time of 0 days, a fixed cost of 900 per period, a variable cost of 2.26 per unit and a failure probability of 0.04.
SUP-02 holds an initial inventory of 297 units of Widget, keeps a safety stock of 149 units of Widget and has a holding cost of 0.28 per unit per day.
SUP-03 is a supplier in the APAC region. It has a capacity of 90 units per day, a processing time of 0 days, a fixed cost of 1900 per period, a variable cost of 2.49 per unit and a failure probability of 0.07.
SUP-03 holds an initial inventory of 120 units of Widget, keeps a safety stock of 60 units of Widget and has a holding cost of 0.29 per unit per day.

PLT-01 is a plant in the EU-West region. It has a capacity of 160 units per day, a processing time of 3 days, a fixed cost of 13500 per period, a variable cost of 6.07 per unit and a failure probability of 0.03.
PLT-01 holds an initial inventory of 297 units of Widget, keeps a safety stock of 149 units of Widget and has a holding cost of 0.27 per unit per day.
PLT-02 is a plant in the EU-West region. It has a capacity of 80 units per day, a processing time of 2 days, a fixed cost of 10600 per period, a variable cost of 5.39 per unit and a failure probability of 0.01.
PLT-02 holds an initial inventory of 180 units of Widget, keeps a safety stock of 90 units of Widget and has a holding cost of 0.22 per unit per day.

DC-01 is a distribution centre in the EU-South region. It has a capacity of 80 units per day, a processing time of 0 days, a fixed cost of 1850 per period, a variable cost of 0.92 per unit and a failure probability of 0.03.
DC-01 holds an initial inventory of 180 units of Widget, keeps a safety stock of 90 units of Widget and has a holding cost of 0.16 per unit per day.
DC-02 is a distribution centre in the EU-South region. It has a capacity of 110 units per day, a processing time of 0 days, a fixed cost of 3250 per period, a variable cost of 0.99 per unit and a failure probability of 0.03.
DC-02 holds an initial inventory of 528 units of Widget, keeps a safety stock of 264 units of Widget and has a holding cost of 0.11 per unit per day.

CUS-01 is a customer in the EU-South region.
CUS-01 demands 15 units of Widget per day.
CUS-02 is a customer in the EU-North region.
CUS-02 demands 12 units of Widget per day.
CUS-03 is a customer in the EU-East region.
CUS-03 demands 11 units of Widget per day.
CUS-04 is a customer in the EU-Central region.
CUS-04 demands 22 units of Widget per day.
CUS-05 is a customer in the EU-South region.
CUS-05 demands 39 units of Widget per day.

SUP-01 ships to PLT-02 with a lead time of 21 days, a link capacity of 90 units per day, a transport cost of 2.87 per unit and a link failure probability of 0.03.
SUP-02 ships to PLT-01 with a lead time of 28 days, a link capacity of 120 units per day, a transport cost of 2.28 per unit and a link failure probability of 0.01.
SUP-03 ships to PLT-02 with a lead time of 21 days, a link capacity of 80 units per day, a transport cost of 1.79 per unit and a link failure probability of 0.05.

PLT-01 ships to DC-01 with a lead time of 1 day, a link capacity of 70 units per day, a transport cost of 0.46 per unit and a link failure probability of 0.01.
PLT-01 ships to DC-02 with a lead time of 2 days, a link capacity of 120 units per day, a transport cost of 0.43 per unit and a link failure probability of 0.06.
PLT-02 ships to DC-01 with a lead time of 3 days, a link capacity of 90 units per day, a transport cost of 0.27 per unit and a link failure probability of 0.04.

DC-01 ships to CUS-01 with a lead time of 2 days, a link capacity of 30 units per day, a transport cost of 0.17 per unit and a link failure probability of 0.05.
DC-01 ships to CUS-02 with a lead time of 1 day, a link capacity of 20 units per day, a transport cost of 0.15 per unit and a link failure probability of 0.02.
DC-01 ships to CUS-03 with a lead time of 3 days, a link capacity of 20 units per day, a transport cost of 0.19 per unit and a link failure probability of 0.04.
DC-01 ships to CUS-04 with a lead time of 1 day, a link capacity of 30 units per day, a transport cost of 0.19 per unit and a link failure probability of 0.05.
DC-02 ships to CUS-01 with a lead time of 3 days, a link capacity of 30 units per day, a transport cost of 0.34 per unit and a link failure probability of 0.05.
DC-02 ships to CUS-02 with a lead time of 1 day, a link capacity of 20 units per day, a transport cost of 0.11 per unit and a link failure probability of 0.06.
DC-02 ships to CUS-04 with a lead time of 1 day, a link capacity of 40 units per day, a transport cost of 0.31 per unit and a link failure probability of 0.03.
DC-02 ships to CUS-05 with a lead time of 3 days, a link capacity of 60 units per day, a transport cost of 0.22 per unit and a link failure probability of 0.05.

# Supply network description: Widget

This document describes the "Widget supply network". The network has 5 echelons and 25 nodes: 4 suppliers, 4 plants, 3 central distribution centres, 3 regional distribution centres and 11 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 44).

SUP-01 is a supplier in the AMER region. It has a capacity of 590 units per day, a processing time of 0 days, a fixed cost of 1650 per period, a variable cost of 1.78 per unit and a failure probability of 0.02.
SUP-01 holds an initial inventory of 1209 units of Widget, keeps a safety stock of 605 units of Widget and has a holding cost of 0.36 per unit per day.
SUP-02 is a supplier in the AMER region. It has a capacity of 400 units per day, a processing time of 0 days, a fixed cost of 2200 per period, a variable cost of 2.22 per unit and a failure probability of 0.03.
SUP-02 holds an initial inventory of 1785 units of Widget, keeps a safety stock of 893 units of Widget and has a holding cost of 0.08 per unit per day.
SUP-03 is a supplier in the APAC region. It has a capacity of 340 units per day, a processing time of 0 days, a fixed cost of 1000 per period, a variable cost of 2.27 per unit and a failure probability of 0.05.
SUP-03 holds an initial inventory of 639 units of Widget, keeps a safety stock of 320 units of Widget and has a holding cost of 0.15 per unit per day.
SUP-04 is a supplier in the EU-South region. It has a capacity of 430 units per day, a processing time of 0 days, a fixed cost of 950 per period, a variable cost of 2.26 per unit and a failure probability of 0.07.
SUP-04 holds an initial inventory of 2142 units of Widget, keeps a safety stock of 1071 units of Widget and has a holding cost of 0.22 per unit per day.

PLT-01 is a plant in the EU-South region. It has a capacity of 550 units per day, a processing time of 3 days, a fixed cost of 5400 per period, a variable cost of 7.77 per unit and a failure probability of 0.02.
PLT-01 holds an initial inventory of 806 units of Widget, keeps a safety stock of 403 units of Widget and has a holding cost of 0.09 per unit per day.
PLT-02 is a plant in the EU-South region. It has a capacity of 500 units per day, a processing time of 3 days, a fixed cost of 7400 per period, a variable cost of 6.04 per unit and a failure probability of 0.01.
PLT-02 holds an initial inventory of 1428 units of Widget, keeps a safety stock of 714 units of Widget and has a holding cost of 0.16 per unit per day.
PLT-03 is a plant in the EU-North region. It has a capacity of 350 units per day, a processing time of 1 day, a fixed cost of 15000 per period, a variable cost of 7.81 per unit and a failure probability of 0.02.
PLT-03 holds an initial inventory of 1240 units of Widget, keeps a safety stock of 620 units of Widget and has a holding cost of 0.22 per unit per day.
PLT-04 is a plant in the EU-East region. It has a capacity of 280 units per day, a processing time of 1 day, a fixed cost of 10500 per period, a variable cost of 7.10 per unit and a failure probability of 0.02.
PLT-04 holds an initial inventory of 1278 units of Widget, keeps a safety stock of 639 units of Widget and has a holding cost of 0.23 per unit per day.

DCC-01 is a central distribution centre in the EU-Central region. It has a capacity of 400 units per day, a processing time of 0 days, a fixed cost of 1550 per period, a variable cost of 0.91 per unit and a failure probability of 0.02.
DCC-01 holds an initial inventory of 2142 units of Widget, keeps a safety stock of 1071 units of Widget and has a holding cost of 0.17 per unit per day.
DCC-02 is a central distribution centre in the EU-South region. It has a capacity of 330 units per day, a processing time of 1 day, a fixed cost of 4800 per period, a variable cost of 0.84 per unit and a failure probability of 0.01.
DCC-02 holds an initial inventory of 1240 units of Widget, keeps a safety stock of 620 units of Widget and has a holding cost of 0.43 per unit per day.
DCC-03 is a central distribution centre in the EU-Central region. It has a capacity of 290 units per day, a processing time of 0 days, a fixed cost of 1300 per period, a variable cost of 1.47 per unit and a failure probability of 0.02.
DCC-03 holds an initial inventory of 852 units of Widget, keeps a safety stock of 426 units of Widget and has a holding cost of 0.21 per unit per day.

DCR-01 is a regional distribution centre in the EU-East region. It has a capacity of 370 units per day, a processing time of 1 day, a fixed cost of 3900 per period, a variable cost of 0.85 per unit and a failure probability of 0.01.
DCR-01 holds an initial inventory of 496 units of Widget, keeps a safety stock of 248 units of Widget and has a holding cost of 0.12 per unit per day.
DCR-02 is a regional distribution centre in the EU-Central region. It has a capacity of 250 units per day, a processing time of 1 day, a fixed cost of 2050 per period, a variable cost of 0.98 per unit and a failure probability of 0.02.
DCR-02 holds an initial inventory of 852 units of Widget, keeps a safety stock of 426 units of Widget and has a holding cost of 0.14 per unit per day.
DCR-03 is a regional distribution centre in the EU-North region. It has a capacity of 180 units per day, a processing time of 0 days, a fixed cost of 3250 per period, a variable cost of 1.15 per unit and a failure probability of 0.03.
DCR-03 holds an initial inventory of 294 units of Widget, keeps a safety stock of 147 units of Widget and has a holding cost of 0.40 per unit per day.

CUS-01 is a customer in the EU-West region.
CUS-01 demands 29 units of Widget per day.
CUS-02 is a customer in the EU-Central region.
CUS-02 demands 34 units of Widget per day.
CUS-03 is a customer in the EU-North region.
CUS-03 demands 15 units of Widget per day.
CUS-04 is a customer in the EU-North region.
CUS-04 demands 24 units of Widget per day.
CUS-05 is a customer in the EU-West region.
CUS-05 demands 43 units of Widget per day.
CUS-06 is a customer in the EU-North region.
CUS-06 demands 46 units of Widget per day.
CUS-07 is a customer in the EU-North region.
CUS-07 demands 32 units of Widget per day.
CUS-08 is a customer in the EU-Central region.
CUS-08 demands 53 units of Widget per day.
CUS-09 is a customer in the EU-South region.
CUS-09 demands 38 units of Widget per day.
CUS-10 is a customer in the EU-South region.
CUS-10 demands 55 units of Widget per day.
CUS-11 is a customer in the EU-East region.
CUS-11 demands 34 units of Widget per day.

SUP-01 ships to PLT-01 with a lead time of 21 days, a link capacity of 570 units per day, a transport cost of 1.96 per unit and a link failure probability of 0.05.
SUP-02 ships to PLT-02 with a lead time of 28 days, a link capacity of 460 units per day, a transport cost of 1.68 per unit and a link failure probability of 0.01.
SUP-02 ships to PLT-03 with a lead time of 14 days, a link capacity of 320 units per day, a transport cost of 1.78 per unit and a link failure probability of 0.06.
SUP-03 ships to PLT-04 with a lead time of 14 days, a link capacity of 260 units per day, a transport cost of 1.72 per unit and a link failure probability of 0.03.
SUP-04 ships to PLT-02 with a lead time of 2 days, a link capacity of 410 units per day, a transport cost of 0.58 per unit and a link failure probability of 0.05.

PLT-01 ships to DCC-02 with a lead time of 1 day, a link capacity of 360 units per day, a transport cost of 0.24 per unit and a link failure probability of 0.04.
PLT-01 ships to DCC-03 with a lead time of 3 days, a link capacity of 330 units per day, a transport cost of 0.24 per unit and a link failure probability of 0.05.
PLT-02 ships to DCC-01 with a lead time of 3 days, a link capacity of 450 units per day, a transport cost of 0.25 per unit and a link failure probability of 0.02.
PLT-03 ships to DCC-02 with a lead time of 3 days, a link capacity of 330 units per day, a transport cost of 0.20 per unit and a link failure probability of 0.02.
PLT-04 ships to DCC-03 with a lead time of 1 day, a link capacity of 340 units per day, a transport cost of 0.47 per unit and a link failure probability of 0.06.

DCC-01 ships to DCR-01 with a lead time of 1 day, a link capacity of 290 units per day, a transport cost of 0.45 per unit and a link failure probability of 0.03.
DCC-01 ships to DCR-03 with a lead time of 3 days, a link capacity of 200 units per day, a transport cost of 0.39 per unit and a link failure probability of 0.02.
DCC-02 ships to DCR-01 with a lead time of 4 days, a link capacity of 290 units per day, a transport cost of 0.24 per unit and a link failure probability of 0.01.
DCC-03 ships to DCR-02 with a lead time of 2 days, a link capacity of 250 units per day, a transport cost of 0.33 per unit and a link failure probability of 0.01.

DCR-01 ships to CUS-01 with a lead time of 2 days, a link capacity of 50 units per day, a transport cost of 0.35 per unit and a link failure probability of 0.06.
DCR-01 ships to CUS-02 with a lead time of 2 days, a link capacity of 50 units per day, a transport cost of 0.15 per unit and a link failure probability of 0.01.
DCR-01 ships to CUS-03 with a lead time of 1 day, a link capacity of 20 units per day, a transport cost of 0.22 per unit and a link failure probability of 0.04.
DCR-01 ships to CUS-05 with a lead time of 2 days, a link capacity of 70 units per day, a transport cost of 0.10 per unit and a link failure probability of 0.01.
DCR-01 ships to CUS-09 with a lead time of 2 days, a link capacity of 60 units per day, a transport cost of 0.22 per unit and a link failure probability of 0.06.
DCR-01 ships to CUS-10 with a lead time of 3 days, a link capacity of 70 units per day, a transport cost of 0.34 per unit and a link failure probability of 0.03.
DCR-01 ships to CUS-11 with a lead time of 3 days, a link capacity of 60 units per day, a transport cost of 0.24 per unit and a link failure probability of 0.04.
DCR-02 ships to CUS-03 with a lead time of 2 days, a link capacity of 30 units per day, a transport cost of 0.26 per unit and a link failure probability of 0.04.
DCR-02 ships to CUS-04 with a lead time of 1 day, a link capacity of 40 units per day, a transport cost of 0.10 per unit and a link failure probability of 0.02.
DCR-02 ships to CUS-05 with a lead time of 2 days, a link capacity of 70 units per day, a transport cost of 0.35 per unit and a link failure probability of 0.01.
DCR-02 ships to CUS-06 with a lead time of 3 days, a link capacity of 60 units per day, a transport cost of 0.21 per unit and a link failure probability of 0.05.
DCR-02 ships to CUS-07 with a lead time of 3 days, a link capacity of 40 units per day, a transport cost of 0.32 per unit and a link failure probability of 0.05.
DCR-02 ships to CUS-08 with a lead time of 2 days, a link capacity of 80 units per day, a transport cost of 0.17 per unit and a link failure probability of 0.01.
DCR-03 ships to CUS-04 with a lead time of 3 days, a link capacity of 30 units per day, a transport cost of 0.28 per unit and a link failure probability of 0.06.
DCR-03 ships to CUS-07 with a lead time of 1 day, a link capacity of 50 units per day, a transport cost of 0.13 per unit and a link failure probability of 0.06.
DCR-03 ships to CUS-08 with a lead time of 2 days, a link capacity of 80 units per day, a transport cost of 0.32 per unit and a link failure probability of 0.04.
DCR-03 ships to CUS-09 with a lead time of 2 days, a link capacity of 60 units per day, a transport cost of 0.14 per unit and a link failure probability of 0.05.

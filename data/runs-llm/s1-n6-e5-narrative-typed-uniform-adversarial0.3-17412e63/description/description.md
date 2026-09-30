# Supply network description: Widget

This document describes the "Widget supply network". The network has 5 echelons and 6 nodes: 2 suppliers, 1 plant, 1 central distribution centre, 1 regional distribution centre and 1 customer. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 78).

## Overview

In the competitor's network, DCC-01 ships to DCR-01 with a transport cost of 0.31 per unit. The 2 suppliers are SUP-01 and SUP-02. Under the proposed expansion, CUS-01 is a customer in the EU-Central region. PLT-01 is the only plant in the network. It was originally planned that SUP-01 keeps a safety stock of 73 units of Widget. DCC-01 is the only central distribution centre in the network. DCR-01 is the network's sole regional distribution centre. There is just one customer in the network, CUS-01.

## Facilities

SUP-01 operates in the AMER region. This vendor has a maximum throughput of 70 units per day, a processing duration of 0 days, a fixed charge of 1750 per period, a variable cost of 2.88 per unit and a probability of failure of 0.04. In terms of location, SUP-02 falls within the AMER region. This supplier is modelled with a throughput capacity of 80 units each day, a fixed charge of 2200 per period, a variable operating cost of 2.36 per unit and a likelihood of failure of 0.07. This supplier shares the same processing time with SUP-01.

PLT-01 is based in the EU-South region. The production site has a capacity of 70 units every day, an internal processing time of 2 days, a fixed cost amounting to 8100 per period, a variable cost of 5.96 per unit and a likelihood of failure of 0.02.

DCC-01 sits in the EU-North region. This warehouse has a maximum throughput of 60 units per day, an internal processing time of 0 days, fixed costs of 2000 per period, variable costs of 0.88 per unit and a 0.01 probability of failure.

DCR-01 operates in the EU-North region. The warehouse is modelled with a maximum throughput of 60 units per day, a processing duration of 1 day, fixed costs of 3700 per period, a variable cost amounting to 0.92 per unit and a likelihood of failure of 0.02.

CUS-01 can be found in the EU-West region.

## Logistics lanes

The link from SUP-01 to PLT-01 has a replenishment lead time of 14 days, a link capacity of 80 units every day, a freight cost of 2.09 per unit and a link failure probability of 0.06. SUP-02 delivers to PLT-01 with a lead time of 28 days, a transport capacity of 70 units per day, a transport cost of 2.04 per unit and a lane failure probability of 0.05.

PLT-01 delivers to DCC-01 with a transit time of 1 day, a link capacity of 70 units per day, a shipping cost of 0.28 per unit and a link failure likelihood of 0.01.

In the competitor's network, SUP-01 is a supplier. It has a fixed cost of 718 per period. DCC-01 sends goods to DCR-01 with a lead time of 4 days, a lane capacity of 70 units per day, transport costs of 0.42 per unit and a 0.03 probability of link failure.

It was originally planned that DCC-02 is a central distribution centre in the APAC region. It has a capacity of 880 units per day. DCR-01 delivers to CUS-01 with a lead time of 1 day, a transport capacity of 80 units per day, a transport cost of 0.21 per unit and a lane failure probability of 0.04.

## Demand and inventory

SUP-01 currently begins the horizon with 98 units of Widget on hand, holds a safety stock of 49 units of Widget and incurs a holding cost of 0.29 per unit per day. Until 2013, DCC-01 holds an initial inventory of 276 units of Widget. SUP-02 currently has an opening inventory of 196 units of Widget, maintains a safety stock of 98 units of Widget and bears a holding cost of 0.26 per unit per day.

In the discontinued Widget Classic network, DCR-02 is a regional distribution centre in the EU-South region. It has a capacity of 200 units per day. PLT-01 also has an opening inventory of 245 units of Widget, keeps 123 units of Widget as safety stock and pays a holding cost of 0.35 per unit per day.

It was originally planned that DCR-01 keeps a safety stock of 65 units of Widget. In the discontinued Widget Classic network, CUS-02 demands 11 units of Widget per day. On inventory, DCC-01 begins the horizon with 147 units of Widget on hand, keeps a safety stock of 74 units of Widget and incurs a holding cost of 0.28 per unit per day.

DCR-01 also holds an initial inventory of 245 units of Widget, maintains a safety stock of 123 units of Widget and pays a holding cost of 0.04 per unit per day.

Every day, CUS-01 requires 49 units of Widget.

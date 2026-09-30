# Supply network description: Widget

This document describes the "Widget supply network". The network has 3 echelons and 6 nodes: 2 suppliers, 2 plants and 2 customers. Planning uses periods of 1 day over a horizon of 90 periods; durations are rounded to the nearest period. The network handles 1 product: Widget (unit value 78).

## Overview

The 2 suppliers are SUP-01 and SUP-02. The 2 plants are PLT-01 and PLT-02. The 2 customers are CUS-01 and CUS-02.

## Facilities

SUP-01 is located in the AMER region. This supplier has the capacity to handle 170 units per day, a standard processing time of 0 days, a fixed operating cost of 2000 per period, a variable operating cost of 2.97 per unit and a likelihood of failure of 0.04. SUP-02 is based in the AMER region. This supplier operates with a capacity of 140 units every day, a fixed charge of 500 per period, a variable charge of 2.11 per unit and a likelihood of failure of 0.07. This supplier is identical to SUP-01 in terms of processing time.

In terms of location, PLT-01 falls within the EU-South region. The production site has a throughput capacity of 60 units each day, a processing time of 3 days, fixed costs of 7100 per period, variable costs of 5.63 per unit and a 0.02 probability of failure. PLT-02 is based in the EU-North region. This factory is characterised by a throughput capacity of 130 units each day, an internal processing time of 2 days, a fixed operating cost of 8800 per period, variable costs of 6.01 per unit and a failure likelihood of 0.01.

CUS-01 sits in the EU-North region. CUS-02 sits in the EU-West region.

## Logistics lanes

Shipments from SUP-01 to PLT-02 have a replenishment lead time of 28 days, a lane capacity of 160 units per day, a shipping cost of 1.77 per unit and a 0.02 probability of link failure. SUP-02 delivers to PLT-01 with 14 days of lead time, a maximum flow of 70 units per day, a freight cost of 2.09 per unit and a link failure probability of 0.06. Shipments from SUP-02 to PLT-02 have 14 days of lead time, a transport capacity of 140 units per day, a transport cost of 2.04 per unit and a link failure probability of 0.05.

PLT-01 sends goods to CUS-01 with a delivery time of 1 day, a link capacity of 70 units per day, a shipping cost of 0.28 per unit and a 0.01 probability of link failure. Shipments from PLT-02 to CUS-01 have a lead time of 2 days, a lane capacity of 80 units per day, a transport cost amounting to 0.25 per unit and a lane failure probability of 0.03. PLT-02 ships to CUS-02 with a transit time of 2 days, a maximum flow of 70 units per day, a freight cost of 0.31 per unit and a link failure likelihood of 0.04.

## Demand and inventory

SUP-01 also has an opening inventory of 208 units of Widget, keeps a safety stock of 104 units of Widget and pays a holding cost of 0.29 per unit per day. SUP-02 currently begins the horizon with 416 units of Widget on hand, holds a safety stock of 208 units of Widget and incurs a holding cost of 0.26 per unit per day.

PLT-01 currently has an opening inventory of 245 units of Widget, maintains a safety stock of 123 units of Widget and bears a holding cost of 0.35 per unit per day. PLT-02 also has an opening inventory of 312 units of Widget, keeps 156 units of Widget as safety stock and pays a holding cost of 0.28 per unit per day.

The demand at CUS-01 is 49 units of Widget per day. CUS-02 requires 55 units of Widget per day.

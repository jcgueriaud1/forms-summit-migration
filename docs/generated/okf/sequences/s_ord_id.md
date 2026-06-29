---
type: Oracle Sequence
title: S_ORD_ID
description: Order-id generator; supplies the new S_ORD.ID in the S_ORD PRE-INSERT trigger.
resource: urn:oracle:summit:sequence:S_ORD_ID
tags: [oracle, summit, sequence, orders]
timestamp: 2026-06-26
---

# S_ORD_ID

Sequence that generates primary keys for `S_ORD`. The ORDERS form draws the next
value in the `S_ORD` PRE-INSERT trigger.

Source: `forms-summit-example/init-summit.sql:232`.

## Definition

```sql
CREATE SEQUENCE S_ORD_ID
  MINVALUE 1
  MAXVALUE 999999999999999999999999999
  INCREMENT BY 1
  START WITH 50000
  CACHE 50
  NOORDER NOCYCLE;
```

| Property | Value |
|---|---|
| Start | 50000 |
| Increment | 1 |
| Cache | 50 |
| Cycle | NOCYCLE |
| Order | NOORDER |

> The committed `init-summit.sql` already advances live data well past 50000;
> seed data for the container should keep `START WITH` above the max existing
> `S_ORD.ID` to avoid PK collisions on the first insert.

## Used by ORDERS

- **`S_ORD` PRE-INSERT** —
  `SELECT S_ORD_ID.nextval INTO :S_ORD.id FROM SYS.DUAL;` with
  `WHEN OTHERS` → "Faild to assign Order Id" (sic) + `form_trigger_failure`.
  This is the new-order id assignment (stretch / create-from-scratch flow).

## Related

- `../../orders-form-spec.md` § Triggers (PRE-INSERT)

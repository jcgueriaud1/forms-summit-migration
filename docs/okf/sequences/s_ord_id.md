---
type: Oracle Sequence
title: S_ORD_ID
description: Sequence generating primary-key values for S_ORD.
resource: urn:oracle:summit:sequence:S_ORD_ID
tags: [oracle, summit, sequence]
timestamp: 2026-06-29
---

# S_ORD_ID

Sequence that generates primary keys for [S_ORD](../tables/s_ord.md).

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
> seed data for a fresh container should keep `START WITH` above the maximum
> existing `S_ORD.ID` to avoid PK collisions on the first insert.

## Related

- [tables/s_ord.md](../tables/s_ord.md) — the table whose `ID` it feeds

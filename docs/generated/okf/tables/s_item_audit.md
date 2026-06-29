---
type: Oracle Table
title: S_ITEM_AUDIT
description: Audit sink — receives a constant row on every S_ITEM insert.
resource: urn:oracle:summit:table:S_ITEM_AUDIT
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_ITEM_AUDIT

Audit sink. A single-column table that receives one constant row for every
`S_ITEM` insert, written by the [AUDIT_S_ITEM](../triggers/audit_s_item.md)
trigger. No keys or constraints.

Source: `forms-summit-example/init-summit.sql:1234`.

## Schema

```sql
CREATE TABLE S_ITEM_AUDIT ( A NUMBER );
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| A | NUMBER | | trigger writes the literal `1` |

## Relationships

- No PK, UK, or FK
- Written by trigger [AUDIT_S_ITEM](../triggers/audit_s_item.md) (`AFTER INSERT` on `S_ITEM`)

## Related

- [triggers/audit_s_item.md](../triggers/audit_s_item.md) · [tables/s_item.md](s_item.md)

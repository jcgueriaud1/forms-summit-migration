---
type: Oracle Trigger
title: AUDIT_S_ITEM
description: AFTER INSERT row trigger on S_ITEM that writes a constant audit row.
resource: urn:oracle:summit:trigger:AUDIT_S_ITEM
tags: [oracle, summit, trigger]
timestamp: 2026-06-29
---

# AUDIT_S_ITEM

Row-level `AFTER INSERT` trigger on [S_ITEM](../tables/s_item.md). For every line
item inserted it writes a single constant row into
[S_ITEM_AUDIT](../tables/s_item_audit.md).

Source: `forms-summit-example/init-summit.sql:56429`.

## Definition (verbatim)

```sql
CREATE OR REPLACE TRIGGER AUDIT_S_ITEM
AFTER INSERT ON s_item
FOR EACH ROW
BEGIN
  INSERT INTO s_item_audit VALUES (1);
END;
```

## Notes

- **Captures no actor** — writes the literal `1`; reads no `USER`, `SYS_CONTEXT`,
  or `CLIENT_IDENTIFIER`.
- Fires transparently inside the inserting transaction; not invoked by
  application code.

## Relationships

- Defined on [S_ITEM](../tables/s_item.md); inserts into [S_ITEM_AUDIT](../tables/s_item_audit.md)

## Related

- [tables/s_item.md](../tables/s_item.md) — the table it audits
- [tables/s_item_audit.md](../tables/s_item_audit.md) — the audit sink

---
type: Oracle Trigger
title: AUDIT_S_ITEM
description: AFTER INSERT row trigger on S_ITEM that writes a constant audit row; captures no actor.
resource: urn:oracle:summit:trigger:AUDIT_S_ITEM
tags: [oracle, summit, trigger, audit, orders, stays-in-db]
timestamp: 2026-06-26
---

# AUDIT_S_ITEM

Row-level `AFTER INSERT` trigger on `S_ITEM`. It inserts a single constant row
into `S_ITEM_AUDIT (A NUMBER)` for every line item inserted. It is **not**
referenced from the form (it fires transparently in the DB), but it is part of
the ORDERS commit path and the project's boundary-rule proof point.

Source: `forms-summit-example/init-summit.sql:56429` (trigger),
`:1234` (`S_ITEM_AUDIT` table).

## Definition (verbatim)

```sql
CREATE OR REPLACE TRIGGER AUDIT_S_ITEM
AFTER INSERT ON s_item
FOR EACH ROW
BEGIN
  INSERT INTO s_item_audit VALUES (1);
END;
```

Backing table:

```sql
CREATE TABLE S_ITEM_AUDIT ( A NUMBER );
```

## Key finding

**Captures no actor** — no `USER`, no `SYS_CONTEXT`, no `CLIENT_IDENTIFIER`; it
writes the literal `1`. This resolves the parked USER-vs-`SYS_CONTEXT` question:
it reads *neither*, so the `CLIENT_IDENTIFIER` connection-pool bridge is **not
exercised** by this slice and is dropped (a plain HikariCP pool suffices).

## Migration note

**Stays in DB.** It fires inside the Vaadin commit's transaction (the
`@Transactional` whole-graph save) with zero Java written — "some things you
don't migrate, and they keep working." Decision recorded in
`orders-form-spec.md` § Server-side DB objects; this file is the resolved DDL
behind it.

## Related

- `../../orders-form-spec.md` § Server-side DB objects
- `PLAN.md` § Server-side DB objects (boundary rule)

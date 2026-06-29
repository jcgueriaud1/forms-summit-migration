---
type: Oracle Table
title: S_ORD
description: Order master table — the ORDERS form's S_ORD block base table.
resource: urn:oracle:summit:table:S_ORD
tags: [oracle, summit, table, base-table, master, orders]
timestamp: 2026-06-26
---

# S_ORD

Order master. Base table of the `S_ORD` block (the master in the
`S_ORD_S_ITEM` relation). Columns are also in `ORDERS.spec.json` as data-source
columns; this file adds the **constraints** the spec omits.

Source: `forms-summit-example/init-summit.sql:1263` (table), `:56906` (constraints),
`:57001` (FKs).

## Schema

```sql
CREATE TABLE S_ORD (
  ID           NUMBER(7,0)   NOT NULL,
  CUSTOMER_ID  NUMBER(7,0)   NOT NULL,
  DATE_ORDERED DATE,
  DATE_SHIPPED DATE,
  SALES_REP_ID NUMBER(7,0),
  TOTAL        NUMBER(11,2),
  PAYMENT_TYPE VARCHAR2(6),
  ORDER_FILLED VARCHAR2(1),
  CONSTRAINT S_ORD_ID_PK PRIMARY KEY (ID),
  CONSTRAINT S_ORD_PAYMENT_TYPE_CK CHECK (PAYMENT_TYPE IN ('CASH','CREDIT')),
  CONSTRAINT S_ORD_ORDER_FILLED_CK CHECK (ORDER_FILLED IN ('Y','N')),
  CONSTRAINT S_ORD_CUSTOMER_ID_FK  FOREIGN KEY (CUSTOMER_ID)  REFERENCES S_CUSTOMER(ID),
  CONSTRAINT S_ORD_SALES_REP_ID_FK FOREIGN KEY (SALES_REP_ID) REFERENCES S_EMP(ID)
);
```

| Column | Type | Null | Notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK; assigned from [S_ORD_ID](../sequences/s_ord_id.md) in PRE-INSERT |
| CUSTOMER_ID | NUMBER(7,0) | NOT NULL | FK → `S_CUSTOMER.ID` |
| DATE_ORDERED | DATE | | item init `$$date$$`; calendar LOV |
| DATE_SHIPPED | DATE | | calendar LOV; WHEN-VALIDATE-RECORD guards `>= DATE_ORDERED` |
| SALES_REP_ID | NUMBER(7,0) | | FK → `S_EMP.ID`; defaulted from customer |
| TOTAL | NUMBER(11,2) | | **stored column** — but the form's `TOTAL` item is a non-DB Summary; see note |
| PAYMENT_TYPE | VARCHAR2(6) | | CHECK `('CASH','CREDIT')` — DB mirror of the radio group (init `CASH`) |
| ORDER_FILLED | VARCHAR2(1) | | CHECK `('Y','N')` — DB mirror of the checkbox (init `N`) |

> **Parked-task signal:** the form's `TOTAL` display item is a non-database
> Summary item (`DatabaseItem="false"`), yet `S_ORD.TOTAL` exists as a real
> column. So the order total is computed in-memory either way; the only open
> question (PLAN.md parked task) is whether the commit also *writes* this column.

## Relations / triggers

- Master of **`S_ORD_S_ITEM`** (`S_ITEM.ORD_ID = S_ORD.ID`, Non Isolated delete →
  guard master delete while lines exist).
- Block triggers: PRE-INSERT (seq id), PRE-QUERY / WHEN-CREATE-RECORD
  (`GLOBAL.customer_id`), POST-QUERY (name lookups), WHEN-VALIDATE-RECORD (date
  guard), plus the CUSTOMER_ID / PAYMENT_TYPE item triggers. See
  `ORDERS.spec.json` and `orders-form-spec.md`.

## Related

- [tables/s_item.md](s_item.md) · [tables/s_customer.md](s_customer.md) · [tables/s_emp.md](s_emp.md)
- [sequences/s_ord_id.md](../sequences/s_ord_id.md)

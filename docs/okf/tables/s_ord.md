---
type: Oracle Table
title: S_ORD
description: Order master table — order header, one row per order.
resource: urn:oracle:summit:table:S_ORD
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_ORD

Order master (order header). Parent of `S_ITEM`.

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

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK; values generated from sequence `S_ORD_ID` |
| CUSTOMER_ID | NUMBER(7,0) | NOT NULL | FK → `S_CUSTOMER.ID` |
| DATE_ORDERED | DATE | | |
| DATE_SHIPPED | DATE | | |
| SALES_REP_ID | NUMBER(7,0) | | FK → `S_EMP.ID` |
| TOTAL | NUMBER(11,2) | | order total (stored column) |
| PAYMENT_TYPE | VARCHAR2(6) | | CHECK `('CASH','CREDIT')` |
| ORDER_FILLED | VARCHAR2(1) | | CHECK `('Y','N')` |

## Relationships

- PK `ID` ← sequence [S_ORD_ID](../sequences/s_ord_id.md)
- FK `CUSTOMER_ID` → [S_CUSTOMER](s_customer.md).ID
- FK `SALES_REP_ID` → [S_EMP](s_emp.md).ID
- Referenced by [S_ITEM](s_item.md).ORD_ID (child line items)

## Related

- [tables/s_item.md](s_item.md) · [tables/s_customer.md](s_customer.md) · [tables/s_emp.md](s_emp.md)
- [sequences/s_ord_id.md](../sequences/s_ord_id.md)

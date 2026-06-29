---
type: Oracle Table
title: S_CUSTOMER
description: Customer master; supplies order customer name, sales rep, and credit rating to the ORDERS form.
resource: urn:oracle:summit:table:S_CUSTOMER
tags: [oracle, summit, table, lookup, orders]
timestamp: 2026-06-26
---

# S_CUSTOMER

Customer master. The ORDERS form never edits it — it reads from it to validate
`S_ORD.CUSTOMER_ID`, denormalize the customer name, derive the default sales rep,
and gate the CREDIT payment type on the customer's credit rating.

Source: `forms-summit-example/init-summit.sql:1118` (table), `:56953` (constraints),
`:56961` (FKs).

## Schema

```sql
CREATE TABLE S_CUSTOMER (
  ID            NUMBER(7,0)   NOT NULL,
  NAME          VARCHAR2(50)  NOT NULL,
  PHONE         VARCHAR2(25),
  ADDRESS       VARCHAR2(400),
  CITY          VARCHAR2(30),
  STATE         VARCHAR2(20),
  COUNTRY       VARCHAR2(30),
  ZIP_CODE      VARCHAR2(75),
  CREDIT_RATING VARCHAR2(9),
  SALES_REP_ID  NUMBER(7,0),
  REGION_ID     NUMBER(7,0),
  COMMENTS      VARCHAR2(255),
  CONSTRAINT S_CUSTOMER_ID_PK PRIMARY KEY (ID),
  CONSTRAINT S_CUSTOMER_CREDIT_RATING_CK
    CHECK (CREDIT_RATING IN ('EXCELLENT','GOOD','POOR')),
  CONSTRAINT S_SALES_REP_ID_FK     FOREIGN KEY (SALES_REP_ID) REFERENCES S_EMP(ID),
  CONSTRAINT S_CUSTOMER_REGION_ID_FK FOREIGN KEY (REGION_ID)  REFERENCES S_REGION(ID)
);
```

| Column | Type | Null | Notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| NAME | VARCHAR2(50) | NOT NULL | → `S_ORD.CUSTOMER_NAME` display |
| CREDIT_RATING | VARCHAR2(9) | | CHECK `('EXCELLENT','GOOD','POOR')` |
| SALES_REP_ID | NUMBER(7,0) | | FK → `S_EMP.ID`; defaults `S_ORD.SALES_REP_ID` |
| REGION_ID | NUMBER(7,0) | | FK → `S_REGION.ID` |
| (PHONE, ADDRESS, CITY, STATE, COUNTRY, ZIP_CODE, COMMENTS) | | | not used by ORDERS |

## Used by ORDERS

- **`S_ORD.CUSTOMER_ID` WHEN-VALIDATE-ITEM** —
  `SELECT C.name, C.sales_rep_id, E.last_name ... FROM S_CUSTOMER C, S_EMP E
  WHERE E.id = C.sales_rep_id AND :S_ORD.customer_id = C.id`. `NO_DATA_FOUND` →
  "Invalid Customer Id!".
- **`S_ORD` POST-QUERY** — re-fetches `C.name` (+ `E.last_name`) to fill the
  display-only `CUSTOMER_NAME` / `SALES_REP_NAME` on load.
- **`S_ORD.PAYMENT_TYPE` WHEN-RADIO-CHANGED** — when CREDIT is chosen,
  `SELECT credit_rating ... WHERE :S_ORD.customer_id = id`; if `NOT IN
  ('GOOD','EXCELLENT')` force back to CASH and raise `PAYMENT_TYPE_ALERT`
  ("This customer must pay cash!"). Note the trigger tolerates `'EXCELLENT'`
  even though the CHECK also allows `'POOR'`.

## Related

- [tables/s_emp.md](s_emp.md) — joined for the sales-rep last name
- `../../orders-form-spec.md` — POST-QUERY / WHEN-VALIDATE-ITEM mappings

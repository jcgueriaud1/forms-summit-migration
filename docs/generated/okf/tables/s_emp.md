---
type: Oracle Table
title: S_EMP
description: Employee master; supplies the sales-rep last name displayed on the ORDERS form.
resource: urn:oracle:summit:table:S_EMP
tags: [oracle, summit, table, lookup, orders]
timestamp: 2026-06-26
---

# S_EMP

Employee master. ORDERS reads it only to resolve a sales rep's `LAST_NAME` for
the display-only `S_ORD.SALES_REP_NAME` item (joined via
`E.id = C.sales_rep_id`).

Source: `forms-summit-example/init-summit.sql:1157` (table), `:56971` (constraints).

## Schema

```sql
CREATE TABLE S_EMP (
  ID             NUMBER(7,0)  NOT NULL,
  LAST_NAME      VARCHAR2(25) NOT NULL,
  FIRST_NAME     VARCHAR2(25),
  USERID         VARCHAR2(8),
  START_DATE     DATE,
  COMMENTS       VARCHAR2(255),
  MANAGER_ID     NUMBER(7,0),
  TITLE          VARCHAR2(25),
  DEPT_ID        NUMBER(7,0),
  SALARY         NUMBER(11,2),
  COMMISSION_PCT NUMBER(4,2),
  CONSTRAINT S_EMP_ID_PK     PRIMARY KEY (ID),
  CONSTRAINT S_EMP_USERID_UK UNIQUE (USERID),
  CONSTRAINT S_EMP_COMMISSION_PCT_CK
    CHECK (COMMISSION_PCT IN (10, 12.5, 15, 17.5, 20))
);
```

| Column | Type | Null | Notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK; target of `S_CUSTOMER.SALES_REP_ID` |
| LAST_NAME | VARCHAR2(25) | NOT NULL | → `S_ORD.SALES_REP_NAME` display |
| USERID | VARCHAR2(8) | | UK (login id) |
| (FIRST_NAME, START_DATE, COMMENTS, MANAGER_ID, TITLE, DEPT_ID, SALARY, COMMISSION_PCT) | | | not used by ORDERS |

## Used by ORDERS

- **`S_ORD.CUSTOMER_ID` WHEN-VALIDATE-ITEM** and **`S_ORD` POST-QUERY** — both
  join `S_EMP E` on `E.id = C.sales_rep_id` to read `E.last_name` into
  `:S_ORD.sales_rep_name`.

## Related

- [tables/s_customer.md](s_customer.md) — `SALES_REP_ID` FK target is this table

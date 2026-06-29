---
type: Oracle Table
title: S_CUSTOMER
description: Customer master — one row per customer.
resource: urn:oracle:summit:table:S_CUSTOMER
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_CUSTOMER

Customer master.

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

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| NAME | VARCHAR2(50) | NOT NULL | |
| PHONE | VARCHAR2(25) | | |
| ADDRESS | VARCHAR2(400) | | |
| CITY | VARCHAR2(30) | | |
| STATE | VARCHAR2(20) | | |
| COUNTRY | VARCHAR2(30) | | |
| ZIP_CODE | VARCHAR2(75) | | |
| CREDIT_RATING | VARCHAR2(9) | | CHECK `('EXCELLENT','GOOD','POOR')` |
| SALES_REP_ID | NUMBER(7,0) | | FK → `S_EMP.ID` |
| REGION_ID | NUMBER(7,0) | | FK → `S_REGION.ID` |
| COMMENTS | VARCHAR2(255) | | |

## Relationships

- FK `SALES_REP_ID` → [S_EMP](s_emp.md).ID
- FK `REGION_ID` → [S_REGION](s_region.md).ID
- Referenced by [S_ORD](s_ord.md).CUSTOMER_ID

## Related

- [tables/s_emp.md](s_emp.md) · [tables/s_region.md](s_region.md) · [tables/s_ord.md](s_ord.md)

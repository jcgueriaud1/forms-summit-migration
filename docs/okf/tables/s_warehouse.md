---
type: Oracle Table
title: S_WAREHOUSE
description: Warehouse master — one row per warehouse.
resource: urn:oracle:summit:table:S_WAREHOUSE
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_WAREHOUSE

Warehouse master.

Source: `forms-summit-example/init-summit.sql:1329` (table), `:56685` (constraints),
`:57017` (FKs).

## Schema

```sql
CREATE TABLE S_WAREHOUSE (
  ID         NUMBER(7,0)  NOT NULL,
  REGION_ID  NUMBER(7,0)  NOT NULL,
  ADDRESS    LONG,
  CITY       VARCHAR2(30),
  STATE      VARCHAR2(20),
  COUNTRY    VARCHAR2(30),
  ZIP_CODE   VARCHAR2(75),
  PHONE      VARCHAR2(25),
  MANAGER_ID NUMBER(7,0),
  CONSTRAINT S_WAREHOUSE_ID_PK         PRIMARY KEY (ID),
  CONSTRAINT S_WAREHOUSE_REGION_ID_FK  FOREIGN KEY (REGION_ID)  REFERENCES S_REGION(ID),
  CONSTRAINT S_WAREHOUSE_MANAGER_ID_FK FOREIGN KEY (MANAGER_ID) REFERENCES S_EMP(ID)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| REGION_ID | NUMBER(7,0) | NOT NULL | FK → `S_REGION.ID` |
| ADDRESS | LONG | | legacy `LONG` type |
| CITY | VARCHAR2(30) | | |
| STATE | VARCHAR2(20) | | |
| COUNTRY | VARCHAR2(30) | | |
| ZIP_CODE | VARCHAR2(75) | | |
| PHONE | VARCHAR2(25) | | |
| MANAGER_ID | NUMBER(7,0) | | FK → `S_EMP.ID` |

## Relationships

- FK `REGION_ID` → [S_REGION](s_region.md).ID
- FK `MANAGER_ID` → [S_EMP](s_emp.md).ID
- Referenced by [S_INVENTORY](s_inventory.md).WAREHOUSE_ID

## Related

- [tables/s_region.md](s_region.md) · [tables/s_emp.md](s_emp.md) · [tables/s_inventory.md](s_inventory.md)

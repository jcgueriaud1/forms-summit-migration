---
type: Oracle Table
title: S_EMP
description: Employee master — one row per employee.
resource: urn:oracle:summit:table:S_EMP
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_EMP

Employee master.

Source: `forms-summit-example/init-summit.sql:1157` (table), `:56674` (constraints),
`:56975` (FKs).

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
    CHECK (COMMISSION_PCT IN (10, 12.5, 15, 17.5, 20)),
  CONSTRAINT S_EMP_MANAGER_ID_FK FOREIGN KEY (MANAGER_ID) REFERENCES S_EMP(ID),
  CONSTRAINT S_EMP_DEPT_ID_FK    FOREIGN KEY (DEPT_ID)    REFERENCES S_DEPT(ID),
  CONSTRAINT S_EMP_TITLE_FK      FOREIGN KEY (TITLE)      REFERENCES S_TITLE(TITLE)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| LAST_NAME | VARCHAR2(25) | NOT NULL | |
| FIRST_NAME | VARCHAR2(25) | | |
| USERID | VARCHAR2(8) | | UK |
| START_DATE | DATE | | |
| COMMENTS | VARCHAR2(255) | | |
| MANAGER_ID | NUMBER(7,0) | | FK → `S_EMP.ID` (self-reference) |
| TITLE | VARCHAR2(25) | | FK → `S_TITLE.TITLE` |
| DEPT_ID | NUMBER(7,0) | | FK → `S_DEPT.ID` |
| SALARY | NUMBER(11,2) | | |
| COMMISSION_PCT | NUMBER(4,2) | | CHECK `(10, 12.5, 15, 17.5, 20)` |

## Relationships

- FK `MANAGER_ID` → `S_EMP.ID` (self-reference)
- FK `DEPT_ID` → [S_DEPT](s_dept.md).ID
- FK `TITLE` → [S_TITLE](s_title.md).TITLE
- Referenced by [S_CUSTOMER](s_customer.md).SALES_REP_ID, [S_ORD](s_ord.md).SALES_REP_ID, [S_WAREHOUSE](s_warehouse.md).MANAGER_ID

## Related

- [tables/s_dept.md](s_dept.md) · [tables/s_title.md](s_title.md) · [tables/s_customer.md](s_customer.md) · [tables/s_ord.md](s_ord.md) · [tables/s_warehouse.md](s_warehouse.md)

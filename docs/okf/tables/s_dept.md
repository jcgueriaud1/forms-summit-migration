---
type: Oracle Table
title: S_DEPT
description: Department lookup — one row per department.
resource: urn:oracle:summit:table:S_DEPT
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_DEPT

Department lookup.

Source: `forms-summit-example/init-summit.sql:1142` (table), `:56756` (constraints),
`:56969` (FKs).

## Schema

```sql
CREATE TABLE S_DEPT (
  ID        NUMBER(7,0)  NOT NULL,
  NAME      VARCHAR2(25) NOT NULL,
  REGION_ID NUMBER(7,0),
  CONSTRAINT S_DEPT_ID_PK             PRIMARY KEY (ID),
  CONSTRAINT S_DEPT_NAME_REGION_ID_UK UNIQUE (NAME, REGION_ID),
  CONSTRAINT S_DEPT_REGION_ID_FK      FOREIGN KEY (REGION_ID) REFERENCES S_REGION(ID)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| NAME | VARCHAR2(25) | NOT NULL | part of UK `(NAME, REGION_ID)` |
| REGION_ID | NUMBER(7,0) | | FK → `S_REGION.ID`; part of UK `(NAME, REGION_ID)` |

## Relationships

- FK `REGION_ID` → [S_REGION](s_region.md).ID
- Referenced by [S_EMP](s_emp.md).DEPT_ID

## Related

- [tables/s_region.md](s_region.md) · [tables/s_emp.md](s_emp.md)

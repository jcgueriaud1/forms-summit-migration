---
type: Oracle Table
title: S_REGION
description: Sales-region lookup — one row per region.
resource: urn:oracle:summit:table:S_REGION
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_REGION

Sales-region lookup.

Source: `forms-summit-example/init-summit.sql:1302` (table), `:56740` (constraints).

## Schema

```sql
CREATE TABLE S_REGION (
  ID   NUMBER(7,0)  NOT NULL,
  NAME VARCHAR2(50) NOT NULL,
  CONSTRAINT S_REGION_ID_PK   PRIMARY KEY (ID),
  CONSTRAINT S_REGION_NAME_UK UNIQUE (NAME)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| NAME | VARCHAR2(50) | NOT NULL | UK |

## Relationships

- No outbound FK
- Referenced by [S_DEPT](s_dept.md).REGION_ID, [S_WAREHOUSE](s_warehouse.md).REGION_ID, [S_CUSTOMER](s_customer.md).REGION_ID

## Related

- [tables/s_dept.md](s_dept.md) · [tables/s_warehouse.md](s_warehouse.md) · [tables/s_customer.md](s_customer.md)

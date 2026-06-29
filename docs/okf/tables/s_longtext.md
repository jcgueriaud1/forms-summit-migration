---
type: Oracle Table
title: S_LONGTEXT
description: Long-text catalog — filename and/or inline text per entry.
resource: urn:oracle:summit:table:S_LONGTEXT
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_LONGTEXT

Long-text catalog. Holds an optional filename and/or inline text (up to 2000
chars).

Source: `forms-summit-example/init-summit.sql:1247` (table), `:56777` (constraints).

## Schema

```sql
CREATE TABLE S_LONGTEXT (
  ID           NUMBER(7,0)    NOT NULL,
  USE_FILENAME VARCHAR2(1),
  FILENAME     VARCHAR2(255),
  TEXT         VARCHAR2(2000),
  CONSTRAINT S_LONGTEXT_ID_PK           PRIMARY KEY (ID),
  CONSTRAINT S_LONGTEXT_USE_FILENAME_CK CHECK (USE_FILENAME IN ('Y','N'))
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| USE_FILENAME | VARCHAR2(1) | | CHECK `('Y','N')` |
| FILENAME | VARCHAR2(255) | | |
| TEXT | VARCHAR2(2000) | | inline text |

## Relationships

- No outbound FK
- Referenced by [S_PRODUCT](s_product.md).LONGTEXT_ID

## Related

- [tables/s_product.md](s_product.md)

---
type: Oracle Table
title: S_TITLE
description: Job-title lookup — one row per valid employee title.
resource: urn:oracle:summit:table:S_TITLE
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_TITLE

Job-title lookup. The title string itself is the primary key.

Source: `forms-summit-example/init-summit.sql:1316` (table), `:56795` (constraints).

## Schema

```sql
CREATE TABLE S_TITLE (
  TITLE VARCHAR2(25) NOT NULL,
  CONSTRAINT S_TITLE_TITLE_PK PRIMARY KEY (TITLE)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| TITLE | VARCHAR2(25) | NOT NULL | PK (the title string) |

## Relationships

- No outbound FK
- Referenced by [S_EMP](s_emp.md).TITLE

## Related

- [tables/s_emp.md](s_emp.md)

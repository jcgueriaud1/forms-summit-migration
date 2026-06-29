---
type: Oracle Table
title: S_IMAGE
description: Image catalog — filename and/or inline binary per image.
resource: urn:oracle:summit:table:S_IMAGE
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_IMAGE

Image catalog. Holds the image format, an optional filename, and/or the inline
binary (`LONG RAW`). Read by the [GET_PRODUCT_IMAGE](../functions/get_product_image.md)
function to resolve a product's image `FILENAME`.

Source: `forms-summit-example/init-summit.sql:1180` (table), `:56892` (constraints).

## Schema

```sql
CREATE TABLE S_IMAGE (
  ID           NUMBER(7,0) NOT NULL,
  FORMAT       VARCHAR2(25),
  USE_FILENAME VARCHAR2(1),
  FILENAME     VARCHAR2(255),
  IMAGE        LONG RAW,
  CONSTRAINT S_IMAGE_ID_PK           PRIMARY KEY (ID),
  CONSTRAINT S_IMAGE_FORMAT_CK       CHECK (FORMAT IN ('JFIFF','JTIFF')),
  CONSTRAINT S_IMAGE_USE_FILENAME_CK CHECK (USE_FILENAME IN ('Y','N'))
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| FORMAT | VARCHAR2(25) | | CHECK `('JFIFF','JTIFF')` |
| USE_FILENAME | VARCHAR2(1) | | CHECK `('Y','N')` |
| FILENAME | VARCHAR2(255) | | image filename; returned by `GET_PRODUCT_IMAGE` |
| IMAGE | LONG RAW | | inline binary (legacy `LONG RAW` type) |

## Relationships

- No outbound FK
- Referenced by [S_PRODUCT](s_product.md).IMAGE_ID
- Read by [GET_PRODUCT_IMAGE](../functions/get_product_image.md) (joins `S_PRODUCT.IMAGE_ID = S_IMAGE.ID`)

## Related

- [tables/s_product.md](s_product.md) · [functions/get_product_image.md](../functions/get_product_image.md)

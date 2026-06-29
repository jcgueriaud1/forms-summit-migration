---
type: Oracle Table
title: S_PRODUCT
description: Product master — one row per product.
resource: urn:oracle:summit:table:S_PRODUCT
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_PRODUCT

Product master.

Source: `forms-summit-example/init-summit.sql:1283` (table), `:56785` (constraints),
`:57009` (FKs).

## Schema

```sql
CREATE TABLE S_PRODUCT (
  ID                    NUMBER(7,0)  NOT NULL,
  NAME                  VARCHAR2(50) NOT NULL,
  SHORT_DESC            VARCHAR2(255),
  LONGTEXT_ID           NUMBER(7,0),
  IMAGE_ID              NUMBER(7,0),
  SUGGESTED_WHLSL_PRICE NUMBER(11,2),
  WHLSL_UNITS           VARCHAR2(25),
  CONSTRAINT S_PRODUCT_ID_PK   PRIMARY KEY (ID),
  CONSTRAINT S_PRODUCT_NAME_UK UNIQUE (NAME),
  CONSTRAINT S_PRODUCT_IMAGE_ID_FK    FOREIGN KEY (IMAGE_ID)    REFERENCES S_IMAGE(ID),
  CONSTRAINT S_PRODUCT_LONGTEXT_ID_FK FOREIGN KEY (LONGTEXT_ID) REFERENCES S_LONGTEXT(ID)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK |
| NAME | VARCHAR2(50) | NOT NULL | UK |
| SHORT_DESC | VARCHAR2(255) | | |
| LONGTEXT_ID | NUMBER(7,0) | | FK → `S_LONGTEXT.ID` |
| IMAGE_ID | NUMBER(7,0) | | FK → `S_IMAGE.ID` |
| SUGGESTED_WHLSL_PRICE | NUMBER(11,2) | | |
| WHLSL_UNITS | VARCHAR2(25) | | |

## Relationships

- FK `IMAGE_ID` → [S_IMAGE](s_image.md).ID; resolved to a filename by DB function
  [GET_PRODUCT_IMAGE](../functions/get_product_image.md)
- FK `LONGTEXT_ID` → [S_LONGTEXT](s_longtext.md).ID
- Referenced by [S_ITEM](s_item.md).PRODUCT_ID and [S_INVENTORY](s_inventory.md).PRODUCT_ID

## Related

- [tables/s_image.md](s_image.md) · [tables/s_longtext.md](s_longtext.md)
- [functions/get_product_image.md](../functions/get_product_image.md) — reads `IMAGE_ID`
- [tables/s_item.md](s_item.md) · [tables/s_inventory.md](s_inventory.md)

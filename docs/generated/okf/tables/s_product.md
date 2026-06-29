---
type: Oracle Table
title: S_PRODUCT
description: Product master; backs the PRODUCTS_LOV and the S_ITEM product defaulter (name + suggested price).
resource: urn:oracle:summit:table:S_PRODUCT
tags: [oracle, summit, table, lookup, lov, orders]
timestamp: 2026-06-26
---

# S_PRODUCT

Product master. Two ORDERS constructs read it: the `PRODUCTS_LOV` picker and the
`S_ITEM.PRODUCT_ID` WHEN-VALIDATE-ITEM defaulter (re-defaults description + price
on any product change, including direct entry).

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

| Column | Type | Null | Notes |
|---|---|---|---|
| ID | NUMBER(7,0) | NOT NULL | PK; LOV return value → `S_ITEM.PRODUCT_ID` |
| NAME | VARCHAR2(50) | NOT NULL | UK; LOV "Name" column + `S_ITEM.DESCRIPTION` defaulter |
| SUGGESTED_WHLSL_PRICE | NUMBER(11,2) | | → `S_ITEM.PRICE` defaulter |
| IMAGE_ID | NUMBER(7,0) | | FK → `S_IMAGE.ID`; path used by [GET_PRODUCT_IMAGE](../functions/get_product_image.md) |
| (SHORT_DESC, LONGTEXT_ID, WHLSL_UNITS) | | | not used by ORDERS |

## Used by ORDERS

- **`PRODUCTS_LOV`** — `Select id, name from s_product order by name`. Columns:
  `ID` (Number, "Id", width 25, **returns to** `S_ITEM.PRODUCT_ID`); `NAME`
  (Character, "Name", width 230, display-only). Picker returns the `id` only.
- **`S_ITEM.PRODUCT_ID` WHEN-VALIDATE-ITEM** —
  `SELECT name, suggested_whlsl_price INTO :S_ITEM.description, :S_ITEM.price
  FROM S_PRODUCT WHERE :S_ITEM.product_id = id`. `NO_DATA_FOUND` → "Invalid
  Product Id!". Fires on any product change, so it is distinct from the LOV.
- **`S_ITEM` POST-QUERY** — `SELECT name INTO :S_ITEM.description ...` to fill the
  display-only description on load.
- **`GET_PRODUCT_IMAGE`** joins `S_PRODUCT.IMAGE_ID → S_IMAGE.ID` to find the
  image filename.

## Related

- [functions/get_product_image.md](../functions/get_product_image.md)
- `../../orders-form-spec.md` § LOV

---
type: Oracle Table
title: S_INVENTORY
description: Per-warehouse stock levels — one row per product per warehouse.
resource: urn:oracle:summit:table:S_INVENTORY
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_INVENTORY

Stock levels per product per warehouse.

Source: `forms-summit-example/init-summit.sql:1197` (table), `:56851` (constraints),
`:56985` (FKs).

## Schema

```sql
CREATE TABLE S_INVENTORY (
  PRODUCT_ID               NUMBER(7,0) NOT NULL,
  WAREHOUSE_ID             NUMBER(7,0) NOT NULL,
  AMOUNT_IN_STOCK          NUMBER(9,0),
  REORDER_POINT            NUMBER(9,0),
  MAX_IN_STOCK             NUMBER(9,0),
  OUT_OF_STOCK_EXPLANATION VARCHAR2(255),
  RESTOCK_DATE             DATE,
  CONSTRAINT S_INVENTORY_PRODID_WARID_PK PRIMARY KEY (PRODUCT_ID, WAREHOUSE_ID),
  CONSTRAINT S_INVENTORY_PRODUCT_ID_FK   FOREIGN KEY (PRODUCT_ID)   REFERENCES S_PRODUCT(ID),
  CONSTRAINT S_INVENTORY_WAREHOUSE_ID_FK FOREIGN KEY (WAREHOUSE_ID) REFERENCES S_WAREHOUSE(ID)
);
```

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| PRODUCT_ID | NUMBER(7,0) | NOT NULL | PK part 1; FK → `S_PRODUCT.ID` |
| WAREHOUSE_ID | NUMBER(7,0) | NOT NULL | PK part 2; FK → `S_WAREHOUSE.ID` |
| AMOUNT_IN_STOCK | NUMBER(9,0) | | |
| REORDER_POINT | NUMBER(9,0) | | |
| MAX_IN_STOCK | NUMBER(9,0) | | |
| OUT_OF_STOCK_EXPLANATION | VARCHAR2(255) | | |
| RESTOCK_DATE | DATE | | |

## Keys

- **Composite PK `(PRODUCT_ID, WAREHOUSE_ID)`** — one product has at most one row
  per warehouse.

## Relationships

- FK `PRODUCT_ID` → [S_PRODUCT](s_product.md).ID
- FK `WAREHOUSE_ID` → [S_WAREHOUSE](s_warehouse.md).ID

## Related

- [tables/s_product.md](s_product.md) — `PRODUCT_ID` FK target
- [tables/s_warehouse.md](s_warehouse.md) — `WAREHOUSE_ID` FK target
- [tables/s_item.md](s_item.md) — shares the `PRODUCT_ID` key

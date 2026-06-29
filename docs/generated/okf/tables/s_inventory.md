---
type: Oracle Table
title: S_INVENTORY
description: Per-warehouse stock levels — the ORDERS form's S_INVENTORY sub-detail block base table.
resource: urn:oracle:summit:table:S_INVENTORY
tags: [oracle, summit, table, base-table, sub-detail, orders]
timestamp: 2026-06-26
---

# S_INVENTORY

Stock levels per product per warehouse. Base table of the `S_INVENTORY` block —
the auto-queried, deferred sub-detail of `S_ITEM_S_INVENTORY`, shown on the
separate `CV_INVENTORY` canvas / `WIN_INVENTORY` window. Columns are in
`ORDERS.spec.json`; this file adds the **constraints**.

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

| Column | Type | Null | Notes |
|---|---|---|---|
| PRODUCT_ID | NUMBER(7,0) | NOT NULL | PK part 1; FK → `S_PRODUCT.ID`; join from `S_ITEM.PRODUCT_ID` |
| WAREHOUSE_ID | NUMBER(7,0) | NOT NULL | PK part 2; FK → `S_WAREHOUSE.ID` |
| AMOUNT_IN_STOCK | NUMBER(9,0) | | "In Stock" |
| REORDER_POINT | NUMBER(9,0) | | "Reorder Point" |
| MAX_IN_STOCK | NUMBER(9,0) | | "Max In Stock" |
| OUT_OF_STOCK_EXPLANATION | VARCHAR2(255) | | not placed on the canvas |
| RESTOCK_DATE | DATE | | "Restock Date" |

> **Composite PK `(PRODUCT_ID, WAREHOUSE_ID)`** → one current line's product can
> have several inventory rows (one per warehouse), which is why this block is
> multi-record. `OUT_OF_STOCK_EXPLANATION` is a column but has no form item.

## Relation

- Detail of **`S_ITEM_S_INVENTORY`** (`s_item.product_id = s_inventory.product_id`),
  **Deferred + AutoQuery** (Isolated delete → no master-delete guard). Demonstrates
  the second coordination pattern (auto-query sub-detail) beyond master-detail.
  No block-level triggers in the form.

## Related

- [tables/s_item.md](s_item.md) — master of the relation
- [tables/s_product.md](s_product.md) — `PRODUCT_ID` FK target

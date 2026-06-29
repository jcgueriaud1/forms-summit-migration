---
type: Oracle Table
title: S_ITEM
description: Order line-item table — the ORDERS form's S_ITEM detail block base table.
resource: urn:oracle:summit:table:S_ITEM
tags: [oracle, summit, table, base-table, detail, orders]
timestamp: 2026-06-26
---

# S_ITEM

Order line items. Base table of the `S_ITEM` block (detail of `S_ORD_S_ITEM`,
master of `S_ITEM_S_INVENTORY`). Columns are in `ORDERS.spec.json`; this file
adds the **constraints** — notably the composite PK that explains the
`MAX(item_id)+1` PRE-INSERT scheme.

Source: `forms-summit-example/init-summit.sql:1216` (table), `:56766` (constraints),
`:56993` (FKs), `:56429` (AUDIT_S_ITEM trigger).

## Schema

```sql
CREATE TABLE S_ITEM (
  ORD_ID           NUMBER(7,0) NOT NULL,
  ITEM_ID          NUMBER(7,0) NOT NULL,
  PRODUCT_ID       NUMBER(7,0) NOT NULL,
  PRICE            NUMBER(11,2),
  QUANTITY         NUMBER(9,0),
  QUANTITY_SHIPPED NUMBER(9,0),
  CONSTRAINT S_ITEM_ORDID_ITEMID_PK PRIMARY KEY (ORD_ID, ITEM_ID),
  CONSTRAINT S_ITEM_ORDID_PRODID_UK UNIQUE (ORD_ID, PRODUCT_ID),
  CONSTRAINT S_ITEM_ORD_ID_FK     FOREIGN KEY (ORD_ID)     REFERENCES S_ORD(ID),
  CONSTRAINT S_ITEM_PRODUCT_ID_FK FOREIGN KEY (PRODUCT_ID) REFERENCES S_PRODUCT(ID)
);
```

| Column | Type | Null | Notes |
|---|---|---|---|
| ORD_ID | NUMBER(7,0) | NOT NULL | PK part 1; FK → `S_ORD.ID`; copied from `S_ORD.ID` |
| ITEM_ID | NUMBER(7,0) | NOT NULL | PK part 2; PRE-INSERT `NVL(MAX(item_id),0)+1` per order |
| PRODUCT_ID | NUMBER(7,0) | NOT NULL | FK → `S_PRODUCT.ID`; LOV-bound; WHEN-VALIDATE-ITEM defaulter |
| PRICE | NUMBER(11,2) | | defaulted from `S_PRODUCT.SUGGESTED_WHLSL_PRICE` |
| QUANTITY | NUMBER(9,0) | | ordered qty |
| QUANTITY_SHIPPED | NUMBER(9,0) | | **drives `ITEM_TOTAL` = qty_shipped × price**, not QUANTITY |

> Form-only (non-DB) items on this block: `ITEM_TOTAL` (Formula),
> `TOTAL` (Summary sum of ITEM_TOTAL), `DESCRIPTION`, `PRODUCT_IMAGE`,
> `IMAGE_DESCRIPTION`. None are columns here.

## Keys & cardinality

- **Composite PK `(ORD_ID, ITEM_ID)`** → item ids are per-order, hence the
  `MAX(item_id)+1` allocation rather than a sequence.
- **UK `(ORD_ID, PRODUCT_ID)`** → a product appears at most once per order; this
  is the rule the `PRODUCT_LOV_BUTTON` enforces with its `id NOT IN (...)`
  exclusion list.

## Relations / triggers / DB objects

- Detail of **`S_ORD_S_ITEM`**; master of **`S_ITEM_S_INVENTORY`**
  (deferred + auto-query).
- Triggers: PRE-INSERT (item id), POST-QUERY (description), WHEN-VALIDATE-ITEM
  (product defaulter), WHEN-NEW-RECORD-INSTANCE (image), KEY-DELREC/DUPREC.
- **[AUDIT_S_ITEM](../triggers/audit_s_item.md)** fires AFTER INSERT — stays in DB.

## Related

- [tables/s_ord.md](s_ord.md) · [tables/s_product.md](s_product.md) · [tables/s_inventory.md](s_inventory.md)
- [triggers/audit_s_item.md](../triggers/audit_s_item.md)

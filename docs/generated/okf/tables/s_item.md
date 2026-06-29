---
type: Oracle Table
title: S_ITEM
description: Order line-item table — one row per product line on an order.
resource: urn:oracle:summit:table:S_ITEM
tags: [oracle, summit, table]
timestamp: 2026-06-29
---

# S_ITEM

Order line items. Child of `S_ORD`; references `S_PRODUCT`.

Source: `forms-summit-example/init-summit.sql:1216` (table), `:56766` (constraints),
`:56993` (FKs), `:56429` (`AUDIT_S_ITEM` trigger).

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

| Column | Type | Null | Constraint / notes |
|---|---|---|---|
| ORD_ID | NUMBER(7,0) | NOT NULL | PK part 1; FK → `S_ORD.ID` |
| ITEM_ID | NUMBER(7,0) | NOT NULL | PK part 2 |
| PRODUCT_ID | NUMBER(7,0) | NOT NULL | FK → `S_PRODUCT.ID` |
| PRICE | NUMBER(11,2) | | |
| QUANTITY | NUMBER(9,0) | | |
| QUANTITY_SHIPPED | NUMBER(9,0) | | |

## Keys

- **Composite PK `(ORD_ID, ITEM_ID)`** — `ITEM_ID` is scoped to the order, not
  globally unique.
- **UK `(ORD_ID, PRODUCT_ID)`** — a given product appears at most once per order.

## Relationships

- FK `ORD_ID` → [S_ORD](s_ord.md).ID (parent)
- FK `PRODUCT_ID` → [S_PRODUCT](s_product.md).ID
- Referenced by [S_INVENTORY](s_inventory.md) on `PRODUCT_ID` (shared product key)
- DB trigger [AUDIT_S_ITEM](../triggers/audit_s_item.md) fires `AFTER INSERT`

## Related

- [tables/s_ord.md](s_ord.md) · [tables/s_product.md](s_product.md) · [tables/s_inventory.md](s_inventory.md)
- [triggers/audit_s_item.md](../triggers/audit_s_item.md)

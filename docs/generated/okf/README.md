---
type: OKF Bundle
title: Summit ORDERS — Referenced DB Objects
description: Resolved DDL and signatures for the database objects the ORDERS structural spec defers to as "resolve from schema".
resource: urn:summit:okf:orders-referenced-db-objects
tags: [oracle, summit, orders, schema, reference]
timestamp: 2026-06-26
---

# Summit ORDERS — Referenced DB Objects (OKF bundle)

This is an [Open Knowledge Format](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md)
bundle: a directory of Markdown-with-frontmatter concept files, one per database
object. It **resolves** the objects that `docs/generated/ORDERS.spec.json` lists
but does not contain — the entries under `referencedDbObjects` (marked
`"unknown (resolve from schema)"`) plus the lookup tables the form's triggers
`SELECT` against.

It is **decision-free reference** (the same contract as the structural spec):
it records what the objects *are*, not what they become in Vaadin. Migration
decisions live in `docs/migration-rules.yaml`; per-form findings live in
`docs/orders-form-spec.md`.

Source of all DDL: `forms-summit-example/init-summit.sql` (line numbers cited per
file). DDL is shown **portable** — Oracle storage/segment/`TABLESPACE "SYSTEM"`
clauses stripped (see the `summit-dump-load-gotchas` note) so it can seed the
container; the dump remains the authority.

## Contents

### Base tables (the form's own blocks)

These back the `S_ORD` / `S_ITEM` / `S_INVENTORY` blocks. Their columns are
already in `ORDERS.spec.json` as data-source columns; these files add the
**constraints** (PKs, CHECKs, FKs) the spec omits but the build needs.

| Concept | Type | Why ORDERS needs it |
|---|---|---|
| [tables/s_ord.md](tables/s_ord.md) | Oracle Table | `S_ORD` master block; PAYMENT_TYPE/ORDER_FILLED CHECKs; stored-`TOTAL` parked-task signal |
| [tables/s_item.md](tables/s_item.md) | Oracle Table | `S_ITEM` detail block; composite PK explains `MAX(item_id)+1`; UK = product-once-per-order |
| [tables/s_inventory.md](tables/s_inventory.md) | Oracle Table | `S_INVENTORY` sub-detail; composite PK `(PRODUCT_ID, WAREHOUSE_ID)` |

### Referenced objects (resolved from the dump)

These are read by the form's triggers/LOV but are not carried by the spec — the
entries it marked `"resolve from schema"`.

| Concept | Type | Why ORDERS needs it |
|---|---|---|
| [tables/s_customer.md](tables/s_customer.md) | Oracle Table | `S_ORD.CUSTOMER_ID` WHEN-VALIDATE-ITEM + POST-QUERY name lookup; `PAYMENT_TYPE` credit check reads `CREDIT_RATING` |
| [tables/s_emp.md](tables/s_emp.md) | Oracle Table | sales-rep name (`E.last_name`) for the `SALES_REP_NAME` display item |
| [tables/s_product.md](tables/s_product.md) | Oracle Table | `PRODUCTS_LOV` query + `S_ITEM.PRODUCT_ID` WHEN-VALIDATE-ITEM defaulter (`name`, `suggested_whlsl_price`) |
| [sequences/s_ord_id.md](sequences/s_ord_id.md) | Oracle Sequence | `S_ORD` PRE-INSERT new-order id (`S_ORD_ID.nextval`) |
| [functions/get_product_image.md](functions/get_product_image.md) | Oracle Function | product-image filename lookup (deferred construct; `WHEN-NEW-RECORD-INSTANCE`) |
| [triggers/audit_s_item.md](triggers/audit_s_item.md) | Oracle Trigger | fires on `S_ITEM` insert; boundary-rule proof point (**stays in DB**) |

## Not resolvable from the DB dump

- **`date_lov.get_date`** — the other `referencedDbObjects` entry. It is **not a
  database object**; it lives in the Forms PL/SQL library `calendar.pll`
  (`AttachedLibrary CALENDAR`). Deferred construct → maps to `DatePicker`
  (`orders-form-spec.md` § Date LOVs). No DB signature to extract.

## Constraint highlights (across the bundle)

Constraints the structural spec omits but that materially shape the build,
now captured per object:

- `S_ITEM` PK = **composite `(ORD_ID, ITEM_ID)`** → the PRE-INSERT
  `MAX(item_id)+1` per-order scheme; UK `(ORD_ID, PRODUCT_ID)` → a product
  appears once per order (the `PRODUCT_LOV_BUTTON` `id NOT IN (...)` rule).
- `S_ORD` CHECKs `PAYMENT_TYPE IN ('CASH','CREDIT')` and
  `ORDER_FILLED IN ('Y','N')` → DB mirror of the radio group / checkbox sets.
- `S_CUSTOMER` CHECK `CREDIT_RATING IN ('EXCELLENT','GOOD','POOR')` → but the
  CREDIT payment trigger only accepts `('GOOD','EXCELLENT')`, so `'POOR'` is the
  rating forced back to CASH.
- `S_INVENTORY` PK = composite `(PRODUCT_ID, WAREHOUSE_ID)` → multi-record
  sub-detail (one product, many warehouses).

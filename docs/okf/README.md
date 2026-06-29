---
type: OKF Bundle
title: Summit Migration — Database Reference
description: Pure database reference (DDL, columns, constraints, relationships) for the Summit objects in migration scope. Database facts only — no form behaviour.
resource: urn:summit:okf:referenced-db-objects
tags: [oracle, summit, migration, schema, reference]
timestamp: 2026-06-29
---

# Summit Migration — Database Reference (OKF bundle)

This is an [Open Knowledge Format](https://github.com/GoogleCloudPlatform/knowledge-catalog/blob/main/okf/SPEC.md)
bundle: a directory of Markdown-with-frontmatter concept files, one per database
object, for understanding the Summit database during the migration.

It is **database facts only** — DDL, columns, constraints, and relationships —
for the subset of objects the migration touches. It says what each object *is*;
it does **not** describe how any form uses it. Form behaviour (which block,
trigger, LOV or query reads an object) lives in the per-form structural specs
(e.g. `docs/generated/ORDERS.spec.json`, `docs/orders-form-spec.md`) and the
source `forms-summit-example/*.fmb.xml`. Migration decisions live in
`docs/migration-rules.yaml`.

DDL is shown **portable** — Oracle storage/segment/`TABLESPACE "SYSTEM"` clauses
stripped (see the `summit-dump-load-gotchas` note) so it can seed a container.
The dump (`forms-summit-example/init-summit.sql`) remains the authority; line
numbers are cited per file.

## Scope

This bundle is the **migration-relevant subset** of the schema, not the whole
dump (`init-summit.sql` carries ~70 tables, most unrelated to the forms being
migrated). The subset is **FK-closed**: every foreign-key target reachable from
the documented objects is itself documented in this bundle.

## Objects

| Concept | Type | Summary |
|---|---|---|
| [tables/s_ord.md](tables/s_ord.md) | Oracle Table | Order master (header); PK from `S_ORD_ID`; CHECKs on PAYMENT_TYPE / ORDER_FILLED |
| [tables/s_item.md](tables/s_item.md) | Oracle Table | Order line items; composite PK `(ORD_ID, ITEM_ID)`; UK `(ORD_ID, PRODUCT_ID)` |
| [tables/s_inventory.md](tables/s_inventory.md) | Oracle Table | Per-warehouse stock; composite PK `(PRODUCT_ID, WAREHOUSE_ID)` |
| [tables/s_customer.md](tables/s_customer.md) | Oracle Table | Customer master; CHECK on CREDIT_RATING |
| [tables/s_emp.md](tables/s_emp.md) | Oracle Table | Employee master; UK on USERID; CHECK on COMMISSION_PCT; FKs to DEPT/TITLE/self |
| [tables/s_product.md](tables/s_product.md) | Oracle Table | Product master; UK on NAME |
| [tables/s_dept.md](tables/s_dept.md) | Oracle Table | Department lookup; UK `(NAME, REGION_ID)`; FK to S_REGION |
| [tables/s_region.md](tables/s_region.md) | Oracle Table | Sales-region lookup; UK on NAME |
| [tables/s_warehouse.md](tables/s_warehouse.md) | Oracle Table | Warehouse master; FKs to S_REGION and S_EMP |
| [tables/s_image.md](tables/s_image.md) | Oracle Table | Image catalog (FILENAME + inline `LONG RAW`); CHECKs on FORMAT / USE_FILENAME |
| [tables/s_longtext.md](tables/s_longtext.md) | Oracle Table | Long-text catalog (FILENAME + inline TEXT) |
| [tables/s_title.md](tables/s_title.md) | Oracle Table | Job-title lookup; PK is the title string |
| [tables/s_item_audit.md](tables/s_item_audit.md) | Oracle Table | Audit sink; one constant row per `S_ITEM` insert |
| [sequences/s_ord_id.md](sequences/s_ord_id.md) | Oracle Sequence | PK generator for `S_ORD` |
| [functions/get_product_image.md](functions/get_product_image.md) | Oracle Function | Returns a product's image filename (or `'No file'`) |
| [triggers/audit_s_item.md](triggers/audit_s_item.md) | Oracle Trigger | `AFTER INSERT` on `S_ITEM` → constant row into `S_ITEM_AUDIT` |

## Relationships (FK overview)

Within the bundle:

- `S_ORD.CUSTOMER_ID` → `S_CUSTOMER.ID`
- `S_ORD.SALES_REP_ID` → `S_EMP.ID`
- `S_ITEM.ORD_ID` → `S_ORD.ID`
- `S_ITEM.PRODUCT_ID` → `S_PRODUCT.ID`
- `S_INVENTORY.PRODUCT_ID` → `S_PRODUCT.ID`
- `S_INVENTORY.WAREHOUSE_ID` → `S_WAREHOUSE.ID`
- `S_CUSTOMER.SALES_REP_ID` → `S_EMP.ID`
- `S_CUSTOMER.REGION_ID` → `S_REGION.ID`
- `S_PRODUCT.IMAGE_ID` → `S_IMAGE.ID`
- `S_PRODUCT.LONGTEXT_ID` → `S_LONGTEXT.ID`
- `S_EMP.MANAGER_ID` → `S_EMP.ID` (self-reference)
- `S_EMP.DEPT_ID` → `S_DEPT.ID`
- `S_DEPT.REGION_ID` → `S_REGION.ID`
- `S_WAREHOUSE.REGION_ID` → `S_REGION.ID`
- `S_WAREHOUSE.MANAGER_ID` → `S_EMP.ID`
- `S_EMP.TITLE` → `S_TITLE.TITLE`

All FK targets are documented in this bundle (FK-closed).

## Key constraints

- `S_ITEM` — PK `(ORD_ID, ITEM_ID)` (item id scoped to the order); UK
  `(ORD_ID, PRODUCT_ID)` (a product at most once per order).
- `S_INVENTORY` — PK `(PRODUCT_ID, WAREHOUSE_ID)` (one row per product/warehouse).
- `S_ORD` — CHECK `PAYMENT_TYPE IN ('CASH','CREDIT')`, CHECK `ORDER_FILLED IN ('Y','N')`.
- `S_CUSTOMER` — CHECK `CREDIT_RATING IN ('EXCELLENT','GOOD','POOR')`.
- `S_EMP` — UK `USERID`; CHECK `COMMISSION_PCT IN (10, 12.5, 15, 17.5, 20)`.
- `S_PRODUCT` — UK `NAME`.
- `S_DEPT` — UK `(NAME, REGION_ID)`. `S_REGION` — UK `NAME`.
- `S_IMAGE` — CHECK `FORMAT IN ('JFIFF','JTIFF')`, CHECK `USE_FILENAME IN ('Y','N')`.
- `S_LONGTEXT` — CHECK `USE_FILENAME IN ('Y','N')`.

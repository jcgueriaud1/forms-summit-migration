# ORDERS form — specification

Form-specific findings for the Summit `ORDERS.fmb` slice (source:
`forms-summit-example/ORDERS_fmb.xml`). This is a hand-written stand-in for the
structural spec the `forms-xml-analyzer` will eventually produce; keep
form-specific facts here, not in `CONTEXT.md`. Open scope questions are marked
**[TBD]** and resolved through the grill session.

## Blocks

| Block | Role | Canvas | Notes |
|---|---|---|---|
| `S_ORD` | master | `CV_ORDER` | one order |
| `S_ITEM` | detail | `CV_ORDER` | order line items |
| `S_INVENTORY` | sub-detail | `CV_INVENTORY` | auto-queried (deferred) stock for the current line's product; shown on a separate canvas toggled by `STOCK_BUTTON`. **OUT OF SCOPE for this PRD** — deferred to its own follow-up PRD (it demonstrates a second coordination pattern, auto-query sub-detail, that stretches this slice beyond the `S_ORD`/`S_ITEM` spine). |

## Slice scope (resolved)

**In the build:** master-detail (`S_ORD`/`S_ITEM`) + search/load (Variant A) + totals
+ product LOV + locking + commit.

**Deferred — out of slice (but listed on the scorecard with a reason):**
- **`S_INVENTORY` auto-query coordination** → **its own follow-up PRD**. Keeps this
  PRD on the `S_ORD`/`S_ITEM` spine; the deferred/auto-query sub-detail is a distinct
  coordination pattern worth its own slice.
- Date LOVs (`date_ordered`/`date_shipped`) → trivially `DatePicker`.
- Product image (filesystem TIFF, default-hidden, toggle button).
- Coordination-control checkboxes (`IMMEDIATE`, `AUTO_QUERY`).

Relations:
- `S_ORD_S_ITEM` — `S_ITEM.ord_id = S_ORD.id`, not deferred, no auto-query.
- `S_ITEM_S_INVENTORY` — `s_item.product_id = s_inventory.product_id`, **deferred + auto-query**.

## Search / query UX (resolved)

The `S_ORD` master is a single-record queryable block (`block-master-single` →
`FormLayout`). How Forms' Execute/Enter-Query surfaces on the web was prototyped
(`docs/prototypes/orders-search-prototype.html`, three toggleable variants) and
**resolved to Variant A — lookup search → load one order**:

- A quick search (by order id / customer / sales rep) with autocomplete, plus an
  **advanced search** panel (customer, rep, payment type, ordered date-range,
  status) for the QBE-style multi-field case.
- The chosen match loads into the header `FormLayout` + line-items `Grid`;
  **prev/next** walks the result set (the record-scrollbar seam).
- No grid-of-orders. Honest losses: arbitrary QBE → curated filter; record
  scrollbar → prev/next pager.

This is exactly what the `block-master-single` aiHint encodes; the prototype
**confirms** the rule rather than changing it.

**Rejected alternatives (recorded so they aren't re-litigated):**
- **Variant B — results grid → load detail inline below.** Rejected: weird UX,
  and it resurrects PLAN's master Grid that `block-master-single` deliberately
  rejects, breaking single-record fidelity.
- **Variant C — search page → navigate to a detail page.** A sound, conventional
  web CRUD shape, but **too far from the Forms original** for this migration; it's
  a good fit for a *different* application, not this one. No ADR superseding
  `block-master-single` is needed (neither rejected option was adopted).

## Items of interest

**S_ORD:** `id`, `customer_id`, `date_ordered`, `date_shipped`, `sales_rep_id`,
`customer_name` (display, POST-QUERY lookup), `sales_rep_name` (display),
`payment_type` (radio group), `order_filled` (checkbox).

**S_ITEM:** `ord_id` (hidden FK), `item_id` (PK, per-order MAX+1 in PRE-INSERT),
`product_id`, `price`, `quantity` (ordered), `quantity_shipped`,
`item_total` (Formula), `total` (Summary), `description` (display),
`product_image` (Image, **Visible="false"** by default), `image_description`.

## Totals (declarative, NOT trigger-driven)

- `ITEM_TOTAL` — Formula item: `:S_ITEM.quantity_shipped * :S_ITEM.price`
  (`CalculateMode="Formula"`). **Uses `quantity_shipped`, not `quantity`.**
- `TOTAL` — Summary item: `SummaryFunction="Sum"` over `ITEM_TOTAL`
  (`CalculateMode="Summary"`).

## Triggers (what they actually do)

- `WHEN-VALIDATE-ITEM` (S_ITEM): `SELECT name, suggested_whlsl_price ... FROM
  S_PRODUCT` → re-defaults `description` + `price` on product change.
- `WHEN-VALIDATE-ITEM` (S_ORD): customer lookup → `customer_name`, `sales_rep_id`,
  `sales_rep_name`.
- `WHEN-VALIDATE-RECORD` (S_ORD): `date_shipped >= date_ordered` guard.
- `POST-QUERY` (both): re-fetch display names (`customer_name`, `description`) —
  denormalized display fields; dissolve into the load query's joins.
- `PRE-INSERT` (S_ORD): `S_ORD_ID.nextval`. (S_ITEM): per-order `MAX(item_id)+1`.
- `PRE-QUERY` (S_ORD): `:S_ORD.customer_id := :GLOBAL.customer_id` — form can be
  launched filtered by a customer context from the CUSTOMERS form.
- `KEY-LISTVAL` (×2): date-picker LOV (`date_lov.get_date` from `calendar.pll`) on
  `date_ordered` and `date_shipped` — these are LOVs too, beyond the product LOV.

## LOV

`PRODUCTS_LOV` (Title "Products") bound to `S_ITEM.PRODUCT_ID`. The analyzer must
emit, for every LOV-bound item, the facts needed to rebuild it as a
LookupField/ComboBox **and** to build the service that feeds its DataProvider:

| Fact | Value (Summit) | Used for |
|---|---|---|
| **Bound item** (`LovRole: primary`) | `S_ITEM.PRODUCT_ID` | picks the classifier target (ComboBox/LookupField) |
| **Query** | `Select id, name from s_product order by name` | the service / DataProvider fetch + count |
| **Columns** (name, datatype, title, width, returnItem) | `ID` (Number, "Id", 25 → **returns to** `S_ITEM.PRODUCT_ID`); `NAME` (Character, "Name", 230 → **display-only**) | which columns to *retrieve* and which to *display* in the popup |
| **Return mapping** | only `ID` is written back | what the selection populates |

Key consequence: the LOV is a **picker only** — it returns the product `id`. The
description/price re-default is **not** part of the LOV; it is the separate
`WHEN-VALIDATE-ITEM` defaulter (rule `trigger-when-validate-item-default` in
`migration-rules.yaml`), fired on any `product_id` change including direct entry. `NAME` is shown in the popup but never
written to any item.

> **General analyzer contract:** for each LOV-bound item emit
> `{ lovName, title, query, columns:[{name, dataType, title, displayWidth, returnItem|null}], primaryReturnItem }`.

### Date LOVs (deferred constructs)

`date_ordered` / `date_shipped` use a calendar LOV (`date_lov.get_date` via
`KEY-LISTVAL`), not a record-group LOV — these map to `DatePicker`, no query to
extract.

## Transaction / locking / delete (cross-cutting — policyClassifiers)

- **Commit:** `KEY-COMMIT` body is `COMMIT_FORM;` — the whole form commits as one
  unit. `commitStyle = COMMIT_FORM` → `transaction-commit-form` (one
  `@Transactional` whole-graph `Service.save`).
- **Locking:** `LockingMode` is **absent** on `S_ORD`/`S_ITEM`/`S_INVENTORY` →
  analyzer injects the Forms default (**pessimistic**) → `concurrency-pessimistic-to-scn`
  (optimistic `ORA_ROWSCN`, no schema change; `ROWDEPENDENCIES` caveat disclosed).
- **Delete cascade (relation `DeleteRecord`):**
  - `S_ORD_S_ITEM` = **`Non Isolated`** → `delete-cascade-guard` (block master
    delete while line items exist). `ON-CHECK-DELETE-MASTER` is the generated
    mirror of this property, not the source.
  - `S_ITEM_S_INVENTORY` = **`Isolated`** → `delete-isolated` (no guard).

> **General analyzer contract:** supply `commitStyle` (form), `databaseBlock` +
> resolved `LockingMode` (block, default-injected), and `DeleteRecord` (relation).

## Server-side DB objects

- **`AUDIT_S_ITEM`** (AFTER INSERT ON `s_item`, FOR EACH ROW): body is
  `INSERT INTO s_item_audit VALUES (1);` into a single-column table
  (`S_ITEM_AUDIT.A NUMBER`). **Captures no actor** — no `USER`, no `SYS_CONTEXT`,
  no `CLIENT_IDENTIFIER`. Resolves the parked USER-vs-SYS_CONTEXT question: it
  reads *neither*. Verdict: **stays in DB**, fires inside the Vaadin commit
  transaction, zero Java — a clean boundary-rule proof point.
- **`get_product_image`** function + `PRODUCT_IMAGE` image item — `imageSource =
  file` → rule `image-item-file-sourced` (Refactor). Summit specifics: the image is
  a **filesystem TIFF** (the repo's `.tif` files), loaded in
  `WHEN-NEW-RECORD-INSTANCE` via `READ_IMAGE_FILE(get_product_image(:product_id),
  'tiff', …)`; `'No file'` skips. Item is **`Visible="false"`** by default (toggled
  by `IMAGE_BUTTON`). TIFF is not browser-native → needs conversion (PNG/JPEG).
  Deferred construct; the function-derived filename is a service lookup, not a
  column. (TIFF is a Summit specific, kept here — not in the generic ruleset.)

> **Decision:** the `CLIENT_IDENTIFIER` bridge is **dropped** — the form's audit
> trigger captures no actor, so nothing in the slice exercises it. A plain
> HikariCP connection pool is sufficient for the POC. (Oracle proxy auth /
> `CLIENT_IDENTIFIER` remain available patterns for real triggers that read the
> actor; out of scope here.)

## Other UI

Secondary canvases: `CV_INVENTORY`, `CV_HELP`, image display. Toolbar buttons:
`STOCK_BUTTON`, `IMAGE_BUTTON`, `SHOW_HELP_BUTTON`, `EXIT_BUTTON`, plus `IMMEDIATE`
and `AUTO_QUERY` checkboxes that change relation-coordination behavior at runtime.
Product image is loaded from a **filesystem TIFF** via `GET_PRODUCT_IMAGE` +
`READ_IMAGE_FILE`, only when the (default-hidden) image item is made visible.

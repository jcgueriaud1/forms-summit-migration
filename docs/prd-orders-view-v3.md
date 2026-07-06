# PRD: Functional Vaadin ORDERS view (S_ORD/S_ITEM master-detail spine) [v3]

## Problem Statement

Summit's `ORDERS.fmb` Oracle Forms screen lets a clerk find an order, read and edit
its header and line items, see live totals, pick products from a list of values, and
commit the whole order as one unit — with Forms' modal Execute/Enter-Query, record
scrollbar, and pessimistic locking. We need to prove that this screen migrates to a
**functional** Vaadin Flow view that a Forms architect would find credible: same data,
same business behaviour, **the same screen structurally** (same fields, in the form's
order, grouped and titled the same way, each rendered as its mapped kind of control),
modern web idioms, and a layered architecture whose business logic is pure and testable.
This PRD covers the `S_ORD` / `S_ITEM` master-detail spine end to end; it is the
credibility piece, not a static scaffold.

> **v3.** This supersedes the v2 PRD (issue #38), regenerated against `master` after v2's
> build shipped. v3's driver is **ADR-0015 — a disposition ledger gates ticket generation
> at mapped-property grain**. A retrofit audit of the v2 build found **four losses that
> every existing guarantee sailed past** — each had spec data *and* a matching ruleset
> entry, yet nothing downstream consumed it: the `GLOBAL.customer_id` calling-context
> contract, the `DATE_ORDERED` `$$date$$` default, the `QUANTITY_SHIPPED` insert-only
> editability mode, and the undocumented `SALES_REP_ID` read-only deviation. v3 makes all
> four **first-class**, backed by the regenerated disposition ledger
> (`docs/generated/ORDERS.ledger.md`, 151 rows) which is now the completeness gate: no
> build tickets while any row is unassigned, `migrated-as` rows become runtime acceptance
> criteria, and cited refs are checked for liveness (`--check-refs`). See "Changes from
> v2" at the end.

## Solution

A `@Route`-mapped **ORDERS view** built on the project's `View ← Presenter ← Service ←
Domain ← Data` layering. The clerk **searches** for an order (quick search by id /
customer / sales rep, plus an expandable advanced multi-field filter), and the chosen
match **loads one order** into a header `FormLayout` + a line-items `Grid`, with
**prev/next** stepping through the result set — the faithful web rendering of Forms'
single-record query (Variant A, decided via the search-UX prototype). From there the
clerk edits line items inline, picks products from the `PRODUCTS_LOV`, sees **order
totals recompute live**, and **commits the whole order graph** in one transaction with
optimistic (`ORA_ROWSCN`) concurrency. Business quantities and invariants live as **pure
Domain methods** (ADR-0001); validation runs through **Binder** with the **Service
commit as the gate** (ADR-0002, no Signals). The migrated screen preserves **structural
layout fidelity** (ADR-0012) — the "Order Information" heading, the bordered `RECT59`
group as a titled section, fields in `layoutPlan` order, and each field as its mapped
control kind — and treats **read-only as a state, not a component type** (ADR-0013).

**v3 makes the four audited losses first-class:**

- **Customer calling-context contract.** The view accepts an **optional `customerId`
  route/query parameter** (the web form of Forms' `:GLOBAL.customer_id`). When present it
  **pre-filters the search** to that customer and **defaults the `customer_id` of a new
  order** — the `PRE-QUERY` / `WHEN-CREATE-RECORD` behaviour. When absent the view opens
  unscoped (`PRE-FORM` cleared the global). The contract is a first-class **cross-form
  interface**, not a hidden trigger detail. *(The upstream CUSTOMERS form that would
  supply the parameter is out of scope; only the receiving contract is built here.)*
- **`DATE_ORDERED` default.** A new order defaults **`date_ordered` to today**
  (`$$date$$`), set in `Order.blank()`.
- **`QUANTITY_SHIPPED` insert-only mode.** `updateAllowed=false, insertAllowed=true` is a
  **record-mode state flip** (`forms-insert-only-field`), not `setReadOnly(true)`:
  `quantity_shipped` is **editable while a line is new/unsaved and read-only once
  persisted**. Collapsing it to always-read-only or always-editable is a recorded
  fidelity loss.
- **`SALES_REP_ID` read-only re-decision.** Recorded on its ledger row: the spec says
  editable, but the field is made **lookup-filled-only** so direct entry cannot bypass
  the customer→rep consistency the `WHEN-VALIDATE-ITEM` lookup enforces.

The work is sequenced as a tracer bullet that is functional from the first slice, then
layered:

- **Step 0 — Design (done).** Search UX prototyped (three variants) and resolved to
  **Variant A** (`docs/prototypes/`, `docs/orders-form-spec.md`). No code.
- **Step 1 — Search + read-only load** (the functional tracer bullet), split:
  - **1a** — quick search → load one `S_ORD` into a **read-only header `FormLayout`**,
    wiring View ← Presenter ← Service ← Domain ← Data end to end. Read-only header renders
    `RadioButtonGroup` / `Checkbox` / `DatePicker` in a **read-only state** (ADR-0013), not
    flattened to text.
  - **1b** — add the **read-only line-items `Grid`** coordinated on the loaded order
    (`S_ORD_S_ITEM`).
  - **1c** — the **window/layout shell** (AppLayout; canvas → `VerticalLayout`; toolbar →
    `HorizontalLayout`; `CONTROL` block buttons hoisted into the surrounding layout) with
    **structural layout fidelity** (ADR-0012: "Order Information" H2, `RECT59` titled
    section, `layoutPlan` order) + **prev/next pager** + **advanced search** panel +
    **`customerId` scoping** of the search.
- **Step 2 — Totals.** `ITEM_TOTAL` and `TOTAL` as **pure Domain methods** (ADR-0001),
  fields bound read-only to their results.
- **Step 3 — Detail editing.** Editable `S_ITEM` Grid (inline, unbuffered editor);
  per-order `item_id` = `MAX+1` on insert; **`quantity_shipped` insert-only** record-mode
  state; Binder validation (ADR-0002, no Signals); Non-Isolated cascade-delete guard;
  detail record actions (New/Duplicate/Delete from `S_ITEM.crud`, `KEY-DUPREC` clear-list).
- **Step 4 — Product LOV.** `PRODUCTS_LOV` → ComboBox/LookupField on `product_id` fed by a
  service DataProvider; the description/price re-default is the **separate**
  `WHEN-VALIDATE-ITEM` defaulter, fired on any `product_id` change.
- **Step 5 — Commit + locking + master editing + record actions + contract.** `COMMIT_FORM`
  → one `@Transactional` whole-graph save; pessimistic → `ORA_ROWSCN` optimistic
  concurrency; master-header edit behaviours (customer-id → name/sales-rep lookup;
  payment-type credit-rating check; ship-date guard; **`date_ordered` default**);
  `S_ORD` record actions (New/Duplicate/Delete from `S_ORD.crud`, guard-composed); and the
  **`customerId` new-order default** (`WHEN-CREATE-RECORD`).

## User Stories

1. As an order clerk, I want to open an Orders view from the app, so that I can work with customer orders in the browser.
2. As an order clerk, I want a quick search box that matches on order id, customer name, or sales rep, so that I can find an order the way I remember it.
3. As an order clerk, I want autocomplete suggestions as I type in the quick search, so that I can pick the right order without typing the whole term.
4. As an order clerk, I want to pick one suggested order and have it load into the form, so that I see a single order's full detail rather than a list.
5. As an order clerk, I want an expandable "advanced search" panel (customer, sales rep, payment type, ordered date-range, status), so that I can narrow down when a quick term is not enough — the QBE replacement.
6. As an order clerk, I want quick and advanced criteria to combine, so that I can filter precisely.
7. As an order clerk, I want a "Clear" action that resets the search, so that I can start a new lookup.
8. As a clerk arriving from a customer context, I want the Orders view to open already filtered to that customer when a customer is in scope, so that I see only that customer's orders without re-typing (the `:GLOBAL.customer_id` / `PRE-QUERY` contract).
9. As a clerk, I want the view to open unscoped when no customer context is supplied, so that I can search all orders (`PRE-FORM` clears the global).
10. As an order clerk, I want the loaded order's header shown in a labelled form (Order Id, Customer Id, Customer Name, Date Ordered, Date Shipped, Sales Rep, Payment Type, Order Filled), so that I can read all header facts at a glance.
11. As an order clerk, I want the header laid out with its "Order Information" heading and bordered field group as in the original screen, in the same field order, so that the migrated screen is structurally the same as the form I know (layout fidelity, ADR-0012).
12. As an order clerk, I want the customer name and sales-rep name shown next to their ids, so that I do not have to memorise customer/employee numbers (the dissolved POST-QUERY lookups).
13. As an order clerk, I want the read-only header fields to keep their control kind — payment type as radios, order-filled as a checkbox, dates as date fields — shown in a read-only state, so that the screen reads the same even before I edit (ADR-0013).
14. As an order clerk, I want the order's line items shown in a grid (Item, Product, Description, Price, Qty, Shipped, Item Total), so that I can see what was ordered.
15. As an order clerk, I want the line-items grid to load automatically for the order I selected, so that header and detail always stay in sync (`S_ORD_S_ITEM` coordination).
16. As an order clerk, I want prev/next controls and a "record N of M" indicator, so that I can step through the orders my search returned — the record-scrollbar replacement.
17. As an order clerk, I want prev/next disabled at the ends of the result set, so that I know when I am at the first or last match.
18. As an order clerk, I want an order total displayed under the line items, so that I can see the order's value.
19. As an order clerk, I want each line's item total to equal quantity-shipped × price, so that the figure matches what was actually shipped (not merely ordered).
20. As an order clerk, I want the order total to be the sum of the line item totals, so that the order value is always consistent with its lines.
21. As an order clerk, I want totals to recompute live as I edit quantities or prices, so that I see the effect of my edits immediately.
22. As an order clerk, I want to edit line items directly in the grid, so that I can adjust an order without a separate screen.
23. As an order clerk, I want a new line to get the next item id within the order automatically, so that I do not assign line numbers by hand (`MAX+1` per order).
24. As an order clerk, I want to enter the quantity shipped only while a line is new, and to see it become read-only once the line is saved, so that shipped quantities are not silently rewritten after the fact (`QUANTITY_SHIPPED` insert-only mode).
25. As an order clerk, I want to add a new line item to an order, so that I can extend an order.
26. As an order clerk, I want to duplicate a line, so that I can add a similar line quickly (with item id / product / description / price cleared per the Forms `KEY-DUPREC`).
27. As an order clerk, I want to delete a line item, so that I can remove something ordered in error.
28. As an order clerk, I want to be blocked from deleting an order that still has line items, so that I cannot orphan detail records (Non-Isolated delete guard).
29. As an order clerk, I want to pick a product from a products list of values on a line, so that I do not have to know product ids.
30. As an order clerk, I want the list of values to show product id and name but write back only the id, so that the line stores the correct key.
31. As an order clerk, I want a line's description and price to default from the chosen product, so that I do not retype catalogue data (the `WHEN-VALIDATE-ITEM` defaulter).
32. As an order clerk, I want the description/price re-default to fire whenever the product id changes — list pick or direct entry — so that the line always reflects the current product.
33. As an order clerk, I want an invalid product id to be rejected with a clear message, so that I cannot save a line for a product that does not exist.
34. As an order clerk, I want to create a new order, so that I can record a fresh order.
35. As an order clerk creating a new order in a customer context, I want its customer id pre-filled from that context, so that I do not re-key the customer I am already working with (`WHEN-CREATE-RECORD`).
36. As an order clerk, I want a new order's ordered date to default to today, so that I do not have to type the current date every time (`DATE_ORDERED` `$$date$$` default).
37. As an order clerk, I want to duplicate an order, so that I can base a new order on an existing one (order id cleared per `KEY-DUPREC`).
38. As an order clerk, I want to delete an order (with confirmation), so that I can remove an order recorded in error — subject to the line-items guard.
39. As an order clerk, I want to edit the header (customer, dates, payment type, order-filled), so that I can correct order details.
40. As an order clerk, I want entering a customer id to fill in the customer name and sales rep, so that the header is consistent with the customer master (the `S_ORD` `WHEN-VALIDATE-ITEM` lookup).
41. As an order clerk, I want the sales rep to come only from the customer lookup and not be hand-editable, so that the rep always matches the customer (recorded `SALES_REP_ID` re-decision).
42. As an order clerk, I want an invalid customer id rejected with a clear message, so that I cannot attach an order to a non-existent customer.
43. As an order clerk, I want a CREDIT payment to be refused (and reset to CASH with a notice) when the customer's credit rating is not GOOD or EXCELLENT, so that we do not extend credit we should not.
44. As an order clerk, I want to be warned and blocked when the ship date is before the order date, so that I cannot record an impossible shipment.
45. As an order clerk, I want to commit the whole order — header and all lines — as one action, so that an order is saved consistently (`COMMIT_FORM`).
46. As an order clerk, I want my commit to fail cleanly if someone else changed the order since I loaded it, so that I do not silently overwrite their changes (optimistic `ORA_ROWSCN`).
47. As an order clerk, I want invalid input flagged inline before commit, so that I can fix it; and I want the commit to refuse anything still invalid, so that nothing bad persists.
48. As a Forms architect evaluating the migration, I want the order-total, invariant, defaulting, and record-mode logic to live in pure, unit-tested Domain methods, so that I can trust the business behaviour is preserved independently of the UI.
49. As a Forms architect auditing completeness, I want every decision-prone spec fact (defaults, editability modes, the cross-form contract) to have a recorded disposition backed by a test or a tracked defer, so that I can see nothing leaked between the spec and the build (the disposition ledger, ADR-0015).
50. As a developer extending the app, I want search, load, save, and concurrency encapsulated behind a repository/service interface, so that I can change the data access without touching the view.
51. As a developer, I want the order graph loaded with its display names already joined, so that the view has no N+1 lookups and no POST-QUERY round-trips.

## Implementation Decisions

**Architecture & layering.** Follow the established `View ← Presenter ← Service ← Domain
← Data` layering (mirrors the `Demo*` skeleton: `OrderView` ← `OrderPresenter` ←
`OrderService` ← `OrderRepository`, with pure logic in `domain`). Business quantities and
cross-record invariants are **Domain methods** (ADR-0001); the **Service commit is the
authoritative gate** (ADR-0002).

**Disposition ledger drives ticket coverage (ADR-0015).** Build tickets carry a
`Covers:` list of `docs/generated/ORDERS.ledger.md` row ids plus a per-field fidelity
table; each `migrated-as` row a ticket covers is an **acceptance criterion** asserting the
named behaviour at runtime. The six rows the v2 audit left unassigned are resolved here
(customer contract → migrated-as; `$$date$$` default → migrated-as; `QUANTITY_SHIPPED`
insert-only → migrated-as; `SALES_REP_ID` → re-decided) so the `to-issues` gate can cut
v3 build tickets. Cited refs must stay live (`--check-refs`).

**Block topology (from `migration-rules.yaml`, authoritative):**
- `S_ORD` → **`FormLayout`** — single-record queryable master (`block-master-single`).
  Forms Execute/Enter-Query + record scrollbar become a **lookup that loads one record +
  prev/next over the result set**, realized as **Variant A** (quick search with
  autocomplete + advanced multi-field filter; no grid-of-orders). Variants B and C were
  prototyped and rejected (see `docs/orders-form-spec.md`).
- `S_ITEM` → **editable `Grid`** (`recordsDisplayed=4`) — inline unbuffered editing.
- `CONTROL` → **`LayoutHost`** — its buttons are hoisted into the surrounding layout.
- Canvas → container: Content → `VerticalLayout`, Toolbar → `HorizontalLayout`.

**Layout fidelity (ADR-0012).** The header renders the "Order Information" H2, the
bordered `RECT59` group as a titled section, and fields in `layoutPlan` order, from the
spec's `frames` / `staticText.role` / `layoutPlan` signals. Each item keeps its mapped
control kind. Visual styling (colour/font/spacing) is owned by the **Aura theme**
(ADR-0008, no Lumo) and deliberately dropped.

**Cross-form contract — `GLOBAL.customer_id` (v3, new).** Modelled as an **optional
`customerId` route/query parameter** on `OrderView`, resolved in the Presenter:
- present → seed `OrderSearchCriteria` with the customer filter (`PRE-QUERY`) and set the
  new-order default (`WHEN-CREATE-RECORD`);
- absent → unscoped (`PRE-FORM` `DEFAULT_VALUE('','GLOBAL.customer_id')`).
It is a first-class interface of the view (a **cross-form contract**), not a hidden
trigger detail. The upstream caller (CUSTOMERS form) is out of scope.

**Read-only is a state (ADR-0013).** Step-1 read-only header keeps `RadioButtonGroup` /
`Checkbox` / `DatePicker` in a read-only state; it does not flatten to `TextField`.

**Domain modules (pure, no Spring/Vaadin/DB):**
- `Order` (aggregate root) + `OrderLine`. The order graph is the unit of load and save.
  - `Order.blank(customerId?)` — factory for a new order: `payment_type = CASH`,
    `order_filled = false (N)`, **`date_ordered = today` (`$$date$$`)**, `customer_id` from
    the contract when scoped.
  - `duplicateForInsert()` on `Order` and `OrderLine` — the authoritative `KEY-DUPREC`
    clear-lists (order: clear id; line: clear item_id / product_id / description / price).
- Totals (the headline ADR-0001 artifact). Item total uses **`quantity_shipped`**, not
  `quantity`:
  ```
  OrderLine.itemTotal() = quantity_shipped * price        // Forms ITEM_TOTAL (Formula)
  Order.total()         = sum(line.itemTotal())           // Forms TOTAL (Summary=Sum)
  ```
- `OrderLine.isNew()` / record-mode predicate — drives the **`quantity_shipped`
  insert-only** editor state (`forms-insert-only-field`): the Grid editor's
  `quantity_shipped` field is editable only while the line is new/unsaved.
- `Order.shipDateValid()` — `date_shipped == null || date_shipped >= date_ordered`
  (`WHEN-VALIDATE-RECORD`).
- `PaymentEligibility` — pure predicate `allows(creditRating, paymentType)`: CREDIT is
  allowed only when `creditRating ∈ {GOOD, EXCELLENT}`, else forced to CASH. The credit
  rating is **fetched by the Service**; the **rule** is Domain.
- `OrderSearchCriteria` — immutable value object capturing quick term + advanced fields
  (customer, rep, payment type, ordered-from, ordered-to, status) **plus the optional
  customer scope** from the contract, translating them into a query specification. Pure and
  unit-testable.

**Data modules (`data`, against the Summit Oracle DB):**
- `OrderRepository` — `findByCriteria(OrderSearchCriteria)` (returns the pager's result
  set, honouring the customer scope); `loadGraph(orderId)` (header + lines, **joining**
  `S_CUSTOMER`, `S_EMP`, `S_PRODUCT` so `customer_name`, `sales_rep_name`, line
  `description` arrive with the row — dissolving both POST-QUERY triggers); `save(Order)`
  (whole-graph insert/update/delete of lines); `nextItemId(orderId)`
  (`NVL(MAX(item_id),0)+1`); optimistic-lock support via `ORA_ROWSCN`; `hasLineItems(orderId)`
  for the delete guard. New order ids come from `S_ORD_ID.nextval` (`PRE-INSERT`).
- `ProductRepository` — `PRODUCTS_LOV` query (`select id, name from s_product order by
  name`) for the DataProvider; `findProductDefaults(productId)` → `(name,
  suggested_whlsl_price)` for the defaulter.
- `CustomerRepository` — `findCustomerHeader(customerId)` → `(name, sales_rep_id,
  sales_rep_name)`; `findCreditRating(customerId)`.

**Service modules:**
- `OrderService` — orchestration and the transaction boundary. `search` (applies the
  customer scope), `load`, **`save` as a single `@Transactional` whole-graph commit**
  (`transaction-commit-form`); optimistic-lock conflict surfaced as a typed result, not a
  silent overwrite; delete guard; DB-backed master validation (customer lookup,
  credit-rating fetch then delegate to `PaymentEligibility`, product defaulter). A plain
  HikariCP pool is sufficient — the form's audit trigger captures no actor, so no
  `CLIENT_IDENTIFIER` bridge.
- `ProductLookupService` — feeds the LOV DataProvider (lazy where appropriate).

**Presenter:** `OrderPresenter` — resolves the optional `customerId` parameter into the
search scope and new-order default; runs the search into a result set; loads the selected
order; owns prev/next position; wires Binder value-change listeners to recompute totals
via the Domain methods; toggles the `quantity_shipped` editor state per line record-mode;
mediates commit and maps Service outcomes (validation failure, lock conflict) to view
feedback.

**UI:** `OrderView` (`@Route` with optional `customerId`, inside the AppLayout
`MainLayout`): `OrderSearchBar` (quick field + autocomplete + collapsible advanced panel),
header `FormLayout` bound via `Binder` with the "Order Information" section, line-items
`Grid` (read-only in Step 1, editable from Step 3) with a `TOTAL` footer, prev/next pager,
record actions (New/Duplicate/Delete), and a Commit action. Payment type →
`RadioButtonGroup` (Cash/Credit); Order Filled → `Checkbox`; dates → `DatePicker`; product
column → ComboBox/LookupField. `quantity_shipped` grid column is editable only on new
lines. Invalid input may appear briefly (no focus-trap, ADR-0002); the commit gate is
authoritative.

**Validation & reactivity (ADR-0002):** field- and bean-level `Binder` validators that
delegate to the Domain checks; live total recompute via value-change listeners +
Presenter; **no Vaadin Signals**.

## Testing Decisions

A good test asserts **external behaviour through a module's public interface**, not its
internals — same spirit as `ServerInfoFormulaTest` (pure Domain, no Spring context, no
Vaadin, no DB). Under ADR-0015, **each `migrated-as` ledger row a ticket covers is an
acceptance criterion**: it is *decided* when the ledger row is assigned and *delivered*
only once a test asserts the named behaviour. All four module groups are in scope:

- **Domain unit tests** — `OrderLine.itemTotal()` / `Order.total()` (item total uses
  `quantity_shipped`, not `quantity`; sums lines; empty order totals to zero; null/zero);
  `Order.shipDateValid()`; `PaymentEligibility.allows(...)`; **`Order.blank()` defaults
  (payment CASH, order-filled N, `date_ordered = today`)**; **`OrderLine.isNew()` /
  record-mode predicate** for the insert-only state; `duplicateForInsert()` clear-lists.
  Prior art: `ServerInfoFormulaTest`.
- **`OrderSearchCriteria`** (unit) — which predicates the criteria produce: quick term
  alone, advanced fields alone, combined (AND); **customer-scope filter applied when
  present and omitted when absent**; empty criteria matches all; date-range bounds
  inclusive.
- **Repository/Service integration** (against the Summit DB, e.g. Testcontainers / the
  gvenzl image; prior art `SummitDbIT`) — `loadGraph` returns joined display names in one
  shot; whole-graph `save` inserts/updates/deletes lines atomically; `nextItemId` is
  `MAX+1` per order; the delete guard blocks deleting an order with lines; an `ORA_ROWSCN`
  conflict makes a stale save fail rather than overwrite; **`findByCriteria` honours the
  customer scope**.
- **UI tests (Playwright + DramaFinder + Oracle, prior art `DemoViewIT` /
  `OracleSpringPlaywrightIT`)** — assert each covered `migrated-as` row's named behaviour
  end-to-end **and** the **layout-fidelity gate** (every in-scope item/frame/heading
  renders, in the form's order, as its mapped control kind). New v3 assertions:
  **`customerId`-scoped launch pre-filters search and defaults a new order's customer id**;
  **`quantity_shipped` is editable on a new line and read-only on a saved line**; the
  header defaults `date_ordered` to today on New.

Tests are written incrementally with each step. The `--check-refs` liveness check runs on
the ledger so a cited ticket must exist and a `deferred` row must cite an open ticket.

## Out of Scope

Reframed as **deferred-surface** review tickets (`ready-for-human`, ADR-0014) — accepted
for the eventual migration, tracked forward, never silently dropped:

- **CUSTOMERS form (the contract's upstream caller)** — v3 builds the *receiving* side of
  the `customerId` contract; the form that launches ORDERS with a customer in scope is a
  separate migration.
- **`S_INVENTORY` auto-query sub-detail** — its own follow-up PRD (a distinct
  deferred/auto-query coordination pattern; keeps this PRD on the `S_ORD`/`S_ITEM` spine).
- **Date LOVs** (`date_ordered`/`date_shipped` `KEY-LISTVAL` calendar) — the `DatePicker`
  built-in calendar replaces them; the `calendar.pll` / `CALENDAR` library infra is
  deferred.
- **Product image** — filesystem TIFF, default-hidden, toggle button; needs TIFF→PNG/JPEG
  conversion. Deferred.
- **Coordination-control checkboxes** (`IMMEDIATE`, `AUTO_QUERY`) that change relation
  coordination at runtime.
- **`pick` called-form / multi-select product picker**, **Help canvas** (`CV_HELP`),
  **exit/menu wiring**, and the **`m_summit` menu module**.

**Out-of-scope rejections** (decided *not to build*, `.out-of-scope/` KB):

- **Vaadin Signals** (ADR-0002) and any `CLIENT_IDENTIFIER`/proxy-auth bridge (the audit
  trigger reads no actor).
- The Forms **modal focus-trap** on validation (web cannot trap focus; commit gate
  guarantees integrity) — a documented honest loss.
- Generic **QBE Enter-Query** — replaced by the curated advanced-search panel (recorded
  re-decision); window-geometry globals (`GLOBAL.HEIGHT_*`/`WIDTH_*`) are browser-owned.

## Further Notes

- **Authoritative sources:** `docs/generated/ORDERS.ledger.md` (disposition ledger — the
  v3 completeness gate), `docs/migration-rules.yaml` (classifiers, incl.
  `forms-insert-only-field`), `docs/orders-form-spec.md` (form findings + resolved scope +
  search-UX verdict), `docs/generated/ORDERS.spec.json` (structural spec), ADR-0001, -0002,
  -0008, -0012, -0013, -0014, **-0015**.
- **Audit trigger** `AUDIT_S_ITEM` stays in the database and fires inside the commit
  transaction — zero Java, a clean boundary proof point.
- **Sequencing intent:** Step 1 is a *functional* tracer bullet; later steps layer
  behaviour onto a working view. 1a → 1b → 1c keeps each slice independently demoable.

## Changes from v2 (issue #38)

Same problem, same spine, same Variant-A decision, same core module set and steps. v3
regenerates against `master` after the v2 build shipped, driven by **ADR-0015 (disposition
ledger gates ticket generation at mapped-property grain)**. The v2 retrofit audit found
four losses every existing guarantee sailed past; v3 makes each first-class:

1. **`GLOBAL.customer_id` cross-form contract (was invisible).** ORDERS is launchable
   filtered by a customer context (`PRE-QUERY` / `WHEN-CREATE-RECORD`), cleared when
   unscoped (`PRE-FORM`). v3 models it as an optional `customerId` route/query parameter
   that pre-filters search and defaults new orders. New: user stories 8/9/35, the
   cross-form-contract implementation block, `OrderSearchCriteria` customer scope, the
   Presenter parameter resolution, and its unit + integration + UI assertions. Ledger rows
   `trigger:S_ORD.PRE-QUERY`, `trigger:S_ORD.WHEN-CREATE-RECORD`, `trigger:FORM.PRE-FORM`,
   `dependency:global-variable:GLOBAL.CUSTOMER_ID` → **migrated-as**.
2. **`DATE_ORDERED` `$$date$$` default (was dropped).** A new order defaults
   `date_ordered` to today via `Order.blank()`. New: user story 36, a Domain default + its
   test. Ledger row `property:S_ORD.DATE_ORDERED.initializeValue` → **migrated-as**.
3. **`QUANTITY_SHIPPED` insert-only mode (was flattened).** `updateAllowed=false,
   insertAllowed=true` is a record-mode state flip (`forms-insert-only-field`): editable on
   a new line, read-only once saved — not `setReadOnly(true)`. New: user story 24, the
   `OrderLine.isNew()` predicate + editor-state toggle + tests. Ledger row
   `property:S_ITEM.QUANTITY_SHIPPED.editability.updateAllowed` → **migrated-as**.
4. **`SALES_REP_ID` read-only deviation (was undocumented).** Now a first-class
   **re-decision** recorded on the ledger row (lookup-filled-only so direct entry cannot
   bypass customer→rep consistency). New: user story 41. Ledger row `item:S_ORD.SALES_REP_ID`
   → **re-decided**.
5. **The disposition ledger is the completeness gate.** Build tickets carry `Covers:` row
   ids + a per-field fidelity table; `migrated-as` rows are runtime acceptance criteria;
   `--check-refs` guards ref liveness. This closes the leak class the census, scorecard,
   and deferred-surface lane all sailed past — "nothing dropped silently" now holds at
   three grains (spec census, slice boundary, migration content).

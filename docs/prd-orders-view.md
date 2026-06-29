## Problem Statement

Summit's `ORDERS.fmb` Oracle Forms screen lets a clerk find an order, read and edit
its header and line items, see live totals, pick products from a list of values, and
commit the whole order as one unit — with Forms' modal Execute/Enter-Query, record
scrollbar, and pessimistic locking. We need to prove that this screen migrates to a
**functional** Vaadin Flow view that a Forms architect would find credible: same data,
same business behaviour, modern web idioms, and a layered architecture whose business
logic is pure and testable. This PRD covers the `S_ORD` / `S_ITEM` master-detail spine
end to end; it is the credibility piece, not a static scaffold.

## Solution

A `@Route`-mapped **ORDERS view** built on the project's `View ← Presenter ← Service ←
Domain ← Data` layering. The clerk **searches** for an order (quick search by id /
customer / sales rep, plus an expandable advanced multi-field filter), and the chosen
match **loads one order** into a header `FormLayout` + a line-items `Grid`, with
**prev/next** stepping through the result set — the faithful web rendering of Forms'
single-record query (Variant A, decided via the search-UX prototype). From there the
clerk edits line items inline (Grid Pro), picks products from the `PRODUCTS_LOV`, sees
**order totals recompute live**, and **commits the whole order graph** in one
transaction with optimistic (`ORA_ROWSCN`) concurrency. Business quantities and
invariants live as **pure Domain methods** (ADR-0001); validation runs through
**Binder** with the **Service commit as the gate** (ADR-0002, no Signals).

The work is sequenced as a tracer bullet that is functional from the first slice, then
layered:

- **Step 0 — Design (done).** Search UX prototyped (three variants) and resolved to
  **Variant A** (`docs/prototypes/`, `docs/orders-form-spec.md`). No code.
- **Step 1 — Search + read-only load** (the functional tracer bullet), split:
  - **1a** — quick search → load one `S_ORD` into a **read-only header `FormLayout`**,
    wiring View ← Presenter ← Service ← Domain ← Data end to end.
  - **1b** — add the **read-only line-items `Grid`** coordinated on the loaded order
    (`S_ORD_S_ITEM`).
  - **1c** — the **window/layout shell** (AppLayout; canvas → `VerticalLayout`; toolbar
    → `HorizontalLayout`; `CONTROL` block buttons hoisted into the surrounding layout)
    + **prev/next pager** + **advanced search** panel.
- **Step 2 — Totals.** `ITEM_TOTAL` and `TOTAL` as **pure Domain methods** (ADR-0001),
  fields bound read-only to their results.
- **Step 3 — Detail editing.** Editable `S_ITEM` Grid (Grid Pro inline); per-order
  `item_id` = `MAX+1` on insert; Binder validation (ADR-0002, no Signals);
  Non-Isolated cascade-delete guard (block master delete while line items exist).
- **Step 4 — Product LOV.** `PRODUCTS_LOV` → ComboBox/LookupField on `product_id`
  fed by a service DataProvider; the description/price re-default is the **separate**
  `WHEN-VALIDATE-ITEM` defaulter, fired on any `product_id` change.
- **Step 5 — Commit + locking + master-header editing.** `COMMIT_FORM` → one
  `@Transactional` whole-graph save; pessimistic → `ORA_ROWSCN` optimistic concurrency;
  master-header edit behaviours folded in (customer-id → name/sales-rep lookup;
  payment-type credit-rating check; ship-date guard).

## User Stories

1. As an order clerk, I want to open an Orders view from the app, so that I can work with customer orders in the browser.
2. As an order clerk, I want a quick search box that matches on order id, customer name, or sales rep, so that I can find an order the way I remember it.
3. As an order clerk, I want autocomplete suggestions as I type in the quick search, so that I can pick the right order without typing the whole term.
4. As an order clerk, I want to pick one suggested order and have it load into the form, so that I see a single order's full detail rather than a list.
5. As an order clerk, I want an expandable "advanced search" panel (customer, sales rep, payment type, ordered date-range, status), so that I can narrow down when a quick term is not enough — the QBE replacement.
6. As an order clerk, I want quick and advanced criteria to combine, so that I can filter precisely.
7. As an order clerk, I want a "Clear" action that resets the search, so that I can start a new lookup.
8. As an order clerk, I want the loaded order's header shown in a labelled form (Order Id, Customer Id, Customer Name, Date Ordered, Date Shipped, Sales Rep, Payment Type, Order Filled), so that I can read all header facts at a glance.
9. As an order clerk, I want the customer name and sales-rep name shown next to their ids, so that I do not have to memorise customer/employee numbers (the dissolved POST-QUERY lookups).
10. As an order clerk, I want the order's line items shown in a grid (Item, Product, Description, Price, Qty, Shipped, Item Total), so that I can see what was ordered.
11. As an order clerk, I want the line-items grid to load automatically for the order I selected, so that header and detail always stay in sync (`S_ORD_S_ITEM` coordination).
12. As an order clerk, I want prev/next controls and a "record N of M" indicator, so that I can step through the orders my search returned — the record-scrollbar replacement.
13. As an order clerk, I want prev/next disabled at the ends of the result set, so that I know when I am at the first or last match.
14. As an order clerk, I want an order total displayed under the line items, so that I can see the order's value.
15. As an order clerk, I want each line's item total to equal quantity-shipped × price, so that the figure matches what was actually shipped (not merely ordered).
16. As an order clerk, I want the order total to be the sum of the line item totals, so that the order value is always consistent with its lines.
17. As an order clerk, I want totals to recompute live as I edit quantities or prices, so that I see the effect of my edits immediately.
18. As an order clerk, I want to edit line items directly in the grid, so that I can adjust an order without a separate screen.
19. As an order clerk, I want a new line to get the next item id within the order automatically, so that I do not assign line numbers by hand (`MAX+1` per order).
20. As an order clerk, I want to add a new line item to an order, so that I can extend an order.
21. As an order clerk, I want to delete a line item, so that I can remove something ordered in error.
22. As an order clerk, I want to be blocked from deleting an order that still has line items, so that I cannot orphan detail records (Non-Isolated delete guard).
23. As an order clerk, I want to pick a product from a products list of values on a line, so that I do not have to know product ids.
24. As an order clerk, I want the list of values to show product id and name but write back only the id, so that the line stores the correct key.
25. As an order clerk, I want a line's description and price to default from the chosen product, so that I do not retype catalogue data (the `WHEN-VALIDATE-ITEM` defaulter).
26. As an order clerk, I want the description/price re-default to fire whenever the product id changes — list pick or direct entry — so that the line always reflects the current product.
27. As an order clerk, I want an invalid product id to be rejected with a clear message, so that I cannot save a line for a product that does not exist.
28. As an order clerk, I want to edit the header (customer, dates, payment type, order-filled), so that I can correct order details.
29. As an order clerk, I want entering a customer id to fill in the customer name and sales rep, so that the header is consistent with the customer master (the `S_ORD` `WHEN-VALIDATE-ITEM` lookup).
30. As an order clerk, I want an invalid customer id rejected with a clear message, so that I cannot attach an order to a non-existent customer.
31. As an order clerk, I want a CREDIT payment to be refused (and reset to CASH with a notice) when the customer's credit rating is not GOOD or EXCELLENT, so that we do not extend credit we should not.
32. As an order clerk, I want to be warned and blocked when the ship date is before the order date, so that I cannot record an impossible shipment.
33. As an order clerk, I want to commit the whole order — header and all lines — as one action, so that an order is saved consistently (`COMMIT_FORM`).
34. As an order clerk, I want my commit to fail cleanly if someone else changed the order since I loaded it, so that I do not silently overwrite their changes (optimistic `ORA_ROWSCN`).
35. As an order clerk, I want invalid input flagged inline before commit, so that I can fix it; and I want the commit to refuse anything still invalid, so that nothing bad persists.
36. As a Forms architect evaluating the migration, I want the order-total and validation logic to live in pure, unit-tested Domain methods, so that I can trust the business behaviour is preserved independently of the UI.
37. As a developer extending the app, I want search, load, save, and concurrency encapsulated behind a repository/service interface, so that I can change the data access without touching the view.
38. As a developer, I want the order graph loaded with its display names already joined, so that the view has no N+1 lookups and no POST-QUERY round-trips.

## Implementation Decisions

**Architecture & layering.** Follow the established `View ← Presenter ← Service ←
Domain ← Data` layering (mirrors the `Demo*` skeleton: `OrderView` ← `OrderPresenter`
← `OrderService` ← `OrderRepository`, with pure logic in `domain`). Business quantities
and cross-record invariants are **Domain methods** (ADR-0001); the **Service commit is
the authoritative gate** (ADR-0002).

**Block topology (from `migration-rules.yaml`, authoritative):**
- `S_ORD` → **`FormLayout`** — single-record queryable master (`block-master-single`).
  Forms Execute/Enter-Query + record scrollbar become a **lookup that loads one record
  + prev/next over the result set**, realized as **Variant A** (quick search with
  autocomplete + advanced multi-field filter; no grid-of-orders). Variants B (results
  grid → inline detail) and C (results page → routed detail) were prototyped and
  rejected (see `docs/orders-form-spec.md`); no ADR supersession is needed.
- `S_ITEM` → **editable `Grid`** (`recordsDisplayed=4`) — Grid Pro inline editing.
- `CONTROL` → **`LayoutHost`** — its buttons are hoisted into the surrounding layout.
- Canvas → container: Content → `VerticalLayout`, Toolbar → `HorizontalLayout`.

**Domain modules (pure, no Spring/Vaadin/DB):**
- `Order` (aggregate root) + `OrderLine`. The order graph is the unit of load and save.
- Totals (the headline ADR-0001 artifact). Encodes that item total uses
  **`quantity_shipped`**, not `quantity`:
  ```
  OrderLine.itemTotal() = quantity_shipped * price        // Forms ITEM_TOTAL (Formula)
  Order.total()         = sum(line.itemTotal())           // Forms TOTAL (Summary=Sum)
  ```
- `Order.shipDateValid()` — `date_shipped == null || date_shipped >= date_ordered`
  (`WHEN-VALIDATE-RECORD`).
- `PaymentEligibility` — pure predicate `allows(creditRating, paymentType)`: CREDIT is
  allowed only when `creditRating ∈ {GOOD, EXCELLENT}`, else the order is forced to
  CASH. The credit rating is **fetched by the Service**; the **rule** is Domain.
- `OrderSearchCriteria` — immutable value object capturing quick term + advanced fields
  (customer, rep, payment type, ordered-from, ordered-to, status) and translating them
  into a query specification. Pure and unit-testable.

**Data modules (`data`, against the Summit Oracle DB):**
- `OrderRepository` — `findByCriteria(OrderSearchCriteria)` (returns the result set the
  pager walks); `loadGraph(orderId)` (header + lines, **joining** `S_CUSTOMER`,
  `S_EMP`, `S_PRODUCT` so `customer_name`, `sales_rep_name`, line `description` arrive
  with the row — dissolving both POST-QUERY triggers); `save(Order)` (whole-graph
  insert/update/delete of lines); `nextItemId(orderId)` (`NVL(MAX(item_id),0)+1`);
  optimistic-lock support via `ORA_ROWSCN` (read the scn on load, verify-unchanged on
  save); `hasLineItems(orderId)` for the delete guard. New order ids come from
  `S_ORD_ID.nextval` (the `PRE-INSERT`).
- `ProductRepository` — `PRODUCTS_LOV` query (`select id, name from s_product order by
  name`) for the DataProvider; `findProductDefaults(productId)` → `(name,
  suggested_whlsl_price)` for the defaulter.
- `CustomerRepository` — `findCustomerHeader(customerId)` → `(name, sales_rep_id,
  sales_rep_name)`; `findCreditRating(customerId)`.

**Service modules:**
- `OrderService` — orchestration and the transaction boundary. `search`, `load`,
  **`save` as a single `@Transactional` whole-graph commit** (`transaction-commit-form`);
  optimistic-lock conflict surfaced as a typed result, not a silent overwrite; delete
  guard; DB-backed master validation (customer lookup, credit-rating fetch then delegate
  to `PaymentEligibility`, product defaulter). A plain HikariCP pool is sufficient — the
  form's audit trigger captures no actor, so no `CLIENT_IDENTIFIER` bridge.
- `ProductLookupService` — feeds the LOV DataProvider (lazy where appropriate).

**Presenter:** `OrderPresenter` — runs the search into a result set; loads the selected
order; owns prev/next position; wires Binder value-change listeners to recompute totals
via the Domain methods; mediates commit and maps Service outcomes (validation failure,
lock conflict) to view feedback.

**UI:** `OrderView` (`@Route`, inside the AppLayout `MainLayout`): `OrderSearchBar`
(quick field + autocomplete + collapsible advanced panel), header `FormLayout` bound via
`Binder`, line-items `Grid` (read-only in Step 1, Grid Pro editable from Step 3) with a
`TOTAL` footer, prev/next pager, and a Commit action. Payment type → `RadioButtonGroup`
(Cash/Credit); Order Filled → `Checkbox`; product column → ComboBox/LookupField. Invalid
input may appear briefly (no focus-trap, ADR-0002); the commit gate is authoritative.

**Validation & reactivity (ADR-0002):** field- and bean-level `Binder` validators that
delegate to the Domain checks; live total recompute via value-change listeners +
Presenter; **no Vaadin Signals**.

## Testing Decisions

A good test asserts **external behaviour through a module's public interface**, not its
internals — same spirit as `ServerInfoFormulaTest` (pure Domain, no Spring context, no
Vaadin, no DB). All four module groups below are in scope for tests:

- **Domain totals formula** (unit) — `OrderLine.itemTotal()` and `Order.total()`:
  item total uses `quantity_shipped` (not `quantity`); order total sums lines; empty
  order totals to zero; null/zero handling. The headline ADR-0001 artifact. Prior art:
  `ServerInfoFormulaTest`.
- **Domain invariants** (unit) — `Order.shipDateValid()` (null ship date allowed; equal
  dates allowed; earlier ship date rejected) and `PaymentEligibility.allows(...)`
  (CREDIT allowed for GOOD/EXCELLENT; refused otherwise; CASH always allowed).
- **`OrderSearchCriteria`** (unit) — which predicates the criteria produce for given
  inputs: quick term alone, advanced fields alone, combined (AND); empty criteria
  matches all; date-range bounds inclusive.
- **Repository/Service integration** (against the Summit DB, e.g. Testcontainers /
  the gvenzl image) — `loadGraph` returns joined display names in one shot; whole-graph
  `save` inserts/updates/deletes lines atomically; `nextItemId` is `MAX+1` per order;
  the delete guard blocks deleting an order with lines; an `ORA_ROWSCN` conflict makes a
  stale save fail rather than overwrite.

Tests are written incrementally with each step (the Domain tests land with Steps 1–2 and
5; the integration tests with the repository work in Steps 1 and 3–5).

## Out of Scope

- **`S_INVENTORY` auto-query sub-detail** — deferred to **its own follow-up PRD**
  (a distinct deferred/auto-query coordination pattern; keeps this PRD on the
  `S_ORD`/`S_ITEM` spine).
- **Date LOVs** (`date_ordered`/`date_shipped`) — trivially `DatePicker`; no record-group
  LOV to rebuild.
- **Product image** — filesystem TIFF, default-hidden, toggle button (`image-item-file-
  sourced`); needs TIFF→PNG/JPEG conversion. Deferred.
- **Coordination-control checkboxes** (`IMMEDIATE`, `AUTO_QUERY`) that change relation
  coordination at runtime.
- **Help canvas** (`CV_HELP`), **exit/menu wiring**, and the `m_summit` menu module.
- **Vaadin Signals** (ADR-0002) and any `CLIENT_IDENTIFIER`/proxy-auth bridge (the audit
  trigger reads no actor).
- The Forms **modal focus-trap** on validation (web cannot trap focus; commit gate
  guarantees integrity) — a documented honest loss, not a feature.

## Further Notes

- **Authoritative sources:** `docs/migration-rules.yaml` (classifiers — the YAML wins
  over PLAN.md's stale "master Grid" framing), `docs/orders-form-spec.md` (form findings
  + resolved scope + the search-UX verdict), `docs/generated/ORDERS.spec.json`
  (structural spec), ADR-0001 (logic in Domain) and ADR-0002 (Binder, no Signals).
- **Sequencing intent:** Step 1 is a *functional* tracer bullet (search loads real data
  through every layer), not a static scaffold; later steps layer behaviour onto a
  working view. 1a → 1b → 1c keeps each slice independently demoable.
- **Audit trigger** `AUDIT_S_ITEM` stays in the database and fires inside the commit
  transaction — zero Java, a clean boundary proof point.
- The Step 0 search-UX prototype (`docs/prototypes/orders-search-prototype.html`) should
  be folded into the real view at Step 1c, then its losing variants + switcher deleted —
  prototype code is not promoted as-is.

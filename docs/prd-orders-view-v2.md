# PRD: Functional Vaadin ORDERS view — v2 (layout-fidelity regeneration)

> **Why v2.** v1 shipped the `S_ORD`/`S_ITEM` spine with correct data and behaviour,
> but the header drifted from the Forms original: fields in an invented order, the
> bordered **"Order Information"** group and its title dropped, and `PAYMENT_TYPE`
> flattened from a radio group to a text field. The v1 PRD prose actually *called for*
> a `RadioButtonGroup` and listed the header fields — so the **intent was right; the
> build drifted and nothing caught it.** The census still read "183 elements, nothing
> dropped" because that guarantee is spec-side, not view-side.
>
> v2 regenerates this PRD against the improved method: **layout fidelity is now a
> first-class, checkable requirement with a test gate**, so the build cannot drift
> silently again. The spine, layering, and behaviour carry over from v1 unchanged;
> everything below in **bold-italic** is what v2 adds. Authoritative new sources:
> ADR-0012 (structural layout fidelity), ADR-0013 (read-only is a state, not a type),
> the analyzer's `frames` / `staticText.role` / `layoutPlan` signals, and the ruleset's
> `graphicsClassifiers` (`frame-group`, `boilerplate-heading/prompt/prose`).

## Problem Statement

Summit's `ORDERS.fmb` screen lets a clerk find an order, read and edit its header and
line items, see live totals, pick products from a list of values, and commit the whole
order as one unit. We must prove it migrates to a **functional** Vaadin Flow view a
Forms architect finds credible — same data, same business behaviour, modern web idioms,
a layered architecture whose business logic is pure and testable — ***and a screen the
architect recognises as the same screen: the same fields, in the form's order, grouped
the same way, titled the same way, with the same kind of control on each field.***

## Solution

A `@Route`-mapped **ORDERS view** on the `View ← Presenter ← Service ← Domain ← Data`
layering. The clerk searches (Variant A: quick search + advanced filter, no
grid-of-orders), the chosen match loads one order into a header + line-items `Grid` with
prev/next over the result set, edits lines inline with live totals, and commits the whole
graph in one transaction with optimistic (`ORA_ROWSCN`) concurrency. Logic is pure Domain
(ADR-0001); validation runs through Binder with the Service commit as the gate (ADR-0002).

***The header renders from the analyzer's layout signals, not an invented arrangement:***

- ***Field order follows the `S_ORD` `layoutPlan` (Y-then-X) — Order Id; then the
  Date Ordered / Customer Id / Customer Name row; the Sales Rep Id / Sales Rep Name row;
  the Date Shipped / Order Filled row; then Payment Type.***
- ***The Forms frame (`RECT59`) that boxes the header becomes a titled grouping section
  (`frame-group`), headed by the boilerplate text "Order Information"
  (`boilerplate-heading` → `H2`).***
- ***Each field is the component its Forms type maps to (`PAYMENT_TYPE` Radio Group →
  `RadioButtonGroup`); read-only slices set read-only state, they never collapse the
  type to a text field (ADR-0013).***

## Steps

Unchanged from v1 (Step 0 design → Step 5 commit/locking/master-edit), with two
amendments:

- **Step 1a (read-only header)** now delivers the header *as a faithful layout*: the
  Order Information section + heading, fields in `layoutPlan` order, `PAYMENT_TYPE` as a
  read-only `RadioButtonGroup`. ***This is the slice that regressed in v1; its v2
  acceptance criteria make the layout checkable.***
- ***Every slice's Definition of Done includes the layout-fidelity gate (below) for the
  constructs it adds.***

## User Stories

Carries v1's stories 1–38. v2 adds the layout-fidelity stories:

39. ***As a Forms user, I want the header fields in the same order as the Forms screen,
    so the view reads the way I already know.***
40. ***As a Forms user, I want the "Order Information" group and its title preserved, so
    I recognise the header as the same boxed section I used in Forms.***
41. ***As a Forms user, I want payment type shown as Cash/Credit options (a radio), not a
    text box reading "CASH", so the control tells me it is a small fixed choice.***
42. ***As a Forms architect, I want a test that fails when a migrated screen loses a
    field's order, a group, a title, or a control type, so layout regressions cannot
    ship silently the way they did in v1.***

## Implementation Decisions

All v1 decisions hold (architecture, block topology, Domain/Data/Service modules,
Binder/no-Signals). v2 adds the **layout-fidelity** decisions:

- **Field order & grouping come from the spec, never invented.** The generator places
  single-record block fields in `blocks[].layoutPlan` order (Y-then-X) and nests the
  fields a frame encloses under that frame's Section. `S_ORD` plan (from
  `docs/generated/ORDERS.spec.json`): `[ID] · [DATE_ORDERED, CUSTOMER_ID, CUSTOMER_NAME]
  · [SALES_REP_ID, SALES_REP_NAME] · [DATE_SHIPPED, ORDER_FILLED] · [PAYMENT_TYPE]`,
  with the first three rows in the `RECT59` "Order Information" section.
- **Boilerplate text is mapped, not dropped.** `staticText.role` drives it: `heading` →
  `H2`/`H3` (TEXT91 "Order Information" → `H2`), `prompt` → the bound field's label,
  `prose` → `Paragraph`. Pure decoration (`graphics-decoration`) is the only thing
  dropped — and that is logged, not silent.
- **Component type is the classifier's call, even read-only (ADR-0013).** Read-only
  slices render the mapped component (`RadioButtonGroup`, `DatePicker`, `Checkbox`, …)
  and set `setReadOnly(true)`; they do not substitute a `TextField`.
- **Aura still owns styling (ADR-0008).** Colours, fonts, bevels, pixel geometry remain
  dropped. v2 changes *structure*, not *appearance* — the screen is structurally the
  same, not pixel-identical.

## Testing Decisions

All v1 tests hold. v2 adds the **layout-fidelity gate** — the build-output completeness
check, counterpart to the spec's form-object grain (CONTEXT.md). For each migrated
construct, a UI test asserts it renders, **in the form's order, as its mapped component
type**:

- header field order equals the `S_ORD` `layoutPlan` order;
- the "Order Information" heading is present;
- `PAYMENT_TYPE` is a `RadioButtonGroup` (and is **not** a text field), read-only with
  value CASH/CREDIT;
- (later slices) each added field/frame/heading renders as its mapped type.

Prior art now in the tree: `OrderViewIT.testHeaderFieldOrderMatchesTheForm`,
`testOrderInformationHeadingIsPresent`, `testPaymentTypeRendersAsARadioGroupNotATextField`.
A construct is **not done** until its layout-fidelity assertion is green.

## Acceptance criteria (cross-cutting, applies to every slice)

- [ ] Fields appear in the order the analyzer's `layoutPlan` reports (no invented order)
- [ ] Forms frames render as titled grouping sections; their boilerplate titles render as headings
- [ ] Every field is the component type the classifier maps (read-only = state, not a `TextField`)
- [ ] A layout-fidelity UI test asserts order + grouping + heading + component type, and is green
- [ ] Anything genuinely dropped (pure decoration) is logged, not silently omitted

## Out of Scope

Unchanged from v1 (`S_INVENTORY` sub-detail, date LOVs, product image, coordination
checkboxes, help/menu, Signals, focus-trap). Visual *styling* parity remains explicitly
out of scope (ADR-0008) — v2 is about layout *structure*, not appearance.

## Further Notes

- **Authoritative sources:** v1's set plus ADR-0012, ADR-0013, and the analyzer's
  `frames` / `staticText.role` / `layoutPlan` (documented in the `forms-xml-analyzer`
  skill's REFERENCE.md).
- **The v1 lesson, recorded:** correct PRD intent does not survive an un-gated build. The
  fix was not a better PRD — it was making layout fidelity a *checkable criterion with a
  test*, so intent and implementation cannot drift apart unnoticed.

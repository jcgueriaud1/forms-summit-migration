# Prototype verdict — ORDERS search UX

**Question:** What is the faithful web rendering of Forms' single-record
Execute/Enter-Query on `S_ORD`? Three toggleable variants in
`orders-search-prototype.html`.

**Decision (2026-06-29): Variant A — lookup search → load one order.**

- Quick search (id / customer / rep) + autocomplete, **plus an advanced
  multi-field filter** panel (customer, rep, payment, ordered date-range, status).
- One match loads into header `FormLayout` + line-items `Grid`; **prev/next**
  walks the result set. No grid-of-orders.

**Why:** It's what the authoritative `block-master-single` rule already encodes
(`migration-rules.yaml`) and keeps the migration honest — single-record fidelity
with QBE → curated filter and the record scrollbar → a prev/next seam.

**Rejected:**
- **B (results grid → inline detail):** weird UX; resurrects PLAN's master Grid
  that `block-master-single` rejects; breaks single-record fidelity.
- **C (search page → routed detail page):** good, conventional web CRUD shape but
  **too far from the Forms original** for this migration — a fit for a *different*
  application, not this one.

No ADR superseding `block-master-single` is needed (neither rejected option won).
The decision is also recorded in `docs/orders-form-spec.md` (form-specific) and
the `block-master-single` aiHint was enriched with the quick+advanced search
affordance.

**Cleanup (done, #41):** Variant A is folded into the real Vaadin ORDERS view
(`src/main/java/com/example/summit/ui/OrderView.java`) — not promoted as-is. The
losing variants (B, C) and the variant switcher have been removed from
`orders-search-prototype.html`; only Variant A remains there, as the record of what
was chosen and why.

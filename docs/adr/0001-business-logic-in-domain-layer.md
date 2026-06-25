# Business logic lands in the Domain layer, not the Binder

`migration-rules.yaml` defaults derived/validation logic into the view's Binder
(Formula → "Binder-driven computed value"; `WHEN-VALIDATE-*` → `BinderValidator`),
matching how production Vaadin Flow apps are usually built. PLAN.md instead calls
for a layered `View ← Presenter ← Service ← Domain` architecture with formulas as
pure, testable Domain methods.

**Decision:** route by what the logic *is*. A **business quantity** (money, totals,
anything feeding a persisted column or the commit) or a **cross-record invariant**
→ a **Domain method** (authoritative, unit-testable), optionally mirrored to a
`BinderValidator`/converter for live UX. **Display-only sugar** stays a Binder
converter. Encoded in `migration-rules.yaml` via the `item-summary`,
`text-item-calculated`, `display-item-calculated`, `trigger-when-validate-item`,
and `trigger-when-validate-record` rules, discriminated by whether the result
feeds a Summary / persisted column or a pure display item.

**Why:** the order-total formula is the most visible architectural claim in a
credibility piece aimed at Forms architects; a pure, tested Domain method is the
artifact that sells it, and keeping invariants authoritative means the Service
commit can't be bypassed by view code.

For Summit this routes `ITEM_TOTAL`, `TOTAL`, and the `WHEN-VALIDATE-RECORD`
ship-date check to the Domain; no display-sugar formula exists in the form, so the
Binder branch is untouched here.

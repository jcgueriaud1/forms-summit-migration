# Validation and reactivity via Binder; no Vaadin Signals in this POC

Forms validation is modal and navigation-driven (`WHEN-VALIDATE-ITEM` blocks
leaving an invalid field; `WHEN-VALIDATE-RECORD` fires on leaving the record). The
web cannot trap focus, and Vaadin 25 offers two reactive styles: classic
`Binder` + value-change listeners, or the newer Signals.

**Decision:**
- **No focus-trapping.** Validate through **Vaadin `Binder`**: field-level
  validators (`Binder.forField(...).withValidator(...)`) and bean/cross-field
  validators (`Binder.withValidator(bean -> ...)`). For business invariants the
  Binder validator **delegates to the authoritative Domain check** (see ADR-0001);
  the **Service commit is the gate** that guarantees nothing invalid persists.
- **Live recompute** (e.g. order totals) is driven by **Binder value-change
  listeners + Presenter orchestration**, not Signals.
- **Vaadin Signals are out of scope for this POC.**

**Why:** the piece is a credibility artifact for Forms architects; the classic
`Binder`-and-listeners model is the established, widely-understood Vaadin Flow
idiom and keeps the migration finding legible. Signals would add an unfamiliar
reactive axis without changing what the POC demonstrates. The choice is reversible
in principle but shapes the reactive model app-wide, so it is recorded.

**Consequence / seam (scorecard behavioral-parity row):** invalid input is allowed
on screen (no focus block). Invalid fields stay flagged as invalid, each showing the
error message explaining why, until the user fixes them; the commit gate guarantees
nothing invalid commits. Honest difference vs Forms's modal navigation block.

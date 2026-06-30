# Read-only is a state, not a component type

A migration slice is often read-only before it is editable — the ORDERS tracer
bullet (#10) loaded an order into a **read-only header** before later slices made
it editable. Implementing that, the header rendered every field as a read-only
`TextField`, including `PAYMENT_TYPE` — a Forms **Radio Group** with options
`Cash`/`Credit`. The ruleset already maps Radio Group → `RadioButtonGroup`
(classifier `radio-group`), but "the header is read-only" was taken to mean "use a
`TextField`", and the mapped type was discarded.

This is a recurring trap: a `DatePicker`, `ComboBox`, `RadioButtonGroup`, or
`Checkbox` shown read-only all collapse to "just print the value in a `TextField`",
and the screen loses the information the control's *shape* carried — that payment
type is a small fixed choice, that a date is a date, that a field is a lookup.

**Decision:** **read-only is a runtime state, not a component-type decision.** A
read-only (or not-yet-editable) slice renders the component the classifier mapped
for the item — `RadioButtonGroup`, `DatePicker`, `ComboBox`, `Checkbox`, `TextField`
— and puts it in a read-only state via the component's own affordance
(`setReadOnly(true)`, or `setEnabled(false)` where read-only is unavailable). The
component type comes from `migration-rules.yaml` classifiers and from the item's
properties; it is the same whether the slice is read-only or editable. A slice's
scope may defer *editing behaviour* (validation, commit, defaulters); it must not
downgrade the *component type*.

This mirrors how `FormLayout.UpdateAllowed` is already handled in the ruleset:
`UpdateAllowed` carries via `forms-updateallowed-to-readonly` to `setReadOnly` — a
**state** on the bound field, never a change of which field it is.

**Why:** the classifier is the single source of truth for "what kind of control is
this". Letting a slice's read-only convenience override it puts the same decision in
two places and guarantees they drift — exactly how `PAYMENT_TYPE` lost its radio
shape. Keeping type and state orthogonal means the read-only slice and the later
editable slice render the *same* control, so making it editable is flipping a state,
not swapping a component. It also keeps the layout-fidelity gate (ADR-0012) honest:
that gate asserts mapped *type*, which only holds if read-only slices respect it.

**Consequence / seam:** read-only views carry slightly richer controls than a bare
text dump — a disabled `RadioButtonGroup` rather than a `TextField` reading `CASH`.
That is the intended fidelity. Where a control has no meaningful read-only rendering,
prefer `setReadOnly(true)`; fall back to `setEnabled(false)` only when the component
lacks read-only support, and note it.

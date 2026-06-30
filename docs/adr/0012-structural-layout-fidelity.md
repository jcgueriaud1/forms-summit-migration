# Structural layout fidelity is migrated; only visual styling is dropped

ADR-0008 made the Aura theme the single owner of **visual styling** and encoded
the Forms visual attributes as the ruleset's "Aura ignore list": typography,
colour, prompt placement, borders, fills, and `VisualAttribute*` groups are
**dropped, not translated**. That decision was right for *appearance*.

But the ignore list reached past appearance. Every view-side target also carries
`XPosition`, `YPosition`, `Width`, `Height` as `ignore: "Vaadin Flow layout owns
positioning/sizing"`, and the navigation flags (`NextNavigationItemName`,
`KeyboardNavigable`, …) as `ignore`. Pixel coordinates *should* be dropped — Vaadin
Flow owns absolute geometry. The mistake is that geometry also encodes **structure**:
the *relative order* of fields, their *grouping* into bounded regions (Forms frames),
and the *section titles* drawn as boilerplate text. Dropping geometry wholesale threw
the structure out with the pixels.

The ORDERS slice showed the cost. Against the Forms original, the migrated header:
- reordered the fields (the view hardcoded its own order rather than the form's);
- lost the bordered **"Order Information"** group (a Forms frame) — fields landed
  in one flat `FormLayout`;
- dropped the **"Order Information"** title (a static-text `Graphics` node);
- rendered `PAYMENT_TYPE` (a Radio Group) as a plain field (see ADR-0013).

None of these is a styling difference. They are **structural** facts a Forms
architect reads as "this is the same screen".

**Decision:** the migration preserves **structural layout fidelity** while ADR-0008
continues to drop **visual styling**. Concretely, four structural facts are
first-class migration constructs, not geometry to be ignored:

1. **Field order.** Fields are placed in the order the form presents them, derived
   from geometry (top-to-bottom, then left-to-right) — surfaced by the analyzer's
   `layoutPlan` (Y-proximity row grouping, ADR-0011). The generator places fields
   from `layoutPlan`/declaration order; it never invents an order.
2. **Grouping.** A Forms **frame** (a bordered `Graphics` region enclosing items)
   becomes a grouping container — a `FormLayout` section with a title, or a
   bordered region. The analyzer emits frames with bounds and the items they
   enclose; the ruleset maps frame → container (rule `frame-group`).
3. **Section titles / headings.** Standalone boilerplate **static text** (a
   `Graphics` text node not acting as a field prompt) becomes a heading or section
   title, not a dropped label (ruleset rule `boilerplate-text`).
4. **Component type.** The classifier's mapped component type is authoritative and
   is never collapsed for convenience (e.g. a Radio Group stays a
   `RadioButtonGroup`) — stated separately in ADR-0013.

What stays dropped (unchanged from ADR-0008): absolute pixel coordinates, widths
and heights, fonts, colours, bevels, fills, and `VisualAttribute*` groups. The
`XPosition`/`YPosition`/`Width`/`Height` ignores remain — geometry is consumed by
the analyzer to *derive structure*, then dropped as setters.

**Why:** the POC is a credibility artifact for Forms architects. ADR-0008 trades
per-field *visual* sprawl for a consistent theme — a defensible, legible loss. But
field order, grouping, section titles, and component type are not sprawl; they are
how the screen communicates. Losing them silently is not "modern web idiom", it is
an unfaithful migration, and — because the analyzer already captured every one of
these facts — an *avoidable* one. Drawing the line at "styling drops, structure is
migrated" keeps ADR-0008's win without paying for it in fidelity.

**Consequence / seam (scorecard — new "layout fidelity" row):** layout fidelity
becomes a tracked completeness dimension alongside behavioural parity (see
`CONTEXT.md`). A migrated view is not done until a **layout-fidelity UI test**
asserts that every in-scope item, frame, and boilerplate heading renders, in the
form's order, as its mapped component type. Screens still will not look
pixel-identical (ADR-0008 holds), but they will be **structurally** the same screen:
same fields, same order, same groups, same titles, same kinds of control.

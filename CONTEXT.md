# Oracle Forms → Vaadin Migration POC

The shared language for an AI-assisted method that migrates Oracle Forms to Vaadin.
This glossary is **form-agnostic**: it defines the migration vocabulary and
architectural concepts that apply to *any* form. Details of a specific form (e.g.
the Summit `ORDERS` form) live in that form's own specification file, not here.

## Language

### The method

**Form object**:
Any node in an FMB XML tree — block, item, trigger, relation, LOV, record group.
The analyzer's unit of inventory. _Avoid_: treating "trigger" as a synonym for
"thing to migrate" — a trigger is one kind of form object among several.

**Form-object grain**:
The leaf grain of the analyzer's structural spec — every form object accounted for.
The completeness guarantee attaches here ("every node in the FMB tree is accounted
for"), testable against the XSD. _Avoid_: "trigger grain", which only guarantees no
trigger slips, not that no behavior slips.

**Structural spec**:
The analyzer's output — a **descriptive, decision-free** record of form objects and
their properties (a Formula item's expression recorded verbatim, etc.). It has no
Vaadin knowledge and makes **no migration decisions**. Migration decisions live in
the mapping ruleset; demo choreography lives in the build phase. _Avoid_: treating
the spec as a place to decide Vaadin targets.

**Construct**:
A unit of migration meaning at the level the audience recognizes (master block,
detail block, filter bar, LOV, totals, locking model). Tickets and scorecard rows
are at construct grain; the spec's leaf grain is finer (form-object grain). A
construct can be **layout** as much as data: a bordered field group, a section
title, the order fields read in — these are constructs too (see _Layout fidelity_).

**Layout fidelity**:
The structural sameness of a migrated screen — the **same fields, in the form's
order, grouped the same way** (a Forms frame → a titled section), with the **same
section titles** and the **same kind of control per field** (a Radio Group stays a
radio, not a text field). Distinct from **visual styling** (colour, font, spacing),
which the Aura theme owns and ADR-0008 deliberately drops. Layout fidelity is what
the analyzer's `frames` / `staticText.role` / `layoutPlan` signals and the ruleset's
`frame-group` / `boilerplate-*` rules exist to preserve (ADR-0012); read-only slices
keep it by treating read-only as a state, not a type (ADR-0013). _Avoid_: treating
"we dropped geometry" as covering layout — geometry encodes structure (order,
grouping), not just pixels; only the pixels are Aura's to drop.

**Layout-fidelity gate**:
The build-output completeness check — the runtime counterpart to the spec's
form-object grain. A migrated view is not done until a _Browser UI test_ asserts that **every
in-scope item, frame, and heading renders, in the form's order, as its mapped
component type**. This catches the silent-loss class (a dropped title, a radio
flattened to a text field, reordered fields) that behaviour-only tests sail past.
_Avoid_: leaning on the spec census ("every node counted") as the fidelity
guarantee — *counted ≠ described ≠ rendered*; the census proved nothing was dropped
from the **spec**, not that nothing was dropped from the **view**.

**Disposition** / **Disposition ledger**:
A disposition is the single recorded fate of one spec fact: **migrated-as** (built,
with the web behaviour named), **re-decided** (deliberately different from Forms —
see _Re-decision_), **deferred** (accepted, tracked for later — an _out-of-slice
surface_), or **obsolete** (Forms-runtime plumbing with no web counterpart). The
disposition ledger holds one such row for every form object **and every
decision-prone mapped property** (a default, an editability mode, a visibility, a
cross-form contract) and gates ticket generation: no build tickets while any row is
unassigned. It closes the gap the census leaves — the census proves nothing was
dropped from the *spec*; the ledger proves nothing was dropped from the
*migration*. A `migrated-as` row is only *decided* until a _Browser UI test_ asserts the
named behaviour — the row's acceptance criterion — which makes it *delivered*;
and its tracking refs must stay live (a deferred row citing a closed ticket is
rot, not tracking). _Avoid_: rows for mechanical carries (a max-length, a
prompt) — the layout-fidelity gate verifies those at runtime, and rows for them
drown the decision-prone ones.

**Re-decision**:
A deliberate, recorded deviation from what Forms did — "the spec says X; we chose Y
because Z, decided by W". It lives on the deviated fact's ledger row, so a
deviation is always distinguishable from an accident; a generalizable re-decision
is additionally encoded into the mapping ruleset as the new behaviour. _Avoid_:
recording re-decisions only in prose (PRD, commit message) — prose predates or
trails the build and cannot be checked for completeness.

**Cross-form contract**:
Shared state a form reads or writes to communicate with the form that calls it (or
forms it calls) — the calling context. Invisible at element grain (it lives only
inside trigger bodies), so it is extracted and scoped like any other dependency.
_Avoid_: treating a global as an implementation detail of the trigger that touches
it — it is an interface of the form.

**Mapping ruleset**:
The published decision tree that maps Forms constructs to Vaadin targets. The
substrate of the generalizable-vs-specific sort; maps 1:1 to the scorecard.

**Generalizable fix** / **Specific fix**:
A correction to generated code that either encodes a rule the tool should have
known (push into the ruleset — works for every form) or an irreducible quirk of one
form (hand-fix, log, move on). A fix is **specific by default** unless the rule
behind it can be stated in form-construct terms.

**Browser UI test**:
A migrated construct is not done until it ships with a test that drives the running
app in a real browser (Playwright + DramaFinder) and asserts user-visible behavior —
the same construct the user recognized in Forms, now exercised end-to-end through
View ← Presenter ← Service ← Domain ← Data against a real Oracle (Testcontainers).
This is the build phase's completeness check, the runtime counterpart to the
analyzer's form-object grain. It has **two dimensions**: *behavioural parity* (the
construct does what Forms did) and *layout fidelity* (it renders as the same screen —
the _Layout-fidelity gate_). _Avoid_: treating unit tests of the domain formula as
sufficient — they prove the calculation, not that the construct works in the view;
and behaviour-only assertions, which pass even when the screen looks wrong; and the
bare "UI test", which no longer says which of the three kinds is meant.

**Browserless UI test**:
A test that drives Vaadin views server-side, with no browser (Vaadin Browserless):
fast feedback on a view's behaviour. It does **not** count toward the completeness
check — it renders nothing, so it cannot assert _Layout fidelity_; only a _Browser UI
test_ can. _Avoid_: calling it a browser test of any kind.

**Oracle Forms UI test**:
A test that drives the **running Oracle Forms client** — its items, records and
status bar — and, when the form is a _Host form_, its _Embedded view_ too, asserting
what crosses between them and what was committed. Defined by where it runs, not by
what it migrates: it can also pin down what a not-yet-migrated Forms screen does,
as the baseline a migrated view's _behavioural parity_ is judged against. _Avoid_:
"end-to-end test" — say which client it drives; and treating it as the completeness
check, which stays with the _Browser UI test_.

**Build ticket** / **Review ticket (HITL ticket)**:
A migration emits two kinds of ticket. A **build ticket** is `ready-for-agent`,
construct-grain, 1:1 with a scorecard row — what an AFK agent can build. A **review
ticket** is `ready-for-human` (HITL — human-in-the-loop): work that turns on a
judgment the AI is not entitled to make. The `migration-review` skill emits three
review-ticket subtypes (ADR-0014): a **careful-review / manual-test** ticket (what a
human must certify by driving the construct side-by-side against live Forms — the
_Browser UI test_'s two dimensions, plus ruleset `Refactor`/`Unsupported`/`escalate`
outcomes); a **deferred follow-up** ticket (see _Out-of-slice surface_); and a
**generalization-review** ticket (see _Generalization candidate_). _Avoid_:
smuggling HITL work into a `ready-for-agent` ticket — that label promises an agent
can finish it.

**Out-of-slice surface** vs **Out-of-scope rejection**:
An **out-of-slice surface** is a form dependency (a called form, menu module,
attached library, object-library subclass) accepted for the eventual migration but
**deferred out of this slice** — it becomes tracked forward work (a follow-up issue
or PRD), never a silent drop. An **out-of-scope rejection** is a feature decided
*not to build at all*, recorded in the `.out-of-scope/` KB for dedup. Two distinct
lanes; a human makes the defer-vs-reject call. _Avoid_: recording a deferred surface
in `.out-of-scope/` — it poisons rejection dedup with a false rejection.

**Generalization candidate** / **Generic layer**:
A construct the ruleset derives *into a form's view* that is really a generic Oracle
Forms affordance — record actions from a block's CRUD capability, the Execute-Query
filter bar, the optimistic-lock commit path — and so belongs in a **generic layer**
(a reusable component/service), not the specific form view. A candidate earns
generalization only when the rule can be **stated in form-construct terms**
(_Generalizable fix_'s bar); otherwise it is **specific by default** and stays in the
view. This is the generalizable-vs-specific sort applied *proactively at design
time*, the counterpart to the reactive fix-time sort in the build loop.

### Hybrid coexistence

**Host form**:
A running Oracle Forms module that displays an _Embedded view_ inside one of its
own canvases; in a hybrid screen, Forms is always the host. _Avoid_: "container
app", "shell" — the host is a form, not a separate desktop application.

**Embedded view**:
A Vaadin view rendered inside a _Host form_ and exchanging calls and events with
it. _Avoid_: "migrated view" unless it actually replaces a Forms construct — a
view can be embedded without being a migration of anything.

### Architectural layers

**Domain**:
Owns the *formula* — pure, unit-testable computation. Where declarative Forms
calculations and validation logic land.

**Presenter**:
Owns *orchestration* — listens for UI events, mutates the in-memory bean, calls
domain methods, pushes results to the view. Calls formulas; does not contain them.

**Service**:
Owns *load and commit* — fetches the object graph, flushes transactionally.

**Data**:
The persistence boundary (Spring `JdbcClient`).

## Relationships

- A **Construct** clusters one or more **Form objects** from the spec.
- The **Mapping ruleset** maps each **Construct** to a Vaadin target and to one
  scorecard row.
- Each correction is sorted into a **Generalizable fix** or a **Specific fix**.
- A **Construct** yields a **Build ticket** and may also yield **Review tickets** —
  a careful-review, a deferred follow-up (from an **Out-of-slice surface**), or a
  **Generalization candidate** (a design-time **Generalizable fix**).

## Flagged ambiguities

- "hybrid application" could mean Vaadin hosting Forms, Forms hosting Vaadin, or a
  third desktop shell hosting both — resolved: Forms is the _Host form_ and Vaadin
  supplies _Embedded views_. A browser cannot run the Forms client, so Vaadin
  cannot host Forms.

- "trigger grain" vs "form-object grain" — resolved: inventory is at form-object
  grain; trigger grain would miss declarative behavior (formula/summary items,
  relations, LOVs).
- "spec completeness" vs "view completeness" — resolved: the spec census guarantees
  every node is *accounted for* (form-object grain), but counted ≠ rendered. View
  completeness is a separate guarantee enforced at build-output by the
  _Layout-fidelity gate_ (ADR-0012). The ORDERS header lost its title, its field
  order, its grouping frame, and its radio shape while the census still read
  "183 elements, nothing dropped" — the lesson that motivated the second gate.

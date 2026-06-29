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
are at construct grain; the spec's leaf grain is finer (form-object grain).

**Mapping ruleset**:
The published decision tree that maps Forms constructs to Vaadin targets. The
substrate of the generalizable-vs-specific sort; maps 1:1 to the scorecard.

**Generalizable fix** / **Specific fix**:
A correction to generated code that either encodes a rule the tool should have
known (push into the ruleset — works for every form) or an irreducible quirk of one
form (hand-fix, log, move on). A fix is **specific by default** unless the rule
behind it can be stated in form-construct terms.

**UI test**:
A migrated construct is not done until it ships with a test that drives the running
app in a real browser (Playwright + DramaFinder) and asserts user-visible behavior —
the same construct the user recognized in Forms, now exercised end-to-end through
View ← Presenter ← Service ← Domain ← Data against a real Oracle (Testcontainers).
This is the build phase's completeness check, the runtime counterpart to the
analyzer's form-object grain. _Avoid_: treating unit tests of the domain formula as
sufficient — they prove the calculation, not that the construct works in the view.

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

## Flagged ambiguities

- "trigger grain" vs "form-object grain" — resolved: inventory is at form-object
  grain; trigger grain would miss declarative behavior (formula/summary items,
  relations, LOVs).

---
name: forms-xml-analyzer
description: Convert an Oracle Forms FMB XML file (frmf2xml output) into a faithful, decision-free structural specification at form-object grain — every block, item, trigger, relation, LOV, record group, alert, program unit, data-source column, and canvas, plus item builder-attributes (maximumLength, required, initializeValue, formatMask, checked/unchecked values, radio options, copyValueFromItem, lovName) and the analyzer-injected Forms defaults and derived signals (bodyShape, relationRole, lockingMode, commitStyle, imageSource, LOV column/return mapping). It also inventories external dependencies (menu module, attached libraries, called sibling forms, object-library subclassing) and cross-form contracts (:GLOBAL/:PARAMETER shared state with per-trigger read/write usage) the migration must scope — never dropping them silently — and ships the disposition-ledger companion script that gates ticket generation (ADR-0015). Use when migrating an Oracle Form, when asked to analyze/spec/inventory an .fmb or *_fmb.xml, or to produce the input the Forms→Vaadin mapping ruleset (docs/migration-rules.yaml) consumes. It makes NO Vaadin/migration decisions.
---

# forms-xml-analyzer

Skill #1 of the migration toolchain (see `PLAN.md`). It is **mechanical and
decision-free**: it describes what the form *is*, never what it should *become*.
Migration decisions belong to the mapping ruleset (`docs/migration-rules.yaml`);
demo choreography belongs to the build phase.

## Quick start

```bash
python3 scripts/analyze_form.py <form_fmb.xml> --out spec.json --md spec.md
```

- `<form_fmb.xml>` — the `frmf2xml` export (the parameter).
- `--out spec.json` — machine-readable spec (feeds the ruleset/planner). Omit to print to stdout.
- `--md spec.md` — optional human-readable digest.

Stdlib only (Python 3.9+). No external dependencies.

## What it guarantees

- **Form-object grain.** Every node is accounted for; the output includes a
  `census` (tag → count, plus `totalElements`) so nothing slips silently. This is
  the completeness guarantee — not "trigger grain".
- **Dependency-complete.** Every external migration unit the form references
  (menu module, attached libraries, called sibling forms, object-library
  subclassing) is inventoried into `dependencies[]`, each `status: "undecided"`.
  Nothing out-of-slice is silently dropped — see the gate below.
- **Contract-complete.** The form's cross-form contracts — `:GLOBAL.*` /
  `:PARAMETER.*` shared state read or written anywhere in its trigger and
  program-unit bodies — are extracted into `dependencies[]` too (types
  `global-variable` / `parameter`), each with a `usage[]` of the owning
  trigger and access direction. These names are invisible to element-grain
  inventory (they live only inside PL/SQL bodies) — the calling-context
  contract class that is otherwise silently lost.
- **Decision-free.** A Formula item's expression, a LOV's query, a relation's
  `DeleteRecord` are recorded verbatim. No Vaadin target, no layer assignment.
- **Forms defaults injected, flagged.** Attributes often absent in real exports
  (`LockingMode`, the record-display count, `commitStyle`) are resolved to the Forms
  default and marked (`injected: true`, `source: derived(...)`), because the
  ruleset's predicates need them. This is the most generalizable correction the
  analyzer makes.

## Derived signals the ruleset consumes

| Signal | On | Meaning |
|---|---|---|
| `bodyShape` | trigger | `select-into` / `raise-only` / `mixed` / `other` — splits defaulting from validation |
| `relationRole` | block | `master` / `detail` / `control` — from `<Relation>` topology, not `RecordsDisplayed` |
| `databaseBlock`, `queryable` | block | base-table + query flow signals |
| `lockingMode` | block | resolved value + `injected` flag (default `Automatic` = pessimistic) |
| `derivedMultiRecord` | block | value + `source` (`RecordsDisplayCount`, else relation membership) |
| `commitStyle` | form | `COMMIT_FORM` when present |
| `crud` | block | insert/update/delete/query allowed (+ `injected`) — editable vs read-only Grid, add/delete affordances |
| `editability` | item | update/insert/query/enabled — which Grid columns are editable / read-only |
| `imageSource` | Image item | `file` (filesystem via `READ_IMAGE_FILE`) vs `blob` |
| `lovRole` | item | `primary` when the item is an LOV `ReturnItem` |
| LOV `{query, columns[...], primaryReturnItem}` | lov | picker contract: which columns to retrieve/display/return |
| `referencedDbObjects` | form | candidate DB program units — **verify signatures against the schema** |

**Layout** is captured too (facts, not decisions): `windows`, `canvases`
(`canvasType` + owning window + viewport), `tabPages`, and per-item `geometry` +
`canvas` + `tabPage`. Geometry is dropped for *setters* (Aura/Flow own pixels,
ADR 0008) but is **consumed to recover structure** (ADR 0012) — the analyzer
emits three derived layout-fidelity signals so the build never invents field
order or silently drops a group/title:

- **`frames[]`** — a Rectangle/Frame Graphics whose bounds enclose ≥1 item, with
  `{name, canvas, graphicsType, frameTitle, geometry, enclosesItems,
  enclosedItems[]}`. A bounded rectangle that groups fields is normalised to
  `graphicsType: "Frame"` (→ ruleset `frame-group` → grouping Section); one that
  encloses nothing stays `"Rectangle"` (→ `graphics-decoration` → Drop).
- **`staticText[].role`** — `heading` / `prompt` / `prose`, derived from font
  size (heading ≥ 1.5× the form's body baseline) and adjacency (prompt = a label
  next to the item named in `labelsItem`), plus `headingLevel` (`h2`/`h3`). Feeds
  `boilerplate-heading` / `boilerplate-prompt` / `boilerplate-prose`.
- **`blocks[].layoutPlan`** — rows of field names clustered by Y-proximity, ordered
  top→bottom then left→right, each tagged with the enclosing frame (`section`).
  This is the field ORDER + grouping the generator places from — the form's order,
  never declaration-accident order. The ruleset consumes it for single-record
  (FormLayout) blocks; Grid blocks ignore it.

**Builder-grade descriptive facts** (verbatim, no decisions) the generator needs
to recreate a form faithfully:

| Fact | On | Source |
|---|---|---|
| `maximumLength`, `required`, `initializeValue`, `formatMask` | item | item attrs — feed ruleset setter tables (`setMaxLength`, `setRequiredIndicatorVisible`, `setValue`, `setI18n`) |
| `checkedValue`, `uncheckedValue`, `label` | item | Check Box value pair + caption |
| `radioButtons[{name, radioButtonValue, label}]` | item | Radio Group option set |
| `copyValueFromItem` | item | master→detail FK propagation (`CopyValueFromItem`) |
| `lovName` | item | record-group LOV bound to the item (vs. a `KEY-LISTVAL` calendar LOV) |
| `dataSourceColumns[{name, dscType, precision, scale, length, mandatory}]` | block | base-table column types — for typing domain-bean fields |
| `alerts[{name, alertStyle, alertMessage, buttonNLabel}]` | form | confirm/question dialogs referenced by `SHOW_ALERT` |
| `programUnits[{name, kind, programUnitType, body}]` | form | the form's own PL/SQL (Forms coordination boilerplate); distinct from `referencedDbObjects` (external/DB calls) |
| `staticText[{name, text, role, headingLevel, labelsItem, canvas, fontSize}]` | form | boilerplate text + derived role (heading/prompt/prose) — feeds `boilerplate-*` rules |
| `frames[{name, canvas, graphicsType, frameTitle, enclosesItems, enclosedItems}]` | form | bounded grouping regions — feeds `frame-group` |
| `blocks[].layoutPlan[{fields[], section}]` | block | Y-then-X field order + frame grouping — the order the generator places from |
| `attachedLibraries`, `visualAttributes`, `objectGroups`, `form.menuModule` | form | recorded descriptively (reused/deferred objects) — no migration decision |

## Dependencies (out-of-slice surfaces) — ask, never silently drop

A form rarely stands alone. It references other migration units: a **menu module**
(`MenuModule`), **attached PL/SQL libraries** (`AttachedLibrary` — e.g.
`calendar.pll`), **sibling forms** invoked at runtime via
`CALL_FORM`/`OPEN_FORM`/`NEW_FORM`/`RUN_PRODUCT` (e.g. a picker list form), and
objects **subclassed from an object library** (`.olb`). The analyzer inventories
every one into `dependencies[]` — `{ type, name, source, via, status }` — each
flagged `status: "undecided"`. The script makes **no** scoping decision.

**Workflow gate (mandatory).** Before treating any dependency as out of scope,
**stop and ask the user**, per dependency, which to do:

- **spec it** — run the analyzer on that module too and emit its spec, recorded
  as a dependency of this form (set the entry's `status: "spec"`); or
- **ignore it** — leave it out as an explicit, acknowledged exclusion
  (set `status: "ignore"`).

Never defer a dependency silently, and never pick for the user. Record each
answer back onto the entry's `status` so the choice is auditable in the spec.
A `called-form` is detected from trigger/program-unit bodies with PL/SQL line
comments stripped, so a commented-out call is not reported as a live dependency.

## The disposition ledger (companion script)

`scripts/disposition_ledger.py` turns a spec into the **disposition ledger**
(ADR-0015) — one row per form object plus per decision-prone mapped property
(defaults, editability modes, visibility, value propagation, cross-form
contracts). Every row must end in exactly one disposition (`migrated-as` /
`re-decided` / `deferred` / `obsolete`); `--check` is the gate
`migration-to-issues` runs before cutting build tickets, and `migration-review`
re-runs when reviewing.
`--merge` carries prior decisions forward across spec regenerations, so the
ledger is safe to refresh. `--check-refs` (needs the `gh` CLI) guards ledger
rot: cited `#N` refs must exist and a `deferred` row must cite a still-open
ticket. Mechanical carries (maximumLength, prompts, field order) get no rows —
the layout-fidelity gate (ADR-0012) verifies those at runtime. A `migrated-as`
row is *delivered* only when a UI test asserts it — build tickets turn covered
rows into acceptance criteria (see `migration-to-issues`).

```bash
python3 scripts/disposition_ledger.py spec.json \
    --merge ledger.json --out ledger.json --md ledger.md --check --check-refs
```

## Notes

- Run it before any mapping/codegen step: the spec is the contract those steps read.
- The output schema and signal heuristics are documented in [REFERENCE.md](REFERENCE.md).
- If the analyzer errors on a form, that itself is a finding — capture the XML shape;
  do not hand-edit the spec to paper over it.

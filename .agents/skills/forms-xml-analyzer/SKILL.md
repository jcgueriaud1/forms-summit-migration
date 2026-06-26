---
name: forms-xml-analyzer
description: Convert an Oracle Forms FMB XML file (frmf2xml output) into a faithful, decision-free structural specification at form-object grain — every block, item, trigger, relation, LOV, record group, alert, program unit, data-source column, and canvas, plus item builder-attributes (maximumLength, required, initializeValue, formatMask, checked/unchecked values, radio options, copyValueFromItem, lovName) and the analyzer-injected Forms defaults and derived signals (bodyShape, relationRole, lockingMode, commitStyle, imageSource, LOV column/return mapping). Use when migrating an Oracle Form, when asked to analyze/spec/inventory an .fmb or *_fmb.xml, or to produce the input the Forms→Vaadin mapping ruleset (docs/migration-rules.yaml) consumes. It makes NO Vaadin/migration decisions.
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
- **Decision-free.** A Formula item's expression, a LOV's query, a relation's
  `DeleteRecord` are recorded verbatim. No Vaadin target, no layer assignment.
- **Forms defaults injected, flagged.** Attributes absent in real exports
  (`LockingMode`, `RecordsDisplayed`, `commitStyle`) are resolved to the Forms
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
| `derivedMultiRecord` | block | value + `source` (relation membership / scrollbar / `RecordsDisplayed`) |
| `commitStyle` | form | `COMMIT_FORM` when present |
| `crud` | block | insert/update/delete/query allowed (+ `injected`) — editable vs read-only Grid, add/delete affordances |
| `editability` | item | update/insert/query/enabled — which Grid columns are editable / read-only |
| `imageSource` | Image item | `file` (filesystem via `READ_IMAGE_FILE`) vs `blob` |
| `lovRole` | item | `primary` when the item is an LOV `ReturnItem` |
| LOV `{query, columns[...], primaryReturnItem}` | lov | picker contract: which columns to retrieve/display/return |
| `referencedDbObjects` | form | candidate DB program units — **verify signatures against the schema** |

**Layout** is captured too (facts, not decisions): `windows`, `canvases`
(`canvasType` + owning window + viewport), `tabPages`, and per-item `geometry` +
`canvas` + `tabPage`. These feed `canvasClassifiers`, the window→view/dialog
mapping, and `layoutPlan` (Y-proximity row grouping). The ruleset drops geometry
for setters but uses it for row layout.

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
| `staticText`, `attachedLibraries`, `visualAttributes`, `objectGroups`, `form.menuModule` | form | recorded descriptively (labels/headers, reused/deferred objects) — no migration decision |

## Notes

- Run it before any mapping/codegen step: the spec is the contract those steps read.
- The output schema and signal heuristics are documented in [REFERENCE.md](REFERENCE.md).
- If the analyzer errors on a form, that itself is a finding — capture the XML shape;
  do not hand-edit the spec to paper over it.

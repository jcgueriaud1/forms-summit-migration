# forms-xml-analyzer — output schema & signal heuristics

The analyzer emits one JSON object. It is **descriptive only**: facts + injected
Forms defaults + derived signals. No Vaadin/migration decisions.

## Top-level shape

```jsonc
{
  "source": "ORDERS_fmb.xml",
  "grain": "form-object",
  "decisionFree": true,
  "form": { "name": "ORDERS", "commitStyle": "COMMIT_FORM" | null,
            "menuModule": "m_summit" | null, "title": "..." },
  "blocks":   [ Block, ... ],
  "relations":[ Relation, ... ],
  "alerts":   [ Alert, ... ],
  "lovs":     [ Lov, ... ],
  "recordGroups": [ RecordGroup, ... ],
  "programUnits": [ ProgramUnit, ... ],
  "attachedLibraries": [ { "name", "libraryLocation", "librarySource" }, ... ],
  "visualAttributes": [ { "name", "backColor", "foregroundColor", "fontName", "fontSize" }, ... ],
  "objectGroups": [ { "name", "parentName", "parentModule", "parentFilename",
                      "children": [ { "name", "type" }, ... ] }, ... ],
  "dependencies": [ Dependency, ... ],
  "windows":  [ { "name", "title", "width", "height" }, ... ],
  "canvases": [ { "name", "canvasType", "window", "width", "height",
                  "viewport": { "x", "y", "width", "height" } }, ... ],
  "tabPages": [ { "name", "label", "canvas" }, ... ],
  "staticText": [ StaticText, ... ],
  "formTriggers": [ Trigger, ... ],
  "referencedDbObjects": [ { "name", "paramShape": "unknown (resolve from schema)" } ],
  "census": { "totalElements": N, "byTag": { "Item": 34, ... } }
}
```

**Key convention.** The spec uses curated lowercase camelCase keys (`itemType`,
`maximumLength`, `formatMask`, `checkedValue`, …). The PascalCase names in
`docs/migration-rules.yaml` (`MaximumLength`, `FormatMask`, `CheckedValue`,
`LovRole`) are the ruleset's *predicate / attribute-table* namespace; a renderer
layer bridges the two (the comment at `migration-rules.yaml` line ~19 documents
`LovRole` as one such synthetic, injected key). New fields follow the spec's
camelCase convention; `check_against_ruleset.py` reads them by their camelCase
keys.

### Block
```jsonc
{
  "name", "databaseBlock": bool, "queryDataSource",
  "relationRole": "master" | "detail" | "control",
  "queryable": bool,
  "crud": { "insertAllowed", "updateAllowed", "deleteAllowed", "queryAllowed": bool,
            "injected": bool },   // absent in export => Forms default true; injected:true flags that
  "recordsDisplayed": "n" | null,
  "derivedMultiRecord": { "value": bool|null, "source": "RecordsDisplayCount" | "derived(relation-membership)" },
  "lockingMode": { "value": "Automatic"|..., "injected": bool },
  "dataSourceColumns": [ { "name", "dscType", "precision", "scale", "length", "mandatory" }, ... ],
  "itemCount", "items": [ Item, ... ], "triggers": [ Trigger, ... ]
}
```

`dataSourceColumns` are the base-table column types verbatim from
`<DataSourceColumn>` (`DSCType`/`DSCPrecision`/`DSCScale`/`DSCMandatory`) — the
source for typing domain-bean fields (e.g. `NUMBER(7,0)` → `Long`,
`NUMBER(11,2)` → `BigDecimal`, `DATE` → `LocalDate`). The mapping is the
ruleset's call, not the analyzer's.

### Item
```jsonc
{
  "name", "itemType", "canvas", "tabPage",
  "geometry": { "x", "y", "width", "height" },   // facts for layoutPlan; ruleset drops for setters
  "visible", "dataType",
  "formula", "calculateMode", "summaryFunction", "summaryItemName",
  "listStyle", "concealData", "multiLine", "prompt", "label", "columnName", "databaseItem",
  // builder-grade attributes, verbatim (null when absent in the XML):
  "maximumLength", "required", "initializeValue", "formatMask",
  "checkedValue", "uncheckedValue",   // Check Box
  "copyValueFromItem",                // master→detail FK propagation
  "lovName",                          // record-group LOV bound to the item
  "editability": { "updateAllowed", "insertAllowed", "queryAllowed", "enabled": bool },  // absent => true
  "lovRole": "primary" | null,
  "radioButtons": [ { "name", "radioButtonValue", "label" }, ... ],  // Radio Group only
  "imageSource": "file" | "blob",   // Image items only
  "triggers": [ Trigger, ... ]
}
```

`lovName` records the item's own `LovName` (a record-group LOV); a date item
instead invokes a calendar LOV via a `KEY-LISTVAL` trigger (`date_lov.get_date`),
so absence of `lovName` with a `KEY-LISTVAL` trigger distinguishes the two
picker styles. `lovRole` (synthetic, from the LOV's `ReturnItem` walk) is
independent of `lovName`.

### Trigger / Relation / Lov / RecordGroup / Alert / ProgramUnit / StaticText
```jsonc
Trigger     { "name", "bodyShape", "body" }
Relation    { "name", "masterBlock", "detailBlock", "joinCondition", "deleteRecord", "deferred", "autoQuery" }
Lov         { "name", "title", "recordGroup", "query",
              "columns": [ { "name", "dataType", "title", "displayWidth", "returnItem" } ],
              "primaryReturnItem" }
RecordGroup { "name", "query", "columns": [ { "name", "dataType", "maximumLength" } ] }
Alert       { "name", "title", "alertStyle", "alertMessage",
              "button1Label", "button2Label", "button3Label" }   // verbatim; referenced by SHOW_ALERT in triggers
ProgramUnit { "name", "kind": "program-unit", "programUnitType", "body" }  // the form's own PL/SQL (≠ referencedDbObjects, which are external/DB calls)
StaticText  { "name", "text", "canvas", "geometry": { "x", "y", "width", "height" } }  // <Graphics GraphicsText="…">; non-text Graphics (Rectangle/Line) are omitted
Dependency  { "type", "name", "source", "via", "status": "undecided" }
```

### Dependency

An external migration unit this form references. Inventoried mechanically; the
analyzer makes **no** scoping decision (`status` is always `"undecided"` on
emit). The skill workflow requires the agent to ask the user, per dependency,
whether to **spec** it (emit its own spec, mark it a dependency → `status:"spec"`)
or **ignore** it (acknowledged exclusion → `status:"ignore"`) — never a silent
drop. See SKILL.md "Dependencies — ask, never silently drop".

| `type` | `name` | `source` | detected from |
|---|---|---|---|
| `menu-module` | menu module name | — | `FormModule.MenuModule` |
| `attached-library` | library name | `LibraryLocation` | `<AttachedLibrary>` |
| `called-form` | form/module invoked at runtime | — | `CALL_FORM`/`OPEN_FORM`/`NEW_FORM`/`RUN_PRODUCT` in a trigger/program-unit body (PL/SQL `--` comments stripped first, so commented-out calls are not reported) |
| `object-library` | parent object name | `.olb` filename | `<ObjectGroup>` with a `ParentFilename` (subclassed) |

## Signal heuristics

- **bodyShape** — `select-into`: body has `SELECT … INTO` (a fetch-and-default; an
  accompanying `NO_DATA_FOUND → RAISE` is part of that shape). `mixed`: a
  `SELECT … INTO` *plus* independent DML or a second `SELECT`. `raise-only`: a
  `RAISE`/guard with no `SELECT … INTO`. `other`: anything else. `mixed` is
  best-effort — a reviewer confirms.
- **relationRole** — `detail` if the block is any relation's `DetailBlock`; else
  `master` if it is a database block; else `control`. Topology, not `RecordsDisplayed`.
- **derivedMultiRecord** — uses the block's record-display count when present
  (`RecordsDisplayCount`, falling back to `RecordsDisplayed`); otherwise the count
  is absent, meaning Forms shows a single record, and multi is derived purely from
  relation membership (a `detail` block renders as a grid). A block scrollbar is
  not used — it navigates records rather than displaying many at once.
- **lockingMode** — read from the block; if absent, injected as `Automatic`
  (Forms default = pessimistic across think-time) with `injected: true`.
- **commitStyle** — `COMMIT_FORM` if any trigger body calls it.
- **imageSource** — `file` if a `READ_IMAGE_FILE(... , '<BLOCK.ITEM>')` populates the
  item (filesystem source, function-derived filename); else `blob`.
- **referencedDbObjects** — identifiers called in bodies after filtering PL/SQL
  keywords, scalar types, SQL functions, and Forms built-ins. The remainder are
  *candidates*; resolve true signatures (and `paramShape`: in-only / has-out /
  ref-cursor / plsql-only-type) against the database schema — the FMB alone cannot
  distinguish a DB stored unit from a PL/SQL-library call.

## Layout

The spec carries the full spatial structure as facts, for `canvasClassifiers`
(`CanvasType` → container: Content→VerticalLayout, Tab→TabSheet, Stacked→Details,
Toolbar→HorizontalLayout), the window→view/dialog mapping, and `layoutPlan`
(cluster items by Y-proximity into rows):

- **windows** — a form may span several (e.g. a master window + a popup detail).
- **canvases** — `canvasType` + owning `window` + viewport.
- **tabPages** — for Tab canvases (children of the canvas).
- **item `geometry` + `canvas` + `tabPage`** — where each field sits. The ruleset
  *drops* geometry for component setters (Aura owns positioning) but `layoutPlan`
  *uses* it for row grouping — a clean spec-records / ruleset-decides split.

## Boundaries

- Reads only the FMB XML. It does **not** read the schema, so DB program-unit
  signatures and true table DDL are out of scope (surfaced as candidates).
- Namespaces are stripped defensively; `TriggerText` attribute or element text is
  read for bodies (entities already decoded by the XML parser).

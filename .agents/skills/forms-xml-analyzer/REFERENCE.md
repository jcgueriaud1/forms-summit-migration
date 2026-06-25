# forms-xml-analyzer — output schema & signal heuristics

The analyzer emits one JSON object. It is **descriptive only**: facts + injected
Forms defaults + derived signals. No Vaadin/migration decisions.

## Top-level shape

```jsonc
{
  "source": "ORDERS_fmb.xml",
  "grain": "form-object",
  "decisionFree": true,
  "form": { "name": "ORDERS", "commitStyle": "COMMIT_FORM" | null },
  "blocks":   [ Block, ... ],
  "relations":[ Relation, ... ],
  "lovs":     [ Lov, ... ],
  "recordGroups": [ RecordGroup, ... ],
  "windows":  [ { "name", "title", "width", "height" }, ... ],
  "canvases": [ { "name", "canvasType", "window", "width", "height",
                  "viewport": { "x", "y", "width", "height" } }, ... ],
  "tabPages": [ { "name", "label", "canvas" }, ... ],
  "formTriggers": [ Trigger, ... ],
  "referencedDbObjects": [ { "name", "paramShape": "unknown (resolve from schema)" } ],
  "census": { "totalElements": N, "byTag": { "Item": 34, ... } }
}
```

### Block
```jsonc
{
  "name", "databaseBlock": bool, "queryDataSource",
  "relationRole": "master" | "detail" | "control",
  "queryable": bool,
  "crud": { "insertAllowed", "updateAllowed", "deleteAllowed", "queryAllowed": bool,
            "injected": bool },   // absent in export => Forms default true; injected:true flags that
  "recordsDisplayed": "n" | null,
  "derivedMultiRecord": { "value": bool|null, "source": "RecordsDisplayed" | "derived(relation-membership/scrollbar)" },
  "lockingMode": { "value": "Automatic"|..., "injected": bool },
  "itemCount", "items": [ Item, ... ], "triggers": [ Trigger, ... ]
}
```

### Item
```jsonc
{
  "name", "itemType", "canvas", "tabPage",
  "geometry": { "x", "y", "width", "height" },   // facts for layoutPlan; ruleset drops for setters
  "visible", "dataType",
  "formula", "calculateMode", "summaryFunction", "summaryItemName",
  "listStyle", "concealData", "multiLine", "prompt", "columnName", "databaseItem",
  "editability": { "updateAllowed", "insertAllowed", "queryAllowed", "enabled": bool },  // absent => true
  "lovRole": "primary" | null,
  "imageSource": "file" | "blob",   // Image items only
  "triggers": [ Trigger, ... ]
}
```

### Trigger / Relation / Lov / RecordGroup
```jsonc
Trigger     { "name", "bodyShape", "body" }
Relation    { "name", "masterBlock", "detailBlock", "joinCondition", "deleteRecord", "deferred", "autoQuery" }
Lov         { "name", "title", "recordGroup", "query",
              "columns": [ { "name", "dataType", "title", "displayWidth", "returnItem" } ],
              "primaryReturnItem" }
RecordGroup { "name", "query", "columns": [ { "name", "dataType", "maximumLength" } ] }
```

## Signal heuristics

- **bodyShape** — `select-into`: body has `SELECT … INTO` (a fetch-and-default; an
  accompanying `NO_DATA_FOUND → RAISE` is part of that shape). `mixed`: a
  `SELECT … INTO` *plus* independent DML or a second `SELECT`. `raise-only`: a
  `RAISE`/guard with no `SELECT … INTO`. `other`: anything else. `mixed` is
  best-effort — a reviewer confirms.
- **relationRole** — `detail` if the block is any relation's `DetailBlock`; else
  `master` if it is a database block; else `control`. Topology, not `RecordsDisplayed`.
- **derivedMultiRecord** — uses `RecordsDisplayed` when present; otherwise derives
  from relation membership (detail) or a block scrollbar. Absent in real exports.
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

#!/usr/bin/env python3
"""forms-xml-analyzer: Oracle Forms FMB XML -> faithful, decision-free structural spec.

Emits facts at *form-object grain* (every node accounted for) plus the
analyzer-injected Forms defaults and derived signals the mapping ruleset's
predicates consume (bodyShape, relationRole, databaseBlock, resolved
LockingMode, recordsDisplayed/derivedMultiRecord, commitStyle, imageSource,
LOV column/return mapping), and the form's external dependencies including
its cross-form contracts (:GLOBAL / :PARAMETER shared state extracted from
trigger bodies). It makes NO Vaadin / migration decisions.

Usage:
    python3 analyze_form.py <form_fmb.xml> [--out spec.json] [--md spec.md]

Stdlib only (xml.etree, json, re, argparse). Python 3.9+.
"""
import argparse
import json
import re
import sys
import xml.etree.ElementTree as ET


def local(tag):
    """Strip any XML namespace from a tag name."""
    return tag.split("}")[-1] if isinstance(tag, str) else tag


def attrs(el):
    """Attributes with namespaces stripped from keys."""
    return {local(k): v for k, v in el.attrib.items()}


def yn(value, default=True):
    """Resolve a Forms boolean attribute; absent => the Forms default (usually true)."""
    if value is None:
        return default
    return value == "true"


# Identifiers we never treat as referenced *database* program units: PL/SQL and
# SQL keywords, scalar types, SQL functions, and Forms built-ins. Anything left
# after this filter is a *candidate* DB program unit to verify against the schema.
_PLSQL_KEYWORDS = {
    "SELECT", "INTO", "FROM", "WHERE", "AND", "OR", "NOT", "VALUES", "IF", "THEN",
    "ELSE", "ELSIF", "BEGIN", "END", "DECLARE", "CURSOR", "OPEN", "FETCH", "CLOSE",
    "EXCEPTION", "LOOP", "FOR", "WHILE", "RETURN", "EXIT", "WHEN", "CASE", "IS",
    "AS", "IN", "OUT", "NOCOPY", "MOD", "LIKE", "BETWEEN", "NULL", "OTHERS",
    "FORM_TRIGGER_FAILURE", "NO_DATA_FOUND", "TOO_MANY_ROWS", "DUAL", "NEXTVAL",
    "CURRVAL", "ROWNUM", "SYSDATE", "USER", "LEVEL", "TRUE", "FALSE",
}
_PLSQL_TYPES = {
    "CHAR", "VARCHAR2", "VARCHAR", "NUMBER", "INTEGER", "INT", "DATE", "DATETIME",
    "BOOLEAN", "PLS_INTEGER", "BINARY_INTEGER", "LONG", "ROWID", "CLOB", "BLOB",
    "TIMESTAMP", "DECIMAL", "FLOAT", "REAL", "SMALLINT",
}
_SQL_FUNCS = {
    "DECODE", "NVL", "NVL2", "MAX", "MIN", "SUM", "COUNT", "AVG", "TO_CHAR",
    "TO_DATE", "TO_NUMBER", "ABS", "ROUND", "TRUNC", "LENGTH", "SUBSTR", "INSTR",
    "UPPER", "LOWER", "LTRIM", "RTRIM", "TRIM", "REPLACE", "GREATEST", "LEAST",
    "COALESCE", "MROUND", "LPAD", "RPAD", "MOD", "SIGN", "CEIL", "FLOOR",
}
_FORMS_BUILTINS = {
    "CALL_FORM", "OPEN_FORM", "NEW_FORM", "GO_FORM", "GO_BLOCK", "GO_ITEM",
    "NEXT_ITEM", "PREVIOUS_ITEM", "NEXT_RECORD", "PREVIOUS_RECORD", "FIRST_RECORD",
    "LAST_RECORD", "NEXT_BLOCK", "PREVIOUS_BLOCK", "EXECUTE_QUERY", "ENTER_QUERY",
    "COMMIT_FORM", "CLEAR_BLOCK", "CLEAR_FORM", "CLEAR_RECORD", "CLEAR_ITEM",
    "DO_KEY", "DUP_VAL", "DUPLICATE_ITEM", "DUPLICATE_RECORD", "CREATE_RECORD",
    "DELETE_RECORD", "INSERT_RECORD", "FIND_ITEM", "FIND_BLOCK", "FIND_RELATION",
    "FIND_GROUP", "FIND_LOV", "FIND_VIEW", "FIND_CANVAS", "FIND_WINDOW",
    "FIND_FORM", "FIND_MENU_ITEM", "FIND_ALERT", "FIND_EDITOR", "FIND_TIMER",
    "GET_ITEM_PROPERTY", "SET_ITEM_PROPERTY", "GET_BLOCK_PROPERTY",
    "SET_BLOCK_PROPERTY", "GET_RELATION_PROPERTY", "SET_RELATION_PROPERTY",
    "GET_WINDOW_PROPERTY", "SET_WINDOW_PROPERTY", "GET_FORM_PROPERTY",
    "GET_VIEW_PROPERTY", "SET_VIEW_PROPERTY", "GET_CANVAS_PROPERTY",
    "GET_APPLICATION_PROPERTY", "SET_APPLICATION_PROPERTY", "GET_RECORD_PROPERTY",
    "SET_RECORD_PROPERTY", "SHOW_VIEW", "HIDE_VIEW", "REPLACE_CONTENT_VIEW",
    "SHOW_WINDOW", "HIDE_WINDOW", "SHOW_ALERT", "SET_ALERT_PROPERTY",
    "SET_ALERT_BUTTON_PROPERTY", "SHOW_LOV", "LIST_VALUES", "SHOW_EDITOR",
    "EDIT_TEXTITEM", "SET_MENU_ITEM_PROPERTY", "GET_MENU_ITEM_PROPERTY",
    "MESSAGE", "ID_NULL", "NAME_IN", "COPY", "DEFAULT_VALUE", "ERASE",
    "READ_IMAGE_FILE", "WRITE_IMAGE_FILE", "HOST", "EXEC_VERB", "DELETE_GROUP",
    "CREATE_GROUP", "CREATE_GROUP_FROM_QUERY", "POPULATE_GROUP", "ADD_GROUP_ROW",
    "ADD_GROUP_COLUMN", "DELETE_GROUP_ROW", "GET_GROUP_ROW_COUNT",
    "GET_GROUP_CHAR_CELL", "GET_GROUP_NUMBER_CELL", "GET_GROUP_DATE_CELL",
    "SET_GROUP_CHAR_CELL", "SET_GROUP_NUMBER_CELL", "QUERY_MASTER_DETAILS",
    "RAISE", "FORM_TRIGGER_FAILURE", "SYNCHRONIZE", "DISPLAY_ITEM",
    "SCROLL_DOWN", "SCROLL_UP", "DOWN", "UP", "CHECKBOX_CHECKED", "FORMS_DDL",
    "SET_TIMER", "CREATE_TIMER", "DELETE_TIMER", "GET_TRIGGER_PROPERTY",
}
_BUILTINS = _PLSQL_KEYWORDS | _PLSQL_TYPES | _SQL_FUNCS | _FORMS_BUILTINS


def body_shape(text):
    """Classify a trigger body: select-into / raise-only / mixed / other.

    Heuristic, documented, best-effort — a reviewer confirms 'mixed'.
    - select-into: contains a SELECT ... INTO (a fetch-and-default shape; an
      accompanying NO_DATA_FOUND -> RAISE is part of that shape).
    - mixed: a SELECT ... INTO *plus* independent DML/extra statements.
    - raise-only: a RAISE/validation guard with no SELECT ... INTO.
    - other: anything else (built-in call, assignment, navigation, ...).
    """
    if not text:
        return "other"
    up = text.upper()
    has_select_into = re.search(r"\bSELECT\b", up) and re.search(r"\bINTO\b", up)
    has_raise = "RAISE" in up
    has_other_dml = re.search(r"\b(INSERT|UPDATE|DELETE)\b", up) or len(
        re.findall(r"\bSELECT\b", up)) > 1
    if has_select_into and has_other_dml:
        return "mixed"
    if has_select_into:
        return "select-into"
    if has_raise:
        return "raise-only"
    return "other"


def referenced_db_objects(text):
    """Uppercase IDENTIFIER( calls in a body that are not built-ins/SQL keywords —
    candidate references to database program units (resolve signatures from schema)."""
    if not text:
        return []
    out = []
    for m in re.finditer(r"([A-Za-z_][A-Za-z0-9_$#.]*)\s*\(", text):
        name = m.group(1).upper()
        bare = name.split(".")[-1]
        if bare in _BUILTINS or name in _BUILTINS:
            continue
        out.append(m.group(1))
    return sorted(set(out))


_FORM_NAV = ("CALL_FORM", "OPEN_FORM", "NEW_FORM", "RUN_PRODUCT")


def called_forms(text):
    """Form/module names invoked via CALL_FORM/OPEN_FORM/NEW_FORM/RUN_PRODUCT in a
    body — candidate sibling forms this form depends on at runtime. PL/SQL line
    comments are stripped first so a commented-out call is not reported as a live
    dependency. Mechanical detection only; no scoping decision."""
    if not text:
        return []
    # FMB bodies separate lines with the literal token '&#10;', not real
    # newlines — normalise so a single-line '--' comment strips to its own line
    # end and does not swallow live calls that follow.
    normalized = text.replace("&#10;", "\n")
    stripped = re.sub(r"--[^\n]*", "", normalized)
    out = []
    pattern = r"\b({})\s*\(\s*'([^']+)'".format("|".join(_FORM_NAV))
    for m in re.finditer(pattern, stripped, re.IGNORECASE):
        out.append(m.group(2))
    return sorted(set(out))


def global_references(text):
    """':GLOBAL.x' / ':PARAMETER.x' references in a body, with access direction.

    Cross-form contracts live only inside trigger/program-unit bodies, so they are
    invisible to element-grain inventory unless extracted here. Mechanical
    heuristic, documented:

    - bind form: ``:GLOBAL.x :=`` -> write; any other ``:GLOBAL.x`` -> read.
    - string-name form: ``COPY(v,'GLOBAL.x')``, ``DEFAULT_VALUE(v,'GLOBAL.x')``,
      ``ERASE('GLOBAL.x')`` -> write; ``NAME_IN('GLOBAL.x')`` -> read.
    - a literal prefix concatenated at runtime (``NAME_IN('GLOBAL.width_' || v)``)
      is recorded as ``GLOBAL.WIDTH_*`` — the name set is dynamic.

    Returns {UPPER_NAME: set of 'read'/'write'} (a name can be both).
    PL/SQL line comments are stripped first, like called_forms.
    """
    if not text:
        return {}
    normalized = text.replace("&#10;", "\n")
    stripped = re.sub(r"--[^\n]*", "", normalized)
    refs = {}

    def add(name, access):
        refs.setdefault(name.upper(), set()).add(access)

    # bind-variable form: :GLOBAL.x / :PARAMETER.x, write when followed by :=
    for m in re.finditer(r":(GLOBAL|PARAMETER)\.([A-Za-z0-9_]+)", stripped, re.IGNORECASE):
        name = "{}.{}".format(m.group(1), m.group(2))
        rest = stripped[m.end():]
        add(name, "write" if re.match(r"\s*:=", rest) else "read")

    # string-name forms routed through built-ins
    string_forms = (
        (r"NAME_IN\s*\(\s*'(GLOBAL|PARAMETER)\.([A-Za-z0-9_]+)'", "read"),
        (r"COPY\s*\([^,()]*,\s*'(GLOBAL|PARAMETER)\.([A-Za-z0-9_]+)'", "write"),
        (r"DEFAULT_VALUE\s*\([^,()]*,\s*'(GLOBAL|PARAMETER)\.([A-Za-z0-9_]+)'", "write"),
        (r"ERASE\s*\(\s*'(GLOBAL|PARAMETER)\.([A-Za-z0-9_]+)'", "write"),
    )
    for pattern, access in string_forms:
        for m in re.finditer(pattern, stripped, re.IGNORECASE):
            name = "{}.{}".format(m.group(1), m.group(2))
            # dynamic suffix: the literal is only a prefix ('GLOBAL.width_' || v)
            if re.match(r"\s*\|\|", stripped[m.end():]):
                name += "*"
            add(name, access)
    return refs


def trigger_text(el):
    a = attrs(el)
    # frmf2xml stores body on the TriggerText attribute (entities already decoded
    # by the XML parser) or, in some exports, as element text.
    return a.get("TriggerText") or (el.text or "").strip()


def collect_triggers(parent, parent_map):
    """Triggers that are *direct* logical children of `parent` (own scope only)."""
    out = []
    for el in parent.iter():
        if local(el.tag) != "Trigger":
            continue
        # nearest ancestor that is a Block/Item/FormModule must be `parent`
        anc = parent_map.get(el)
        while anc is not None and local(anc.tag) not in (
                "Block", "Item", "FormModule", "FormModuleData"):
            anc = parent_map.get(anc)
        if anc is parent:
            a = attrs(el)
            body = trigger_text(el)
            out.append({
                "name": a.get("Name"),
                "bodyShape": body_shape(body),
                "body": body,
            })
    return out


def analyze(path):
    tree = ET.parse(path)
    root = tree.getroot()
    parent_map = {c: p for p in root.iter() for c in p}

    # census: every element tag counted, so nothing slips silently.
    census = {}
    total = 0
    for el in root.iter():
        total += 1
        t = local(el.tag)
        census[t] = census.get(t, 0) + 1

    # ---- relations (topology source of truth) ----------------------------
    relations = []
    detail_blocks = set()
    for el in root.iter():
        if local(el.tag) != "Relation":
            continue
        a = attrs(el)
        # master block = nearest ancestor Block of the Relation element
        anc = parent_map.get(el)
        while anc is not None and local(anc.tag) != "Block":
            anc = parent_map.get(anc)
        master = attrs(anc).get("Name") if anc is not None else None
        detail = a.get("DetailBlock")
        if detail:
            detail_blocks.add(detail)
        relations.append({
            "name": a.get("Name"),
            "masterBlock": master,
            "detailBlock": detail,
            "joinCondition": a.get("JoinCondition"),
            "deleteRecord": a.get("DeleteRecord"),
            "deferred": a.get("Deferred"),
            "autoQuery": a.get("AutoQuery"),
        })

    # ---- record groups (LOV queries) -------------------------------------
    record_groups = {}
    for el in root.iter():
        if local(el.tag) != "RecordGroup":
            continue
        a = attrs(el)
        cols = []
        for c in el.iter():
            if local(c.tag) == "RecordGroupColumn":
                ca = attrs(c)
                cols.append({"name": ca.get("Name"),
                             "dataType": ca.get("ColumnDataType"),
                             "maximumLength": ca.get("MaximumLength")})
        record_groups[a.get("Name")] = {"name": a.get("Name"),
                                        "query": a.get("RecordGroupQuery"),
                                        "columns": cols}

    # ---- LOVs (picker contract) ------------------------------------------
    lovs = []
    lov_return_items = set()
    for el in root.iter():
        if local(el.tag) != "LOV":
            continue
        a = attrs(el)
        rg = record_groups.get(a.get("RecordGroupName"), {})
        rg_types = {c["name"]: c["dataType"] for c in rg.get("columns", [])}
        cols = []
        primary = None
        for c in el.iter():
            if local(c.tag) != "LOVColumnMapping":
                continue
            ca = attrs(c)
            ret = ca.get("ReturnItem") or None
            if ret:
                lov_return_items.add(ret.upper())
                if primary is None:
                    primary = ret
            cols.append({"name": ca.get("Name"),
                         "dataType": rg_types.get(ca.get("Name")),
                         "title": ca.get("Title"),
                         "displayWidth": ca.get("DisplayWidth"),
                         "returnItem": ret})
        lovs.append({"name": a.get("Name"), "title": a.get("Title"),
                     "recordGroup": a.get("RecordGroupName"),
                     "query": rg.get("query"), "columns": cols,
                     "primaryReturnItem": primary})

    # ---- form-level signals ----------------------------------------------
    all_bodies = []
    for el in root.iter():
        if local(el.tag) == "Trigger":
            all_bodies.append(trigger_text(el))
    joined = "\n".join(b for b in all_bodies if b)
    commit_style = "COMMIT_FORM" if "COMMIT_FORM" in joined.upper() else None
    image_file_targets = set(
        m.group(1).upper() for m in re.finditer(
            r"READ_IMAGE_FILE\s*\([^,]*,[^,]*,\s*'([^']+)'", joined))

    form_el = None
    for el in root.iter():
        if local(el.tag) in ("FormModule", "FormModuleData"):
            form_el = el
            break
    form_attrs = attrs(form_el) if form_el is not None else {}
    form_name = form_attrs.get("Name")

    # ---- blocks + items --------------------------------------------------
    blocks = []
    for bel in root.iter():
        if local(bel.tag) != "Block":
            continue
        ba = attrs(bel)
        bname = ba.get("Name")
        db_block = bool(ba.get("QueryDataSourceName") or ba.get("DMLDataName"))
        is_detail = bname in detail_blocks
        if is_detail:
            role = "detail"
        elif db_block:
            role = "master"
        else:
            role = "control"
        queryable = db_block and ba.get("QueryAllowed") != "false"
        # project the incoming relation (where this block is the detail) onto the block,
        # so blockClassifiers predicates (deferred / autoQuery) can resolve.
        in_rel = next((r for r in relations if r["detailBlock"] == bname), None)
        crud_editable = (yn(ba.get("InsertAllowed")) or yn(ba.get("UpdateAllowed"))
                         or yn(ba.get("DeleteAllowed")))
        lock_attr = ba.get("LockingMode")
        locking = {"value": lock_attr or "Automatic", "injected": lock_attr is None}
        # Forms exports the count as RecordsDisplayCount; older/edge exports use
        # RecordsDisplayed. Read both so the explicit signal isn't lost.
        rd = ba.get("RecordsDisplayCount") or ba.get("RecordsDisplayed")
        if rd is not None:
            multi = int(rd) > 1 if rd.isdigit() else None
            src = "RecordsDisplayCount"
        else:
            # No count attribute => Forms displays a single record. Only a
            # detail block (rendered as a grid) is multi by topology; a block
            # scrollbar just navigates records and is not a reliable signal.
            multi = is_detail
            src = "derived(relation-membership)"

        # base-table column types (verbatim) — the bean field types live here.
        data_source_columns = []
        for dsc in bel.iter():
            if local(dsc.tag) != "DataSourceColumn":
                continue
            da = attrs(dsc)
            data_source_columns.append({
                "name": da.get("DSCName"),
                "dscType": da.get("DSCType"),
                "precision": da.get("DSCPrecision"),
                "scale": da.get("DSCScale"),
                "length": da.get("DSCLength"),
                "mandatory": da.get("DSCMandatory"),
            })

        items = []
        for iel in bel.iter():
            if local(iel.tag) != "Item":
                continue
            ia = attrs(iel)
            iname = ia.get("Name")
            qualified = "{}.{}".format(bname, iname).upper() if iname else ""
            itype = ia.get("ItemType")
            # Radio Group option set: each <RadioButton> child's value + label.
            radio_buttons = [
                {"name": attrs(rb).get("Name"),
                 "radioButtonValue": attrs(rb).get("RadioButtonValue"),
                 "label": attrs(rb).get("Label")}
                for rb in iel.iter()
                if local(rb.tag) == "RadioButton" and parent_map.get(rb) is iel
            ]
            item = {
                "name": iname,
                "itemType": itype,
                "canvas": ia.get("CanvasName") or None,
                "tabPage": ia.get("TabPageName") or None,
                "geometry": {
                    "x": ia.get("XPosition"), "y": ia.get("YPosition"),
                    "width": ia.get("Width"), "height": ia.get("Height"),
                },
                "visible": ia.get("Visible"),
                "dataType": ia.get("DataType"),
                "formula": ia.get("Formula"),
                "calculateMode": ia.get("CalculateMode"),
                "summaryFunction": ia.get("SummaryFunction"),
                "summaryItemName": ia.get("SummaryItemName"),
                "listStyle": ia.get("ListStyle"),
                "concealData": ia.get("ConcealData"),
                "multiLine": ia.get("MultiLine"),
                "prompt": ia.get("Prompt"),
                "label": ia.get("Label"),
                "columnName": ia.get("ColumnName"),
                "databaseItem": ia.get("DatabaseItem"),
                # Builder-grade attributes recorded verbatim (consumed by ruleset
                # per-target attribute tables: MaximumLength, Required,
                # InitializeValue, FormatMask, Checked/UncheckedValue, ...).
                "maximumLength": ia.get("MaximumLength"),
                "required": ia.get("Required"),
                "initializeValue": ia.get("InitializeValue"),
                "formatMask": ia.get("FormatMask"),
                "checkedValue": ia.get("CheckedValue"),
                "uncheckedValue": ia.get("UncheckedValue"),
                "copyValueFromItem": ia.get("CopyValueFromItem"),
                "lovName": ia.get("LovName"),
                "editability": {
                    "updateAllowed": yn(ia.get("UpdateAllowed")),
                    "insertAllowed": yn(ia.get("InsertAllowed")),
                    "queryAllowed": yn(ia.get("QueryAllowed")),
                    "enabled": yn(ia.get("Enabled")),
                },
                "lovRole": "primary" if qualified in lov_return_items else None,
                "triggers": collect_triggers(iel, parent_map),
            }
            if radio_buttons:
                item["radioButtons"] = radio_buttons
            if itype == "Image":
                item["imageSource"] = "file" if qualified in image_file_targets else "blob"
            items.append(item)

        blocks.append({
            "name": bname,
            "databaseBlock": db_block,
            "queryDataSource": ba.get("QueryDataSourceName"),
            "relationRole": role,
            "queryable": queryable,
            "deferred": in_rel["deferred"] if in_rel else None,
            "autoQuery": in_rel["autoQuery"] if in_rel else None,
            "editable": crud_editable,
            "crud": {
                "insertAllowed": yn(ba.get("InsertAllowed")),
                "updateAllowed": yn(ba.get("UpdateAllowed")),
                "deleteAllowed": yn(ba.get("DeleteAllowed")),
                "queryAllowed": yn(ba.get("QueryAllowed")),
                "injected": all(ba.get(k) is None for k in (
                    "InsertAllowed", "UpdateAllowed", "DeleteAllowed", "QueryAllowed")),
            },
            "recordsDisplayed": rd,
            "derivedMultiRecord": {"value": multi, "source": src},
            "lockingMode": locking,
            "dataSourceColumns": data_source_columns,
            "itemCount": len(items),
            "items": items,
            "triggers": collect_triggers(bel, parent_map),
        })

    # ---- windows / canvases / tab pages (layout structure) ---------------
    windows = []
    for el in root.iter():
        if local(el.tag) != "Window":
            continue
        a = attrs(el)
        windows.append({"name": a.get("Name"), "title": a.get("Title"),
                        "width": a.get("Width"), "height": a.get("Height")})

    canvases = []
    tab_pages = []
    for el in root.iter():
        if local(el.tag) != "Canvas":
            continue
        a = attrs(el)
        canvases.append({
            "name": a.get("Name"),
            "canvasType": a.get("CanvasType"),
            "window": a.get("WindowName"),
            "width": a.get("Width"), "height": a.get("Height"),
            "viewport": {"x": a.get("ViewportX"), "y": a.get("ViewportY"),
                         "width": a.get("ViewportWidth"), "height": a.get("ViewportHeight")},
        })
        # Tab pages are children of a Tab canvas.
        for tp in el.iter():
            if local(tp.tag) != "TabPage":
                continue
            ta = attrs(tp)
            tab_pages.append({"name": ta.get("Name"), "label": ta.get("Label"),
                              "canvas": a.get("Name")})

    # ---- alerts (confirm/question dialogs referenced by triggers) --------
    alerts = []
    for el in root.iter():
        if local(el.tag) != "Alert":
            continue
        a = attrs(el)
        alerts.append({
            "name": a.get("Name"),
            "title": a.get("Title"),
            "alertStyle": a.get("AlertStyle"),
            "alertMessage": a.get("AlertMessage"),
            "button1Label": a.get("Button1Label"),
            "button2Label": a.get("Button2Label"),
            "button3Label": a.get("Button3Label"),
        })

    # ---- in-form program units (Forms coordination boilerplate, verbatim) -
    # Distinct from referencedDbObjects (external/DB calls); these are the
    # form's own PL/SQL, so a planner can recognise dissolvable boilerplate.
    program_units = []
    for el in root.iter():
        if local(el.tag) != "ProgramUnit":
            continue
        a = attrs(el)
        program_units.append({
            "name": a.get("Name"),
            "kind": "program-unit",
            "programUnitType": a.get("ProgramUnitType"),
            "body": a.get("ProgramUnitText"),
        })

    # ---- attached PL/SQL libraries ---------------------------------------
    attached_libraries = []
    for el in root.iter():
        if local(el.tag) != "AttachedLibrary":
            continue
        a = attrs(el)
        attached_libraries.append({
            "name": a.get("Name"),
            "libraryLocation": a.get("LibraryLocation"),
            "librarySource": a.get("LibrarySource"),
        })

    # ---- visual attributes (named style bundles) -------------------------
    visual_attributes = []
    for el in root.iter():
        if local(el.tag) != "VisualAttribute":
            continue
        a = attrs(el)
        visual_attributes.append({
            "name": a.get("Name"),
            "backColor": a.get("BackColor"),
            "foregroundColor": a.get("ForegroundColor"),
            "fontName": a.get("FontName"),
            "fontSize": a.get("FontSize"),
        })

    # ---- object groups (subclassed/reused object bundles) ----------------
    object_groups = []
    for el in root.iter():
        if local(el.tag) != "ObjectGroup":
            continue
        a = attrs(el)
        children = [
            {"name": attrs(c).get("Name"), "type": attrs(c).get("Type")}
            for c in el.iter() if local(c.tag) == "ObjectGroupChild"
        ]
        object_groups.append({
            "name": a.get("Name"),
            "parentName": a.get("ParentName"),
            "parentModule": a.get("ParentModule"),
            "parentFilename": a.get("ParentFilename"),
            "children": children,
        })

    # ---- static text (boilerplate labels / headers / help paragraph) -----
    # Verbatim text + placement; no judgement about role (header vs help).
    static_text = []
    for el in root.iter():
        if local(el.tag) != "Graphics":
            continue
        a = attrs(el)
        text = a.get("GraphicsText")
        if not text:
            continue  # non-text Graphics (Rectangle, Line, ...) carry no label
        # owning canvas = nearest ancestor Canvas
        anc = parent_map.get(el)
        while anc is not None and local(anc.tag) != "Canvas":
            anc = parent_map.get(anc)
        static_text.append({
            "name": a.get("Name"),
            "text": text,
            "graphicsType": "Text",
            "canvas": attrs(anc).get("Name") if anc is not None else None,
            "fontSize": a.get("GraphicsFontSize"),
            "fontWeight": a.get("GraphicsFontWeight"),
            "geometry": {"x": a.get("XPosition"), "y": a.get("YPosition"),
                         "width": a.get("Width"), "height": a.get("Height")},
        })

    # ---- layout fidelity: frames, static-text roles, layoutPlan (ADR 0012) -
    # Geometry is dropped for *setters* (Aura/Flow own pixels, ADR 0008) but is
    # consumed here to recover *structure*: which fields a frame groups, which
    # static text is a heading vs a field prompt, and the row order of fields.
    # All three are descriptive derived signals (like bodyShape / relationRole),
    # not migration decisions — no Vaadin target is chosen here.
    def _f(v):
        try:
            return float(v)
        except (TypeError, ValueError):
            return None

    def _center(g):
        x, y = _f(g.get("x")), _f(g.get("y"))
        w, h = _f(g.get("width")), _f(g.get("height"))
        if x is None or y is None:
            return None
        return (x + (w or 0) / 2.0, y + (h or 0) / 2.0)

    # index every item's geometry + canvas (qualified name -> info)
    item_geom = {}
    for b in blocks:
        for it in b["items"]:
            item_geom["{}.{}".format(b["name"], it["name"]).upper()] = {
                "block": b["name"], "name": it["name"], "canvas": it.get("canvas"),
                "geometry": it.get("geometry") or {},
            }

    # Frames: a Rectangle/Frame Graphics whose bounds enclose >=1 item. A bounded
    # rectangle that groups fields is normalised to graphicsType "Frame" (the
    # ruleset's frame-group operand); a rectangle that encloses nothing stays
    # "Rectangle" (decoration → Drop). Forms exports the header group as a
    # titleless Rectangle, so frameTitle is usually null and the section heading
    # comes from a heading-role static text sitting on the frame.
    frames = []
    for el in root.iter():
        if local(el.tag) != "Graphics":
            continue
        a = attrs(el)
        if a.get("GraphicsType") not in ("Rectangle", "Frame"):
            continue
        fx, fy = _f(a.get("XPosition")), _f(a.get("YPosition"))
        fw, fh = _f(a.get("Width")), _f(a.get("Height"))
        if None in (fx, fy, fw, fh):
            continue
        anc = parent_map.get(el)
        while anc is not None and local(anc.tag) != "Canvas":
            anc = parent_map.get(anc)
        fcanvas = attrs(anc).get("Name") if anc is not None else None
        enclosed = []
        for q, info in item_geom.items():
            if info["canvas"] and fcanvas and info["canvas"] != fcanvas:
                continue
            c = _center(info["geometry"])
            if c is not None and fx <= c[0] <= fx + fw and fy <= c[1] <= fy + fh:
                enclosed.append(q)
        frames.append({
            "name": a.get("Name"),
            "graphicsType": "Frame" if enclosed else a.get("GraphicsType"),
            "rawGraphicsType": a.get("GraphicsType"),
            "canvas": fcanvas,
            "frameTitle": a.get("FrameTitle") or None,
            "geometry": {"x": a.get("XPosition"), "y": a.get("YPosition"),
                         "width": a.get("Width"), "height": a.get("Height")},
            "enclosesItems": bool(enclosed),
            "enclosedItems": sorted(enclosed),
        })

    # Static-text role: heading (font larger than the canvas's typical text),
    # prompt (a label adjacent to a field — carries as that field's label, not a
    # standalone node), or prose. headingLevel ranks the dominant title (h2) vs
    # section titles (h3). Heuristic, descriptive, font/position-derived.
    # body baseline = the smallest static-text size on the form (Forms prompts /
    # help prose sit at the base size; titles are set markedly larger). A text is
    # a heading when it is at least 1.5x the body baseline — robust even when a
    # canvas carries a single title (a canvas-relative threshold can't see that).
    all_sizes = [x for x in (_f(t.get("fontSize")) for t in static_text) if x]
    body_size = min(all_sizes) if all_sizes else 0
    canvas_max = {}
    for s in static_text:
        fs = _f(s.get("fontSize")) or 0
        canvas_max[s.get("canvas")] = max(canvas_max.get(s.get("canvas"), 0), fs)
    for t in static_text:
        fs = _f(t.get("fontSize")) or 0
        tg = t.get("geometry") or {}
        tc = _center(tg)
        # prompt: a field sits just to the right on the same row, or directly below
        labels = None
        if tc is not None:
            tx, ty = _f(tg.get("x")) or 0, tc[1]
            trh = _f(tg.get("height")) or 12
            for q, info in item_geom.items():
                if info["canvas"] != t.get("canvas"):
                    continue
                ic = _center(info["geometry"])
                if ic is None:
                    continue
                ix = _f((info["geometry"]).get("x")) or 0
                right_same_row = ix >= tx and abs(ic[1] - ty) <= trh
                below_aligned = ic[1] > ty and abs(ix - tx) <= trh and 0 < (ic[1] - ty) <= 3 * trh
                if right_same_row or below_aligned:
                    labels = q
                    break
        if fs and body_size and fs >= 1.5 * body_size:
            t["role"] = "heading"
            t["headingLevel"] = "h2" if fs >= canvas_max.get(t.get("canvas"), fs) else "h3"
        elif labels:
            t["role"] = "prompt"
            t["labelsItem"] = labels
        else:
            t["role"] = "prose"

    # layoutPlan per block: cluster the block's visible, placed items into rows
    # by Y-proximity, order rows top→bottom and fields left→right within a row,
    # and tag each row with the frame that encloses it (else null = block top
    # level). This is the field ORDER + grouping the generator places from; it is
    # the form's order, never declaration-accident order (ADR 0011 / 0012). The
    # ruleset consumes it for single-record (FormLayout) blocks; Grid blocks
    # ignore it. rowAffinity/fallbackTolerance mirror migration-rules.yaml.
    ROW_AFFINITY, FALLBACK_TOL = 0.5, 50.0

    def _frame_for(qnames):
        for fr in frames:
            if fr["enclosesItems"] and all(q in fr["enclosedItems"] for q in qnames):
                return fr["name"]
        return None

    for b in blocks:
        placed = []
        for it in b["items"]:
            g = it.get("geometry") or {}
            y, x = _f(g.get("y")), _f(g.get("x"))
            if y is None or x is None:
                continue
            placed.append((y, x, _f(g.get("height")), it["name"]))
        placed.sort(key=lambda p: (p[0], p[1]))
        rows = []
        for y, x, h, name in placed:
            row = rows[-1] if rows else None
            if row is not None:
                ph = [m[2] for m in row["members"] if m[2]] + ([h] if h else [])
                tol = ROW_AFFINITY * min(ph) if ph else FALLBACK_TOL
                if abs(y - row["y"]) <= max(tol, 1.0):
                    row["members"].append((y, x, h, name))
                    continue
            rows.append({"y": y, "members": [(y, x, h, name)]})
        plan = []
        for row in rows:
            members = sorted(row["members"], key=lambda m: m[1])
            names = [m[3] for m in members]
            qnames = ["{}.{}".format(b["name"], n).upper() for n in names]
            plan.append({"fields": names, "section": _frame_for(qnames)})
        b["layoutPlan"] = plan

    # ---- referenced DB program units (resolve signatures from schema) ----
    refs = set()
    for b in all_bodies:
        for r in referenced_db_objects(b):
            refs.add(r)

    form_triggers = collect_triggers(form_el, parent_map) if form_el is not None else []

    # ---- cross-form contracts (:GLOBAL / :PARAMETER) ----------------------
    # Shared-state names read/written by this form's PL/SQL. They are the
    # form's runtime contract with whichever form calls it (or is called by
    # it), so they are inventoried like every other external dependency —
    # per name, with the owning trigger/program-unit and access direction —
    # and flagged status:"undecided". Mechanical extraction only.
    global_usage = {}

    def _scan_globals(qualified, body):
        for gname, accesses in global_references(body).items():
            for access in sorted(accesses):
                global_usage.setdefault(gname, []).append(
                    {"trigger": qualified, "access": access})

    for b in blocks:
        for tg in b["triggers"]:
            _scan_globals("{}.{}".format(b["name"], tg["name"]), tg["body"])
        for it in b["items"]:
            for tg in it["triggers"]:
                _scan_globals("{}.{}.{}".format(b["name"], it["name"], tg["name"]),
                              tg["body"])
    for tg in form_triggers:
        _scan_globals("FORM.{}".format(tg["name"]), tg["body"])
    for pu in program_units:
        _scan_globals("PROGRAM-UNIT.{}".format(pu["name"]), pu.get("body"))

    # ---- external dependencies (other migration units this form needs) ---
    # Inventoried mechanically; every entry is flagged status:"undecided". The
    # analyzer makes NO scoping decision — the SKILL workflow requires the agent
    # to ask, per dependency, whether to produce a separate spec (mark it a
    # dependency) or ignore it. This is the "never silently drop an out-of-slice
    # surface" guarantee.
    dependencies = []
    if form_attrs.get("MenuModule"):
        dependencies.append({
            "type": "menu-module", "name": form_attrs.get("MenuModule"),
            "source": None, "via": "FormModule.MenuModule", "status": "undecided",
        })
    for lib in attached_libraries:
        dependencies.append({
            "type": "attached-library", "name": lib["name"],
            "source": lib.get("libraryLocation"), "via": "AttachedLibrary",
            "status": "undecided",
        })
    nav_sources = list(all_bodies) + [pu.get("body") for pu in program_units]
    for fn in sorted({f for b in nav_sources for f in called_forms(b)}):
        dependencies.append({
            "type": "called-form", "name": fn, "source": None,
            "via": "CALL_FORM/OPEN_FORM/NEW_FORM/RUN_PRODUCT", "status": "undecided",
        })
    for og in object_groups:
        if og.get("parentFilename"):
            dependencies.append({
                "type": "object-library", "name": og.get("parentName") or og["name"],
                "source": og["parentFilename"], "via": "ObjectGroup (subclassed)",
                "status": "undecided",
            })
    for gname in sorted(global_usage):
        dependencies.append({
            "type": "parameter" if gname.startswith("PARAMETER.") else "global-variable",
            "name": gname, "source": None,
            "via": "trigger-body scan (:GLOBAL/:PARAMETER)", "status": "undecided",
            "usage": global_usage[gname],
        })

    return {
        "source": path,
        "grain": "form-object",
        "decisionFree": True,
        "form": {"name": form_name, "commitStyle": commit_style,
                 "menuModule": form_attrs.get("MenuModule") or None,
                 "title": form_attrs.get("Title")},
        "blocks": blocks,
        "relations": relations,
        "alerts": alerts,
        "lovs": lovs,
        "recordGroups": list(record_groups.values()),
        "programUnits": program_units,
        "attachedLibraries": attached_libraries,
        "visualAttributes": visual_attributes,
        "objectGroups": object_groups,
        "dependencies": dependencies,
        "windows": windows,
        "canvases": canvases,
        "tabPages": tab_pages,
        "frames": frames,
        "staticText": static_text,
        "formTriggers": form_triggers,
        "referencedDbObjects": [
            {"name": n, "paramShape": "unknown (resolve from schema)"} for n in sorted(refs)
        ],
        "census": {"totalElements": total, "byTag": dict(sorted(census.items()))},
    }


def to_markdown(spec):
    L = []
    f = spec["form"]
    L.append("# Structural spec — {}".format(f.get("name") or spec["source"]))
    L.append("")
    L.append("> Decision-free, form-object grain. commitStyle: `{}`".format(f.get("commitStyle")))
    L.append("")
    L.append("## Census ({} elements)".format(spec["census"]["totalElements"]))
    L.append("")
    for t, c in spec["census"]["byTag"].items():
        L.append("- {}: {}".format(t, c))
    L.append("")
    L.append("## Layout")
    L.append("")
    L.append("Windows: " + ", ".join(
        "{} ({})".format(w["name"], w["title"]) for w in spec.get("windows", [])))
    L.append("")
    L.append("| Canvas | type | window |")
    L.append("|---|---|---|")
    for c in spec.get("canvases", []):
        L.append("| {} | {} | {} |".format(c["name"], c["canvasType"], c.get("window")))
    if spec.get("tabPages"):
        L.append("")
        L.append("Tab pages: " + ", ".join(
            "{}/{}".format(t["canvas"], t["name"]) for t in spec["tabPages"]))
    L.append("")
    L.append("## Blocks")
    L.append("")
    L.append("| Block | role | dbBlock | CRUD i/u/d/q (injected) | multiRecord | locking (injected) |")
    L.append("|---|---|---|---|---|---|")
    for b in spec["blocks"]:
        dm = b["derivedMultiRecord"]
        lk = b["lockingMode"]
        c = b["crud"]
        crud = "{}/{}/{}/{} ({})".format(
            int(c["insertAllowed"]), int(c["updateAllowed"]), int(c["deleteAllowed"]),
            int(c["queryAllowed"]), c["injected"])
        L.append("| {} | {} | {} | {} | {} | {} ({}) |".format(
            b["name"], b["relationRole"], b["databaseBlock"], crud, dm["value"],
            lk["value"], lk["injected"]))
    L.append("")
    if spec.get("frames"):
        L.append("## Frames (grouping regions — ADR 0012)")
        L.append("")
        for fr in spec["frames"]:
            if not fr.get("enclosesItems"):
                continue
            enc = ", ".join(q.split(".", 1)[-1] for q in fr["enclosedItems"])
            L.append("- **{}** on {} title={} encloses: {}".format(
                fr["name"], fr.get("canvas"), fr.get("frameTitle") or "—", enc))
        L.append("")
    has_plan = any(b.get("layoutPlan") for b in spec["blocks"])
    if has_plan:
        L.append("## Layout plan (field order + section — ADR 0011/0012)")
        L.append("")
        L.append("> Field order is the form's order (Y-then-X), never invented. `section` is the enclosing frame.")
        L.append("")
        for b in spec["blocks"]:
            plan = b.get("layoutPlan") or []
            if not plan:
                continue
            L.append("- **{}**".format(b["name"]))
            for row in plan:
                sect = " [section: {}]".format(row["section"]) if row.get("section") else ""
                L.append("  - {}{}".format(", ".join(row["fields"]), sect))
        L.append("")
    L.append("## Block data-source columns (base-table types)")
    L.append("")
    for b in spec["blocks"]:
        dsc = b.get("dataSourceColumns") or []
        if not dsc:
            continue
        cols = ", ".join("{} {}{}{}".format(
            c["name"], c["dscType"],
            "({},{})".format(c["precision"], c["scale"]) if c.get("precision") and c["precision"] != "0" else "",
            "*" if c.get("mandatory") == "true" else "") for c in dsc)
        L.append("- **{}**: {}".format(b["name"], cols))
    L.append("")
    L.append("## Relations")
    L.append("")
    for r in spec["relations"]:
        L.append("- **{}**: {} → {} | DeleteRecord={} Deferred={} AutoQuery={}".format(
            r["name"], r["masterBlock"], r["detailBlock"], r["deleteRecord"],
            r["deferred"], r["autoQuery"]))
    L.append("")
    if spec.get("dependencies"):
        L.append("## Dependencies (out-of-slice surfaces — decide per item)")
        L.append("")
        L.append("> Each is `status: undecided`. Per the skill workflow, ask the user"
                 " whether to spec each as a dependency or ignore it — never drop silently.")
        L.append("")
        L.append("| Type | Name | Source | Via | Status |")
        L.append("|---|---|---|---|---|")
        for d in spec["dependencies"]:
            L.append("| {} | {} | {} | {} | {} |".format(
                d["type"], d["name"], d.get("source") or "—", d["via"], d["status"]))
        L.append("")
        contract_deps = [d for d in spec["dependencies"] if d.get("usage")]
        if contract_deps:
            L.append("### Cross-form contracts (:GLOBAL / :PARAMETER usage)")
            L.append("")
            L.append("> Shared state this form reads/writes — its runtime contract with"
                     " calling/called forms. Decide each like any dependency.")
            L.append("")
            for d in contract_deps:
                use = "; ".join("{} ({})".format(u["trigger"], u["access"])
                                for u in d["usage"])
                L.append("- **{}**: {}".format(d["name"], use))
            L.append("")
    if spec.get("alerts"):
        L.append("## Alerts")
        L.append("")
        for a in spec["alerts"]:
            L.append("- **{}** style={} buttons=[{}, {}] message={}".format(
                a["name"], a["alertStyle"], a["button1Label"], a["button2Label"],
                a["alertMessage"]))
        L.append("")
    L.append("## LOVs")
    L.append("")
    for lov in spec["lovs"]:
        L.append("- **{}** ({}) primaryReturnItem={}".format(
            lov["name"], lov["title"], lov["primaryReturnItem"]))
        L.append("  - query: `{}`".format(lov["query"]))
        for c in lov["columns"]:
            L.append("  - col {} ({}) title={} returnItem={}".format(
                c["name"], c["dataType"], c["title"], c["returnItem"]))
    L.append("")
    L.append("## Triggers (name · bodyShape)")
    L.append("")
    for b in spec["blocks"]:
        for tg in b["triggers"]:
            L.append("- {}.{} · {}".format(b["name"], tg["name"], tg["bodyShape"]))
        for it in b["items"]:
            for tg in it["triggers"]:
                L.append("- {}.{}.{} · {}".format(b["name"], it["name"], tg["name"], tg["bodyShape"]))
    for tg in spec.get("formTriggers", []):
        L.append("- FORM.{} · {}".format(tg["name"], tg["bodyShape"]))
    L.append("")
    # Option-set details a builder needs (Radio Group / Check Box).
    option_rows = []
    for b in spec["blocks"]:
        for it in b["items"]:
            if it.get("radioButtons"):
                opts = ", ".join("{}={}".format(rb["label"], rb["radioButtonValue"])
                                 for rb in it["radioButtons"])
                option_rows.append("- {}.{} (Radio Group) init={}: {}".format(
                    b["name"], it["name"], it.get("initializeValue"), opts))
            elif it.get("checkedValue") is not None or it.get("uncheckedValue") is not None:
                option_rows.append("- {}.{} (Check Box) checked={} unchecked={} init={} label={}".format(
                    b["name"], it["name"], it.get("checkedValue"),
                    it.get("uncheckedValue"), it.get("initializeValue"), it.get("label")))
    if option_rows:
        L.append("## Option sets (radio / checkbox values)")
        L.append("")
        L.extend(option_rows)
        L.append("")
    if spec.get("programUnits"):
        L.append("## In-form program units")
        L.append("")
        for p in spec["programUnits"]:
            L.append("- {} ({})".format(p["name"], p["programUnitType"]))
        L.append("")
    reused = []
    if spec.get("attachedLibraries"):
        reused.append("AttachedLibraries: " + ", ".join(
            l["name"] for l in spec["attachedLibraries"]))
    if spec.get("visualAttributes"):
        reused.append("VisualAttributes: " + ", ".join(
            v["name"] for v in spec["visualAttributes"]))
    if spec.get("objectGroups"):
        reused.append("ObjectGroups: " + ", ".join(
            "{}({})".format(g["name"], len(g["children"])) for g in spec["objectGroups"]))
    if spec["form"].get("menuModule"):
        reused.append("MenuModule: " + spec["form"]["menuModule"])
    if reused:
        L.append("## Reused / deferred objects (recorded, not detailed)")
        L.append("")
        for r in reused:
            L.append("- " + r)
        L.append("")
    if spec.get("staticText"):
        L.append("## Static text (verbatim labels / headers / help)")
        L.append("")
        for s in spec["staticText"]:
            snippet = s["text"].replace("\n", " ")
            if len(snippet) > 80:
                snippet = snippet[:77] + "..."
            role = s.get("role") or "—"
            extra = ""
            if role == "heading":
                extra = " ({})".format(s.get("headingLevel") or "h?")
            elif role == "prompt" and s.get("labelsItem"):
                extra = " (labels {})".format(s["labelsItem"].split(".", 1)[-1])
            L.append("- {}/{} [{}{}]: \"{}\"".format(
                s["canvas"], s["name"], role, extra, snippet))
        L.append("")
    if spec["referencedDbObjects"]:
        L.append("## Referenced DB program units (resolve signatures from schema)")
        L.append("")
        for r in spec["referencedDbObjects"]:
            L.append("- {} — paramShape: {}".format(r["name"], r["paramShape"]))
        L.append("")
    return "\n".join(L)


def main(argv=None):
    ap = argparse.ArgumentParser(description="Oracle Forms FMB XML -> structural spec (decision-free).")
    ap.add_argument("xml", help="path to the FMB XML (frmf2xml output)")
    ap.add_argument("--out", help="write JSON spec to this path (default: stdout)")
    ap.add_argument("--md", help="also write a human-readable markdown digest to this path")
    args = ap.parse_args(argv)

    try:
        spec = analyze(args.xml)
    except ET.ParseError as e:
        sys.stderr.write("XML parse error: {}\n".format(e))
        return 2
    except FileNotFoundError:
        sys.stderr.write("File not found: {}\n".format(args.xml))
        return 2

    payload = json.dumps(spec, indent=2, ensure_ascii=False)
    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(payload)
        sys.stderr.write("Wrote JSON spec -> {}\n".format(args.out))
    else:
        print(payload)
    if args.md:
        with open(args.md, "w", encoding="utf-8") as fh:
            fh.write(to_markdown(spec))
        sys.stderr.write("Wrote markdown digest -> {}\n".format(args.md))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

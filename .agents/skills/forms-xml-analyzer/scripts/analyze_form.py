#!/usr/bin/env python3
"""forms-xml-analyzer: Oracle Forms FMB XML -> faithful, decision-free structural spec.

Emits facts at *form-object grain* (every node accounted for) plus the
analyzer-injected Forms defaults and derived signals the mapping ruleset's
predicates consume (bodyShape, relationRole, databaseBlock, resolved
LockingMode, recordsDisplayed/derivedMultiRecord, commitStyle, imageSource,
LOV column/return mapping). It makes NO Vaadin / migration decisions.

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
    form_name = attrs(form_el).get("Name") if form_el is not None else None

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
        rd = ba.get("RecordsDisplayed")
        scrollbar = bool(ba.get("ScrollbarLength") or ba.get("ScrollBarLength"))
        if rd is not None:
            multi = int(rd) > 1 if rd.isdigit() else None
            src = "RecordsDisplayed"
        else:
            multi = is_detail or scrollbar
            src = "derived(relation-membership/scrollbar)"

        items = []
        for iel in bel.iter():
            if local(iel.tag) != "Item":
                continue
            ia = attrs(iel)
            iname = ia.get("Name")
            qualified = "{}.{}".format(bname, iname).upper() if iname else ""
            itype = ia.get("ItemType")
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
                "columnName": ia.get("ColumnName"),
                "databaseItem": ia.get("DatabaseItem"),
                "editability": {
                    "updateAllowed": yn(ia.get("UpdateAllowed")),
                    "insertAllowed": yn(ia.get("InsertAllowed")),
                    "queryAllowed": yn(ia.get("QueryAllowed")),
                    "enabled": yn(ia.get("Enabled")),
                },
                "lovRole": "primary" if qualified in lov_return_items else None,
                "triggers": collect_triggers(iel, parent_map),
            }
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

    # ---- referenced DB program units (resolve signatures from schema) ----
    refs = set()
    for b in all_bodies:
        for r in referenced_db_objects(b):
            refs.add(r)

    return {
        "source": path,
        "grain": "form-object",
        "decisionFree": True,
        "form": {"name": form_name, "commitStyle": commit_style},
        "blocks": blocks,
        "relations": relations,
        "lovs": lovs,
        "recordGroups": list(record_groups.values()),
        "windows": windows,
        "canvases": canvases,
        "tabPages": tab_pages,
        "formTriggers": collect_triggers(form_el, parent_map) if form_el is not None else [],
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
    L.append("## Relations")
    L.append("")
    for r in spec["relations"]:
        L.append("- **{}**: {} → {} | DeleteRecord={} Deferred={} AutoQuery={}".format(
            r["name"], r["masterBlock"], r["detailBlock"], r["deleteRecord"],
            r["deferred"], r["autoQuery"]))
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

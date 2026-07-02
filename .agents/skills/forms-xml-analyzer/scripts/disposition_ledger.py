#!/usr/bin/env python3
"""disposition_ledger: structural spec -> disposition ledger (the to-issues gate).

The census proved nothing was dropped from the *spec*; this ledger proves
nothing is dropped from the *migration* (ADR-0015). It emits one row per
form object the spec inventories, PLUS one row per decision-prone mapped
property (defaults, editability modes, visibility, value propagation) —
the grain at which v2's silent losses happened. Every row must end in
exactly one disposition before build tickets may be cut:

  migrated-as   built; `as` names the web behaviour, `ref` the ticket/PR
  re-decided    deliberately different from Forms; `as` + `reason` + `decidedBy`
  deferred      accepted, not this slice; `ref` the tracking ticket/PRD
  obsolete      Forms-runtime plumbing with no web counterpart; `rule` says why

Mechanical carries (maximumLength, prompts, geometry-derived order) get NO
rows: the layout-fidelity gate (ADR-0012) already verifies them at runtime,
and rows for them would drown the decision-prone ones. Ignore-verb ruleset
properties likewise need no row — the ruleset entry IS their disposition.

Usage:
    python3 disposition_ledger.py spec.json --out ledger.json [--md ledger.md]
        [--merge prior-ledger.json]   # preserve dispositions by row id
        [--check]                     # exit 1 listing unassigned rows

Regenerating is safe: --merge carries dispositions forward by row id, so a
spec change only adds/removes rows, never wipes decisions.

Stdlib only. Python 3.9+.
"""
import argparse
import json
import sys

DISPOSITIONS = ("migrated-as", "re-decided", "deferred", "obsolete")

# Decision-prone item properties: (row suffix, predicate on the item dict,
# spec-value renderer). Mechanical carries deliberately absent (see docstring).
_ITEM_PROPERTY_ROWS = (
    ("initializeValue",
     lambda it: it.get("initializeValue") not in (None, ""),
     lambda it: it.get("initializeValue")),
    ("formatMask",
     lambda it: it.get("formatMask") not in (None, ""),
     lambda it: it.get("formatMask")),
    ("required",
     lambda it: it.get("required") == "true",
     lambda it: "true"),
    ("copyValueFromItem",
     lambda it: bool(it.get("copyValueFromItem")),
     lambda it: it.get("copyValueFromItem")),
    ("visible",
     lambda it: it.get("visible") == "false",
     lambda it: "false"),
    ("concealData",
     lambda it: it.get("concealData") == "true",
     lambda it: "true"),
    ("editability.updateAllowed",
     lambda it: (it.get("editability") or {}).get("updateAllowed") is False,
     lambda it: "false (insert-only)" if (it.get("editability") or {}).get(
         "insertAllowed") else "false"),
    ("editability.insertAllowed",
     lambda it: (it.get("editability") or {}).get("insertAllowed") is False,
     lambda it: "false"),
    ("editability.enabled",
     lambda it: (it.get("editability") or {}).get("enabled") is False,
     lambda it: "false"),
)


def _row(rid, kind, spec_value=None):
    return {"id": rid, "kind": kind, "specValue": spec_value,
            "disposition": None, "as": None, "reason": None,
            "decidedBy": None, "ref": None, "rule": None}


def build_rows(spec):
    rows = []

    for b in spec.get("blocks", []):
        bname = b["name"]
        rows.append(_row("block:{}".format(bname), "block",
                         "{} ({})".format(b.get("relationRole"),
                                          "db" if b.get("databaseBlock") else "control")))
        for it in b.get("items", []):
            iq = "{}.{}".format(bname, it["name"])
            rows.append(_row("item:{}".format(iq), "item", it.get("itemType")))
            for suffix, pred, render in _ITEM_PROPERTY_ROWS:
                if pred(it):
                    rows.append(_row("property:{}.{}".format(iq, suffix),
                                     "property", render(it)))
            for tg in it.get("triggers", []):
                rows.append(_row("trigger:{}.{}".format(iq, tg["name"]),
                                 "trigger", tg.get("bodyShape")))
        for tg in b.get("triggers", []):
            rows.append(_row("trigger:{}.{}".format(bname, tg["name"]),
                             "trigger", tg.get("bodyShape")))

    for tg in spec.get("formTriggers", []):
        rows.append(_row("trigger:FORM.{}".format(tg["name"]), "trigger",
                         tg.get("bodyShape")))

    simple_sections = (
        ("relations", "relation", lambda e: e.get("joinCondition")),
        ("lovs", "lov", lambda e: e.get("query")),
        ("recordGroups", "record-group", lambda e: e.get("query")),
        ("alerts", "alert", lambda e: e.get("alertStyle")),
        ("programUnits", "program-unit", lambda e: e.get("programUnitType")),
        ("attachedLibraries", "attached-library", lambda e: e.get("libraryLocation")),
        ("visualAttributes", "visual-attribute", None),
        ("objectGroups", "object-group", None),
        ("windows", "window", lambda e: e.get("title")),
        ("canvases", "canvas", lambda e: e.get("canvasType")),
        ("tabPages", "tab-page", lambda e: e.get("canvas")),
        ("referencedDbObjects", "db-object", lambda e: e.get("paramShape")),
    )
    for section, kind, render in simple_sections:
        for e in spec.get(section) or []:
            rows.append(_row("{}:{}".format(kind, e["name"]), kind,
                             render(e) if render else None))

    # only structural frames / role-carrying static text gate; decoration is
    # dropped by rule (graphics-decoration) and needs no row.
    for fr in spec.get("frames") or []:
        if fr.get("enclosesItems"):
            rows.append(_row("frame:{}".format(fr["name"]), "frame",
                             "{} items".format(len(fr.get("enclosedItems") or []))))
    for st in spec.get("staticText") or []:
        if st.get("role") in ("heading", "prose"):
            rows.append(_row("static-text:{}".format(st["name"]), "static-text",
                             "{}: {}".format(st.get("role"),
                                             (st.get("text") or "")[:40])))

    for d in spec.get("dependencies") or []:
        rows.append(_row("dependency:{}:{}".format(d["type"], d["name"]),
                         "dependency", d.get("via")))

    return rows


def cross_check(spec, rows, err=sys.stderr):
    """Honesty check: ledger trigger/item rows must match the spec census."""
    by_tag = (spec.get("census") or {}).get("byTag") or {}
    counts = {"trigger": 0, "item": 0}
    for r in rows:
        if r["kind"] in counts:
            counts[r["kind"]] += 1
    ok = True
    for kind, tag in (("trigger", "Trigger"), ("item", "Item")):
        expected = by_tag.get(tag)
        if expected is not None and counts[kind] != expected:
            err.write("census mismatch: {} {} rows vs census {}={}\n".format(
                counts[kind], kind, tag, expected))
            ok = False
    return ok


def merge(rows, prior_rows):
    prior = {r["id"]: r for r in prior_rows}
    carried = 0
    for r in rows:
        p = prior.get(r["id"])
        if p and p.get("disposition"):
            for k in ("disposition", "as", "reason", "decidedBy", "ref", "rule"):
                r[k] = p.get(k)
            carried += 1
    return carried


def unassigned(rows):
    return [r for r in rows if not r.get("disposition")]


def validate(rows):
    bad = [r for r in rows
           if r.get("disposition") and r["disposition"] not in DISPOSITIONS]
    return bad


def to_markdown(ledger):
    rows = ledger["rows"]
    todo = unassigned(rows)
    L = ["# Disposition ledger — {}".format(ledger.get("form")), ""]
    L.append("> Generated from `{}` (spec census: {} elements). One row per form"
             " object + per decision-prone mapped property. The to-issues gate"
             " refuses to cut build tickets while any row is unassigned"
             " (ADR-0015).".format(ledger.get("source"),
                                   ledger.get("censusTotal")))
    L.append("")
    L.append("**{} rows — {} assigned, {} unassigned.**".format(
        len(rows), len(rows) - len(todo), len(todo)))
    L.append("")
    if todo:
        L.append("## UNASSIGNED (gate fails)")
        L.append("")
        for r in todo:
            L.append("- `{}`{}".format(
                r["id"], " — {}".format(r["specValue"]) if r.get("specValue") else ""))
        L.append("")
    L.append("## Rows")
    L.append("")
    L.append("| Row | Spec value | Disposition | As / rule | Ref | Reason |")
    L.append("|---|---|---|---|---|---|")

    def cell(v):
        return str(v).replace("|", "\\|").replace("\n", " ") if v else "—"

    for r in rows:
        L.append("| `{}` | {} | {} | {} | {} | {} |".format(
            r["id"], cell(r.get("specValue")),
            r.get("disposition") or "**UNASSIGNED**",
            cell(r.get("as") or r.get("rule")), cell(r.get("ref")),
            cell(r.get("reason"))))
    L.append("")
    return "\n".join(L)


def main(argv=None):
    ap = argparse.ArgumentParser(
        description="Structural spec -> disposition ledger (the to-issues gate).")
    ap.add_argument("spec", help="path to the analyzer's spec.json")
    ap.add_argument("--out", help="write the ledger JSON here")
    ap.add_argument("--md", help="also write a human-readable markdown view")
    ap.add_argument("--merge", help="prior ledger.json whose dispositions carry forward")
    ap.add_argument("--check", action="store_true",
                    help="gate mode: exit 1 listing unassigned rows")
    args = ap.parse_args(argv)

    with open(args.spec, encoding="utf-8") as fh:
        spec = json.load(fh)

    rows = build_rows(spec)
    cross_check(spec, rows)

    if args.merge:
        with open(args.merge, encoding="utf-8") as fh:
            carried = merge(rows, json.load(fh).get("rows", []))
        sys.stderr.write("carried {} dispositions forward\n".format(carried))

    bad = validate(rows)
    if bad:
        sys.stderr.write("invalid dispositions (not in {}):\n".format(
            "/".join(DISPOSITIONS)))
        for r in bad:
            sys.stderr.write("  {}: {}\n".format(r["id"], r["disposition"]))
        return 2

    ledger = {
        "form": (spec.get("form") or {}).get("name"),
        "source": args.spec,
        "censusTotal": (spec.get("census") or {}).get("totalElements"),
        "grain": "form-object + decision-prone mapped properties",
        "rows": rows,
    }

    if args.out:
        with open(args.out, "w", encoding="utf-8") as fh:
            fh.write(json.dumps(ledger, indent=2, ensure_ascii=False))
        sys.stderr.write("Wrote ledger -> {}\n".format(args.out))
    if args.md:
        with open(args.md, "w", encoding="utf-8") as fh:
            fh.write(to_markdown(ledger))
        sys.stderr.write("Wrote markdown view -> {}\n".format(args.md))

    todo = unassigned(rows)
    if args.check:
        if todo:
            sys.stderr.write(
                "GATE FAILS: {} unassigned row(s) — every row needs a disposition"
                " (migrated-as / re-decided / deferred / obsolete) before build"
                " tickets are cut:\n".format(len(todo)))
            for r in todo:
                sys.stderr.write("  {}\n".format(r["id"]))
            return 1
        sys.stderr.write("GATE PASSES: all {} rows assigned.\n".format(len(rows)))
    elif not args.out and not args.md:
        print(json.dumps(ledger, indent=2, ensure_ascii=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

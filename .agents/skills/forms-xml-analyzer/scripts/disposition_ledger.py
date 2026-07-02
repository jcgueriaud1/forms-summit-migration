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
        [--check-refs]                # exit 1 on dead/rotten tracker refs

Regenerating is safe: --merge carries dispositions forward by row id, so a
spec change only adds/removes rows, never wipes decisions.

--check-refs guards ledger rot (needs the `gh` CLI and network): every `#N`
cited in a row's `ref` must exist on the tracker, a `deferred` row must cite
at least one ref, and a deferred ref must still be OPEN — a closed deferred
ticket means the work happened, so the row should flip to `migrated-as` (or
be re-deferred), not keep pointing at a finished ticket.

Stdlib only. Python 3.9+.
"""
import argparse
import json
import re
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


def extract_refs(text):
    """Tracker numbers cited in a ref string ('#40 #45', 'PRD #38 / #43')."""
    return sorted({int(n) for n in re.findall(r"#(\d+)", text or "")})


def check_refs(rows, err=sys.stderr):
    """Ref-liveness gate: dispositions must cite live tracker items.

    Uses `gh api repos/{owner}/{repo}/issues/N` (resolves issues AND pull
    requests, and the owner/repo of the current directory's remote).
    Findings, each a failure:
    - a cited #N that does not exist on the tracker;
    - a `deferred` row with no ref at all (deferred means *tracked*);
    - a `deferred` ref that is closed (deliver the row or re-defer).
    Returns True when clean.
    """
    import subprocess
    numbers = sorted({n for r in rows for n in extract_refs(r.get("ref"))})
    state = {}
    for n in numbers:
        try:
            out = subprocess.run(
                ["gh", "api", "repos/{owner}/{repo}/issues/" + str(n)],
                capture_output=True, text=True, check=False)
        except FileNotFoundError:
            err.write("--check-refs needs the `gh` CLI on PATH\n")
            return False
        state[n] = (json.loads(out.stdout).get("state")
                    if out.returncode == 0 else None)

    findings = []
    for r in rows:
        refs = extract_refs(r.get("ref"))
        if r.get("disposition") == "deferred" and not refs:
            findings.append("{}: deferred without a tracking ref".format(r["id"]))
        for n in refs:
            if state.get(n) is None:
                findings.append("{}: ref #{} does not exist".format(r["id"], n))
            elif r.get("disposition") == "deferred" and state[n] != "open":
                findings.append(
                    "{}: deferred ref #{} is closed — flip the row to"
                    " migrated-as or re-defer it".format(r["id"], n))
    if findings:
        err.write("REF CHECK FAILS: {} finding(s):\n".format(len(findings)))
        for f in findings:
            err.write("  {}\n".format(f))
        return False
    err.write("REF CHECK PASSES: {} refs across {} rows are live.\n".format(
        len(numbers), sum(1 for r in rows if extract_refs(r.get("ref")))))
    return True


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
    ap.add_argument("--check-refs", action="store_true",
                    help="ref-liveness gate: exit 1 on dead refs, unref'd or"
                         " closed deferred rows (needs the gh CLI)")
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

    failed = False
    if args.check_refs and not check_refs(rows):
        failed = True
    todo = unassigned(rows)
    if args.check:
        if todo:
            sys.stderr.write(
                "GATE FAILS: {} unassigned row(s) — every row needs a disposition"
                " (migrated-as / re-decided / deferred / obsolete) before build"
                " tickets are cut:\n".format(len(todo)))
            for r in todo:
                sys.stderr.write("  {}\n".format(r["id"]))
            failed = True
        else:
            sys.stderr.write("GATE PASSES: all {} rows assigned.\n".format(len(rows)))
    elif not args.check_refs and not args.out and not args.md:
        print(json.dumps(ledger, indent=2, ensure_ascii=False))
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())

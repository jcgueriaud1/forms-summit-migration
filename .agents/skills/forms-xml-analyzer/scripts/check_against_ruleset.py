#!/usr/bin/env python3
"""End-to-end contract check: an analyzer spec.json feeds migration-rules.yaml.

Asserts that the spec carries the fields that make specific ruleset rules fire,
and that each referenced rule id actually exists in the YAML. YAML-lib-free
(reads rule ids and `when:` operands by regex), so it runs on a bare Python 3.9.

Usage:
    python3 check_against_ruleset.py <spec.json> <migration-rules.yaml>

Exit code 0 = all contracts hold; 1 = at least one failed.
"""
import json
import re
import sys


def load_rule_ids(yaml_path):
    ids = set()
    with open(yaml_path, encoding="utf-8") as fh:
        for line in fh:
            m = re.match(r"\s*-\s*id:\s*(\S+)", line)
            if m:
                ids.add(m.group(1))
    return ids


def find_block(spec, name):
    return next((b for b in spec["blocks"] if b["name"] == name), None)


def find_item(block, name):
    if not block:
        return None
    return next((it for it in block["items"] if it["name"] == name), None)


def find_item_trigger(block, item_name, trig_name):
    it = find_item(block, item_name)
    if not it:
        return None
    return next((t for t in it["triggers"] if t["name"] == trig_name), None)


def find_block_trigger(block, trig_name):
    if not block:
        return None
    return next((t for t in block["triggers"] if t["name"] == trig_name), None)


def find_relation(spec, name):
    return next((r for r in spec["relations"] if r["name"] == name), None)


def main(argv=None):
    argv = argv or sys.argv[1:]
    if len(argv) != 2:
        sys.stderr.write(__doc__)
        return 2
    spec = json.load(open(argv[0], encoding="utf-8"))
    rule_ids = load_rule_ids(argv[1])

    s_ord = find_block(spec, "S_ORD")
    s_item = find_block(spec, "S_ITEM")
    s_inv = find_block(spec, "S_INVENTORY")

    # (description, condition, rule id the fact should route to)
    checks = [
        ("form commitStyle = COMMIT_FORM",
         spec["form"]["commitStyle"] == "COMMIT_FORM", "transaction-commit-form"),
        ("S_ORD is queryable master DB block",
         bool(s_ord) and s_ord["relationRole"] == "master" and s_ord["databaseBlock"]
         and s_ord["queryable"], "block-master-queryable"),
        ("S_ORD lockingMode default-injected (pessimistic)",
         bool(s_ord) and s_ord["lockingMode"]["injected"] is True,
         "concurrency-pessimistic-to-scn"),
        ("S_ITEM is a detail block",
         bool(s_item) and s_item["relationRole"] == "detail", "block-detail"),
        ("S_ITEM CRUD insert+update+delete allowed (editable detail Grid)",
         bool(s_item) and s_item["crud"]["insertAllowed"] and s_item["crud"]["updateAllowed"]
         and s_item["crud"]["deleteAllowed"], "block-detail"),
        ("S_ITEM.ITEM_TOTAL is read-only (updateAllowed=false)",
         (find_item(s_item, "ITEM_TOTAL") or {}).get("editability", {}).get("updateAllowed")
         is False, "item-summary"),
        ("S_INVENTORY is a deferred/auto-query detail",
         bool(s_inv) and s_inv["relationRole"] == "detail", "block-detail-readonly"),
        ("S_ITEM.PRODUCT_ID lovRole = primary",
         (find_item(s_item, "PRODUCT_ID") or {}).get("lovRole") == "primary",
         "text-item-lov"),
        ("S_ITEM.ITEM_TOTAL has a Formula",
         bool((find_item(s_item, "ITEM_TOTAL") or {}).get("formula")),
         "text-item-calculated"),
        ("S_ITEM.TOTAL has a SummaryFunction",
         bool((find_item(s_item, "TOTAL") or {}).get("summaryFunction")),
         "item-summary"),
        ("S_ITEM.PRODUCT_IMAGE imageSource = file",
         (find_item(s_item, "PRODUCT_IMAGE") or {}).get("imageSource") == "file",
         "image-item-file-sourced"),
        ("S_ITEM WHEN-VALIDATE-ITEM bodyShape = select-into",
         (find_item_trigger(s_item, "PRODUCT_ID", "WHEN-VALIDATE-ITEM") or {}).get("bodyShape")
         == "select-into", "trigger-when-validate-item-default"),
        ("S_ORD WHEN-VALIDATE-RECORD bodyShape = raise-only",
         (find_block_trigger(s_ord, "WHEN-VALIDATE-RECORD") or {}).get("bodyShape")
         == "raise-only", "trigger-when-validate-record"),
        ("relation S_ORD_S_ITEM DeleteRecord = Non Isolated",
         (find_relation(spec, "S_ORD_S_ITEM") or {}).get("deleteRecord") == "Non Isolated",
         "delete-cascade-guard"),
        ("relation S_ITEM_S_INVENTORY DeleteRecord = Isolated",
         (find_relation(spec, "S_ITEM_S_INVENTORY") or {}).get("deleteRecord") == "Isolated",
         "delete-isolated"),
        ("GET_PRODUCT_IMAGE surfaced as a DB-callable candidate",
         any(r["name"].upper() == "GET_PRODUCT_IMAGE" for r in spec["referencedDbObjects"]),
         "db-call-direct"),
    ]

    failures = 0
    for desc, ok, rid in checks:
        rule_exists = rid in rule_ids
        status = "PASS" if (ok and rule_exists) else "FAIL"
        if status == "FAIL":
            failures += 1
        note = "" if rule_exists else "  [rule id '%s' NOT FOUND in YAML]" % rid
        print("  [%s] %-55s -> %s%s" % (status, desc, rid, note))

    print("\n%d/%d contracts hold." % (len(checks) - failures, len(checks)))
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())

package com.example.summit.domain;

/**
 * Thrown when a master delete is vetoed because detail rows still exist — the
 * <strong>Non-Isolated</strong> cascade-delete guard for the {@code S_ORD_S_ITEM} relation
 * ({@code delete-cascade-guard} in {@code migration-rules.yaml}, issue #45).
 *
 * <p>Forms enforced this with the {@code Non Isolated} {@code DeleteRecord} property (its generated
 * {@code ON-CHECK-DELETE-MASTER} trigger is the mirror, not the source): deleting an {@code S_ORD}
 * with {@code S_ITEM} lines was blocked so detail records could not be orphaned. The migrated app
 * raises this instead, and the {@code @Service} delete is the gate — the same "commit/service is the
 * gate" stance ADR-0002 takes for validation.
 */
public class CascadeDeleteBlockedException extends RuntimeException {

    public CascadeDeleteBlockedException(String message) {
        super(message);
    }
}

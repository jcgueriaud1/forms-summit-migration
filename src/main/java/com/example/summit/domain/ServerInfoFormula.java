package com.example.summit.domain;

/**
 * Domain layer — owns the <em>formula</em> (ADR-0001). Pure, side-effect-free,
 * unit-testable: no Spring, no Vaadin, no database. For the scaffold the "formula"
 * is a trivial presentation label; this is the seam where Forms {@code WHEN-VALIDATE}
 * PL/SQL (e.g. {@code recalculateTotal()}) will land for the real constructs.
 */
public final class ServerInfoFormula {

    private ServerInfoFormula() {
    }

    /** A human-readable label for the connected database, e.g. "Oracle AI Database 26ai Free (23.26.2.0.0)". */
    public static String label(ServerInfo info) {
        if (info == null || info.product() == null || info.product().isBlank()) {
            return "Oracle Database (version unknown)";
        }
        String product = info.product().trim();
        String version = info.version() == null ? "" : info.version().trim();
        return version.isBlank() ? product : product + " (" + version + ")";
    }
}

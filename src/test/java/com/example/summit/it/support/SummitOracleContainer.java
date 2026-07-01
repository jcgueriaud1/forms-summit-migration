package com.example.summit.it.support;

import org.testcontainers.oracle.OracleContainer;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

/**
 * The single Summit Oracle container for the whole test JVM.
 *
 * <p>A real Summit Oracle (the gvenzl image, same family the app runs against in
 * {@code compose.yaml}), seeded with the slice schema + data from
 * {@code db/summit-slice.sql} so the unqualified SQL the repositories run resolves
 * identically.
 *
 * <p>Started once on first touch and reaped by Testcontainers' Ryuk at JVM exit. Both
 * test harnesses — the repository/service harness ({@link com.example.summit.data.SummitDbIT})
 * and the UI harness ({@link OracleSpringPlaywrightIT}) — point at this same instance, so
 * a full {@code mvn verify} pays the Oracle boot cost exactly once rather than booting a
 * separate container per hierarchy.
 */
public final class SummitOracleContainer {

    /** The shared, already-started container. */
    public static final OracleContainer INSTANCE =
            new OracleContainer("gvenzl/oracle-free:23-slim")
                    .withInitScript("db/summit-slice.sql")
                    // First Oracle boot is ~40s standalone, but the Vaadin frontend build
                    // runs alongside the first UI test and starves CPU — well past the 60s
                    // default. Give it generous headroom rather than fail flakily.
                    .withStartupTimeout(Duration.ofMinutes(5));

    static {
        INSTANCE.start();
        createAuditTrigger();
    }

    /**
     * Creates the {@code AUDIT_S_ITEM} trigger (AFTER INSERT ON {@code s_item} FOR EACH ROW) once the
     * container is up. It is a PL/SQL block, which the {@code withInitScript} SQL splitter cannot
     * parse (its {@code BEGIN ... END;} body carries inner statement terminators), so it is created
     * here as a single JDBC statement instead. Its target table {@code s_item_audit} is seeded by the
     * init script. This makes the DB behaviour real for every harness: a whole-graph commit's line
     * inserts fire the trigger inside the same transaction, unchanged and with zero Java (issue #19).
     */
    private static void createAuditTrigger() {
        try (Connection connection = INSTANCE.createConnection("");
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE OR REPLACE TRIGGER audit_s_item
                    AFTER INSERT ON s_item
                    FOR EACH ROW
                    BEGIN
                      INSERT INTO s_item_audit VALUES (1);
                    END;""");
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to create the AUDIT_S_ITEM trigger", e);
        }
    }

    private SummitOracleContainer() {
    }
}

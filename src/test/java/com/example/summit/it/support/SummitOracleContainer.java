package com.example.summit.it.support;

import org.testcontainers.oracle.OracleContainer;

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
    }

    private SummitOracleContainer() {
    }
}

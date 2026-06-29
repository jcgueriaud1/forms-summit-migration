package com.example.summit.data;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;
import org.testcontainers.oracle.OracleContainer;

import oracle.jdbc.OracleDriver;

import java.time.Duration;

/**
 * Reusable integration-test harness: a real Summit Oracle in a container (the gvenzl
 * image, same family the app runs against), seeded with the slice schema + data from
 * {@code db/summit-slice.sql}.
 *
 * <p>The container is a JVM-wide singleton — started once on first use and reused by
 * every subclass (Testcontainers' Ryuk reaps it at JVM exit), so the whole repository
 * test suite pays the Oracle boot cost once. Later repository slices extend this by
 * subclassing and adding rows/tables to the seed script.
 */
public abstract class SummitDbIT {

    private static final OracleContainer SUMMIT_DB =
            new OracleContainer("gvenzl/oracle-free:23-slim")
                    .withInitScript("db/summit-slice.sql")
                    // A full `mvn verify` runs this repository harness in the same JVM as the
                    // UI harness (OracleSpringPlaywrightIT), so a second Oracle is booting
                    // alongside this one and the Vaadin frontend build — well past the 60s
                    // default. Give it the same generous headroom rather than fail flakily.
                    .withStartupTimeout(Duration.ofMinutes(5));

    private static final JdbcClient JDBC_CLIENT;

    static {
        SUMMIT_DB.start();
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new OracleDriver(),
                SUMMIT_DB.getJdbcUrl(),
                SUMMIT_DB.getUsername(),
                SUMMIT_DB.getPassword());
        JDBC_CLIENT = JdbcClient.create(dataSource);
    }

    /** A {@link JdbcClient} bound to the seeded Summit container, for wiring repositories under test. */
    protected static JdbcClient jdbcClient() {
        return JDBC_CLIENT;
    }
}

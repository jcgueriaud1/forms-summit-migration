package com.example.summit.data;

import com.example.summit.it.support.SummitOracleContainer;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.SimpleDriverDataSource;

import oracle.jdbc.OracleDriver;

/**
 * Reusable integration-test harness: a real Summit Oracle in a container (the gvenzl
 * image, same family the app runs against), seeded with the slice schema + data from
 * {@code db/summit-slice.sql}.
 *
 * <p>Points at the shared {@link SummitOracleContainer} — a JVM-wide singleton also used
 * by the UI harness ({@code OracleSpringPlaywrightIT}), so a full {@code mvn verify} boots
 * Oracle once rather than once per hierarchy. Later repository slices extend this by
 * subclassing and adding rows/tables to the seed script.
 */
public abstract class SummitDbIT {

    private static final JdbcClient JDBC_CLIENT;

    static {
        SimpleDriverDataSource dataSource = new SimpleDriverDataSource(
                new OracleDriver(),
                SummitOracleContainer.INSTANCE.getJdbcUrl(),
                SummitOracleContainer.INSTANCE.getUsername(),
                SummitOracleContainer.INSTANCE.getPassword());
        JDBC_CLIENT = JdbcClient.create(dataSource);
    }

    /** A {@link JdbcClient} bound to the seeded Summit container, for wiring repositories under test. */
    protected static JdbcClient jdbcClient() {
        return JDBC_CLIENT;
    }
}

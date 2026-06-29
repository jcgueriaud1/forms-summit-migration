package com.example.summit.it.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.oracle.OracleContainer;

import java.time.Duration;

/**
 * Base for UI integration tests that drive the running app in a real browser against a
 * real Oracle. Boots the Spring app on a random port (the DramaFinder plumbing in
 * {@link SpringPlaywrightIT} wires Playwright to that port) and repoints the datasource
 * at a throwaway gvenzl Oracle container — the same image family the app runs against in
 * {@code compose.yaml}, so the unqualified SQL the repositories run resolves identically.
 *
 * <p>The container is a JVM-wide singleton: started once on first subclass and reused by
 * every UI test (Testcontainers' Ryuk reaps it at JVM exit), so the whole suite pays the
 * Oracle boot cost once. Mirrors the repository-test harness in
 * {@code SummitDbIntegrationTest}, but wired into the Spring context rather than a
 * standalone {@code JdbcClient}.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class OracleSpringPlaywrightIT extends SpringPlaywrightIT {

    private static final OracleContainer SUMMIT_DB =
            new OracleContainer("gvenzl/oracle-free:23-slim")
                    // First Oracle boot is ~40s standalone, but the Vaadin frontend build
                    // runs alongside the first UI test and starves CPU — well past the 60s
                    // default. Give it generous headroom rather than fail flakily.
                    .withStartupTimeout(Duration.ofMinutes(5));

    static {
        SUMMIT_DB.start();
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SUMMIT_DB::getJdbcUrl);
        registry.add("spring.datasource.username", SUMMIT_DB::getUsername);
        registry.add("spring.datasource.password", SUMMIT_DB::getPassword);
    }
}

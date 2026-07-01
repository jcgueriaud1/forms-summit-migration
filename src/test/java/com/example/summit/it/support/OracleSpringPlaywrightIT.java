package com.example.summit.it.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Base for UI integration tests that drive the running app in a real browser against a
 * real Oracle. Boots the Spring app on a random port (the DramaFinder plumbing in
 * {@link SpringPlaywrightIT} wires Playwright to that port) and repoints the datasource
 * at the shared {@link SummitOracleContainer} — the same image family the app runs
 * against in {@code compose.yaml}, so the unqualified SQL the repositories run resolves
 * identically. The container is seeded with the slice schema + data, so views that read
 * Summit tables have data to render (harmless to views that don't, e.g. DemoView reads
 * only the version banner).
 *
 * <p>{@link SummitOracleContainer} is a JVM-wide singleton shared with the
 * repository/service harness ({@code SummitDbIT}), so a full {@code mvn verify} boots
 * Oracle once rather than once per hierarchy.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public abstract class OracleSpringPlaywrightIT extends SpringPlaywrightIT {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SummitOracleContainer.INSTANCE::getJdbcUrl);
        registry.add("spring.datasource.username", SummitOracleContainer.INSTANCE::getUsername);
        registry.add("spring.datasource.password", SummitOracleContainer.INSTANCE::getPassword);
    }
}

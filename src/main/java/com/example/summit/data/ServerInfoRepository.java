package com.example.summit.data;

import com.example.summit.domain.ServerInfo;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Data layer — the persistence boundary, Spring {@link JdbcClient} (PLAN: chosen
 * over jOOQ to avoid Oracle-dialect licensing). For the real POC this is where the
 * lazy-paged {@code S_ORD} query and the {@code ORA_ROWSCN} read/check live.
 */
@Repository
public class ServerInfoRepository {

    private final JdbcClient jdbcClient;

    public ServerInfoRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * Reads the Oracle product banner and version. Uses {@code PRODUCT_COMPONENT_VERSION}
     * (publicly readable by any schema) rather than {@code V$VERSION} (needs a SELECT
     * grant the non-privileged app user does not have) — same datum, correct posture.
     * The {@code %Database%} match tolerates the product naming differing across
     * editions (e.g. "Oracle Database 23ai Free" vs "Oracle AI Database 26ai Free").
     */
    public ServerInfo fetchServerInfo() {
        return jdbcClient
                .sql("""
                        SELECT product, version_full
                        FROM product_component_version
                        WHERE product LIKE '%Database%'
                        FETCH FIRST 1 ROW ONLY
                        """)
                .query((rs, rowNum) -> new ServerInfo(rs.getString("product"), rs.getString("version_full")))
                .single();
    }
}

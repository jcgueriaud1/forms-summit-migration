package com.example.summit.data;

import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Data layer for {@code S_PRODUCT} — the two reads behind the ORDERS product LOV (issue #46).
 * All SQL lives here; the view, presenter, and service hold none. Table names are
 * <strong>unqualified</strong> for the same reason {@link OrderRepository} gives — the app and
 * the IT harness both resolve them against their own {@code summit} schema.
 *
 * <p>The two reads are deliberately separate, mirroring the Forms constructs they migrate: the
 * {@code PRODUCTS_LOV} record group is a <em>picker</em> that lists {@code (id, name)}, while the
 * {@code WHEN-VALIDATE-ITEM} defaulter is a per-id {@code SELECT … INTO} that fetches the columns a
 * chosen product defaults onto the line. Keeping them apart keeps the pick and the re-default
 * independent — the re-default fires on any {@code product_id} change, not only a LOV pick.
 */
@Repository
public class ProductRepository {

    private final JdbcClient jdbcClient;

    public ProductRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * The {@code PRODUCTS_LOV} record-group query — {@code select id, name from s_product order by
     * name} verbatim ({@code orders-form-spec.md} §LOV), the reference list feeding the LOV's
     * DataProvider. Ordered by name, as the Forms popup listed it.
     */
    public List<Product> findAll() {
        return jdbcClient
                .sql("SELECT id, name FROM s_product ORDER BY name")
                .query((rs, rowNum) -> new Product(rs.getLong("id"), rs.getString("name")))
                .list();
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} defaulter's {@code SELECT … INTO} — {@code SELECT name,
     * suggested_whlsl_price FROM s_product WHERE id = :id} — returning the columns a chosen product
     * defaults onto the line. An id that is not a real product returns {@link Optional#empty()}: the
     * {@code NO_DATA_FOUND} branch the defaulter turns into a rejection (the Forms
     * {@code RAISE FORM_TRIGGER_FAILURE}).
     */
    public Optional<ProductDefaults> findProductDefaults(long productId) {
        return jdbcClient
                .sql("SELECT name, suggested_whlsl_price FROM s_product WHERE id = :id")
                .param("id", productId)
                .query((rs, rowNum) -> new ProductDefaults(
                        rs.getString("name"),
                        rs.getObject("suggested_whlsl_price", BigDecimal.class)))
                .optional();
    }
}

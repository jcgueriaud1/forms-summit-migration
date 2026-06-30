package com.example.summit.data;

import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Data layer for {@code S_PRODUCT} — the two reads the {@code PRODUCTS_LOV} and its
 * {@code WHEN-VALIDATE-ITEM} defaulter need ({@code orders-form-spec.md}). All SQL lives here;
 * the view and presenter hold none. Table names are unqualified for the same reason as
 * {@link OrderRepository}: the app connects as the schema owner.
 */
@Repository
public class ProductRepository {

    private final JdbcClient jdbcClient;

    public ProductRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * The {@code PRODUCTS_LOV} query verbatim — {@code SELECT id, name FROM s_product ORDER BY name}
     * — feeding the lookup ComboBox's DataProvider. Only the two columns the LOV retrieves; the
     * popup shows {@code id — name} and writes back only the id.
     */
    public List<Product> findAll() {
        return jdbcClient
                .sql("SELECT id, name FROM s_product ORDER BY name")
                .query((rs, rowNum) -> new Product(rs.getLong("id"), rs.getString("name")))
                .list();
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} defaulter lookup — {@code SELECT name, suggested_whlsl_price
     * FROM s_product WHERE id = :id}. A present result re-defaults the line's {@code description}
     * and {@code price}; an {@link Optional#empty() empty} one is the Forms {@code NO_DATA_FOUND}
     * branch — an invalid (non-existent) product id, which the caller rejects.
     */
    public Optional<ProductDefaults> findProductDefaults(long productId) {
        return jdbcClient
                .sql("SELECT name, suggested_whlsl_price FROM s_product WHERE id = :id")
                .param("id", productId)
                .query((rs, rowNum) -> new ProductDefaults(
                        rs.getString("name"),
                        rs.getBigDecimal("suggested_whlsl_price")))
                .optional();
    }
}

package com.example.summit.data;

import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves {@code ProductRepository} against a real Summit Oracle (Testcontainers, via the shared
 * {@code SummitDbIT} harness) — the two reads behind the ORDERS product LOV (issue #46).
 *
 * <p>The headline is the {@code WHEN-VALIDATE-ITEM} defaulter's read
 * ({@link ProductRepository#findProductDefaults}): given a product id it returns the {@code name} and
 * {@code suggested_whlsl_price} a chosen product defaults onto the line, and an empty
 * {@link Optional} for an id that is not a real product (the {@code NO_DATA_FOUND} case). The
 * {@code PRODUCTS_LOV} reference read ({@link ProductRepository#findAll}) is asserted to come back
 * ordered by name, exactly as the record-group query {@code select id, name from s_product order by
 * name} specifies.
 */
class ProductRepositoryIT extends SummitDbIT {

    private final ProductRepository repository = new ProductRepository(jdbcClient());

    @Test
    void findProductDefaultsReturnsNameAndSuggestedPrice() {
        // 41010 is "Ski Boots" at a 50.00 suggested wholesale price — the columns the defaulter
        // maps onto the line's description and price.
        ProductDefaults defaults = repository.findProductDefaults(41010).orElseThrow();

        assertEquals("Ski Boots", defaults.name());
        assertEquals(0, new BigDecimal("50.00").compareTo(defaults.suggestedPrice()));
    }

    @Test
    void findProductDefaultsIsEmptyForAnUnknownProduct() {
        // No such product -> the NO_DATA_FOUND branch: an empty Optional the defaulter rejects.
        assertTrue(repository.findProductDefaults(999999).isEmpty());
    }

    @Test
    void findAllReturnsTheLovReferenceListOrderedByName() {
        List<Product> products = repository.findAll();

        // The record-group query orders by name: Bunny Boot, Pro Ski Boot, Ski Boots, Ski Pole.
        assertEquals(
                List.of(10011L, 30421L, 41010L, 20108L),
                products.stream().map(Product::id).toList());
        assertEquals("Bunny Boot", products.getFirst().name());
    }
}

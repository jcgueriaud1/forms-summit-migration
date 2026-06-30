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
 * Proves {@code ProductRepository} — the {@code PRODUCTS_LOV} reads (issue #17) — against a real
 * Summit Oracle (Testcontainers). The headline: {@code findProductDefaults} returns a product's
 * {@code name} and {@code suggested_whlsl_price} (the {@code WHEN-VALIDATE-ITEM} defaulter's
 * {@code SELECT … INTO}), and an unknown id comes back empty — the Forms {@code NO_DATA_FOUND}
 * branch that rejects an invalid product. {@code findAll} returns the LOV list ordered by name.
 */
class ProductRepositoryIT extends SummitDbIT {

    private final ProductRepository repository = new ProductRepository(jdbcClient());

    @Test
    void findProductDefaultsReturnsNameAndSuggestedPrice() {
        ProductDefaults defaults = repository.findProductDefaults(41010).orElseThrow();

        assertEquals("Ski Boots", defaults.name());
        assertEquals(0, new BigDecimal("50.00").compareTo(defaults.suggestedPrice()));
    }

    @Test
    void findProductDefaultsIsEmptyForAnUnknownProduct() {
        // The NO_DATA_FOUND reject path: no such product, so no defaults to apply.
        Optional<ProductDefaults> defaults = repository.findProductDefaults(99999);

        assertTrue(defaults.isEmpty());
    }

    @Test
    void findAllReturnsTheLovItemsOrderedByName() {
        List<Product> products = repository.findAll();

        // Every catalog row, id + name, ordered by name (the PRODUCTS_LOV query verbatim).
        assertEquals(
                List.of("Bunny Boot", "Pro Ski Boot", "Ski Boots", "Ski Pole"),
                products.stream().map(Product::name).toList());
        assertEquals(41010L, products.stream()
                .filter(p -> "Ski Boots".equals(p.name())).findFirst().orElseThrow().id());
    }
}

package com.example.summit.presenter;

import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import com.example.summit.service.ProductService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test for the presenter's product-LOV orchestration (issue #17) — no Spring, no DB, no
 * browser. A stub {@link ProductService} stands in for the {@code S_PRODUCT} reads; the test drives
 * the {@code WHEN-VALIDATE-ITEM} defaulter the editable grid calls into: a known product re-defaults
 * the line's description and price, and an unknown id is the {@code NO_DATA_FOUND} reject that leaves
 * the line untouched so the view can reject it.
 */
class OrderPresenterProductTest {

    private static final Product BOOTS = new Product(41010, "Ski Boots");
    private static final Product POLE = new Product(20108, "Ski Pole");

    /** A presenter wired to a stub product service with two known products. */
    private static OrderPresenter presenter() {
        ProductService products = new ProductService(null) {
            @Override
            public List<Product> lookup() {
                return List.of(BOOTS, POLE);
            }

            @Override
            public Optional<ProductDefaults> findProductDefaults(long productId) {
                if (productId == 41010) {
                    return Optional.of(new ProductDefaults("Ski Boots", new BigDecimal("50.00")));
                }
                if (productId == 20108) {
                    return Optional.of(new ProductDefaults("Ski Pole", new BigDecimal("13.00")));
                }
                return Optional.empty();
            }
        };
        return new OrderPresenter(null, products);
    }

    @Test
    void productsAreTheLovList() {
        assertEquals(List.of(BOOTS, POLE), presenter().products());
    }

    @Test
    void applyingAKnownProductReDefaultsDescriptionAndPrice() {
        OrderPresenter presenter = presenter();
        EditableLine line = EditableLine.blank(1);

        Optional<ProductDefaults> applied = presenter.applyProductDefaults(line, 41010);

        assertTrue(applied.isPresent());
        assertEquals("Ski Boots", line.getProductName());
        assertEquals(0, new BigDecimal("50.00").compareTo(line.getPrice()));
    }

    @Test
    void anUnknownProductIdIsRejectedAndLeavesTheLineUntouched() {
        OrderPresenter presenter = presenter();
        EditableLine line = EditableLine.blank(1);

        Optional<ProductDefaults> applied = presenter.applyProductDefaults(line, 99999);

        // NO_DATA_FOUND: empty result, and the line keeps its blank description/price (nothing
        // half-applied) so the caller can reject the invalid id.
        assertTrue(applied.isEmpty());
        assertEquals("", line.getProductName());
        assertNull(line.getPrice());
    }
}

package com.example.summit.service;

import com.example.summit.data.ProductRepository;
import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for {@code S_PRODUCT} — the read side of the {@code PRODUCTS_LOV} and its
 * {@code WHEN-VALIDATE-ITEM} defaulter ({@code orders-form-spec.md}). Both operations are
 * read-only; it owns the transactional boundary and delegates the SQL to {@link ProductRepository}.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** The {@code PRODUCTS_LOV} item list (id + name, ordered by name) for the lookup ComboBox. */
    @Transactional(readOnly = true)
    public List<Product> lookup() {
        return productRepository.findAll();
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} defaulter: the {@code name} + {@code suggested_whlsl_price} a
     * product re-defaults onto a line, or {@link Optional#empty()} when no such product exists (the
     * {@code NO_DATA_FOUND} reject path — an invalid product id).
     */
    @Transactional(readOnly = true)
    public Optional<ProductDefaults> findProductDefaults(long productId) {
        return productRepository.findProductDefaults(productId);
    }
}

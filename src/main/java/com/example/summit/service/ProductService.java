package com.example.summit.service;

import com.example.summit.data.ProductRepository;
import com.example.summit.domain.Product;
import com.example.summit.domain.ProductDefaults;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for the ORDERS product LOV (issue #46) — the transactional boundary over the two
 * {@code S_PRODUCT} reads. Both are read-only: the {@code PRODUCTS_LOV} reference list and the
 * per-id {@code WHEN-VALIDATE-ITEM} defaulter lookup. The Forms {@code SELECT … INTO} lookup surfaces
 * here as the {@code @Service} call the rule {@code trigger-when-validate-item-default} prescribes;
 * turning its empty result into a validation error is the presenter's job.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /** The {@code PRODUCTS_LOV} reference list — {@code (id, name)} ordered by name, for the LOV picker. */
    @Transactional(readOnly = true)
    public List<Product> lookupProducts() {
        return productRepository.findAll();
    }

    /**
     * The {@code WHEN-VALIDATE-ITEM} defaulter lookup — the {@code name} and {@code suggested_whlsl_price}
     * a chosen product defaults onto the line, or {@link Optional#empty()} if {@code productId} is not a
     * real product (the {@code NO_DATA_FOUND} case the caller rejects).
     */
    @Transactional(readOnly = true)
    public Optional<ProductDefaults> findProductDefaults(long productId) {
        return productRepository.findProductDefaults(productId);
    }
}

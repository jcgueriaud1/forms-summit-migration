package com.example.summit.domain;

import java.math.BigDecimal;

/**
 * One {@code S_ITEM} line of an order, with its product <em>display name</em> already
 * resolved (the dissolved {@code description} POST-QUERY lookup). The name arrives joined
 * on the row from {@code OrderRepository.loadGraph}, so the grid never does a per-row
 * round-trip to {@code S_PRODUCT}.
 *
 * <p>{@code itemTotal} is the Forms {@code ITEM_TOTAL} <em>Formula</em> item, kept in the
 * domain (ADR-0001): {@code quantity_shipped * price} — deliberately {@code quantity_shipped},
 * not {@code quantity} (see {@code orders-form-spec.md}). Editing, the LOV-driven product
 * change and the order {@code TOTAL} summary arrive in later slices; for the read-only
 * detail grid this carries the line facts only.
 */
public record OrderLine(
        long itemId,
        long productId,
        String productName,
        BigDecimal price,
        long quantity,
        long quantityShipped) {

    /**
     * The {@code ITEM_TOTAL} formula: {@code quantity_shipped * price}. A null price (an
     * item not yet priced) totals to zero rather than throwing — the grid stays renderable.
     */
    public BigDecimal itemTotal() {
        return price == null ? BigDecimal.ZERO : price.multiply(BigDecimal.valueOf(quantityShipped));
    }

    /**
     * Whether this line has a product yet — {@code S_ITEM.product_id} is a mandatory FK, so a line
     * with none (a blank line the clerk added but never filled) is invalid and cannot be persisted.
     * The whole-graph commit gate uses this to refuse such a line cleanly rather than letting the
     * insert fail on the FK ({@code ORA-02291}).
     */
    public boolean hasProduct() {
        return productId > 0;
    }
}

package com.example.summit.domain;

import java.math.BigDecimal;

/**
 * One {@code S_ITEM} line of an order — a product, its price, and the ordered/shipped
 * quantities. Part of the {@link Order} aggregate: lines are loaded with their parent
 * header as one graph, never fetched per-row.
 *
 * <p>{@code description} is the product's display name, resolved from {@code S_PRODUCT}
 * by the join in {@link com.example.summit.data.OrderRepository#loadGraph} — the same
 * dissolution the header applies to its customer/sales-rep names. In Forms this name was
 * a POST-QUERY re-fetch per line; here it arrives with the row, so there is no N+1.
 *
 * <p>{@link #itemTotal()} is the {@code ITEM_TOTAL} formula as a <strong>pure domain
 * method</strong> (ADR-0001): {@code quantity_shipped * price} — deliberately shipped, not
 * ordered, quantity (per {@code orders-form-spec.md}). The read-only grid binds this
 * column to the method; the editable slice and the order-level {@code TOTAL} summary build
 * on the same formula in a later step.
 */
public record OrderLine(
        long itemId,
        long productId,
        String description,
        BigDecimal price,
        long quantity,
        long quantityShipped) {

    /**
     * The line total — {@code quantity_shipped * price}, the Forms {@code ITEM_TOTAL}
     * formula. A null price (the column is nullable) contributes zero rather than
     * throwing, so a half-entered line still renders.
     */
    public BigDecimal itemTotal() {
        if (price == null) {
            return BigDecimal.ZERO;
        }
        return price.multiply(BigDecimal.valueOf(quantityShipped));
    }

    /**
     * A copy of this line prepared to insert as a <em>new</em> {@code S_ITEM} — the pure encoding of
     * the Forms {@code KEY-DUPREC} clear-list (issue #45, {@code blockActionClassifiers}
     * {@code block-action-duplicate}). The Duplicate row action clones the selected line, then clears
     * exactly the fields the {@code KEY-DUPREC} body nulls so the copy commits as an insert rather
     * than colliding with the original: the {@code item_id} (reassigned {@code MAX+1} per order), the
     * {@code product_id}, its joined {@code description}, and the {@code price}. The
     * <em>quantities</em> are deliberately carried over — they are what the clerk is duplicating.
     */
    public OrderLine duplicateForInsert() {
        return new OrderLine(0L, 0L, null, null, quantity, quantityShipped);
    }
}

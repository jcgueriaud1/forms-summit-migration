package com.example.summit.domain;

import java.time.LocalDate;

/**
 * The {@code S_ORD} order header as a domain type — one loaded order. Its customer
 * and sales-rep <em>display names</em> are already resolved on the record: the two
 * Forms POST-QUERY lookups ({@code customer_name}, {@code sales_rep_name}) are
 * dissolved into the join in {@link com.example.summit.data.OrderRepository#loadGraph},
 * so nothing here triggers a per-field round-trip.
 *
 * <p>The read-only header tracer bullet (#39) carries the header facts only; the
 * {@code S_ITEM} line items join this aggregate in a later slice (#40).
 *
 * <p>{@code salesRepId} / {@code salesRepName} are nullable: {@code S_ORD.sales_rep_id}
 * is a nullable column, so an order with no assigned rep is a valid load.
 */
public record Order(
        long id,
        long customerId,
        String customerName,
        LocalDate dateOrdered,
        LocalDate dateShipped,
        Long salesRepId,
        String salesRepName,
        String paymentType,
        boolean orderFilled) {
}

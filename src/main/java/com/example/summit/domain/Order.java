package com.example.summit.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * The {@code S_ORD} order as a domain type — the <strong>aggregate root</strong> of the
 * {@code S_ORD}/{@code S_ITEM} master-detail spine. Its customer and sales-rep
 * <em>display names</em> are already resolved on the record: the two Forms POST-QUERY
 * lookups ({@code customer_name}, {@code sales_rep_name}) are dissolved into the join in
 * {@link com.example.summit.data.OrderRepository#loadGraph}, so nothing here triggers a
 * per-field round-trip.
 *
 * <p>The order carries its {@link OrderLine} list as the <strong>unit of load</strong>:
 * {@code loadGraph} brings back the header and its {@code S_ITEM} lines together, so the
 * header and the line-items grid are always coordinated on the same order
 * ({@code S_ORD_S_ITEM}) — selecting a different order reloads the whole graph.
 *
 * <p>{@code salesRepId} / {@code salesRepName} are nullable: {@code S_ORD.sales_rep_id}
 * is a nullable column, so an order with no assigned rep is a valid load. {@code lines}
 * is never null — an order with no line items carries an empty list.
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
        boolean orderFilled,
        List<OrderLine> lines) {
}

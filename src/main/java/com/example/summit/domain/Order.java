package com.example.summit.domain;

import java.time.LocalDate;
import java.util.List;

/**
 * The {@code S_ORD} order as a domain aggregate — one loaded order with its customer
 * and sales-rep <em>display names</em> already resolved (the dissolved Forms POST-QUERY
 * lookups) <strong>and its {@code S_ITEM} line items</strong>. The whole graph arrives
 * from {@code OrderRepository.loadGraph} on a single query path, so the view never does
 * per-field or per-line round-trips.
 *
 * <p>The {@link #lines()} list is the unit of load: the {@code S_ORD_S_ITEM} master-detail
 * coordination means header and detail are always loaded — and reloaded — together. An
 * order with no line items carries an empty list.
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

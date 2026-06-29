package com.example.summit.domain;

import java.time.LocalDate;

/**
 * The {@code S_ORD} order header as a domain type — one loaded order, with its
 * customer and sales-rep <em>display names</em> already resolved (the dissolved
 * Forms POST-QUERY lookups). The names arrive joined on the row from
 * {@code OrderRepository.loadGraph}, so the view never does per-field round-trips.
 *
 * <p>Line items ({@code S_ITEM}) join this aggregate in a later slice; for the
 * read-only header tracer bullet this carries the header facts only.
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

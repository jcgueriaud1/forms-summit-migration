package com.example.summit.domain;

import java.time.LocalDate;

/**
 * The Summit {@code S_ORD} header as a domain type — one order's header fields, loaded
 * read-only in this slice (issue #85). {@code customerName} and {@code salesRepName} are
 * the denormalized display names Forms filled with a {@code POST-QUERY} lookup; here they
 * arrive on the single {@code loadGraph} join (no N+1), so they are part of the loaded
 * header, not a second read.
 *
 * <p>Making these fields editable, their defaults, and the line-item graph are later
 * slices (V9+); this record carries only what the read-only header renders. {@code
 * paymentType} is kept as its raw column value ({@code CASH}/{@code CREDIT}) — the view
 * maps it to the radio's Cash/Credit labels (ADR-0013); {@code orderFilled} is the {@code
 * Y}/{@code N} check-box column as a boolean. {@code salesRepId}/{@code salesRepName} are
 * nullable — an order need not have a sales rep (e.g. Summit order 50003).
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

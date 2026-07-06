package com.example.summit.domain;

import java.time.LocalDate;

/**
 * The {@code S_ORD} order header as a domain aggregate — the unit of load for the
 * ORDERS view. In this slice (issue #85) it is read-only; later slices make it
 * editable, add the {@code S_ITEM} line graph, and add the {@code Order.total()} /
 * {@code Order.blank()} behaviours (PRD steps 2–5).
 *
 * <p>{@code customerName} and {@code salesRepName} are <em>display</em> fields, not
 * base-table columns: Forms filled them with the {@code POST-QUERY} lookup
 * ({@code S_CUSTOMER.name} / {@code S_EMP.last_name}). Here they arrive already joined
 * on the single {@code loadGraph} read, so the aggregate carries them — no N+1, no
 * round-trip (the POST-QUERY trigger is dissolved into the join).
 */
public record Order(
        Integer id,
        Integer customerId,
        String customerName,
        LocalDate dateOrdered,
        LocalDate dateShipped,
        Integer salesRepId,
        String salesRepName,
        PaymentType paymentType,
        boolean orderFilled) {
}

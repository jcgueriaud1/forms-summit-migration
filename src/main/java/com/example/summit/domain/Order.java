package com.example.summit.domain;

import java.time.LocalDate;

/**
 * The ORDERS master-block ({@code S_ORD}) header as a domain type. It carries the
 * base-table columns <em>and</em> the two lookup names ({@code customerName},
 * {@code salesRepName}) that Forms filled with the {@code S_ORD.POST-QUERY}
 * select-into triggers. On the web those lookups are dissolved into a single
 * {@code loadGraph} read whose SQL joins {@code S_CUSTOMER} and {@code S_EMP}, so the
 * names arrive with the row — no N+1, no POST-QUERY round-trip (ledger:
 * {@code trigger:S_ORD.POST-QUERY} migrated-as loadGraph join).
 *
 * <p>{@code salesRepId}/{@code salesRepName} are nullable: an order need not have a
 * sales rep (the {@code S_ORD.SALES_REP_ID} FK is nullable). {@code orderFilled}
 * carries the {@code S_ORD.ORDER_FILLED} {@code Y}/{@code N} check-box column as a
 * boolean (checkedValue {@code Y}), so the read-only header renders it as a
 * {@code Checkbox} rather than flattening it to text (ADR-0013).
 */
public record Order(
        Long id,
        Long customerId,
        String customerName,
        LocalDate dateOrdered,
        LocalDate dateShipped,
        Long salesRepId,
        String salesRepName,
        String paymentType,
        boolean orderFilled) {
}

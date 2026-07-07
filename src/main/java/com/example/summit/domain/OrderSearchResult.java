package com.example.summit.domain;

/**
 * One quick-search suggestion for the ORDERS view: an order id plus the joined
 * customer and sales-rep names used to build the autocomplete label. This is what
 * the Forms {@code S_ORD.KEY-EXEQRY} "execute query" produced — a result set the
 * clerk picks from — re-shaped as a search: the clerk types an order id, a customer
 * name, or a sales rep, and picks one suggestion (ledger:
 * {@code trigger:S_ORD.KEY-EXEQRY} migrated-as quick search commits the result set).
 *
 * <p>{@code salesRepName} is nullable — an order need not have a sales rep.
 */
public record OrderSearchResult(Long id, String customerName, String salesRepName) {

    /**
     * The autocomplete label: {@code "<id> — <customer> (<rep>)"}, e.g.
     * {@code "50001 — Unisports (Colin Magee)"}. The sales-rep clause is omitted when
     * the order has no rep, so the label never reads {@code "(null)"}.
     */
    public String label() {
        StringBuilder sb = new StringBuilder().append(id).append(" — ").append(customerName);
        if (salesRepName != null && !salesRepName.isBlank()) {
            sb.append(" (").append(salesRepName).append(')');
        }
        return sb.toString();
    }
}

package com.example.summit.domain;

/**
 * A single quick-search hit: the minimum an order clerk needs to recognise an order
 * in the autocomplete list — its id, the customer it is for, and the sales rep. It is
 * the web form of picking a record from Forms' Execute/Enter-Query result set: the
 * clerk chooses one {@link #id()}, which the view then loads in full via
 * {@code OrderRepository.loadGraph}.
 *
 * <p>Like {@link Order}'s display fields, {@code customerName} / {@code salesRepName}
 * arrive already joined ({@code S_CUSTOMER.name} / {@code S_EMP.last_name}), so a
 * search term can match on any of the three the way the clerk remembers the order.
 */
public record OrderSummary(int id, String customerName, String salesRepName) {
}

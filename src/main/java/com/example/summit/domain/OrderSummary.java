package com.example.summit.domain;

/**
 * One quick-search suggestion: the minimum needed to recognise an order in the
 * autocomplete list and load it — the id, plus the customer and sales-rep names that,
 * with the id, are the three things a clerk searches on (issue #85, mirrors Forms
 * {@code KEY-EXEQRY} quick search). {@code salesRepName} is nullable (an order need not
 * have a sales rep). The composite display label is a view concern (OrderView's item
 * label generator); this record stays data-only.
 */
public record OrderSummary(long id, String customerName, String salesRepName) {
}

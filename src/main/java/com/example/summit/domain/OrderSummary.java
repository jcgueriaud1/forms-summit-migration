package com.example.summit.domain;

import java.time.LocalDate;

/**
 * A lightweight match in the quick-search result set — just enough to render an
 * autocomplete suggestion and identify the order to load. The full header arrives
 * later via {@code OrderRepository.loadGraph(id())} when the clerk picks a suggestion.
 */
public record OrderSummary(
        long id,
        String customerName,
        String salesRepName,
        LocalDate dateOrdered) {
}

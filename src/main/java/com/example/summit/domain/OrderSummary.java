package com.example.summit.domain;

import java.time.LocalDate;

/**
 * One row of the quick-search result set — the minimum needed to render an
 * autocomplete suggestion and identify which order to load. When the clerk picks a
 * suggestion the full header is fetched with {@code OrderRepository.loadGraph(id())};
 * the summary itself is deliberately thin so the suggestion query stays cheap.
 */
public record OrderSummary(
        long id,
        String customerName,
        String salesRepName,
        LocalDate dateOrdered) {
}

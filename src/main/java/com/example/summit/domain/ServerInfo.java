package com.example.summit.domain;

/**
 * The demo datum as a domain type: the Oracle product banner and version as read
 * from the database (e.g. {@code "Oracle AI Database 26ai Free"} / {@code "23.26.2.0.0"}).
 * A stand-in for the real Order/OrderItem domain types that arrive with the
 * master-detail construct.
 */
public record ServerInfo(String product, String version) {
}

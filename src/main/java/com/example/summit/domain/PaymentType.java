package com.example.summit.domain;

/**
 * The Forms {@code S_ORD.PAYMENT_TYPE} Radio Group as a domain type. The two options
 * are {@code Cash=CASH} / {@code Credit=CREDIT} (from the spec's option set); the
 * stored column value is the enum name, the {@link #label()} is the radio prompt.
 *
 * <p>Modelling the choice as an enum keeps the control-kind decision honest: the
 * header renders a {@code RadioButtonGroup<PaymentType>} (ADR-0013), not a text field
 * holding the raw {@code "CASH"} string.
 */
public enum PaymentType {
    CASH("Cash"),
    CREDIT("Credit");

    private final String label;

    PaymentType(String label) {
        this.label = label;
    }

    /** The radio option prompt shown to the clerk (Forms {@code Label}). */
    public String label() {
        return label;
    }
}

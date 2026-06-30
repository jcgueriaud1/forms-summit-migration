package com.example.summit.domain;

import java.math.BigDecimal;

/**
 * What the {@code S_ITEM} {@code WHEN-VALIDATE-ITEM} defaulter reads back for a product — the
 * {@code SELECT name, suggested_whlsl_price ... FROM S_PRODUCT} lookup ({@code orders-form-spec.md},
 * rule {@code trigger-when-validate-item-default}). On any {@code product_id} change the line's
 * {@code description} and {@code price} are re-defaulted from these two columns.
 *
 * <p>This is kept distinct from {@link Product} (the LOV picker, id + name) because the
 * defaulter is <strong>not</strong> part of the LOV: it fires on a direct id change too, and a
 * missing row ({@code NO_DATA_FOUND}) is the reject path — the absence of a {@code ProductDefaults}
 * is how an invalid product id is recognised.
 *
 * @param name          the product name — re-defaults the line's {@code description}
 * @param suggestedPrice {@code suggested_whlsl_price} — re-defaults the line's {@code price};
 *                       may be {@code null} for an unpriced product (the line then totals to zero)
 */
public record ProductDefaults(String name, BigDecimal suggestedPrice) {
}

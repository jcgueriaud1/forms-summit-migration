package com.example.summit.domain;

import java.math.BigDecimal;

/**
 * The columns the Forms {@code S_ITEM} {@code WHEN-VALIDATE-ITEM} defaulter selects from
 * {@code S_PRODUCT} — {@code SELECT name, suggested_whlsl_price FROM s_product WHERE id = :id}
 * (issue #46, rule {@code trigger-when-validate-item-default}). On any {@code product_id}
 * change — a {@code PRODUCTS_LOV} pick <em>or</em> a directly typed id — the line re-defaults
 * its {@code description} from {@link #name()} and its {@code price} from
 * {@link #suggestedPrice()}.
 *
 * <p>These are the product's facts, not the line's fields: the defaulter maps {@code name}
 * onto the line's {@code description} and {@code suggested_whlsl_price} onto its {@code price}
 * (the mapping lives in {@link com.example.summit.presenter.OrderPresenter#applyProductDefaults}).
 * A product with no suggested price ({@code SUGGESTED_WHLSL_PRICE} is nullable) carries a
 * {@code null} price, which the {@code ITEM_TOTAL} formula already treats as zero.
 *
 * <p>Absence is the {@code NO_DATA_FOUND} case: the repository returns an empty
 * {@link java.util.Optional} for an id that is not a real product, which the defaulter turns
 * into a rejection with a clear message (the Forms {@code RAISE FORM_TRIGGER_FAILURE}).
 */
public record ProductDefaults(String name, BigDecimal suggestedPrice) {
}

package com.example.summit.domain;

/**
 * One {@code S_PRODUCT} row as the {@code PRODUCTS_LOV} picker sees it — <strong>id and name
 * only</strong>, the two columns the Forms LOV retrieves
 * ({@code Select id, name from s_product order by name}). The LOV displays {@code id — name}
 * in the popup but writes back only the {@link #id()} to {@code S_ITEM.PRODUCT_ID}
 * ({@code orders-form-spec.md}): it is a <em>picker</em>, not a defaulter.
 *
 * <p>The description/price re-default is a deliberately separate concern — the
 * {@code WHEN-VALIDATE-ITEM} defaulter ({@link ProductDefaults}), fired on any
 * {@code product_id} change — so this record carries no price.
 */
public record Product(long id, String name) {
}

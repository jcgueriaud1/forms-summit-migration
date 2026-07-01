package com.example.summit.domain;

/**
 * One row of the Forms {@code PRODUCTS_LOV} — the pair the lookup <em>displays</em>,
 * {@code (id, name)}, from its record-group query {@code select id, name from s_product
 * order by name} (issue #46, {@code orders-form-spec.md} §LOV).
 *
 * <p>The LOV is a <strong>picker only</strong>: it shows the id and name in the dropdown
 * but returns just the {@code id} to {@code S_ITEM.PRODUCT_ID}. Re-defaulting the line's
 * description and price from the chosen product is <em>not</em> the LOV's job — that is the
 * separate {@code WHEN-VALIDATE-ITEM} defaulter ({@link ProductDefaults}), fired on any
 * {@code product_id} change including direct entry. So this record deliberately carries no
 * price: it is the display/return pair, nothing more.
 */
public record Product(long id, String name) {

    /** The dropdown label — id and name together, as the Forms LOV popup showed both columns. */
    public String label() {
        return id + " — " + name;
    }
}

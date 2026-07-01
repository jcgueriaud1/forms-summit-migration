package com.example.summit.presenter;

import com.example.summit.domain.OrderLine;

import java.math.BigDecimal;

/**
 * The <strong>mutable</strong> working copy of one {@code S_ITEM} line, bound to the editable
 * line-items {@link com.vaadin.flow.component.grid.Grid Grid} editor (issue #45). The immutable
 * domain {@link OrderLine} is the load/truth representation; a Vaadin {@code Binder} writes field
 * edits back into a bean, so the in-place editor needs this mutable mirror.
 *
 * <p>It holds no formula of its own: {@link #itemTotal()} delegates to {@link OrderLine#itemTotal()}
 * via {@link #toOrderLine()}, so the {@code ITEM_TOTAL} formula stays authoritative in the Domain
 * (ADR-0001) and an edited row totals exactly as a loaded one does. New and duplicated lines are
 * built through the Domain too — a blank line, or {@link OrderLine#duplicateForInsert()} for the
 * {@code KEY-DUPREC} clear-list. Persisting these edits is the commit slice's job; this slice keeps
 * the editing in memory.
 */
public class EditableLine {

    private long itemId;
    private long productId;
    private String description;
    private BigDecimal price;
    private long quantity;
    private long quantityShipped;

    /** Wraps a loaded (or Domain-prepared) line for editing. */
    public EditableLine(OrderLine line) {
        this.itemId = line.itemId();
        this.productId = line.productId();
        this.description = line.description() == null ? "" : line.description();
        this.price = line.price();
        this.quantity = line.quantity();
        this.quantityShipped = line.quantityShipped();
    }

    private EditableLine(long itemId) {
        this.itemId = itemId;
        this.description = "";
    }

    /**
     * A blank new line with its assigned {@code item_id} — product and amounts empty for the clerk to
     * fill. Product selection (the LOV-driven defaulter) is a later slice, so a new line starts
     * unproducted and unpriced (its {@link #itemTotal()} is zero until edited).
     */
    public static EditableLine blank(long itemId) {
        return new EditableLine(itemId);
    }

    /** A snapshot of the current field values as the immutable domain line. */
    public OrderLine toOrderLine() {
        return new OrderLine(itemId, productId, description, price, quantity, quantityShipped);
    }

    /** The {@code ITEM_TOTAL} formula for the current edits — delegated to the Domain (ADR-0001). */
    public BigDecimal itemTotal() {
        return toOrderLine().itemTotal();
    }

    public long getItemId() {
        return itemId;
    }

    public void setItemId(long itemId) {
        this.itemId = itemId;
    }

    public long getProductId() {
        return productId;
    }

    public void setProductId(long productId) {
        this.productId = productId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public long getQuantity() {
        return quantity;
    }

    public void setQuantity(long quantity) {
        this.quantity = quantity;
    }

    public long getQuantityShipped() {
        return quantityShipped;
    }

    public void setQuantityShipped(long quantityShipped) {
        this.quantityShipped = quantityShipped;
    }
}

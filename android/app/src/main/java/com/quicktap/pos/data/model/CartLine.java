package com.quicktap.pos.data.model;

import com.quicktap.pos.data.entity.Product;

/** In-memory cart row. Never persisted directly; converted to BillItem on print. */
public class CartLine {
    public final long productId;
    public final String name;
    public final String categoryName;
    public final double price;
    public int qty;

    public CartLine(Product product, String categoryName) {
        this.productId = product.id;
        this.name = product.name;
        this.categoryName = categoryName;
        this.price = product.price;
        this.qty = 1;
    }

    /** Rebuilds a row from storage (parked bills) without touching the database. */
    public CartLine(long productId, String name, String categoryName, double price, int qty) {
        this.productId = productId;
        this.name = name;
        this.categoryName = categoryName;
        this.price = price;
        this.qty = Math.max(1, qty);
    }

    public CartLine copy() {
        return new CartLine(productId, name, categoryName, price, qty);
    }

    public double total() { return price * qty; }
}

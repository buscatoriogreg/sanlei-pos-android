package com.sanlei.pos.ui.pos;

public class CartItem {
    public int productId;
    public String name;
    public double unitPrice;
    public int quantity;
    public boolean isVatable;
    public int maxStock;

    public CartItem(int productId, String name, double unitPrice, int quantity, boolean isVatable, int maxStock) {
        this.productId = productId;
        this.name = name;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.isVatable = isVatable;
        this.maxStock = maxStock;
    }

    public double getLineTotal() {
        return unitPrice * quantity;
    }
}

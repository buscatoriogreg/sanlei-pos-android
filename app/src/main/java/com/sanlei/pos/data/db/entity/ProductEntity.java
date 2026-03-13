package com.sanlei.pos.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "products")
public class ProductEntity {
    @PrimaryKey
    public int id;
    public String name;
    public String genericName;
    public String barcode;
    public int categoryId;
    public String categoryName;
    public String unit;
    public double costPrice;
    public double sellingPrice;
    public boolean isVatable;
    public boolean isPrescription;
    public String image;
    public int stock;
    public String updatedAt;
}

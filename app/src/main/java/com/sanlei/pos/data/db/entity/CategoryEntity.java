package com.sanlei.pos.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories")
public class CategoryEntity {
    @PrimaryKey
    public int id;
    public String name;
}

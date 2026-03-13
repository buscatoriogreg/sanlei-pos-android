package com.sanlei.pos.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.sanlei.pos.data.db.entity.ProductEntity;

import java.util.List;

@Dao
public interface ProductDao {
    @Query("SELECT * FROM products WHERE stock > 0 ORDER BY name")
    List<ProductEntity> getAllInStock();

    @Query("SELECT * FROM products ORDER BY name")
    List<ProductEntity> getAll();

    @Query("SELECT * FROM products WHERE name LIKE '%' || :query || '%' OR barcode = :query OR genericName LIKE '%' || :query || '%'")
    List<ProductEntity> search(String query);

    @Query("SELECT * FROM products WHERE barcode = :barcode LIMIT 1")
    ProductEntity findByBarcode(String barcode);

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    ProductEntity findById(int id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<ProductEntity> products);

    @Query("UPDATE products SET stock = stock - :qty WHERE id = :productId")
    void decrementStock(int productId, int qty);

    @Query("DELETE FROM products")
    void deleteAll();
}

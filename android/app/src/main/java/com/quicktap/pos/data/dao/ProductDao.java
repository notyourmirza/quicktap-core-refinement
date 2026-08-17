package com.quicktap.pos.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.quicktap.pos.data.entity.Product;

import java.util.List;

@Dao
public interface ProductDao {

    /**
     * Billing grid query. Favourites and best sellers float to the top so the
     * cashier reaches the common items with no scrolling.
     */
    @Query("SELECT * FROM products "
            + "WHERE (:categoryId = 0 OR categoryId = :categoryId) "
            + "AND (:query = '' OR name LIKE '%' || :query || '%' OR barcode = :query) "
            + "ORDER BY favorite DESC, soldCount DESC, name ASC")
    LiveData<List<Product>> observeFiltered(long categoryId, String query);

    @Query("SELECT * FROM products ORDER BY name ASC")
    LiveData<List<Product>> observeAll();

    @Query("SELECT * FROM products WHERE id = :id")
    Product byId(long id);

    @Query("SELECT COUNT(*) FROM products")
    int count();

    @Query("UPDATE products SET soldCount = soldCount + :qty WHERE id = :id")
    void bumpSold(long id, int qty);

    @Query("UPDATE products SET stock = stock - :qty WHERE id = :id AND stock >= 0")
    void reduceStock(long id, int qty);

    @Insert
    long insert(Product product);

    @Update
    void update(Product product);

    @Delete
    void delete(Product product);
}

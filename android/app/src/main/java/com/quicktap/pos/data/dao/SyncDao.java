package com.quicktap.pos.data.dao;

import androidx.room.Dao;
import androidx.room.Query;

import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;

import java.util.List;

/**
 * Raw access used only by the offline sync engine. Keeping it separate leaves
 * the feature DAOs focused on the POS screens.
 */
@Dao
public interface SyncDao {

    // ---- outgoing (device -> server) ----

    @Query("SELECT * FROM products WHERE dirty = 1 LIMIT :limit")
    List<Product> dirtyProducts(int limit);

    @Query("SELECT * FROM categories WHERE dirty = 1 LIMIT :limit")
    List<Category> dirtyCategories(int limit);

    @Query("SELECT * FROM bills WHERE dirty = 1 ORDER BY createdAt ASC LIMIT :limit")
    List<Bill> dirtyBills(int limit);

    @Query("SELECT * FROM bill_items WHERE billId = :billId")
    List<BillItem> itemsOf(long billId);

    @Query("UPDATE products SET dirty = 0 WHERE uuid = :uuid")
    void markProductSynced(String uuid);

    @Query("UPDATE categories SET dirty = 0 WHERE uuid = :uuid")
    void markCategorySynced(String uuid);

    @Query("UPDATE bills SET dirty = 0 WHERE uuid = :uuid")
    void markBillSynced(String uuid);

    // ---- incoming (server -> device) ----

    @Query("SELECT * FROM products WHERE uuid = :uuid LIMIT 1")
    Product productByUuid(String uuid);

    @Query("SELECT * FROM categories WHERE uuid = :uuid LIMIT 1")
    Category categoryByUuid(String uuid);

    @Query("SELECT id FROM categories WHERE uuid = :uuid LIMIT 1")
    Long categoryIdByUuid(String uuid);

    @Query("SELECT uuid FROM categories WHERE id = :id LIMIT 1")
    String categoryUuid(long id);

    @Query("SELECT * FROM bills WHERE uuid = :uuid LIMIT 1")
    Bill billByUuid(String uuid);

    @Query("DELETE FROM products WHERE uuid = :uuid")
    void hardDeleteProduct(String uuid);

    @Query("DELETE FROM categories WHERE uuid = :uuid")
    void hardDeleteCategory(String uuid);

    // ---- one-off backfill for rows created before sync existed ----

    @Query("SELECT COUNT(*) FROM products WHERE uuid IS NULL")
    int productsMissingUuid();

    @Query("UPDATE products SET uuid = lower(hex(randomblob(16))) WHERE uuid IS NULL OR uuid = ''")
    void backfillProductUuids();

    @Query("UPDATE categories SET uuid = lower(hex(randomblob(16))) WHERE uuid IS NULL OR uuid = ''")
    void backfillCategoryUuids();

    @Query("UPDATE bills SET uuid = lower(hex(randomblob(16))) WHERE uuid IS NULL OR uuid = ''")
    void backfillBillUuids();

    // ---- keep the local uuid identical to the one we pushed ----

    @Query("UPDATE categories SET uuid = :uuid WHERE id = :id")
    void setCategoryUuid(long id, String uuid);

    @Query("UPDATE products SET uuid = :uuid WHERE id = :id")
    void setProductUuid(long id, String uuid);

    @Query("UPDATE bills SET uuid = :uuid WHERE id = :id")
    void setBillUuid(long id, String uuid);

    // ---- one-time cleanup of rows duplicated by earlier syncs ----

    @Query("UPDATE products SET categoryId = (SELECT MIN(c2.id) FROM categories c2 "
            + "WHERE c2.name = (SELECT c1.name FROM categories c1 WHERE c1.id = products.categoryId)) "
            + "WHERE categoryId IS NOT NULL")
    void repointProductsToFirstCategory();

    @Query("DELETE FROM categories WHERE id NOT IN (SELECT MIN(id) FROM categories GROUP BY name)")
    void dedupeCategories();

    @Query("DELETE FROM products WHERE id NOT IN "
            + "(SELECT MIN(id) FROM products GROUP BY name, categoryId)")
    void dedupeProducts();

    // ---- identity adoption: match an existing local row instead of duplicating ----

    @Query("SELECT * FROM categories WHERE name = :name ORDER BY id ASC LIMIT 1")
    Category categoryByName(String name);

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode <> '' ORDER BY id ASC LIMIT 1")
    Product productByBarcode(String barcode);

    @Query("SELECT * FROM products WHERE name = :name AND categoryId = :categoryId ORDER BY id ASC LIMIT 1")
    Product productByNameAndCategory(String name, long categoryId);

    // ---- duplicate uuid cleanup (a uuid must only ever exist once locally) ----

    @Query("DELETE FROM categories WHERE uuid IS NOT NULL AND uuid <> '' AND id NOT IN "
            + "(SELECT MIN(id) FROM categories WHERE uuid IS NOT NULL AND uuid <> '' GROUP BY uuid)")
    void dedupeCategoriesByUuid();

    @Query("DELETE FROM products WHERE uuid IS NOT NULL AND uuid <> '' AND id NOT IN "
            + "(SELECT MIN(id) FROM products WHERE uuid IS NOT NULL AND uuid <> '' GROUP BY uuid)")
    void dedupeProductsByUuid();

    @Query("DELETE FROM bills WHERE uuid IS NOT NULL AND uuid <> '' AND id NOT IN "
            + "(SELECT MIN(id) FROM bills WHERE uuid IS NOT NULL AND uuid <> '' GROUP BY uuid)")
    void dedupeBillsByUuid();

    @Query("DELETE FROM bill_items WHERE billId NOT IN (SELECT id FROM bills)")
    void deleteOrphanBillItems();

    @Query("SELECT COUNT(*) FROM products WHERE dirty = 1")
    int pendingProducts();

    @Query("SELECT COUNT(*) FROM bills WHERE dirty = 1")
    int pendingBills();
}

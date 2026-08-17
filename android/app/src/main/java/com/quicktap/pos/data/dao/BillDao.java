package com.quicktap.pos.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Transaction;

import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.data.model.CategorySales;
import com.quicktap.pos.data.model.DaySummary;
import com.quicktap.pos.data.model.ProductSales;

import java.util.List;

@Dao
public interface BillDao {

    @Insert
    long insertBill(Bill bill);

    @Insert
    void insertItems(List<BillItem> items);

    @Transaction
    default long saveBill(Bill bill, List<BillItem> items) {
        long id = insertBill(bill);
        for (BillItem item : items) item.billId = id;
        insertItems(items);
        return id;
    }

    @Query("SELECT * FROM bills ORDER BY createdAt DESC LIMIT :limit")
    LiveData<List<Bill>> observeRecent(int limit);

    @Query("SELECT * FROM bills WHERE createdAt BETWEEN :from AND :to ORDER BY createdAt DESC")
    List<Bill> between(long from, long to);

    @Query("SELECT * FROM bills WHERE id = :id")
    Bill byId(long id);

    @Query("SELECT * FROM bills ORDER BY createdAt DESC LIMIT 1")
    Bill last();

    @Query("SELECT * FROM bill_items WHERE billId = :billId")
    List<BillItem> itemsOf(long billId);

    @Query("DELETE FROM bills WHERE id = :id")
    void deleteBill(long id);

    @Query("DELETE FROM bill_items WHERE billId = :billId")
    void deleteItems(long billId);

    @Transaction
    default void deleteBillCascade(long id) {
        deleteItems(id);
        deleteBill(id);
    }

    @Query("SELECT COUNT(*) AS orders, "
            + "IFNULL(SUM(total),0) AS revenue, "
            + "IFNULL(SUM(CASE WHEN paid = 1 THEN 1 ELSE 0 END),0) AS paidCount, "
            + "IFNULL(SUM(CASE WHEN paid = 0 THEN 1 ELSE 0 END),0) AS unpaidCount, "
            + "IFNULL(SUM(CASE WHEN paid = 0 THEN total ELSE 0 END),0) AS unpaidAmount "
            + "FROM bills WHERE createdAt BETWEEN :from AND :to")
    LiveData<DaySummary> observeSummary(long from, long to);

    @Query("SELECT COUNT(*) AS orders, "
            + "IFNULL(SUM(total),0) AS revenue, "
            + "IFNULL(SUM(CASE WHEN paid = 1 THEN 1 ELSE 0 END),0) AS paidCount, "
            + "IFNULL(SUM(CASE WHEN paid = 0 THEN 1 ELSE 0 END),0) AS unpaidCount, "
            + "IFNULL(SUM(CASE WHEN paid = 0 THEN total ELSE 0 END),0) AS unpaidAmount "
            + "FROM bills WHERE createdAt BETWEEN :from AND :to")
    DaySummary summary(long from, long to);

    @Query("SELECT i.name AS name, SUM(i.qty) AS qty, SUM(i.price * i.qty) AS amount "
            + "FROM bill_items i JOIN bills b ON b.id = i.billId "
            + "WHERE b.createdAt BETWEEN :from AND :to "
            + "GROUP BY i.name ORDER BY amount DESC")
    List<ProductSales> salesByProduct(long from, long to);

    @Query("SELECT IFNULL(i.categoryName,'Uncategorised') AS category, "
            + "SUM(i.qty) AS qty, SUM(i.price * i.qty) AS amount "
            + "FROM bill_items i JOIN bills b ON b.id = i.billId "
            + "WHERE b.createdAt BETWEEN :from AND :to "
            + "GROUP BY category ORDER BY amount DESC")
    List<CategorySales> salesByCategory(long from, long to);

    @Query("SELECT COUNT(*) FROM bills")
    int totalBills();
}

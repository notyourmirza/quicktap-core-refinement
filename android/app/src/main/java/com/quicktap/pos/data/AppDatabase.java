package com.quicktap.pos.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.quicktap.pos.data.dao.BillDao;
import com.quicktap.pos.data.dao.CategoryDao;
import com.quicktap.pos.data.dao.ProductDao;
import com.quicktap.pos.data.dao.SyncDao;
import com.quicktap.pos.data.entity.Bill;
import com.quicktap.pos.data.entity.BillItem;
import com.quicktap.pos.data.entity.Category;
import com.quicktap.pos.data.entity.Product;

@Database(
        entities = {Product.class, Category.class, Bill.class, BillItem.class},
        version = 3,
        exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    public static final String NAME = "quicktap.db";

    public abstract ProductDao productDao();

    public abstract CategoryDao categoryDao();

    public abstract BillDao billDao();

    public abstract SyncDao syncDao();

    /**
     * v1 -> v2: adds the offline-sync bookkeeping columns (uuid, updatedAt,
     * dirty, deleted) and backfills existing rows so nothing is lost.
     */
    public static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            for (String table : new String[]{"products", "categories", "bills"}) {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN uuid TEXT");
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0");
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN dirty INTEGER NOT NULL DEFAULT 1");
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN deleted INTEGER NOT NULL DEFAULT 0");
                db.execSQL("UPDATE " + table + " SET uuid = lower(hex(randomblob(16))) WHERE uuid IS NULL");
                db.execSQL("UPDATE " + table + " SET updatedAt = strftime('%s','now') * 1000");
            }
        }
    };

    /**
     * v2 -> v3: de-duplicates rows that earlier auto-sync builds copied in and
     * enforces "one row per uuid" with a unique index, so a re-sync or a
     * restored backup can never insert a second copy of the same record again.
     */
    public static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase db) {
            for (String table : new String[]{"products", "categories", "bills"}) {
                db.execSQL("UPDATE " + table + " SET uuid = lower(hex(randomblob(16))) "
                        + "WHERE uuid IS NULL OR uuid = ''");
                db.execSQL("DELETE FROM " + table + " WHERE id NOT IN "
                        + "(SELECT MIN(id) FROM " + table + " GROUP BY uuid)");
            }
            db.execSQL("DELETE FROM bill_items WHERE billId NOT IN (SELECT id FROM bills)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_uuid ON products(uuid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_categories_uuid ON categories(uuid)");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_bills_uuid ON bills(uuid)");
        }
    };

    private static volatile AppDatabase instance;

    public static AppDatabase get(Context context) {
        if (instance == null) {
            synchronized (AppDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                                    context.getApplicationContext(), AppDatabase.class, NAME)
                            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return instance;
    }

    /** Used by restore-from-backup, which swaps the underlying file. */
    public static void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}

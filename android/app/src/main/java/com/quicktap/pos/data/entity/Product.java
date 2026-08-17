package com.quicktap.pos.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "products", indices = {@Index("categoryId"), @Index("name"),
        @Index(value = "uuid", unique = true)})
public class Product {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    public double price;

    public long categoryId;

    /** Local content:// or file:// URI of the product photo. Nullable. */
    public String imageUri;

    public String barcode;

    @ColumnInfo(defaultValue = "1")
    public boolean available = true;

    @ColumnInfo(defaultValue = "0")
    public boolean favorite = false;

    /** Optional inventory count. Negative means "not tracked". */
    @ColumnInfo(defaultValue = "-1")
    public int stock = -1;

    /** Running counter used to sort best sellers to the top. */
    @ColumnInfo(defaultValue = "0")
    public int soldCount = 0;

    // ---- offline sync metadata (see com.quicktap.pos.sync.SyncEngine) ----

    /** Stable cross-device identifier used by the server as the upsert key. */
    public String uuid;

    /** Local last-modified stamp in epoch millis; drives last-write-wins. */
    @ColumnInfo(defaultValue = "0")
    public long updatedAt;

    /** 1 while the row still has to be pushed to the server. */
    @ColumnInfo(defaultValue = "1")
    public boolean dirty = true;

    /** Soft delete so the deletion can be replicated to other devices. */
    @ColumnInfo(defaultValue = "0")
    public boolean deleted = false;
}

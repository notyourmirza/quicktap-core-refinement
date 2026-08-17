package com.quicktap.pos.data.entity;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "categories", indices = {@Index(value = "uuid", unique = true)})
public class Category {
    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Manual ordering position in the category chip row. */
    public int position;

    public Category() { }

    public Category(@NonNull String name, int position) {
        this.name = name;
        this.position = position;
    }

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

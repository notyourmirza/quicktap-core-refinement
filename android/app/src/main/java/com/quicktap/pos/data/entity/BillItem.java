package com.quicktap.pos.data.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@Entity(tableName = "bill_items", indices = {@Index("billId")})
public class BillItem {
    @PrimaryKey(autoGenerate = true)
    public long id;

    public long billId;
    public long productId;

    @NonNull
    public String name = "";

    public String categoryName;

    public double price;
    public int qty;

    public double lineTotal() { return price * qty; }
}

package com.quicktap.pos.data.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import com.quicktap.pos.data.entity.Category;

import java.util.List;

@Dao
public interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY position ASC, name ASC")
    LiveData<List<Category>> observeAll();

    @Query("SELECT * FROM categories ORDER BY position ASC, name ASC")
    List<Category> getAll();

    @Query("SELECT name FROM categories WHERE id = :id")
    String nameOf(long id);

    @Insert
    long insert(Category category);

    @Update
    void update(Category category);

    @Delete
    void delete(Category category);

    @Query("SELECT COUNT(*) FROM categories")
    int count();
}

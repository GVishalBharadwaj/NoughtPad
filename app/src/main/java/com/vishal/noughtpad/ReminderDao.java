package com.vishal.noughtpad;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;
import java.util.List;

@Dao
public interface ReminderDao {

    @Insert
    void insert(ReminderItem item);

    @Update
    void update(ReminderItem item);

    @Delete
    void delete(ReminderItem item);

    @Query("SELECT * FROM reminder_items ORDER BY id DESC")
    LiveData<List<ReminderItem>> getAllItems();

    @Query("SELECT * FROM reminder_items WHERE id = :itemId")
    LiveData<ReminderItem> getById(int itemId);

    @Query("SELECT * FROM reminder_items WHERE category = :category ORDER BY id DESC")
    LiveData<List<ReminderItem>> getItemsByCategory(String category);

    @Query("SELECT * FROM reminder_items WHERE category IN (:categories) ORDER BY id DESC")
    LiveData<List<ReminderItem>> getItemsByCategories(String[] categories);
}
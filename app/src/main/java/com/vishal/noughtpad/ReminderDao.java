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

    @Query("SELECT * FROM reminder_items")
    List<ReminderItem> getAllItemsSync();

    @Query("SELECT COUNT(*) FROM reminder_items WHERE amount = :amount AND title = :title AND reminderTime > :startTime")
    int checkForDuplicate(double amount, String title, long startTime);

    @Query("SELECT category FROM reminder_items WHERE title = :title ORDER BY id DESC LIMIT 1")
    String getLastCategory(String title);

    @Query("SELECT tags FROM reminder_items WHERE title = :merchant AND tags IS NOT NULL AND tags != '' ORDER BY id DESC LIMIT 1")
    String getLastTagForMerchant(String merchant);

    @Query("SELECT * FROM reminder_items WHERE category = 'RECEIPT' AND reminderTime >= :startTime AND reminderTime <= :endTime ORDER BY reminderTime DESC")
    LiveData<List<ReminderItem>> getExpensesByDateRange(long startTime, long endTime);
}
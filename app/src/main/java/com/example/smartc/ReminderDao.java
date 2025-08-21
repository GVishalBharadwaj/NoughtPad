package com.example.smartc;

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

    // ✅ This is the main change. We are modifying your existing query.
    @Query("SELECT * FROM reminder_items ORDER BY type DESC, reminderTime ASC")
    LiveData<List<ReminderItem>> getAllItems();

    // Your other methods are great, we'll keep them.
    @Query("DELETE FROM reminder_items WHERE id = :itemId")
    void deleteById(int itemId);

    @Query("UPDATE reminder_items SET isActive = :isActive WHERE id = :itemId")
    void updateIsActive(int itemId, boolean isActive);

    // Add this to ReminderDao.java
    @Query("SELECT * FROM reminder_items WHERE id = :itemId")
    LiveData<ReminderItem> getById(int itemId);
}
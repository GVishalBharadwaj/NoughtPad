package com.example.smartc;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface ReminderDao {

    @Insert
    void insert(ReminderItem item);

    @Query("SELECT * FROM reminder_items ORDER BY id DESC")
    LiveData<List<ReminderItem>> getAllItems();

    @Query("DELETE FROM reminder_items WHERE id = :itemId")
    void deleteById(int itemId);

    @Query("UPDATE reminder_items SET isActive = :isActive WHERE id = :itemId")
    void updateIsActive(int itemId, boolean isActive);
}
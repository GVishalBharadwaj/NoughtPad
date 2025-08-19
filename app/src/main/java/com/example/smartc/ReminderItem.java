package com.example.smartc;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reminder_items")
public class ReminderItem {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String content = "";
    public String type = "";
    public String details = "";
    public String amount = "";


    public long reminderTime; // The due date in milliseconds, 0 for notes

    public boolean isActive; // true if the alarm is set, false if cancelled or it's a note
}
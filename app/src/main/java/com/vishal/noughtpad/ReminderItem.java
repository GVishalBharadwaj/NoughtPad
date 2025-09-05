package com.vishal.noughtpad;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "reminder_items")
public class ReminderItem {

    @PrimaryKey(autoGenerate = true)
    public int id;

    public String content = "";
    public String type = "";
    public String details = "";
    public double amount;

    public String title = ""; // ✅ The field that was missing

    public String description = "";
    public String tags = ""; // Will store a comma-separated list, e.g., "bill,finance,payment due"
    public String category = ""; // e.g., "BILL", "RECEIPT", "NOTE"

    public long reminderTime; // The due date in milliseconds, 0 for notes
    public String embedding;

    public boolean isActive; // true if the alarm is set, false if cancelled or it's a note
}
package com.example.smartc;

import android.app.Application;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class ReminderViewModel extends AndroidViewModel {

    private final ReminderDao reminderDao;
    private final LiveData<List<ReminderItem>> allItems;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public ReminderViewModel(@NonNull Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        reminderDao = database.reminderDao();
        allItems = reminderDao.getAllItems();
    }

    // This method gets all items and is already correct.
    public LiveData<List<ReminderItem>> getAllItems() {
        return allItems;
    }

    // ✅ ADD THIS METHOD to save new items to the database.
    public void insert(ReminderItem item) {
        executor.execute(() -> reminderDao.insert(item));
    }

    // This delete method is also correct and we will use it later.
    public void delete(ReminderItem item) {
        executor.execute(() -> reminderDao.deleteById(item.id));
    }

    // Add these methods to ReminderViewModel.java
    public LiveData<ReminderItem> getById(int itemId) {
        return reminderDao.getById(itemId);
    }

    public void update(ReminderItem item) {
        executor.execute(() -> reminderDao.update(item));
    }
}
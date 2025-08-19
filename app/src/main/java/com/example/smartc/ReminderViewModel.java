package com.example.smartc;

import android.app.Application;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;

import java.util.List;

public class ReminderViewModel extends AndroidViewModel {

    private final ReminderDao reminderDao;
    private final LiveData<List<ReminderItem>> allItems;
    private final Executor executor = Executors.newSingleThreadExecutor();

    public ReminderViewModel(Application application) {
        super(application);
        AppDatabase database = AppDatabase.getDatabase(application);
        reminderDao = database.reminderDao();
        allItems = reminderDao.getAllItems();
    }

    public LiveData<List<ReminderItem>> getAllItems() {
        return allItems;
    }
    public void delete(ReminderItem item) {
        executor.execute(() -> reminderDao.deleteById(item.id));
    }

    // We can add methods for inserting, deleting, etc. here later
}
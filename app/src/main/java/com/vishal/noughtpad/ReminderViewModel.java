package com.vishal.noughtpad;

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

    public LiveData<List<ReminderItem>> getAllItems() { return allItems; }
    public void insert(ReminderItem item) { executor.execute(() -> reminderDao.insert(item)); }
    public void update(ReminderItem item) { executor.execute(() -> reminderDao.update(item)); }
    public void delete(ReminderItem item) { executor.execute(() -> reminderDao.delete(item)); }
    public LiveData<ReminderItem> getById(int itemId) { return reminderDao.getById(itemId); }
    public LiveData<List<ReminderItem>> getItemsByCategories(String[] categories) { return reminderDao.getItemsByCategories(categories); }
    public LiveData<List<ReminderItem>> getItemsByCategory(String category) { return reminderDao.getItemsByCategory(category); }
}
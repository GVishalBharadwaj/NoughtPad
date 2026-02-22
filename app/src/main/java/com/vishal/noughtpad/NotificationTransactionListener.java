package com.vishal.noughtpad;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class NotificationTransactionListener extends NotificationListenerService {

    private static final String TAG = "NotifTxListener";
    private ExecutorService executor;
    private AppDatabase database;
    private Set<String> processedNotifications = new HashSet<>();

    @Override
    public void onCreate() {
        super.onCreate();
        executor = Executors.newSingleThreadExecutor();
        database = AppDatabase.getDatabase(getApplicationContext());
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String packageName = sbn.getPackageName();

        // Filter for relevant apps (Banks, UPI, SMS, Email)
        if (!isFinancialApp(packageName) && !isSmsApp(packageName)) {
            return;
        }

        Notification notification = sbn.getNotification();
        if (notification == null)
            return;

        CharSequence ticker = notification.tickerText;
        CharSequence title = notification.extras.getCharSequence(Notification.EXTRA_TITLE);
        CharSequence text = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
        CharSequence bigText = notification.extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        CharSequence subText = notification.extras.getCharSequence(Notification.EXTRA_SUB_TEXT);
        CharSequence summaryText = notification.extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT);

        String fullText = (ticker != null ? ticker + " " : "") +
                (title != null ? title + " " : "") +
                (text != null ? text + " " : "") +
                (bigText != null ? bigText + " " : "") +
                (subText != null ? subText + " " : "") +
                (summaryText != null ? summaryText + " " : "");

        // Simple dedup using hash of text
        String key = fullText.hashCode() + "";
        if (processedNotifications.contains(key))
            return;
        processedNotifications.add(key);
        if (processedNotifications.size() > 100)
            processedNotifications.clear();

        Log.d(TAG, "Checking Notification from " + packageName + ": " + fullText);

        TransactionParser.TransactionInfo info = TransactionParser.parse(fullText);

        // info.isDebit is true for Debit, false for Credit/Income
        if (info != null) {
            Log.d(TAG, "Transaction Detected from " + packageName + ": " + info);
            saveTransaction(info);
        }
    }

    private void saveTransaction(TransactionParser.TransactionInfo info) {
        executor.execute(() -> {
            // Check for duplicates robustly (same amount within last 2 hours + merchant
            // string similarity)
            long twoHoursAgo = System.currentTimeMillis() - (2 * 60 * 60 * 1000);
            java.util.List<ReminderItem> recentItems = database.reminderDao().getRecentTransactionsByAmount(info.amount,
                    twoHoursAgo);

            boolean isDuplicate = false;
            String newMerchantLower = info.merchant.toLowerCase();
            for (ReminderItem recent : recentItems) {
                String existingMerchantLower = recent.title.toLowerCase();
                // Check if one merchant name contains the other (e.g., "Amazon" vs "Amazon
                // Pay")
                if (existingMerchantLower.contains(newMerchantLower)
                        || newMerchantLower.contains(existingMerchantLower)) {
                    isDuplicate = true;
                    break;
                }
            }

            if (isDuplicate) {
                Log.d(TAG, "Duplicate Transaction Ignored: " + info);
                return;
            }

            ReminderItem item = new ReminderItem();
            item.title = info.merchant;

            boolean isBill = info.description != null && info.description.startsWith("[BILL]");
            if (isBill) {
                // Remove the flag
                info.description = info.description.replace("[BILL] ", "").trim();
            }

            item.description = info.description;

            if (isBill) {
                item.category = "BILL";
                item.type = "note";
                item.isActive = true; // Make it an active reminder
                // Set default reminder time slightly in the future if we don't know the exact
                // date.
                // Or just keep the active flag to show it in the Upcoming tab.
            } else {
                item.category = "RECEIPT";
                item.type = info.isDebit ? "note" : "income";
                item.isActive = false;
            }

            item.amount = info.amount;
            item.reminderTime = System.currentTimeMillis();

            // Auto-Categorization: "Tag once, tag forever"
            if (info.isDebit && !isBill) {
                String existingTag = database.reminderDao().getLastTagForMerchant(info.merchant);
                if (existingTag != null && !existingTag.isEmpty()) {
                    item.tags = existingTag;
                }
            }

            database.reminderDao().insert(item);
            Log.d(TAG, "Transaction Saved to DB");
        });
    }

    private boolean isFinancialApp(String pkg) {
        return pkg.contains("google.android.apps.nbu.paisa") || // GPay
                pkg.contains("phonepe") ||
                pkg.contains("paytm") ||
                pkg.contains("upi") ||
                pkg.contains("bank") || // Generic bank match
                pkg.contains("gm") || // Gmail (com.google.android.gm)
                pkg.contains("outlook") || // Outlook
                pkg.contains("cricorg"); // Example
    }

    private boolean isSmsApp(String pkg) {
        return pkg.contains("messaging") || pkg.contains("mms");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (executor != null)
            executor.shutdown();
    }
}

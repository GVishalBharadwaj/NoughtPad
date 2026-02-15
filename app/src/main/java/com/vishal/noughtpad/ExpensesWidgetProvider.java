package com.vishal.noughtpad;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

import java.text.NumberFormat;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;

public class ExpensesWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_expenses);

        // 1. Set up the "Scan & Pay" button pending intent
        Intent scanIntent = new Intent(context, QRScannerActivity.class);
        PendingIntent scanPendingIntent = PendingIntent.getActivity(context, 0, scanIntent,
                PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_button_scan, scanPendingIntent);

        // Also make the whole widget open MainActivity on click (optional, but good UX)
        Intent mainIntent = new Intent(context, MainActivity.class);
        PendingIntent mainPendingIntent = PendingIntent.getActivity(context, 0, mainIntent,
                PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_text_today, mainPendingIntent);

        // 2. Fetch data in basic background thread
        Executors.newSingleThreadExecutor().execute(() -> {
            AppDatabase db = AppDatabase.getDatabase(context);
            List<ReminderItem> allItems = db.reminderDao().getAllItemsSync();

            double totalToday = 0;
            double totalMonth = 0;
            long startOfDay = getStartOfDay();
            long startOfMonth = getStartOfMonth();

            for (ReminderItem item : allItems) {
                if ("RECEIPT".equals(item.category)) {
                    if (item.reminderTime >= startOfDay)
                        totalToday += item.amount;
                    if (item.reminderTime >= startOfMonth)
                        totalMonth += item.amount;
                }
            }

            // 3. Format Strings
            // Use Locale.Builder for flexibility and correctness
            NumberFormat currencyFormat = NumberFormat
                    .getCurrencyInstance(new Locale.Builder().setLanguage("en").setRegion("IN").build());
            String todayStr = currencyFormat.format(totalToday);
            String monthStr = currencyFormat.format(totalMonth);

            // 4. Update UI on Main Thread (AppWidgetManager updates are thread-safe or
            // handled via IPC)
            views.setTextViewText(R.id.widget_text_today, todayStr);
            views.setTextViewText(R.id.widget_text_month, monthStr);

            // Instruct the widget manager to update the widget
            appWidgetManager.updateAppWidget(appWidgetId, views);
        });
    }

    // Helper methods (duplicated from Fragment, ideally should be in specific Utils
    // class)
    private static long getStartOfDay() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }

    private static long getStartOfMonth() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.DAY_OF_MONTH, 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTimeInMillis();
    }
}

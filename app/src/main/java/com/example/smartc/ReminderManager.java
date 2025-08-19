package com.example.smartc;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;

import java.util.Calendar;
import java.util.Locale;

public final class ReminderManager {

    private ReminderManager() {} // Private constructor

    // This is your existing method for setting alarms
    public static void setReminder(Context context, long timeInMillis, String title, String message) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Permission needed to set reminders.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                context.startActivity(intent);
                return;
            }
        }

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        intent.putExtra("EXTRA_TITLE", title);
        intent.putExtra("EXTRA_MESSAGE", message);
        intent.putExtra("EXTRA_ID", (int) timeInMillis);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) timeInMillis,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                timeInMillis,
                pendingIntent
        );

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        String toastMessage = String.format(Locale.getDefault(), "Reminder set for %s", calendar.getTime().toString());
        Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
    }

    // ✅ ADD THIS ENTIRE METHOD TO YOUR CLASS
    public static void cancelReminder(Context context, ReminderItem item) {
        // We only need to cancel if it's an active reminder
        if (item.type.equals("REMINDER") && item.isActive) {
            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            Intent intent = new Intent(context, ReminderBroadcastReceiver.class);

            // To cancel an alarm, you must re-create a PendingIntent that is an exact match
            // to the one you used to set it. This includes the same request code and flags.
            PendingIntent pendingIntent = PendingIntent.getBroadcast(
                    context,
                    (int) item.reminderTime, // The same unique ID
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );

            // Cancel the alarm
            alarmManager.cancel(pendingIntent);
        }
    }
}
package com.vishal.noughtpad;

import android.util.Log;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.provider.Settings;
import android.widget.Toast;
import java.util.Calendar;
import java.util.Locale;
import android.content.SharedPreferences; // Import this
import androidx.preference.PreferenceManager; // Import this


public final class ReminderManager {

    private ReminderManager() {}

    public static void setReminder(Context context, long timeInMillis, String title, String message) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager != null && !alarmManager.canScheduleExactAlarms()) {
                Toast.makeText(context, "Permission needed to set reminders.", Toast.LENGTH_LONG).show();
                Intent intent = new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                context.startActivity(intent);
                return;
            }
        }

        // 1. Get the saved offset value from settings (in minutes)
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String offsetMinutesStr = prefs.getString("reminder_offset", "0");
        long offsetMinutes = Long.parseLong(offsetMinutesStr);

        // 2. Convert the offset to milliseconds
        long offsetMillis = offsetMinutes * 60 * 1000;

        // 3. Calculate the new, earlier alarm time
        long finalAlarmTime = timeInMillis - offsetMillis;

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        intent.putExtra("EXTRA_TITLE", title);
        intent.putExtra("EXTRA_MESSAGE", message);
        // ✅ Use the final alarm time as the unique ID
        intent.putExtra("EXTRA_ID", (int) finalAlarmTime);

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) finalAlarmTime, // ✅ Use the final alarm time as the unique request code
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            Log.d("AlarmManager", "SETTING alarm with ID: " + finalAlarmTime);
            // ✅ Use the final alarm time to schedule the alarm
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, finalAlarmTime, pendingIntent);
        }

        // The toast message can still show the original event time for clarity
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(timeInMillis);
        String toastMessage = String.format(Locale.getDefault(), "Reminder set for event at %s", calendar.getTime().toString());
        Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show();
    }

    public static void cancelReminder(Context context, ReminderItem item) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);

        Intent intent = new Intent(context, ReminderBroadcastReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context,
                (int) item.reminderTime,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        if (alarmManager != null) {
            alarmManager.cancel(pendingIntent);
        }
    }
}
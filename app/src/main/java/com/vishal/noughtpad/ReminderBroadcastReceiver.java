package com.vishal.noughtpad;
// app/src/main/java/com/vishal/smartreminders/ReminderBroadcastReceiver.java
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.core.app.NotificationCompat;

public class ReminderBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        NotificationManager notificationManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        String channelId = "reminder_channel_id";
        CharSequence channelName = "Reminders";
        NotificationChannel notificationChannel = new NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(notificationChannel);

        String notificationTitle = intent.getStringExtra("EXTRA_TITLE");
        if (notificationTitle == null) {
            notificationTitle = "Reminder";
        }
        String notificationText = intent.getStringExtra("EXTRA_MESSAGE");
        if (notificationText == null) {
            notificationText = "You have a pending task.";
        }
        int notificationId = intent.getIntExtra("EXTRA_ID", 0);

        NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(context, channelId)
                .setSmallIcon(R.drawable.ic_launcher_foreground) // Ensure this drawable exists
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true);

        notificationManager.notify(notificationId, notificationBuilder.build());
    }
}

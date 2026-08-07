package com.territorywars.notifications;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.territorywars.R;

/**
 * Notification categories + helpers:
 * friend requests, reward available, season ending, subscription expiring.
 */
public final class NotificationHelper {

    public static final String CHANNEL_SOCIAL = "social";
    public static final String CHANNEL_REWARDS = "rewards";
    public static final String CHANNEL_ACCOUNT = "account";

    private NotificationHelper() {
    }

    /** Must be called once from Application.onCreate. */
    public static void ensureChannels(android.content.Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);

        manager.createNotificationChannel(channel(CHANNEL_SOCIAL, "Friends & Chat",
                NotificationManager.IMPORTANCE_DEFAULT));
        manager.createNotificationChannel(channel(CHANNEL_REWARDS, "Rewards",
                NotificationManager.IMPORTANCE_HIGH));
        manager.createNotificationChannel(channel(CHANNEL_ACCOUNT, "Account",
                NotificationManager.IMPORTANCE_LOW));
    }

    private static NotificationChannel channel(String id, String name, int importance) {
        NotificationChannel c = new NotificationChannel(id, name, importance);
        c.setDescription("Notifications for " + name);
        return c;
    }

    public static void notifyFriendRequest(Context ctx, String fromUsername) {
        notify(ctx, CHANNEL_SOCIAL, 1001, "Friend request",
                fromUsername + " wants to play with you");
    }

    public static void notifyDailyReward(Context ctx) {
        notify(ctx, CHANNEL_REWARDS, 1002, "Daily reward ready",
                "Claim your coins before the day resets");
    }

    public static void notifySeasonEnding(Context ctx) {
        notify(ctx, CHANNEL_REWARDS, 1003, "Season ending soon",
                "Push your rank before the season resets!");
    }

    public static void notifySubscriptionExpiring(Context ctx) {
        notify(ctx, CHANNEL_ACCOUNT, 1004, "Premium expiring",
                "Renew to keep your premium perks");
    }

    private static void notify(Context ctx, String channel, int id, String title, String body) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(ctx, channel)
                .setSmallIcon(com.territorywars.R.drawable.ic_play)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true);

        if (NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
            try {
                NotificationManagerCompat.from(ctx).notify(id, builder.build());
            } catch (SecurityException ignored) {
                // unknown permission issue on some OEMs
            }
        }
    }
}
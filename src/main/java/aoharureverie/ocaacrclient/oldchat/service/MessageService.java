package aoharureverie.ocaacrclient.oldchat.service;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import aoharureverie.ocaacrclient.oldchat.MainActivity;
import aoharureverie.ocaacrclient.oldchat.api.WSManager;
import aoharureverie.ocaacrclient.oldchat.util.AppState;
import aoharureverie.ocaacrclient.oldchat.util.NotificationChannelCompat;
import aoharureverie.ocaacrclient.oldchat.util.NotificationCompatUtil;

public class MessageService extends Service {
    private static final String CHANNEL_ID = "oldchat_service";
    private static final int NOTIFY_ID = 42;

    private volatile boolean foregroundStarted = false;

    public static void start(Context context) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(context, MessageService.class);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                if (AppState.isForeground()) {
                    context.startService(intent);
                } else {
                    context.startForegroundService(intent);
                }
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            try {
                WSManager.getInstance().start(context.getApplicationContext());
            } catch (Exception ignored) {
            }
        }
    }

    public static void startIfAllowed(Context context) {
        if (context == null) {
            return;
        }
        Context appContext = context.getApplicationContext();
        if (Build.VERSION.SDK_INT >= 31 && !AppState.isForeground()) {
            try {
                WSManager.getInstance().start(appContext);
            } catch (Exception ignored) {
            }
            return;
        }
        start(appContext);
    }

    public static void stop(Context context) {
        if (context == null) {
            return;
        }
        context.stopService(new Intent(context, MessageService.class));
    }

    @Override
    public void onCreate() {
        super.onCreate();
        ensureForegroundStarted();
        WSManager.getInstance().start(getApplicationContext());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        ensureForegroundStarted();
        WSManager.getInstance().start(getApplicationContext());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        WSManager.getInstance().stop();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void ensureForegroundStarted() {
        if (foregroundStarted) {
            return;
        }
        Notification notification = buildForegroundNotificationSafely();
        try {
            startForeground(NOTIFY_ID, notification);
            foregroundStarted = true;
        } catch (Throwable e) {
            try {
                Notification fallback = buildMinimalFallbackNotification();
                startForeground(NOTIFY_ID, fallback);
                foregroundStarted = true;
            } catch (Throwable ignored) {
                stopSelf();
            }
        }
    }

    private Notification buildForegroundNotificationSafely() {
        try {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26 && nm != null) {
                NotificationChannelCompat.ensureChannel(nm, CHANNEL_ID, "后台连接", NotificationChannelCompat.IMPORTANCE_LOW);
            }
            Notification normal = NotificationCompatUtil.buildOngoingNotification(
                    this,
                    CHANNEL_ID,
                    android.R.drawable.stat_notify_chat,
                    "旧聊已连接",
                    "正在后台接收消息",
                    true,
                    createContentIntent()
            );
            if (normal != null) {
                return normal;
            }
        } catch (Throwable ignored) {
        }
        return buildMinimalFallbackNotification();
    }

    private Notification buildMinimalFallbackNotification() {
        PendingIntent contentIntent = createContentIntent();
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) {
                NotificationChannelCompat.ensureChannel(nm, CHANNEL_ID, "后台连接", NotificationChannelCompat.IMPORTANCE_LOW);
            }
            return new Notification.Builder(this, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle("旧聊已连接")
                    .setContentText("正在后台接收消息")
                    .setOngoing(true)
                    .setContentIntent(contentIntent)
                    .build();
        }
        Notification.Builder builder = new Notification.Builder(this)
                .setSmallIcon(android.R.drawable.stat_notify_chat)
                .setContentTitle("旧聊已连接")
                .setContentText("正在后台接收消息")
                .setOngoing(true)
                .setContentIntent(contentIntent);
        return builder.build();
    }

    private PendingIntent createContentIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(this, 0, intent, pendingFlags());
    }

    private static int pendingFlags() {
        final int FLAG_IMMUTABLE = 1 << 26;
        if (Build.VERSION.SDK_INT >= 23) {
            return PendingIntent.FLAG_UPDATE_CURRENT | FLAG_IMMUTABLE;
        }
        return PendingIntent.FLAG_UPDATE_CURRENT;
    }
}

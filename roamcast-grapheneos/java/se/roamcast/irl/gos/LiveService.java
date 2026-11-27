package se.roamcast.irl.gos;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

/**
 * Foreground service that keeps camera, microphone, CPU and Wi-Fi alive while
 * RoamCast is live, so the stream survives screen-off and app switching
 * (GrapheneOS / Android 14+ kill background camera & mic access otherwise).
 */
public class LiveService extends Service {

    private static final String TAG = "RoamCastLive";
    private static final String CHANNEL = "roamcast_live";
    private static final int NOTIFICATION_ID = 4711;

    private PowerManager.WakeLock wakeLock;
    private WifiManager.WifiLock wifiLock;
    private WifiManager.WifiLock wifiLockLowLatency;

    /** Called when the user goes live (app is in the foreground). */
    public static void start(Context c) {
        if (c == null) return;
        try {
            askNotificationPermissionOnce(c);
            if (!inForeground()) {
                // Camera/mic foreground services may only be started from the foreground.
                Log.w(TAG, "not in foreground - skipping service start");
                return;
            }
            Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
            app.startForegroundService(new Intent(app, LiveService.class));
        } catch (Throwable t) {
            Log.w(TAG, "start failed", t);
        }
    }

    /** Called when the stream stops. */
    public static void stop(Context c) {
        if (c == null) return;
        try {
            Context app = c.getApplicationContext() != null ? c.getApplicationContext() : c;
            app.stopService(new Intent(app, LiveService.class));
        } catch (Throwable t) {
            Log.w(TAG, "stop failed", t);
        }
    }

    private static boolean inForeground() {
        try {
            ActivityManager.RunningAppProcessInfo info = new ActivityManager.RunningAppProcessInfo();
            ActivityManager.getMyMemoryState(info);
            return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
        } catch (Throwable t) {
            return true;
        }
    }

    private static void askNotificationPermissionOnce(Context c) {
        if (Build.VERSION.SDK_INT < 33 || !(c instanceof Activity)) return;
        try {
            if (c.checkSelfPermission("android.permission.POST_NOTIFICATIONS") == PackageManager.PERMISSION_GRANTED) {
                return;
            }
            SharedPreferences p = c.getSharedPreferences("gos_fix", Context.MODE_PRIVATE);
            if (p.getBoolean("asked_notif", false)) return;
            p.edit().putBoolean("asked_notif", true).apply();
            ((Activity) c).requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 4711);
        } catch (Throwable t) {
            Log.w(TAG, "notification permission", t);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        goForeground();
        acquireLocks();
    }

    private Notification buildNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && nm.getNotificationChannel(CHANNEL) == null) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Livesändning", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Visas medan RoamCast sänder live");
            ch.setShowBadge(false);
            nm.createNotificationChannel(ch);
        }
        PendingIntent open = null;
        try {
            Intent launch = getPackageManager().getLaunchIntentForPackage(getPackageName());
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                open = PendingIntent.getActivity(this, 0, launch,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            }
        } catch (Throwable ignored) {
        }
        Notification.Builder b = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(getApplicationInfo().icon != 0 ? getApplicationInfo().icon : android.R.drawable.presence_video_online)
                .setContentTitle("RoamCast är LIVE 🔴")
                .setContentText("Sändningen fortsätter även med släckt skärm")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (open != null) b.setContentIntent(open);
        if (Build.VERSION.SDK_INT >= 31) {
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE);
        }
        return b.build();
    }

    private void goForeground() {
        Notification n = buildNotification();
        int type = 0;
        if (checkSelfPermission("android.permission.CAMERA") == PackageManager.PERMISSION_GRANTED) {
            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA;
        }
        if (checkSelfPermission("android.permission.RECORD_AUDIO") == PackageManager.PERMISSION_GRANTED) {
            type |= ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
        }
        int[] attempts = {type, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE, ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA};
        for (int t : attempts) {
            if (t == 0) continue;
            try {
                startForeground(NOTIFICATION_ID, n, t);
                return;
            } catch (Throwable e) {
                Log.w(TAG, "startForeground type " + t, e);
            }
        }
        try {
            startForeground(NOTIFICATION_ID, n);
        } catch (Throwable e) {
            Log.w(TAG, "startForeground", e);
        }
    }

    private void acquireLocks() {
        try {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RoamCast:live");
                wakeLock.setReferenceCounted(false);
                wakeLock.acquire(12L * 60 * 60 * 1000); // max 12 h
            }
        } catch (Throwable t) {
            Log.w(TAG, "wakelock", t);
        }
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
            if (wm != null) {
                // High-perf keeps Wi-Fi awake with the screen off,
                // low-latency improves it while the screen is on.
                wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "RoamCast:live");
                wifiLock.setReferenceCounted(false);
                wifiLock.acquire();
                if (Build.VERSION.SDK_INT >= 29) {
                    wifiLockLowLatency = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_LOW_LATENCY, "RoamCast:live-ll");
                    wifiLockLowLatency.setReferenceCounted(false);
                    wifiLockLowLatency.acquire();
                }
            }
        } catch (Throwable t) {
            Log.w(TAG, "wifilock", t);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        stopSelf();
        super.onTaskRemoved(rootIntent);
    }

    @Override
    public void onDestroy() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Throwable ignored) {
        }
        try {
            if (wifiLock != null && wifiLock.isHeld()) wifiLock.release();
        } catch (Throwable ignored) {
        }
        try {
            if (wifiLockLowLatency != null && wifiLockLowLatency.isHeld()) wifiLockLowLatency.release();
        } catch (Throwable ignored) {
        }
        try {
            stopForeground(Service.STOP_FOREGROUND_REMOVE);
        } catch (Throwable ignored) {
        }
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

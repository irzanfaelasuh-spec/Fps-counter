package com.fpsplus.vector;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class OverlayService extends Service {
    public static final String ACTION_START = "com.fpsplus.vector.START";
    public static final String ACTION_STOP = "com.fpsplus.vector.STOP";
    public static final String ACTION_REFRESH = "com.fpsplus.vector.REFRESH";
    public static final String ACTION_METRICS = "com.fpsplus.vector.METRICS";
    public static final String EXTRA_FPS = "fps";
    private static final String CHANNEL_ID = "fps_vector_monitor";
    private static final int NOTIFICATION_ID = 501;
    private static final int GREEN = 0xFFA6F36C;
    private static final int TEXT = 0xFFEAF2EA;
    private static final int MUTED = 0xFF91A098;
    private static final int RED = 0xFFFF5F62;
    private static final int ORANGE = 0xFFFF9B43;
    private static final int YELLOW = 0xFFFFC857;
    private static final int GREEN_LIGHT = 0xFF79D982;
    private static final int GREEN_DARK = 0xFF3BBA68;

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final AtomicBoolean fpsBusy = new AtomicBoolean(false);
    private WindowManager windowManager;
    private WindowManager.LayoutParams windowParams;
    private LinearLayout overlay;
    private LinearLayout metricList;
    private TextView fpsText;
    private TextView collapseButton;
    private TextView metricsText;
    private TextView resizeHandle;
    private boolean collapsed;
    private float latestFps = -1f;
    private ShizukuShellBridge shellBridge;
    private SurfaceFlingerReader reader;

    private final Runnable telemetryTicker = new Runnable() {
        @Override public void run() {
            if (overlay == null) return;
            updateTelemetry();
            int interval = AppPrefs.get(OverlayService.this).getInt(AppPrefs.REFRESH_MS, 1000);
            main.postDelayed(this, Math.max(500, Math.min(3000, interval)));
        }
    };

    private final Runnable fpsTicker = new Runnable() {
        @Override public void run() {
            if (overlay == null) return;
            if (fpsBusy.compareAndSet(false, true)) {
                worker.execute(() -> {
                    Float sample = null;
                    try {
                        if (reader != null && reader.canRead()) sample = reader.sampleFps();
                    } catch (Exception ignored) { }
                    final float value = sample == null ? -1f : sample;
                    main.post(() -> {
                        latestFps = value;
                        sendFpsBroadcast(value);
                        updateOverlayText();
                        fpsBusy.set(false);
                    });
                });
            }
            int interval = AppPrefs.get(OverlayService.this).getInt(AppPrefs.REFRESH_MS, 1000);
            main.postDelayed(this, Math.max(750, Math.min(3000, interval)));
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_START : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_REFRESH.equals(action) && overlay == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (overlay == null) {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, buildNotification(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(NOTIFICATION_ID, buildNotification());
            }
            createOverlay();
            if (overlay != null) {
                shellBridge = new ShizukuShellBridge(this);
                reader = new SurfaceFlingerReader(shellBridge);
                shellBridge.bind();
                main.removeCallbacks(telemetryTicker);
                main.removeCallbacks(fpsTicker);
                main.post(telemetryTicker);
                main.post(fpsTicker);
                worker.execute(() -> {
                    if (reader != null && reader.canRead()) reader.enable();
                });
            }
        } else {
            applyPreferences();
            updateTelemetry();
            updateOverlayText();
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "FPS+ VECTOR monitoring", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Visible notification while the FPS+ floating monitor is active.");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pending = PendingIntent.getActivity(this, 0, open,
                PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.drawable.ic_stat_fps)
                .setContentTitle("FPS+ VECTOR is active")
                .setContentText("Floating performance HUD is running")
                .setContentIntent(pending)
                .setOngoing(true)
                .setOnlyAlertOnce(true);
        return builder.build();
    }

    private void createOverlay() {
        if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        SharedPreferences prefs = AppPrefs.get(this);
        collapsed = prefs.getBoolean(AppPrefs.COLLAPSED, false);
        overlay = new LinearLayout(this);
        overlay.setOrientation(LinearLayout.VERTICAL);
        overlay.setPadding(dp(11), dp(9), dp(11), dp(8));
        overlay.setBackground(background(prefs.getInt(AppPrefs.OPACITY, 94)));

        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = text("FPS+  /  VECTOR", 9, GREEN, true);
        brand.setLetterSpacing(0.09f);
        header.addView(brand, new LinearLayout.LayoutParams(0, dp(26), 1));
        collapseButton = text(collapsed ? "+" : "−", 17, TEXT, true);
        collapseButton.setGravity(Gravity.CENTER);
        header.addView(collapseButton, new LinearLayout.LayoutParams(dp(28), dp(26)));
        TextView close = text("×", 17, MUTED, true);
        close.setGravity(Gravity.CENTER);
        header.addView(close, new LinearLayout.LayoutParams(dp(25), dp(26)));
        overlay.addView(header);

        fpsText = text("FPS: --", 19, MUTED, true);
        fpsText.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        LinearLayout.LayoutParams fpsParams = new LinearLayout.LayoutParams(-1, -2);
        fpsParams.topMargin = dp(8);
        overlay.addView(fpsText, fpsParams);

        View divider = new View(this);
        divider.setBackgroundColor(0x557A9584);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(-1, dp(1));
        dividerParams.topMargin = dp(7);
        overlay.addView(divider, dividerParams);

        metricList = new LinearLayout(this);
        metricList.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams metricsParams = new LinearLayout.LayoutParams(-1, -2);
        metricsParams.topMargin = dp(6);
        overlay.addView(metricList, metricsParams);

        resizeHandle = text("◢", 13, GREEN, true);
        resizeHandle.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams resizeParams = new LinearLayout.LayoutParams(-1, dp(18));
        resizeParams.topMargin = dp(2);
        overlay.addView(resizeHandle, resizeParams);

        collapseButton.setOnClickListener(v -> toggleCollapsed());
        close.setOnClickListener(v -> stopSelf());
        header.getChildAt(0).setOnTouchListener(new DragTouchListener());
        brand.setOnTouchListener(new DragTouchListener());
        resizeHandle.setOnTouchListener(new ResizeTouchListener());

        int width = prefs.getInt(AppPrefs.OVERLAY_W, dp(226));
        int height = prefs.getInt(AppPrefs.OVERLAY_H, defaultOverlayHeight(prefs));
        windowParams = new WindowManager.LayoutParams(width, height,
                Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        windowParams.gravity = Gravity.TOP | Gravity.START;
        windowParams.x = prefs.getInt(AppPrefs.OVERLAY_X, dp(12));
        windowParams.y = prefs.getInt(AppPrefs.OVERLAY_Y, dp(115));
        try {
            windowManager.addView(overlay, windowParams);
            applyPreferences();
            updateTelemetry();
            updateOverlayText();
        } catch (Exception e) {
            overlay = null;
            stopSelf();
        }
    }

    private void applyPreferences() {
        if (overlay == null) return;
        SharedPreferences prefs = AppPrefs.get(this);
        overlay.setBackground(background(prefs.getInt(AppPrefs.OPACITY, 94)));
        metricList.removeAllViews();
        if (prefs.getBoolean(AppPrefs.SHOW_RAM, false)) addMetricLine("RAM", "--");
        if (prefs.getBoolean(AppPrefs.SHOW_BATTERY, false)) addMetricLine("BAT", "--");
        if (prefs.getBoolean(AppPrefs.SHOW_TEMP, false)) addMetricLine("BAT TEMP", "--");
        if (metricList.getChildCount() == 0) addMetricLine("VECTOR", "FPS monitor active");
        if (!prefs.getBoolean(AppPrefs.SHOW_FPS, true)) fpsText.setVisibility(View.GONE);
        else fpsText.setVisibility(View.VISIBLE);
        collapsed = prefs.getBoolean(AppPrefs.COLLAPSED, false);
        applyCollapsedState();
        if (!collapsed && windowParams != null && overlay != null) {
            int requiredHeight = defaultOverlayHeight(prefs);
            if (windowParams.height < requiredHeight) {
                windowParams.height = requiredHeight;
                try { windowManager.updateViewLayout(overlay, windowParams); }
                catch (Exception ignored) { }
            }
        }
    }

    private void addMetricLine(String title, String value) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = text(title, 9, MUTED, true);
        row.addView(name, new LinearLayout.LayoutParams(0, dp(18), 1));
        TextView data = text(value, 9, TEXT, true);
        data.setTag("metric_" + title);
        row.addView(data);
        metricList.addView(row);
    }

    private void updateTelemetry() {
        if (overlay == null) return;
        DeviceMetrics metrics = DeviceMetrics.read(this);
        setMetric("RAM", metrics.ramPercent + "%  ·  "
                + DeviceMetrics.formatRam(metrics.usedRamBytes) + " / "
                + DeviceMetrics.formatRam(metrics.totalRamBytes));
        setMetric("BAT", metrics.batteryPercent < 0 ? "--" : metrics.batteryPercent + "%");
        setMetric("BAT TEMP", metrics.batteryTemperature);
        updateOverlayText();
    }

    private void setMetric(String name, String value) {
        if (metricList == null) return;
        for (int i = 0; i < metricList.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) metricList.getChildAt(i);
            for (int j = 0; j < row.getChildCount(); j++) {
                View child = row.getChildAt(j);
                if (child instanceof TextView && ("metric_" + name).equals(child.getTag())) {
                    ((TextView) child).setText(value);
                }
            }
        }
    }

    private void updateOverlayText() {
        if (fpsText == null) return;
        SharedPreferences prefs = AppPrefs.get(this);
        String value = latestFps < 0 ? "--" : String.format(Locale.US, "%.0f", latestFps);
        fpsText.setText("FPS:" + value);
        fpsText.setTextColor(fpsColor(latestFps));
        if (!prefs.getBoolean(AppPrefs.SHOW_FPS, true)) fpsText.setVisibility(View.GONE);
        else fpsText.setVisibility(View.VISIBLE);
        sendFpsBroadcast(latestFps);
    }

    private void sendFpsBroadcast(float fps) {
        Intent intent = new Intent(ACTION_METRICS);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_FPS, fps);
        sendBroadcast(intent);
    }

    private void toggleCollapsed() {
        collapsed = !collapsed;
        AppPrefs.get(this).edit().putBoolean(AppPrefs.COLLAPSED, collapsed).apply();
        applyCollapsedState();
        if (windowParams != null && overlay != null) {
            SharedPreferences prefs = AppPrefs.get(this);
            windowParams.height = collapsed ? dp(66) : Math.max(defaultOverlayHeight(prefs),
                    prefs.getInt(AppPrefs.OVERLAY_H, defaultOverlayHeight(prefs)));
            try { windowManager.updateViewLayout(overlay, windowParams); } catch (Exception ignored) { }
        }
    }

    private void applyCollapsedState() {
        if (metricList == null || collapseButton == null || resizeHandle == null) return;
        metricList.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        resizeHandle.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        collapseButton.setText(collapsed ? "+" : "−");
    }

    private int defaultOverlayHeight(SharedPreferences prefs) {
        int rows = 0;
        if (prefs.getBoolean(AppPrefs.SHOW_RAM, false)) rows++;
        if (prefs.getBoolean(AppPrefs.SHOW_BATTERY, false)) rows++;
        if (prefs.getBoolean(AppPrefs.SHOW_TEMP, false)) rows++;
        if (rows == 0) rows = 1;
        return dp(126 + Math.max(0, rows - 1) * 18);
    }

    private GradientDrawable background(int opacity) {
        int alpha = Math.max(55, Math.min(100, opacity)) * 255 / 100;
        GradientDrawable shape = new GradientDrawable();
        shape.setColor((alpha << 24) | 0x00111816);
        shape.setCornerRadius(dp(13));
        shape.setStroke(dp(1), 0xAA55735F);
        return shape;
    }

    private int fpsColor(float fps) {
        if (fps < 0) return MUTED;
        if (fps <= 10) return RED;
        if (fps <= 20) return ORANGE;
        if (fps <= 40) return YELLOW;
        if (fps <= 50) return GREEN_LIGHT;
        return GREEN_DARK;
    }

    private TextView text(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class DragTouchListener implements View.OnTouchListener {
        private int startX, startY;
        private float downX, downY;
        @Override public boolean onTouch(View view, MotionEvent event) {
            if (windowParams == null || overlay == null) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startX = windowParams.x;
                    startY = windowParams.y;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    windowParams.x = startX + (int) (event.getRawX() - downX);
                    windowParams.y = startY + (int) (event.getRawY() - downY);
                    try { windowManager.updateViewLayout(overlay, windowParams); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    AppPrefs.get(OverlayService.this).edit()
                            .putInt(AppPrefs.OVERLAY_X, windowParams.x)
                            .putInt(AppPrefs.OVERLAY_Y, windowParams.y).apply();
                    return true;
            }
            return true;
        }
    }

    private final class ResizeTouchListener implements View.OnTouchListener {
        private int startW, startH;
        private float downX, downY;
        @Override public boolean onTouch(View view, MotionEvent event) {
            if (windowParams == null || overlay == null || collapsed) return false;
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    startW = windowParams.width;
                    startH = windowParams.height;
                    downX = event.getRawX();
                    downY = event.getRawY();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    int newWidth = startW + (int) (event.getRawX() - downX);
                    int newHeight = startH + (int) (event.getRawY() - downY);
                    windowParams.width = Math.max(dp(172), Math.min(dp(360), newWidth));
                    windowParams.height = Math.max(dp(104), Math.min(dp(270), newHeight));
                    try { windowManager.updateViewLayout(overlay, windowParams); } catch (Exception ignored) { }
                    return true;
                case MotionEvent.ACTION_UP:
                    AppPrefs.get(OverlayService.this).edit()
                            .putInt(AppPrefs.OVERLAY_W, windowParams.width)
                            .putInt(AppPrefs.OVERLAY_H, windowParams.height).apply();
                    return true;
            }
            return true;
        }
    }

    @Override public void onDestroy() {
        main.removeCallbacks(telemetryTicker);
        main.removeCallbacks(fpsTicker);
        if (overlay != null && windowManager != null) {
            try { windowManager.removeView(overlay); } catch (Exception ignored) { }
        }
        overlay = null;
        try {
            worker.execute(() -> {
                if (reader != null) reader.disable();
                if (shellBridge != null) shellBridge.unbind();
            });
        } catch (Exception ignored) { }
        worker.shutdown();
        sendFpsBroadcast(-1f);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}

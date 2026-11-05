package com.fpsplus.vector;

import android.Manifest;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;

import rikka.shizuku.Shizuku;

public class MainActivity extends Activity {
    static final int SHIZUKU_REQUEST = 741;
    private static final int NOTIFICATION_REQUEST = 742;
    private static final int BG = 0xFF090D0C;
    private static final int PANEL = 0xFF111816;
    private static final int PANEL_ALT = 0xFF17211D;
    private static final int LINE = 0xFF26332D;
    private static final int TEXT = 0xFFEAF2EA;
    private static final int MUTED = 0xFF91A098;
    private static final int GREEN = 0xFFA6F36C;
    private static final int YELLOW = 0xFFFFC857;
    private static final int RED = 0xFFFF5F62;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private String currentScreen = "splash";
    private LinearLayout page;
    private TextView fpsValue;
    private TextView ramValue;
    private TextView ramDetail;
    private TextView batteryValue;
    private TextView temperatureValue;
    private RingGaugeView ramGauge;
    private boolean receiverRegistered;

    private final Shizuku.OnRequestPermissionResultListener shizukuPermissionListener =
            (requestCode, grantResult) -> runOnUiThread(() -> {
                if (requestCode == SHIZUKU_REQUEST && currentScreen.equals("permissions")) {
                    renderPermissions();
                }
            });

    private final android.content.BroadcastReceiver metricsReceiver = new android.content.BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent == null || !currentScreen.equals("dashboard")) return;
            float fps = intent.getFloatExtra(OverlayService.EXTRA_FPS, -1f);
            if (fpsValue != null) {
                fpsValue.setText(fps < 0 ? "--" : String.format(Locale.US, "%.0f", fps));
                fpsValue.setTextColor(fpsColor(fps));
            }
        }
    };

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (Build.VERSION.SDK_INT >= 33) {
            getWindow().getDecorView().setSystemUiVisibility(0);
        }
        try { Shizuku.addRequestPermissionResultListener(shizukuPermissionListener); }
        catch (Exception ignored) { }
        showSplash();
    }

    @Override protected void onResume() {
        super.onResume();
        if (currentScreen.equals("permissions")) renderPermissions();
        if (currentScreen.equals("dashboard")) refreshDashboardMetrics();
    }

    @Override protected void onDestroy() {
        try { Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener); }
        catch (Exception ignored) { }
        unregisterMetricsReceiver();
        super.onDestroy();
    }

    private void showSplash() {
        currentScreen = "splash";
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        SplashMarkView mark = new SplashMarkView(this);
        FrameLayout.LayoutParams markParams = new FrameLayout.LayoutParams(dp(172), dp(172), Gravity.CENTER);
        markParams.bottomMargin = dp(62);
        root.addView(mark, markParams);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
        infoParams.topMargin = dp(155);
        root.addView(info, infoParams);

        TextView title = label("FPS+ VECTOR", 23, TEXT, true);
        title.setLetterSpacing(0.13f);
        info.addView(title);
        TextView subtitle = label("SYSTEM PERFORMANCE MONITOR", 10, MUTED, true);
        subtitle.setLetterSpacing(0.18f);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(-2, -2);
        subParams.topMargin = dp(9);
        info.addView(subtitle, subParams);

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setPadding(dp(30), 0, dp(30), dp(38));
        bottom.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams bottomParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        root.addView(bottom, bottomParams);
        View barBg = new View(this);
        barBg.setBackground(round(0xFF1D2A24, 100));
        bottom.addView(barBg, new LinearLayout.LayoutParams(-1, dp(3)));
        View barFill = new View(this);
        barFill.setBackground(round(GREEN, 100));
        FrameLayout barFrame = new FrameLayout(this);
        barFrame.addView(barBg, new FrameLayout.LayoutParams(-1, dp(3)));
        FrameLayout.LayoutParams fillParams = new FrameLayout.LayoutParams(0, dp(3), Gravity.LEFT);
        barFrame.addView(barFill, fillParams);
        bottom.removeView(barBg);
        bottom.addView(barFrame, new LinearLayout.LayoutParams(-1, dp(3)));
        TextView loading = label("INITIALIZING SYSTEM", 9, MUTED, true);
        loading.setLetterSpacing(0.2f);
        LinearLayout.LayoutParams loadingParams = new LinearLayout.LayoutParams(-2, -2);
        loadingParams.topMargin = dp(15);
        bottom.addView(loading, loadingParams);

        setContentView(root);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(9000L);
        animator.setInterpolator(new DecelerateInterpolator(0.6f));
        animator.addUpdateListener(a -> {
            float progress = (float) a.getAnimatedValue();
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) barFill.getLayoutParams();
            lp.width = Math.max(1, (int) (barFrame.getWidth() * progress));
            barFill.setLayoutParams(lp);
            mark.setProgress(progress);
            loading.setText(progress < 0.34f ? "INITIALIZING SYSTEM"
                    : progress < 0.72f ? "CHECKING ENVIRONMENT" : "PREPARING INTERFACE");
        });
        animator.start();
        handler.postDelayed(() -> {
            boolean completed = AppPrefs.get(this).getBoolean("onboarding_complete", false);
            if (completed) showDashboard(); else renderPermissions();
        }, 9000L);
    }

    private void renderPermissions() {
        currentScreen = "permissions";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(20), dp(18), dp(20), dp(18));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView brand = label("FPS+  /  VECTOR", 12, GREEN, true);
        brand.setLetterSpacing(0.12f);
        top.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));
        TextView step = label("SETUP  01 / 01", 9, MUTED, true);
        step.setLetterSpacing(0.12f);
        top.addView(step);
        root.addView(top);

        TextView heading = label("Give VECTOR\nroom to work.", 31, TEXT, true);
        heading.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams headingParams = new LinearLayout.LayoutParams(-1, -2);
        headingParams.topMargin = dp(30);
        root.addView(heading, headingParams);
        TextView note = label("A few system permissions are needed for the floating monitor and live diagnostics.", 13, MUTED, false);
        note.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams noteParams = new LinearLayout.LayoutParams(-1, -2);
        noteParams.topMargin = dp(10);
        root.addView(note, noteParams);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(20);
        scroll.addView(rows);
        root.addView(scroll, scrollParams);

        boolean overlay = Settings.canDrawOverlays(this);
        boolean shizuku = isShizukuGranted();
        boolean notification = hasNotificationPermission();
        boolean background = isBackgroundExempt();

        rows.addView(permissionRow("01", "Display over apps", "Allows the live HUD to stay above games and apps.",
                overlay, "OPEN SETTINGS", this::openOverlaySettings, true));
        rows.addView(permissionRow("02", "Shizuku access", "Used to request SurfaceFlinger frame statistics. Requires Shizuku to be running.",
                shizuku, shizuku ? "AUTHORIZED" : "CONNECT", this::requestShizuku, true));
        rows.addView(permissionRow("03", "Notifications", "Keeps the monitoring service visible while it is active.",
                notification, notification ? "ALLOWED" : "ALLOW", this::requestNotificationPermission, true));
        rows.addView(permissionRow("04", "Background activity", "Allows Android to be less likely to suspend the monitor during background use.",
                background, background ? "UNRESTRICTED" : "REQUIRED", this::openBackgroundSettings, true));

        TextView footnote = label("FPS access can vary by Android build. VECTOR shows “--” when the system does not expose frame data; it never invents a reading.", 10, MUTED, false);
        footnote.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(-1, -2);
        footParams.topMargin = dp(12);
        rows.addView(footnote, footParams);

        TextView next = button("CONTINUE  →", true);
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(-1, dp(54));
        nextParams.topMargin = dp(14);
        root.addView(next, nextParams);
        boolean ready = overlay && shizuku && notification && background;
        next.setVisibility(ready ? View.VISIBLE : View.GONE);
        next.setOnClickListener(v -> {
            AppPrefs.get(this).edit().putBoolean("onboarding_complete", true).apply();
            showDashboard();
        });
        if (ready) {
            next.setAlpha(0f);
            next.setTranslationY(dp(38));
            next.animate().alpha(1f).translationY(0).setDuration(420)
                    .setInterpolator(new DecelerateInterpolator()).start();
        }
        setContentView(root);
    }

    private View permissionRow(String number, String title, String description, boolean granted,
                               String action, Runnable click, boolean required) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(13));
        card.setBackground(round(PANEL, dp(17), LINE, dp(1)));
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
        cardParams.bottomMargin = dp(9);
        card.setLayoutParams(cardParams);

        LinearLayout upper = new LinearLayout(this);
        upper.setGravity(Gravity.CENTER_VERTICAL);
        TextView index = label(number, 10, MUTED, true);
        index.setLetterSpacing(0.1f);
        upper.addView(index, new LinearLayout.LayoutParams(dp(27), -2));
        TextView name = label(title, 14, TEXT, true);
        upper.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        TextView state = label(granted ? "GRANTED" : required ? "REQUIRED" : "OPTIONAL", 8,
                granted ? GREEN : YELLOW, true);
        state.setLetterSpacing(0.12f);
        upper.addView(state);
        card.addView(upper);

        TextView detail = label(description, 11, MUTED, false);
        detail.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.leftMargin = dp(27);
        detailParams.topMargin = dp(8);
        card.addView(detail, detailParams);

        TextView button = label(action + "   ↗", 9, granted ? GREEN : (required ? YELLOW : MUTED), true);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(27), dp(12), 0, 0);
        button.setLetterSpacing(0.1f);
        card.addView(button);
        card.setOnClickListener(v -> click.run());
        button.setOnClickListener(v -> click.run());
        return card;
    }

    private void showDashboard() {
        currentScreen = "dashboard";
        unregisterMetricsReceiver();
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(metricsReceiver, new android.content.IntentFilter(OverlayService.ACTION_METRICS),
                    Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(metricsReceiver, new android.content.IntentFilter(OverlayService.ACTION_METRICS));
        }
        receiverRegistered = true;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(30));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout brandBlock = new LinearLayout(this);
        brandBlock.setOrientation(LinearLayout.VERTICAL);
        TextView brand = label("FPS+ VECTOR", 15, TEXT, true);
        brand.setLetterSpacing(0.1f);
        brandBlock.addView(brand);
        TextView overline = label("PERFORMANCE SYSTEM", 8, MUTED, true);
        overline.setLetterSpacing(0.16f);
        LinearLayout.LayoutParams overParams = new LinearLayout.LayoutParams(-2, -2);
        overParams.topMargin = dp(4);
        brandBlock.addView(overline, overParams);
        top.addView(brandBlock, new LinearLayout.LayoutParams(0, -2, 1));
        TextView settings = smallButton("SETTINGS", false);
        top.addView(settings);
        settings.setOnClickListener(v -> showSettings());
        content.addView(top);

        LinearLayout hero = panel();
        hero.setPadding(dp(17), dp(17), dp(17), dp(16));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1, -2);
        heroParams.topMargin = dp(23);
        content.addView(hero, heroParams);
        LinearLayout heroTop = new LinearLayout(this);
        heroTop.setGravity(Gravity.CENTER_VERTICAL);
        TextView liveTag = label("LIVE TELEMETRY", 9, GREEN, true);
        liveTag.setLetterSpacing(0.15f);
        heroTop.addView(liveTag, new LinearLayout.LayoutParams(0, -2, 1));
        TextView mode = label("SHIZUKU  /  SF", 8, MUTED, true);
        mode.setLetterSpacing(0.08f);
        heroTop.addView(mode);
        hero.addView(heroTop);

        LinearLayout fpsBlock = new LinearLayout(this);
        fpsBlock.setGravity(Gravity.CENTER_VERTICAL);
        fpsBlock.setPadding(0, dp(18), 0, dp(12));
        fpsValue = label("--", 52, TEXT, true);
        fpsValue.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        fpsBlock.addView(fpsValue);
        TextView fpsUnit = label(" FPS", 14, MUTED, true);
        fpsUnit.setPadding(dp(6), dp(17), 0, 0);
        fpsBlock.addView(fpsUnit);
        hero.addView(fpsBlock);
        View separator = new View(this);
        separator.setBackgroundColor(LINE);
        hero.addView(separator, new LinearLayout.LayoutParams(-1, dp(1)));
        TextView fpsInfo = label("Frame rate appears after SurfaceFlinger reports a frame sample.", 10, MUTED, false);
        LinearLayout.LayoutParams fpsInfoParams = new LinearLayout.LayoutParams(-1, -2);
        fpsInfoParams.topMargin = dp(10);
        hero.addView(fpsInfo, fpsInfoParams);

        LinearLayout sectionTitle = new LinearLayout(this);
        sectionTitle.setGravity(Gravity.CENTER_VERTICAL);
        TextView snapshot = label("DEVICE SNAPSHOT", 10, MUTED, true);
        snapshot.setLetterSpacing(0.16f);
        sectionTitle.addView(snapshot, new LinearLayout.LayoutParams(0, -2, 1));
        TextView realtime = label("REFRESHING", 8, GREEN, true);
        realtime.setLetterSpacing(0.12f);
        sectionTitle.addView(realtime);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(-1, -2);
        sectionParams.topMargin = dp(24);
        sectionParams.bottomMargin = dp(10);
        content.addView(sectionTitle, sectionParams);

        LinearLayout firstRow = new LinearLayout(this);
        firstRow.setOrientation(LinearLayout.HORIZONTAL);
        ramGauge = new RingGaugeView(this);
        LinearLayout ramCard = metricPanel("MEMORY", firstRow);
        content.addView(firstRow, new LinearLayout.LayoutParams(-1, -2));
        ramCard.setPadding(dp(13), dp(13), dp(13), dp(13));
        LinearLayout ramContent = new LinearLayout(this);
        ramContent.setGravity(Gravity.CENTER_VERTICAL);
        ramContent.addView(ramGauge, new LinearLayout.LayoutParams(dp(76), dp(76)));
        LinearLayout ramText = new LinearLayout(this);
        ramText.setOrientation(LinearLayout.VERTICAL);
        ramValue = label("--", 20, TEXT, true);
        ramText.addView(ramValue);
        TextView ramHint = label("SYSTEM RAM", 8, MUTED, true);
        ramHint.setLetterSpacing(0.11f);
        LinearLayout.LayoutParams ramHintParams = new LinearLayout.LayoutParams(-2, -2);
        ramHintParams.topMargin = dp(5);
        ramText.addView(ramHint, ramHintParams);
        ramDetail = label("-- / -- GB", 8, MUTED, false);
        LinearLayout.LayoutParams ramDetailParams = new LinearLayout.LayoutParams(-1, -2);
        ramDetailParams.topMargin = dp(5);
        ramText.addView(ramDetail, ramDetailParams);
        LinearLayout.LayoutParams ramTextParams = new LinearLayout.LayoutParams(0, -2, 1);
        ramTextParams.leftMargin = dp(10);
        ramContent.addView(ramText, ramTextParams);
        ramCard.addView(ramContent);

        LinearLayout secondRow = new LinearLayout(this);
        secondRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout batteryCard = metricPanel("BATTERY", secondRow);
        batteryValue = label("--", 22, TEXT, true);
        batteryCard.addView(batteryValue);
        TextView batteryHint = label("CHARGE LEVEL", 8, MUTED, true);
        batteryHint.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams bHint = new LinearLayout.LayoutParams(-2, -2);
        bHint.topMargin = dp(7);
        batteryCard.addView(batteryHint, bHint);
        LinearLayout tempCard = metricPanel("BATTERY TEMP", secondRow);
        LinearLayout.LayoutParams secondRowParams = new LinearLayout.LayoutParams(-1, -2);
        secondRowParams.topMargin = dp(9);
        content.addView(secondRow, secondRowParams);
        temperatureValue = label("--", 22, TEXT, true);
        tempCard.addView(temperatureValue);
        TextView tempHint = label("SENSOR READING", 8, MUTED, true);
        tempHint.setLetterSpacing(0.1f);
        LinearLayout.LayoutParams tHint = new LinearLayout.LayoutParams(-2, -2);
        tHint.topMargin = dp(7);
        tempCard.addView(tempHint, tHint);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = dp(22);
        content.addView(actions, actionsParams);
        TextView start = button("START OVERLAY", true);
        actions.addView(start, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView stop = button("STOP", false);
        LinearLayout.LayoutParams stopParams = new LinearLayout.LayoutParams(dp(92), dp(52));
        stopParams.leftMargin = dp(9);
        actions.addView(stop, stopParams);
        start.setOnClickListener(v -> startOverlay());
        stop.setOnClickListener(v -> stopOverlay());

        LinearLayout foot = panel();
        foot.setPadding(dp(14), dp(13), dp(14), dp(13));
        LinearLayout.LayoutParams footParams = new LinearLayout.LayoutParams(-1, -2);
        footParams.topMargin = dp(12);
        content.addView(foot, footParams);
        TextView footTitle = label("MONITOR STATUS", 9, GREEN, true);
        footTitle.setLetterSpacing(0.12f);
        foot.addView(footTitle);
        TextView footText = label("HUD stays active while its foreground service is running. Frame access depends on the device firmware.", 10, MUTED, false);
        footText.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams ft = new LinearLayout.LayoutParams(-1, -2);
        ft.topMargin = dp(7);
        foot.addView(footText, ft);

        setContentView(root);
        refreshDashboardMetrics();
        handler.removeCallbacks(dashboardTicker);
        handler.post(dashboardTicker);
    }

    private LinearLayout metricPanel(String title, LinearLayout parent) {
        LinearLayout card = panel();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        if (parent.getChildCount() > 0) params.leftMargin = dp(9);
        parent.addView(card, params);
        TextView heading = label(title, 8, MUTED, true);
        heading.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams h = new LinearLayout.LayoutParams(-1, -2);
        h.bottomMargin = dp(12);
        card.addView(heading, h);
        return card;
    }

    private final Runnable dashboardTicker = new Runnable() {
        @Override public void run() {
            if (!currentScreen.equals("dashboard")) return;
            refreshDashboardMetrics();
            handler.postDelayed(this, 1000L);
        }
    };

    private void refreshDashboardMetrics() {
        DeviceMetrics metrics = DeviceMetrics.read(this);
        if (ramValue != null) {
            ramValue.setText(metrics.ramPercent + "%");
            if (ramDetail != null) ramDetail.setText(DeviceMetrics.formatRam(metrics.usedRamBytes)
                    + " / " + DeviceMetrics.formatRam(metrics.totalRamBytes));
            ramGauge.setPercent(metrics.ramPercent);
        }
        if (batteryValue != null) batteryValue.setText(metrics.batteryPercent < 0 ? "--" : metrics.batteryPercent + "%");
        if (temperatureValue != null) temperatureValue.setText(metrics.batteryTemperature);
    }

    private void showSettings() {
        currentScreen = "settings";
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(18), dp(20), dp(18), dp(22));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView back = smallButton("←  BACK", false);
        top.addView(back);
        TextView heading = label("DISPLAY SETTINGS", 10, MUTED, true);
        heading.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams headParams = new LinearLayout.LayoutParams(0, -2, 1);
        top.addView(heading, headParams);
        root.addView(top);
        back.setOnClickListener(v -> showDashboard());

        TextView title = label("Make it\nyour HUD.", 31, TEXT, true);
        title.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = dp(28);
        root.addView(title, titleParams);

        android.content.SharedPreferences prefs = AppPrefs.get(this);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, 0, 1);
        scrollParams.topMargin = dp(20);
        scroll.addView(list);
        root.addView(scroll, scrollParams);

        LinearLayout metrics = panel();
        metrics.setPadding(dp(14), dp(14), dp(14), dp(8));
        list.addView(metrics);
        TextView metricsTitle = label("VISIBLE METRICS", 9, GREEN, true);
        metricsTitle.setLetterSpacing(0.13f);
        metrics.addView(metricsTitle);
        addMetricSwitch(metrics, "FPS", "show_fps", prefs.getBoolean(AppPrefs.SHOW_FPS, true));
        addMetricSwitch(metrics, "Memory usage", AppPrefs.SHOW_RAM, prefs.getBoolean(AppPrefs.SHOW_RAM, false));
        addMetricSwitch(metrics, "Battery level", AppPrefs.SHOW_BATTERY, prefs.getBoolean(AppPrefs.SHOW_BATTERY, false));
        addMetricSwitch(metrics, "Battery temperature", AppPrefs.SHOW_TEMP, prefs.getBoolean(AppPrefs.SHOW_TEMP, false));

        LinearLayout tune = panel();
        tune.setPadding(dp(14), dp(14), dp(14), dp(15));
        LinearLayout.LayoutParams tuneParams = new LinearLayout.LayoutParams(-1, -2);
        tuneParams.topMargin = dp(11);
        list.addView(tune, tuneParams);
        TextView tuneTitle = label("OVERLAY APPEARANCE", 9, GREEN, true);
        tuneTitle.setLetterSpacing(0.13f);
        tune.addView(tuneTitle);

        LinearLayout opacityHead = new LinearLayout(this);
        opacityHead.setGravity(Gravity.CENTER_VERTICAL);
        TextView opacityLabel = label("Panel opacity", 12, TEXT, true);
        opacityHead.addView(opacityLabel, new LinearLayout.LayoutParams(0, -2, 1));
        TextView opacityNumber = label(prefs.getInt(AppPrefs.OPACITY, 94) + "%", 11, MUTED, true);
        opacityHead.addView(opacityNumber);
        LinearLayout.LayoutParams oh = new LinearLayout.LayoutParams(-1, -2);
        oh.topMargin = dp(17);
        tune.addView(opacityHead, oh);
        SeekBar opacity = new SeekBar(this);
        opacity.setMax(45);
        opacity.setProgress(Math.max(0, Math.min(45, prefs.getInt(AppPrefs.OPACITY, 94) - 55)));
        opacity.setProgressTintList(android.content.res.ColorStateList.valueOf(GREEN));
        opacity.setThumbTintList(android.content.res.ColorStateList.valueOf(GREEN));
        tune.addView(opacity);
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                opacityNumber.setText((progress + 55) + "%");
                if (fromUser) prefs.edit().putInt(AppPrefs.OPACITY, progress + 55).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { refreshRunningOverlay(); }
        });

        TextView refreshTitle = label("Refresh interval", 12, TEXT, true);
        LinearLayout.LayoutParams rt = new LinearLayout.LayoutParams(-1, -2);
        rt.topMargin = dp(14);
        tune.addView(refreshTitle, rt);
        LinearLayout intervalRow = new LinearLayout(this);
        intervalRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams ir = new LinearLayout.LayoutParams(-1, dp(40));
        ir.topMargin = dp(9);
        tune.addView(intervalRow, ir);
        int[] intervals = {500, 750, 1000, 1500, 2000};
        for (int interval : intervals) {
            TextView choice = label(interval < 1000 ? interval + "ms" : (interval / 1000f) + "s", 9,
                    prefs.getInt(AppPrefs.REFRESH_MS, 1000) == interval ? GREEN : MUTED, true);
            choice.setGravity(Gravity.CENTER);
            choice.setBackground(round(prefs.getInt(AppPrefs.REFRESH_MS, 1000) == interval ? PANEL_ALT : PANEL, dp(9), LINE, dp(1)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, -1, 1);
            if (intervalRow.getChildCount() > 0) cp.leftMargin = dp(5);
            intervalRow.addView(choice, cp);
            choice.setOnClickListener(v -> {
                prefs.edit().putInt(AppPrefs.REFRESH_MS, interval).apply();
                for (int i = 0; i < intervalRow.getChildCount(); i++) {
                    TextView child = (TextView) intervalRow.getChildAt(i);
                    int val = intervals[i];
                    boolean active = val == interval;
                    child.setTextColor(active ? GREEN : MUTED);
                    child.setBackground(round(active ? PANEL_ALT : PANEL, dp(9), LINE, dp(1)));
                }
                refreshRunningOverlay();
            });
        }

        TextView note = label("FPS sampling is separate from the visual refresh. Faster polling can use more system resources; 1 second is the balanced default.", 10, MUTED, false);
        note.setLineSpacing(dp(2), 1f);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = dp(14);
        list.addView(note, np);

        TextView done = button("SAVE & UPDATE HUD", true);
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(-1, dp(52));
        doneParams.topMargin = dp(13);
        root.addView(done, doneParams);
        done.setOnClickListener(v -> {
            refreshRunningOverlay();
            showDashboard();
        });
        setContentView(root);
    }

    private void addMetricSwitch(LinearLayout parent, String title, String key, boolean initial) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(46));
        parent.addView(row, rp);
        TextView text = label(title, 12, TEXT, false);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
        Switch toggle = new Switch(this);
        toggle.setChecked(initial);
        toggle.setThumbTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{GREEN, 0xFF607068}));
        toggle.setTrackTintList(new android.content.res.ColorStateList(
                new int[][]{new int[]{android.R.attr.state_checked}, new int[]{}},
                new int[]{0x887DAF5B, 0xFF2A3630}));
        row.addView(toggle);
        toggle.setOnCheckedChangeListener((buttonView, checked) ->
                AppPrefs.get(this).edit().putBoolean(key, checked).apply());
    }

    private void startOverlay() {
        if (!Settings.canDrawOverlays(this)) {
            Toast.makeText(this, "Allow display over apps first.", Toast.LENGTH_LONG).show();
            openOverlaySettings();
            return;
        }
        Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent); else startService(intent);
            Toast.makeText(this, "FPS+ overlay started", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Could not start the overlay service: " + e.getClass().getSimpleName(), Toast.LENGTH_LONG).show();
        }
    }

    private void stopOverlay() {
        Intent intent = new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_STOP);
        startService(intent);
    }

    private void refreshRunningOverlay() {
        try { startService(new Intent(this, OverlayService.class).setAction(OverlayService.ACTION_REFRESH)); }
        catch (Exception ignored) { }
    }

    private void openOverlaySettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (Exception e) { openAppDetails(); }
    }

    private void requestShizuku() {
        try {
            if (!Shizuku.pingBinder()) {
                Intent launch = getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");
                if (launch != null) startActivity(launch);
                else Toast.makeText(this, "Start Shizuku, then return here.", Toast.LENGTH_LONG).show();
                return;
            }
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                renderPermissions();
            } else {
                Shizuku.requestPermission(SHIZUKU_REQUEST);
            }
        } catch (Exception e) {
            Toast.makeText(this, "Shizuku is not ready. Open Shizuku and start its service.", Toast.LENGTH_LONG).show();
        }
    }

    private void requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_REQUEST);
        } else renderPermissions();
    }

    private void openBackgroundSettings() {
        try {
            if (Build.VERSION.SDK_INT >= 23 && !isBackgroundExempt()) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            } else openAppDetails();
        } catch (Exception e) { openAppDetails(); }
    }

    private void openAppDetails() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName()))); }
        catch (Exception ignored) { }
    }

    private boolean hasNotificationPermission() {
        return Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private boolean isShizukuGranted() {
        try { return Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED; }
        catch (Exception e) { return false; }
    }

    private boolean isBackgroundExempt() {
        if (Build.VERSION.SDK_INT < 23) return true;
        PowerManager manager = (PowerManager) getSystemService(POWER_SERVICE);
        return manager != null && manager.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void unregisterMetricsReceiver() {
        if (receiverRegistered) {
            try { unregisterReceiver(metricsReceiver); } catch (Exception ignored) { }
            receiverRegistered = false;
        }
    }

    private int fpsColor(float fps) {
        if (fps < 0) return MUTED;
        if (fps <= 10) return RED;
        if (fps <= 20) return 0xFFFF9B43;
        if (fps <= 40) return YELLOW;
        if (fps <= 50) return 0xFF79D982;
        return 0xFF3BBA68;
    }

    private LinearLayout panel() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setBackground(round(PANEL, dp(17), LINE, dp(1)));
        return view;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(false);
        view.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private TextView button(String value, boolean primary) {
        TextView view = label(value, 10, primary ? BG : TEXT, true);
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.12f);
        view.setBackground(round(primary ? GREEN : PANEL_ALT, dp(12), primary ? GREEN : LINE, dp(1)));
        return view;
    }

    private TextView smallButton(String value, boolean primary) {
        TextView view = label(value, 9, primary ? BG : GREEN, true);
        view.setPadding(dp(12), dp(9), dp(12), dp(9));
        view.setGravity(Gravity.CENTER);
        view.setLetterSpacing(0.09f);
        view.setBackground(round(primary ? GREEN : PANEL, dp(9), LINE, dp(1)));
        return view;
    }

    private GradientDrawable round(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable round(int color, int radius, int stroke, int strokeWidth) {
        GradientDrawable d = round(color, radius);
        d.setStroke(strokeWidth, stroke);
        return d;
    }

    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }

    private final class SplashMarkView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float progress;
        SplashMarkView(Context context) { super(context); }
        void setProgress(float progress) { this.progress = progress; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.37f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(0xFF24352B);
            canvas.drawCircle(cx, cy, radius, paint);
            paint.setStrokeWidth(dp(5));
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(GREEN);
            canvas.drawArc(new RectF(cx - radius, cy - radius, cx + radius, cy + radius),
                    -90, 360 * progress, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(GREEN);
            canvas.drawRoundRect(cx - dp(4), cy - dp(28), cx + dp(4), cy + dp(28), dp(3), dp(3), paint);
            canvas.drawRoundRect(cx - dp(28), cy - dp(4), cx + dp(28), cy + dp(4), dp(3), dp(3), paint);
            paint.setColor(0xFF5D7A64);
            canvas.drawCircle(cx + radius * 0.84f, cy - radius * 0.84f, dp(3), paint);
        }
    }

    private final class RingGaugeView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int percent;
        RingGaugeView(Context context) { super(context); }
        void setPercent(int percent) { this.percent = percent; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float stroke = dp(6);
            float side = Math.min(getWidth(), getHeight()) - stroke * 2;
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            RectF rect = new RectF(cx - side / 2, cy - side / 2, cx + side / 2, cy + side / 2);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(stroke);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setColor(LINE);
            canvas.drawArc(rect, 0, 360, false, paint);
            paint.setColor(percent >= 85 ? YELLOW : GREEN);
            canvas.drawArc(rect, -90, percent * 3.6f, false, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(TEXT);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            paint.setTextSize(dp(15));
            canvas.drawText(percent + "%", cx, cy + dp(5), paint);
        }
    }
}

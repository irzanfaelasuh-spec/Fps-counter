package com.fpsplus.vector;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;

import java.util.Locale;

final class DeviceMetrics {
    final int batteryPercent;
    final String batteryTemperature;
    final long usedRamBytes;
    final long totalRamBytes;
    final int ramPercent;

    private DeviceMetrics(int batteryPercent, String batteryTemperature,
                          long usedRamBytes, long totalRamBytes, int ramPercent) {
        this.batteryPercent = batteryPercent;
        this.batteryTemperature = batteryTemperature;
        this.usedRamBytes = usedRamBytes;
        this.totalRamBytes = totalRamBytes;
        this.ramPercent = ramPercent;
    }

    static DeviceMetrics read(Context context) {
        int battery = -1;
        String temperature = "--";
        try {
            Intent batteryIntent = context.registerReceiver(null,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (batteryIntent != null) {
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = batteryIntent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) battery = Math.round(level * 100f / scale);
                int rawTemp = batteryIntent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                if (rawTemp > 0) temperature = String.format(Locale.US, "%.1f°C", rawTemp / 10f);
            }
        } catch (Exception ignored) { }

        long used = 0;
        long total = 0;
        int percent = 0;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            if (manager != null) {
                manager.getMemoryInfo(info);
                total = info.totalMem;
                used = Math.max(0L, total - info.availMem);
                if (total > 0) percent = Math.min(100, Math.max(0, Math.round(used * 100f / total)));
            }
        } catch (Exception ignored) { }
        return new DeviceMetrics(battery, temperature, used, total, percent);
    }

    static String formatRam(long bytes) {
        if (bytes <= 0) return "--";
        return String.format(Locale.US, "%.1f GB", bytes / (1024d * 1024d * 1024d));
    }
}

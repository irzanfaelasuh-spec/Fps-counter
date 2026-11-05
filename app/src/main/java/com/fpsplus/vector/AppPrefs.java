package com.fpsplus.vector;

import android.content.Context;
import android.content.SharedPreferences;

final class AppPrefs {
    static final String FILE = "fps_vector_settings";
    static final String SHOW_FPS = "show_fps";
    static final String SHOW_RAM = "show_ram";
    static final String SHOW_BATTERY = "show_battery";
    static final String SHOW_TEMP = "show_temp";
    static final String OPACITY = "opacity";
    static final String REFRESH_MS = "refresh_ms";
    static final String OVERLAY_X = "overlay_x";
    static final String OVERLAY_Y = "overlay_y";
    static final String OVERLAY_W = "overlay_w";
    static final String OVERLAY_H = "overlay_h";
    static final String COLLAPSED = "overlay_collapsed";

    private AppPrefs() { }

    static SharedPreferences get(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    static boolean showAnyMetric(SharedPreferences p) {
        return p.getBoolean(SHOW_FPS, true)
                || p.getBoolean(SHOW_RAM, false)
                || p.getBoolean(SHOW_BATTERY, false)
                || p.getBoolean(SHOW_TEMP, false);
    }
}

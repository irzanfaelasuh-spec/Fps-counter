package com.fpsplus.vector;

import android.os.SystemClock;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads cumulative SurfaceFlinger layer counters and converts counter deltas to FPS. */
final class SurfaceFlingerReader {
    private static final Pattern RESUMED = Pattern.compile(
            "(?:mResumedActivity|topResumedActivity).*?\\s([A-Za-z0-9_.]+)/");
    private static final Pattern PACKAGE = Pattern.compile("packageName\\s*=\\s*([^\\s]+)");
    private static final Pattern LAYER = Pattern.compile("layerName\\s*=\\s*(.+)");
    private static final Pattern FRAMES = Pattern.compile("totalFrames\\s*=\\s*(\\d+)");

    private final ShizukuShellBridge shell;
    private String previousPackage = "";
    private long previousFrames = -1;
    private long previousAt = -1;
    private boolean enabled;

    SurfaceFlingerReader(ShizukuShellBridge shell) {
        this.shell = shell;
    }

    boolean canRead() {
        return shell != null && shell.isReady();
    }

    boolean enable() {
        String result = shell == null ? null : shell.run("dumpsys SurfaceFlinger --timestats -clear -enable");
        previousPackage = "";
        previousFrames = -1;
        previousAt = -1;
        enabled = result != null;
        return enabled;
    }

    Float sampleFps() {
        if (!canRead()) return null;
        if (!enabled && !enable()) return null;
        if (!enabled) return null;
        String foreground = readForegroundPackage();
        if (foreground.isEmpty()) return null;
        String dump = shell.run("dumpsys SurfaceFlinger --timestats -dump");
        if (dump == null || dump.isEmpty()) return null;

        LayerCounter selected = chooseForegroundLayer(dump, foreground);
        if (selected == null) return null;
        long now = SystemClock.elapsedRealtime();
        if (!selected.packageName.equals(previousPackage) || previousFrames < 0
                || selected.frames < previousFrames || previousAt < 0) {
            previousPackage = selected.packageName;
            previousFrames = selected.frames;
            previousAt = now;
            return null;
        }
        long elapsed = Math.max(1L, now - previousAt);
        long delta = selected.frames - previousFrames;
        previousFrames = selected.frames;
        previousAt = now;
        if (delta > 500) return null;
        return Math.max(0f, Math.min(1000f, delta * 1000f / elapsed));
    }

    void disable() {
        if (shell != null && shell.isReady()) shell.run("dumpsys SurfaceFlinger --timestats -disable");
        previousFrames = -1;
        previousAt = -1;
        previousPackage = "";
        enabled = false;
    }

    private String readForegroundPackage() {
        String output = shell.run("dumpsys activity activities");
        if (output == null) return "";
        Matcher matcher = RESUMED.matcher(output);
        return matcher.find() ? matcher.group(1) : "";
    }

    private LayerCounter chooseForegroundLayer(String dump, String foreground) {
        String[] lines = dump.split("\\r?\\n");
        List<LayerCounter> layers = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            Matcher layerMatcher = LAYER.matcher(lines[i]);
            if (!layerMatcher.find()) continue;

            String layerName = layerMatcher.group(1).trim();
            String packageName = "";
            long frames = -1;
            for (int j = i + 1; j < lines.length; j++) {
                if (LAYER.matcher(lines[j]).find()) break;
                Matcher packageMatcher = PACKAGE.matcher(lines[j]);
                Matcher frameMatcher = FRAMES.matcher(lines[j]);
                if (packageMatcher.find()) packageName = packageMatcher.group(1).trim();
                if (frameMatcher.find()) frames = safeLong(frameMatcher.group(1));
                if (frames >= 0 && !packageName.isEmpty()) break;
            }
            if (packageName.isEmpty() && !layerName.isEmpty()) {
                int slash = layerName.indexOf('/');
                int bracket = layerName.indexOf('[');
                if (slash > 0) packageName = layerName.substring(0, slash);
                else if (bracket >= 0) {
                    int end = layerName.indexOf('/', bracket);
                    if (end > bracket) packageName = layerName.substring(bracket + 1, end);
                }
            }
            if (frames >= 0) layers.add(new LayerCounter(packageName, layerName, frames));
        }
        for (LayerCounter item : layers) {
            if (item.packageName.equals(foreground) || item.layerName.contains(foreground)) return item;
        }
        // Do not substitute another app's layer when the foreground layer is unavailable.
        return null;
    }

    private long safeLong(String value) {
        try { return Long.parseLong(value); } catch (Exception e) { return -1; }
    }

    private static final class LayerCounter {
        final String packageName;
        final String layerName;
        final long frames;
        LayerCounter(String packageName, String layerName, long frames) {
            this.packageName = packageName;
            this.layerName = layerName;
            this.frames = frames;
        }
    }
}

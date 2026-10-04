package com.example.clocklauncher;

import android.content.SharedPreferences;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public final class ClockPrefs {
    public static final String NAME = "launcher_clock_settings";

    public static final String KEY_TEXT_COLOR = "text_color";
    public static final String KEY_SHADOW_COLOR = "shadow_color";
    public static final String KEY_TEXT_SIZE = "text_size";
    public static final String KEY_FORMAT = "time_format";
    public static final String KEY_BOLD = "bold";

    public static final String KEY_DESKTOP_TEXT_COLOR = "desktop_text_color";
    public static final String KEY_DESKTOP_SHADOW_COLOR = "desktop_shadow_color";
    public static final String KEY_DESKTOP_TEXT_SIZE = "desktop_text_size";
    public static final String KEY_DESKTOP_FORMAT = "desktop_time_format";
    public static final String KEY_DESKTOP_BOLD = "desktop_bold";
    public static final String KEY_DESKTOP_SHOW_BATTERY = "desktop_show_battery";
    public static final String KEY_DESKTOP_SHOW_NETWORK = "desktop_show_network";
    public static final String KEY_DESKTOP_SHOW_CPU = "desktop_show_cpu";
    public static final String KEY_CPU_OVERLAY = "cpu_overlay";
    public static final String KEY_CPU_OVERLAY_X = "cpu_overlay_x";
    public static final String KEY_CPU_OVERLAY_Y = "cpu_overlay_y";

    public static final String DEFAULT_TEXT_COLOR = "#FFFFFF";
    public static final String DEFAULT_SHADOW_COLOR = "#FF000000";
    public static final int DEFAULT_TEXT_SIZE = 36;
    public static final String DEFAULT_FORMAT = "HH:mm:ss";

    public static final String DEFAULT_DESKTOP_TEXT_COLOR = "#FFFFFF";
    public static final String DEFAULT_DESKTOP_SHADOW_COLOR = "#FF000000";
    public static final int DEFAULT_DESKTOP_TEXT_SIZE = 76;
    public static final String DEFAULT_DESKTOP_FORMAT = "HH:mm:ss";

    private ClockPrefs() { }

    public static void ensureDefaults(SharedPreferences prefs) {
        SharedPreferences.Editor editor = prefs.edit();
        boolean changed = false;
        if (!prefs.contains(KEY_TEXT_COLOR)) { editor.putString(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR); changed = true; }
        if (!prefs.contains(KEY_SHADOW_COLOR)) { editor.putString(KEY_SHADOW_COLOR, DEFAULT_SHADOW_COLOR); changed = true; }
        if (!prefs.contains(KEY_TEXT_SIZE)) { editor.putInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE); changed = true; }
        if (!prefs.contains(KEY_FORMAT)) { editor.putString(KEY_FORMAT, DEFAULT_FORMAT); changed = true; }
        if (!prefs.contains(KEY_BOLD)) { editor.putBoolean(KEY_BOLD, false); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_TEXT_COLOR)) { editor.putString(KEY_DESKTOP_TEXT_COLOR, DEFAULT_DESKTOP_TEXT_COLOR); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_SHADOW_COLOR)) { editor.putString(KEY_DESKTOP_SHADOW_COLOR, DEFAULT_DESKTOP_SHADOW_COLOR); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_TEXT_SIZE)) { editor.putInt(KEY_DESKTOP_TEXT_SIZE, DEFAULT_DESKTOP_TEXT_SIZE); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_FORMAT)) { editor.putString(KEY_DESKTOP_FORMAT, DEFAULT_DESKTOP_FORMAT); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_BOLD)) { editor.putBoolean(KEY_DESKTOP_BOLD, false); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_SHOW_BATTERY)) { editor.putBoolean(KEY_DESKTOP_SHOW_BATTERY, true); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_SHOW_NETWORK)) { editor.putBoolean(KEY_DESKTOP_SHOW_NETWORK, true); changed = true; }
        if (!prefs.contains(KEY_DESKTOP_SHOW_CPU)) { editor.putBoolean(KEY_DESKTOP_SHOW_CPU, true); changed = true; }
        if (!prefs.contains(KEY_CPU_OVERLAY)) { editor.putBoolean(KEY_CPU_OVERLAY, false); changed = true; }
        if (changed) editor.apply();
        DesktopConfig.ensureDefaults(prefs);
    }

    public static String getTextColor(SharedPreferences prefs) { return prefs.getString(KEY_TEXT_COLOR, DEFAULT_TEXT_COLOR); }
    public static String getShadowColor(SharedPreferences prefs) { return prefs.getString(KEY_SHADOW_COLOR, DEFAULT_SHADOW_COLOR); }
    public static int getTextSize(SharedPreferences prefs) { return clamp(prefs.getInt(KEY_TEXT_SIZE, DEFAULT_TEXT_SIZE), 12, 72); }
    public static String getFormat(SharedPreferences prefs) { return prefs.getString(KEY_FORMAT, DEFAULT_FORMAT); }
    public static boolean isBold(SharedPreferences prefs) { return prefs.getBoolean(KEY_BOLD, false); }

    public static String getDesktopTextColor(SharedPreferences prefs) { return prefs.getString(KEY_DESKTOP_TEXT_COLOR, DEFAULT_DESKTOP_TEXT_COLOR); }
    public static String getDesktopShadowColor(SharedPreferences prefs) { return prefs.getString(KEY_DESKTOP_SHADOW_COLOR, DEFAULT_DESKTOP_SHADOW_COLOR); }
    public static int getDesktopTextSize(SharedPreferences prefs) { return clamp(prefs.getInt(KEY_DESKTOP_TEXT_SIZE, DEFAULT_DESKTOP_TEXT_SIZE), 36, 180); }
    public static String getDesktopFormat(SharedPreferences prefs) { return stripMilliseconds(prefs.getString(KEY_DESKTOP_FORMAT, DEFAULT_DESKTOP_FORMAT)); }
    public static boolean isDesktopBold(SharedPreferences prefs) { return prefs.getBoolean(KEY_DESKTOP_BOLD, false); }
    public static boolean showDesktopBattery(SharedPreferences prefs) { return prefs.getBoolean(KEY_DESKTOP_SHOW_BATTERY, true); }
    public static boolean showDesktopNetwork(SharedPreferences prefs) { return prefs.getBoolean(KEY_DESKTOP_SHOW_NETWORK, true); }
    public static boolean showDesktopCpu(SharedPreferences prefs) { return prefs.getBoolean(KEY_DESKTOP_SHOW_CPU, true); }
    public static boolean showCpuOverlay(SharedPreferences prefs) { return prefs.getBoolean(KEY_CPU_OVERLAY, false); }

    public static SimpleDateFormat createFormatter(String pattern) {
        validateFormat(pattern);
        return new SimpleDateFormat(pattern, Locale.getDefault());
    }

    public static boolean usesMilliseconds(String pattern) {
        boolean quoted = false;
        for (int i = 0; i < pattern.length(); i++) {
            char c = pattern.charAt(i);
            if (c == '\'') {
                if (i + 1 < pattern.length() && pattern.charAt(i + 1) == '\'') i++;
                else quoted = !quoted;
            } else if (!quoted && c == 'S') return true;
        }
        return false;
    }

    public static String stripMilliseconds(String pattern) {
        if (pattern == null || pattern.trim().isEmpty()) return DEFAULT_DESKTOP_FORMAT;
        String result = pattern.trim();
        result = result.replaceAll("\\.S+", "");
        result = result.replaceAll("S+", "");
        result = result.replaceAll("\\s+", " ").trim();
        return result.isEmpty() ? DEFAULT_DESKTOP_FORMAT : result;
    }

    public static void validateFormat(String pattern) {
        try {
            new SimpleDateFormat(pattern, Locale.getDefault()).format(new Date());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("时间格式错误");
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

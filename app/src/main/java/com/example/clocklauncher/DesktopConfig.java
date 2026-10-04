package com.example.clocklauncher;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

/** 桌面时钟组件样式、位置与预设的集中管理。 */
public final class DesktopConfig {
    public static final String KEY_SHOW_DATE = "desktop_show_date";
    public static final String KEY_DATE_COLOR = "desktop_date_color";
    public static final String KEY_DATE_SIZE = "desktop_date_size";
    public static final String KEY_DATE_BOLD = "desktop_date_bold";
    public static final String KEY_BATTERY_COLOR = "desktop_battery_color";
    public static final String KEY_BATTERY_SIZE = "desktop_battery_size";
    public static final String KEY_BATTERY_BOLD = "desktop_battery_bold";
    public static final String KEY_NETWORK_COLOR = "desktop_network_color";
    public static final String KEY_NETWORK_SIZE = "desktop_network_size";
    public static final String KEY_NETWORK_BOLD = "desktop_network_bold";
    public static final String KEY_CPU_WIDTH = "desktop_cpu_width";
    public static final String KEY_CPU_HEIGHT = "desktop_cpu_height";
    public static final String KEY_CPU_ALPHA = "desktop_cpu_alpha";
    public static final String KEY_SHOW_WEATHER = "desktop_show_weather";
    public static final String KEY_WEATHER_CITY = "desktop_weather_city";
    public static final String KEY_WEATHER_LAT = "desktop_weather_lat";
    public static final String KEY_WEATHER_LON = "desktop_weather_lon";
    public static final String KEY_WEATHER_MODE = "desktop_weather_mode";
    public static final String KEY_WEATHER_COLOR = "desktop_weather_color";
    public static final String KEY_WEATHER_SIZE = "desktop_weather_size";
    public static final String KEY_WEATHER_BOLD = "desktop_weather_bold";
    public static final String KEY_WEATHER_WIDTH = "desktop_weather_width";
    public static final String KEY_WEATHER_HEIGHT = "desktop_weather_height";
    public static final String KEY_WEATHER_ALPHA = "desktop_weather_alpha";
    public static final String KEY_WEATHER_TEXT_SCALE = "desktop_weather_text_scale";
    public static final String KEY_WEATHER_DATA_CACHE = "desktop_weather_data_cache";
    public static final String KEY_WEATHER_CACHE_TIME = "desktop_weather_cache_time";
    public static final String KEY_NIGHT_MODE_MANUAL = "desktop_night_mode_manual";
    public static final String KEY_NIGHT_MODE_AUTO = "desktop_night_mode_auto";
    public static final String KEY_NIGHT_MODE_START = "desktop_night_mode_start";
    public static final String KEY_NIGHT_MODE_END = "desktop_night_mode_end";
    public static final String KEY_NIGHT_MODE_BRIGHTNESS = "desktop_night_mode_brightness";
    public static final String KEY_NIGHT_EXTRA_DIM = "desktop_night_extra_dim";
    public static final String KEY_NIGHT_EXTRA_DIM_DEPTH = "desktop_night_extra_dim_depth";
    public static final String KEY_NORMAL_BRIGHTNESS = "desktop_normal_brightness";
    public static final String KEY_POS_PREFIX = "desktop_pos_";
    public static final String KEY_PRESETS = "desktop_presets_json";

    public static final String DEFAULT_NIGHT_START = "22:00";
    public static final String DEFAULT_NIGHT_END = "07:00";
    public static final int DEFAULT_NIGHT_BRIGHTNESS = 1;
    public static final boolean DEFAULT_NIGHT_EXTRA_DIM = true;
    public static final int DEFAULT_NIGHT_EXTRA_DIM_DEPTH = 50;
    public static final float DEFAULT_NORMAL_BRIGHTNESS = 0.7f;

    public static final String COMPONENT_CLOCK = "clock";
    public static final String COMPONENT_DATE = "date";
    public static final String COMPONENT_BATTERY = "battery";
    public static final String COMPONENT_NETWORK = "network";
    public static final String COMPONENT_CPU = "cpu";
    public static final String COMPONENT_WEATHER = "weather";

    private static final String[] SNAPSHOT_KEYS = new String[]{
            ClockPrefs.KEY_DESKTOP_TEXT_COLOR, ClockPrefs.KEY_DESKTOP_SHADOW_COLOR,
            ClockPrefs.KEY_DESKTOP_TEXT_SIZE, ClockPrefs.KEY_DESKTOP_FORMAT, ClockPrefs.KEY_DESKTOP_BOLD,
            KEY_SHOW_DATE, ClockPrefs.KEY_DESKTOP_SHOW_BATTERY, ClockPrefs.KEY_DESKTOP_SHOW_NETWORK,
            ClockPrefs.KEY_DESKTOP_SHOW_CPU, KEY_DATE_COLOR, KEY_DATE_SIZE, KEY_DATE_BOLD,
            KEY_BATTERY_COLOR, KEY_BATTERY_SIZE, KEY_BATTERY_BOLD,
            KEY_NETWORK_COLOR, KEY_NETWORK_SIZE, KEY_NETWORK_BOLD,
            KEY_CPU_WIDTH, KEY_CPU_HEIGHT, KEY_CPU_ALPHA,
            KEY_SHOW_WEATHER, KEY_WEATHER_CITY, KEY_WEATHER_LAT, KEY_WEATHER_LON,
            KEY_WEATHER_MODE, KEY_WEATHER_COLOR, KEY_WEATHER_SIZE, KEY_WEATHER_BOLD,
            KEY_WEATHER_WIDTH, KEY_WEATHER_HEIGHT, KEY_WEATHER_ALPHA, KEY_WEATHER_TEXT_SCALE,
            posX(COMPONENT_CLOCK), posY(COMPONENT_CLOCK), posX(COMPONENT_DATE), posY(COMPONENT_DATE),
            posX(COMPONENT_BATTERY), posY(COMPONENT_BATTERY), posX(COMPONENT_NETWORK), posY(COMPONENT_NETWORK),
            posX(COMPONENT_CPU), posY(COMPONENT_CPU), posX(COMPONENT_WEATHER), posY(COMPONENT_WEATHER)
    };

    private DesktopConfig() { }

    public static void ensureDefaults(SharedPreferences prefs) {
        SharedPreferences.Editor e = prefs.edit();
        boolean changed = false;
        changed |= putBooleanIfMissing(prefs, e, KEY_SHOW_DATE, true);
        changed |= putStringIfMissing(prefs, e, KEY_DATE_COLOR, "#D2FFFFFF");
        changed |= putIntIfMissing(prefs, e, KEY_DATE_SIZE, 22);
        changed |= putBooleanIfMissing(prefs, e, KEY_DATE_BOLD, true);
        changed |= putStringIfMissing(prefs, e, KEY_BATTERY_COLOR, "#B9FFFFFF");
        changed |= putIntIfMissing(prefs, e, KEY_BATTERY_SIZE, 18);
        changed |= putBooleanIfMissing(prefs, e, KEY_BATTERY_BOLD, true);
        changed |= putStringIfMissing(prefs, e, KEY_NETWORK_COLOR, "#B9FFFFFF");
        changed |= putIntIfMissing(prefs, e, KEY_NETWORK_SIZE, 16);
        changed |= putBooleanIfMissing(prefs, e, KEY_NETWORK_BOLD, true);
        changed |= putIntIfMissing(prefs, e, KEY_CPU_WIDTH, 140);
        changed |= putIntIfMissing(prefs, e, KEY_CPU_HEIGHT, 220);
        changed |= putIntIfMissing(prefs, e, KEY_CPU_ALPHA, 100);
        changed |= putBooleanIfMissing(prefs, e, KEY_SHOW_WEATHER, true);
        changed |= putStringIfMissing(prefs, e, KEY_WEATHER_CITY, "北京");
        changed |= putFloatIfMissing(prefs, e, KEY_WEATHER_LAT, 39.9042f);
        changed |= putFloatIfMissing(prefs, e, KEY_WEATHER_LON, 116.4074f);
        changed |= putIntIfMissing(prefs, e, KEY_WEATHER_MODE, 0);
        changed |= putStringIfMissing(prefs, e, KEY_WEATHER_COLOR, "#D2FFFFFF");
        changed |= putIntIfMissing(prefs, e, KEY_WEATHER_SIZE, 16);
        changed |= putBooleanIfMissing(prefs, e, KEY_WEATHER_BOLD, true);
        changed |= putIntIfMissing(prefs, e, KEY_WEATHER_WIDTH, 300);
        changed |= putIntIfMissing(prefs, e, KEY_WEATHER_HEIGHT, 96);
        changed |= putIntIfMissing(prefs, e, KEY_WEATHER_ALPHA, 100);
        changed |= putIntIfMissing(prefs, e, KEY_WEATHER_TEXT_SCALE, 100);
        changed |= putBooleanIfMissing(prefs, e, KEY_NIGHT_MODE_MANUAL, false);
        changed |= putBooleanIfMissing(prefs, e, KEY_NIGHT_MODE_AUTO, false);
        changed |= putStringIfMissing(prefs, e, KEY_NIGHT_MODE_START, DEFAULT_NIGHT_START);
        changed |= putStringIfMissing(prefs, e, KEY_NIGHT_MODE_END, DEFAULT_NIGHT_END);
        changed |= putIntIfMissing(prefs, e, KEY_NIGHT_MODE_BRIGHTNESS, DEFAULT_NIGHT_BRIGHTNESS);
        changed |= putBooleanIfMissing(prefs, e, KEY_NIGHT_EXTRA_DIM, DEFAULT_NIGHT_EXTRA_DIM);
        changed |= putIntIfMissing(prefs, e, KEY_NIGHT_EXTRA_DIM_DEPTH, DEFAULT_NIGHT_EXTRA_DIM_DEPTH);
        if (!prefs.contains(KEY_NORMAL_BRIGHTNESS)) {
            e.putFloat(KEY_NORMAL_BRIGHTNESS, DEFAULT_NORMAL_BRIGHTNESS);
            changed = true;
        }
        if (changed) e.apply();
    }

    public static boolean isTimeInRange(String startStr, String endStr, Calendar now) {
        if (startStr == null || endStr == null || now == null) return false;
        String[] startParts = startStr.split(":");
        String[] endParts = endStr.split(":");
        if (startParts.length < 2 || endParts.length < 2) return false;
        try {
            int startH = Integer.parseInt(startParts[0].trim());
            int startM = Integer.parseInt(startParts[1].trim());
            int endH = Integer.parseInt(endParts[0].trim());
            int endM = Integer.parseInt(endParts[1].trim());
            int curH = now.get(Calendar.HOUR_OF_DAY);
            int curM = now.get(Calendar.MINUTE);
            int startMin = startH * 60 + startM;
            int endMin = endH * 60 + endM;
            int curMin = curH * 60 + curM;
            if (startMin == endMin) {
                return false;
            }
            if (startMin < endMin) {
                return curMin >= startMin && curMin < endMin;
            } else {
                return curMin >= startMin || curMin < endMin;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isNightModeActive(SharedPreferences prefs) {
        if (prefs == null) return false;
        if (prefs.getBoolean(KEY_NIGHT_MODE_MANUAL, false)) return true;
        if (!prefs.getBoolean(KEY_NIGHT_MODE_AUTO, false)) return false;
        String start = prefs.getString(KEY_NIGHT_MODE_START, DEFAULT_NIGHT_START);
        String end = prefs.getString(KEY_NIGHT_MODE_END, DEFAULT_NIGHT_END);
        return isTimeInRange(start, end, Calendar.getInstance());
    }

    private static boolean putStringIfMissing(SharedPreferences p, SharedPreferences.Editor e, String k, String v) {
        if (p.contains(k)) return false; e.putString(k, v); return true;
    }
    private static boolean putIntIfMissing(SharedPreferences p, SharedPreferences.Editor e, String k, int v) {
        if (p.contains(k)) return false; e.putInt(k, v); return true;
    }
    private static boolean putFloatIfMissing(SharedPreferences p, SharedPreferences.Editor e, String k, float v) {
        if (p.contains(k)) return false; e.putFloat(k, v); return true;
    }
    private static boolean putBooleanIfMissing(SharedPreferences p, SharedPreferences.Editor e, String k, boolean v) {
        if (p.contains(k)) return false; e.putBoolean(k, v); return true;
    }

    public static String posX(String component) { return KEY_POS_PREFIX + component + "_x"; }
    public static String posY(String component) { return KEY_POS_PREFIX + component + "_y"; }

    public static void savePosition(SharedPreferences prefs, String component, float xFraction, float yFraction) {
        prefs.edit().putFloat(posX(component), clamp01(xFraction)).putFloat(posY(component), clamp01(yFraction)).apply();
    }

    public static boolean hasPosition(SharedPreferences prefs, String component) {
        return prefs.contains(posX(component)) && prefs.contains(posY(component));
    }

    public static void resetAllPositions(SharedPreferences prefs) {
        prefs.edit()
                .remove(posX(COMPONENT_CLOCK)).remove(posY(COMPONENT_CLOCK))
                .remove(posX(COMPONENT_DATE)).remove(posY(COMPONENT_DATE))
                .remove(posX(COMPONENT_BATTERY)).remove(posY(COMPONENT_BATTERY))
                .remove(posX(COMPONENT_NETWORK)).remove(posY(COMPONENT_NETWORK))
                .remove(posX(COMPONENT_CPU)).remove(posY(COMPONENT_CPU))
                .remove(posX(COMPONENT_WEATHER)).remove(posY(COMPONENT_WEATHER))
                .apply();
    }

    public static float getX(SharedPreferences prefs, String component) { return prefs.getFloat(posX(component), 0.5f); }
    public static float getY(SharedPreferences prefs, String component) { return prefs.getFloat(posY(component), 0.5f); }

    public static JSONObject snapshot(SharedPreferences prefs) throws JSONException {
        JSONObject data = new JSONObject();
        for (String key : SNAPSHOT_KEYS) {
            if (!prefs.contains(key)) continue;
            Object value = prefs.getAll().get(key);
            if (value != null) data.put(key, value);
        }
        return data;
    }

    public static void applySnapshot(SharedPreferences prefs, JSONObject data) throws JSONException {
        SharedPreferences.Editor e = prefs.edit();
        for (String key : SNAPSHOT_KEYS) {
            if (!data.has(key)) continue;
            Object value = data.get(key);
            if (value instanceof Boolean) e.putBoolean(key, (Boolean) value);
            else if (value instanceof Integer) e.putInt(key, (Integer) value);
            else if (value instanceof Long) e.putLong(key, (Long) value);
            else if (value instanceof Double) {
                if (key.startsWith(KEY_POS_PREFIX) || key.equals(KEY_WEATHER_LAT) || key.equals(KEY_WEATHER_LON)) {
                    e.putFloat(key, ((Double) value).floatValue());
                } else {
                    e.putInt(key, ((Double) value).intValue());
                }
            } else e.putString(key, String.valueOf(value));
        }
        e.commit();
        ensureDefaults(prefs);
    }

    public static JSONArray getPresets(SharedPreferences prefs) {
        try { return new JSONArray(prefs.getString(KEY_PRESETS, "[]")); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    public static List<String> presetNames(SharedPreferences prefs) {
        JSONArray array = getPresets(prefs);
        List<String> names = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) names.add(array.optJSONObject(i).optString("name", "预设 " + (i + 1)));
        return names;
    }

    public static void savePreset(SharedPreferences prefs, String name) throws JSONException {
        JSONArray array = getPresets(prefs);
        JSONObject item = new JSONObject().put("name", name).put("data", snapshot(prefs));
        int existing = findPreset(array, name);
        if (existing >= 0) array.put(existing, item); else array.put(item);
        prefs.edit().putString(KEY_PRESETS, array.toString()).commit();
    }

    public static void applyPreset(SharedPreferences prefs, int index) throws JSONException {
        JSONObject item = getPresets(prefs).getJSONObject(index);
        applySnapshot(prefs, item.getJSONObject("data"));
    }

    public static void renamePreset(SharedPreferences prefs, int index, String name) throws JSONException {
        JSONArray array = getPresets(prefs);
        array.getJSONObject(index).put("name", name);
        prefs.edit().putString(KEY_PRESETS, array.toString()).commit();
    }

    public static void deletePreset(SharedPreferences prefs, int index) throws JSONException {
        JSONArray source = getPresets(prefs);
        JSONArray result = new JSONArray();
        for (int i = 0; i < source.length(); i++) if (i != index) result.put(source.get(i));
        prefs.edit().putString(KEY_PRESETS, result.toString()).commit();
    }

    public static String exportAll(SharedPreferences prefs) throws JSONException {
        return new JSONObject().put("version", 1).put("current", snapshot(prefs)).put("presets", getPresets(prefs)).toString(2);
    }

    public static void importAll(SharedPreferences prefs, String json) throws JSONException {
        JSONObject root = new JSONObject(json);
        if (root.has("current")) applySnapshot(prefs, root.getJSONObject("current"));
        if (root.has("presets")) prefs.edit().putString(KEY_PRESETS, root.getJSONArray("presets").toString()).commit();
        ensureDefaults(prefs);
    }

    private static int findPreset(JSONArray array, String name) {
        for (int i = 0; i < array.length(); i++) if (name.equals(array.optJSONObject(i).optString("name"))) return i;
        return -1;
    }

    private static float clamp01(float value) { return Math.max(0f, Math.min(1f, value)); }
}

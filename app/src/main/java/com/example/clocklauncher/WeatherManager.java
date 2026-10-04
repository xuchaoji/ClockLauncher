package com.example.clocklauncher;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Open-Meteo 免费天气 API 管理器。
 * 支持免 Key 获取：实时天气、过去24小时/未来24小时逐小时走势、三日对比（昨天/今天/明天）、未来一周预报与日出日落。
 */
public final class WeatherManager {
    public static final int MODE_CURRENT = 0;   // 实时天气
    public static final int MODE_THREE_DAYS = 1; // 三日对比（昨天/今天/明天）
    public static final int MODE_HOURLY = 2;     // 24小时逐小时走势
    public static final int MODE_WEEKLY = 3;     // 一周天气预报
    public static final int MODE_ASTRO = 4;      // 日出日落与环境
    public static final int MODE_COUNT = 5;

    public static final String[] MODE_NAMES = new String[]{
            "实时概况", "三日对比", "24h走势", "一周预报", "天文环境"
    };

    private static final long CACHE_VALID_MS = 20 * 60 * 1000L; // 20 分钟缓存
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());

    public interface WeatherCallback {
        void onSuccess(String json);
        void onError(String message);
    }

    public interface CitySearchCallback {
        void onSuccess(String cityName, float lat, float lon);
        void onError(String message);
    }

    private WeatherManager() { }

    /**
     * 异步搜索城市经纬度
     */
    public static void searchCity(String query, CitySearchCallback callback) {
        if (query == null || query.trim().isEmpty()) {
            callback.onError("城市名称不能为空");
            return;
        }
        new Thread(() -> {
            try {
                String encoded = URLEncoder.encode(query.trim(), "UTF-8");
                String urlStr = "https://geocoding-api.open-meteo.com/v1/search?name="
                        + encoded + "&count=1&language=zh&format=json";
                String resp = httpGet(urlStr, 8000);
                JSONObject root = new JSONObject(resp);
                JSONArray results = root.optJSONArray("results");
                if (results == null || results.length() == 0) {
                    MAIN_HANDLER.post(() -> callback.onError("未找到该城市"));
                    return;
                }
                JSONObject first = results.getJSONObject(0);
                String name = first.optString("name", query.trim());
                float lat = (float) first.getDouble("latitude");
                float lon = (float) first.getDouble("longitude");
                MAIN_HANDLER.post(() -> callback.onSuccess(name, lat, lon));
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> callback.onError("查询失败: " + e.getMessage()));
            }
        }).start();
    }

    /**
     * 获取天气（自动处理本地缓存与网络刷新）
     */
    public static void fetchWeather(Context context, float lat, float lon, boolean force, WeatherCallback callback) {
        SharedPreferences prefs = context.getSharedPreferences(ClockPrefs.NAME, Context.MODE_PRIVATE);
        String cachedJson = prefs.getString(DesktopConfig.KEY_WEATHER_DATA_CACHE, null);
        long cachedTime = prefs.getLong(DesktopConfig.KEY_WEATHER_CACHE_TIME, 0L);
        long now = System.currentTimeMillis();

        if (!force && cachedJson != null && (now - cachedTime < CACHE_VALID_MS)) {
            callback.onSuccess(cachedJson);
            return;
        }

        new Thread(() -> {
            try {
                String urlStr = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f"
                                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m"
                                + "&hourly=temperature_2m,weather_code"
                                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset"
                                + "&past_days=1&forecast_days=7&past_hours=12&forecast_hours=12&timezone=auto",
                        lat, lon);

                String resp = httpGet(urlStr, 10000);
                JSONObject check = new JSONObject(resp);
                if (check.has("current") && check.has("daily")) {
                    prefs.edit()
                            .putString(DesktopConfig.KEY_WEATHER_DATA_CACHE, resp)
                            .putLong(DesktopConfig.KEY_WEATHER_CACHE_TIME, System.currentTimeMillis())
                            .apply();
                    MAIN_HANDLER.post(() -> callback.onSuccess(resp));
                } else {
                    throw new IllegalArgumentException("返回数据不完整");
                }
            } catch (Exception e) {
                MAIN_HANDLER.post(() -> {
                    if (cachedJson != null) {
                        callback.onSuccess(cachedJson);
                    } else {
                        callback.onError("天气获取失败: " + e.getMessage());
                    }
                });
            }
        }).start();
    }

    public static String formatWeatherText(String jsonStr, int mode, String cityName) {
        if (jsonStr == null || jsonStr.isEmpty()) {
            return (cityName != null ? cityName : "天气") + " · 加载中... (点击刷新)";
        }
        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject current = root.optJSONObject("current");
            JSONObject daily = root.optJSONObject("daily");
            JSONObject hourly = root.optJSONObject("hourly");
            String city = (cityName != null && !cityName.trim().isEmpty()) ? cityName.trim() : "本地";

            switch (mode) {
                case MODE_THREE_DAYS:
                    return formatThreeDays(city, daily);
                case MODE_HOURLY:
                    return formatHourly(city, hourly);
                case MODE_WEEKLY:
                    return formatWeekly(city, daily);
                case MODE_ASTRO:
                    return formatAstro(city, current, daily);
                case MODE_CURRENT:
                default:
                    return formatCurrent(city, current, daily);
            }
        } catch (Exception e) {
            return (cityName != null ? cityName : "天气") + " · 解析异常 (点击刷新)";
        }
    }

    private static String formatCurrent(String city, JSONObject current, JSONObject daily) {
        if (current == null) return city + " · 无实时数据";
        double temp = current.optDouble("temperature_2m", 0);
        double appTemp = current.optDouble("apparent_temperature", temp);
        int hum = current.optInt("relative_humidity_2m", 0);
        int code = current.optInt("weather_code", 0);
        double wind = current.optDouble("wind_speed_10m", 0);

        String range = "";
        if (daily != null) {
            JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
            JSONArray minArr = daily.optJSONArray("temperature_2m_min");
            if (maxArr != null && minArr != null && maxArr.length() > 1 && minArr.length() > 1) {
                range = String.format(Locale.getDefault(), " (%d~%d℃)",
                        Math.round(minArr.optDouble(1)), Math.round(maxArr.optDouble(1)));
            }
        }

        return String.format(Locale.getDefault(),
                "%s · %s %.0f℃ %s%s · [实时]\n体感 %.0f℃ · 湿度 %d%% · 风速 %.1fkm/h",
                city, getWeatherIcon(code), temp, getWeatherDesc(code), range,
                appTemp, hum, wind);
    }

    private static String formatThreeDays(String city, JSONObject daily) {
        if (daily == null) return city + " · 无多日数据";
        JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
        JSONArray minArr = daily.optJSONArray("temperature_2m_min");
        JSONArray codeArr = daily.optJSONArray("weather_code");

        if (maxArr == null || minArr == null || codeArr == null || maxArr.length() < 3) {
            return city + " · 三日数据不足";
        }

        String yest = String.format(Locale.getDefault(), "昨 %d~%d℃ %s %s",
                Math.round(minArr.optDouble(0)), Math.round(maxArr.optDouble(0)),
                getWeatherIcon(codeArr.optInt(0)), getWeatherDesc(codeArr.optInt(0)));
        String today = String.format(Locale.getDefault(), "今 %d~%d℃ %s %s",
                Math.round(minArr.optDouble(1)), Math.round(maxArr.optDouble(1)),
                getWeatherIcon(codeArr.optInt(1)), getWeatherDesc(codeArr.optInt(1)));
        String tom = String.format(Locale.getDefault(), "明 %d~%d℃ %s %s",
                Math.round(minArr.optDouble(2)), Math.round(maxArr.optDouble(2)),
                getWeatherIcon(codeArr.optInt(2)), getWeatherDesc(codeArr.optInt(2)));

        return city + " · 三日对比 · [三日]\n" + yest + "\n" + today + "  " + tom;
    }

    private static String formatHourly(String city, JSONObject hourly) {
        if (hourly == null) return city + " · 无逐小时数据";
        JSONArray timeArr = hourly.optJSONArray("time");
        JSONArray tempArr = hourly.optJSONArray("temperature_2m");
        JSONArray codeArr = hourly.optJSONArray("weather_code");
        if (timeArr == null || tempArr == null || timeArr.length() < 12) {
            return city + " · 走势数据不足";
        }

        StringBuilder pastLine = new StringBuilder("前: ");
        StringBuilder futureLine = new StringBuilder("后: ");

        int[] pastIdxs = new int[]{3, 6, 9};
        for (int idx : pastIdxs) {
            if (idx < timeArr.length()) {
                String t = formatHour(timeArr.optString(idx));
                pastLine.append(t).append(" ")
                        .append(Math.round(tempArr.optDouble(idx))).append("° ")
                        .append(getWeatherIcon(codeArr != null ? codeArr.optInt(idx) : 0))
                        .append("  ");
            }
        }

        int[] futureIdxs = new int[]{12, 15, 18, 21};
        for (int idx : futureIdxs) {
            if (idx < timeArr.length()) {
                String t = (idx == 12) ? "现" : formatHour(timeArr.optString(idx));
                futureLine.append(t).append(" ")
                        .append(Math.round(tempArr.optDouble(idx))).append("° ")
                        .append(getWeatherIcon(codeArr != null ? codeArr.optInt(idx) : 0))
                        .append("  ");
            }
        }

        return city + " · 24h走势 · [24h]\n" + pastLine.toString().trim() + "\n" + futureLine.toString().trim();
    }

    private static String formatWeekly(String city, JSONObject daily) {
        if (daily == null) return city + " · 无一周数据";
        JSONArray timeArr = daily.optJSONArray("time");
        JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
        JSONArray minArr = daily.optJSONArray("temperature_2m_min");
        JSONArray codeArr = daily.optJSONArray("weather_code");

        if (timeArr == null || maxArr == null || minArr == null || timeArr.length() < 7) {
            return city + " · 一周数据不足";
        }

        StringBuilder line1 = new StringBuilder();
        StringBuilder line2 = new StringBuilder();

        for (int i = 1; i < Math.min(timeArr.length(), 7); i++) {
            String week = getWeekDayName(timeArr.optString(i), i == 1);
            String item = String.format(Locale.getDefault(), "%s %d~%d° %s",
                    week, Math.round(minArr.optDouble(i)), Math.round(maxArr.optDouble(i)),
                    getWeatherIcon(codeArr != null ? codeArr.optInt(i) : 0));

            if (i <= 3) {
                if (line1.length() > 0) line1.append(" · ");
                line1.append(item);
            } else {
                if (line2.length() > 0) line2.append(" · ");
                line2.append(item);
            }
        }

        return city + " · 未来一周预报 · [一周]\n" + line1 + "\n" + line2;
    }

    private static String formatAstro(String city, JSONObject current, JSONObject daily) {
        String sunriseStr = "--:--";
        String sunsetStr = "--:--";
        if (daily != null) {
            JSONArray sunriseArr = daily.optJSONArray("sunrise");
            JSONArray sunsetArr = daily.optJSONArray("sunset");
            if (sunriseArr != null && sunriseArr.length() > 1) {
                sunriseStr = extractTime(sunriseArr.optString(1));
            }
            if (sunsetArr != null && sunsetArr.length() > 1) {
                sunsetStr = extractTime(sunsetArr.optString(1));
            }
        }

        double wind = (current != null) ? current.optDouble("wind_speed_10m", 0) : 0;
        int hum = (current != null) ? current.optInt("relative_humidity_2m", 0) : 0;

        return String.format(Locale.getDefault(),
                "%s · 天文与风况 · [天文]\n🌅 日出 %s · 🌇 日落 %s\n💨 风速 %.1f km/h · 💧 相对湿度 %d%%",
                city, sunriseStr, sunsetStr, wind, hum);
    }

    private static String extractTime(String isoDateTime) {
        if (isoDateTime == null) return "--:--";
        int tIdx = isoDateTime.indexOf('T');
        if (tIdx >= 0 && tIdx + 6 <= isoDateTime.length()) {
            return isoDateTime.substring(tIdx + 1, tIdx + 6);
        }
        return isoDateTime;
    }

    private static String formatHour(String isoDateTime) {
        if (isoDateTime == null) return "--";
        int tIdx = isoDateTime.indexOf('T');
        if (tIdx >= 0 && tIdx + 6 <= isoDateTime.length()) {
            return isoDateTime.substring(tIdx + 1, tIdx + 3) + "时";
        }
        return isoDateTime;
    }

    private static String getWeekDayName(String dateStr, boolean isToday) {
        if (isToday) return "今";
        try {
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());
            Date date = sdf.parse(dateStr);
            if (date != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(date);
                int day = cal.get(Calendar.DAY_OF_WEEK);
                switch (day) {
                    case Calendar.SUNDAY: return "日";
                    case Calendar.MONDAY: return "一";
                    case Calendar.TUESDAY: return "二";
                    case Calendar.WEDNESDAY: return "三";
                    case Calendar.THURSDAY: return "四";
                    case Calendar.FRIDAY: return "五";
                    case Calendar.SATURDAY: return "六";
                }
            }
        } catch (Exception ignored) { }
        return dateStr;
    }

    public static String getWeatherDesc(int code) {
        switch (code) {
            case 0: return "晴";
            case 1: return "大部晴朗";
            case 2: return "晴间多云";
            case 3: return "阴";
            case 45: case 48: return "雾";
            case 51: case 53: case 55: return "毛毛雨";
            case 56: case 57: return "冻毛毛雨";
            case 61: return "小雨";
            case 63: return "中雨";
            case 65: return "大雨";
            case 66: case 67: return "冻雨";
            case 71: return "小雪";
            case 73: return "中雪";
            case 75: return "大雪";
            case 77: return "雪粒";
            case 80: return "阵雨";
            case 81: return "中度阵雨";
            case 82: return "暴雨";
            case 85: case 86: return "阵雪";
            case 95: return "雷阵雨";
            case 96: case 99: return "雷暴伴冰雹";
            default: return "多云";
        }
    }

    public static String getWeatherIcon(int code) {
        switch (code) {
            case 0: return "☀️";
            case 1: return "🌤️";
            case 2: return "⛅";
            case 3: return "☁️";
            case 45: case 48: return "🌫️";
            case 51: case 53: case 55: return "🌦️";
            case 61: case 63: case 65: case 80: case 81: case 82: return "🌧️";
            case 71: case 73: case 75: case 77: case 85: case 86: return "🌨️";
            case 95: case 96: case 99: return "⛈️";
            default: return "🌤️";
        }
    }

    private static String httpGet(String urlStr, int timeout) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(timeout);
        conn.setReadTimeout(timeout);
        conn.setRequestProperty("User-Agent", "ClockLauncher/1.0");
        int responseCode = conn.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new RuntimeException("HTTP错误码: " + responseCode);
        }
        InputStream is = conn.getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line);
        }
        reader.close();
        is.close();
        conn.disconnect();
        return sb.toString();
    }
}

package com.example.clocklauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 紧凑型结构化天气卡片（不占多余高度）：
 * 1. 去除首列冗余标题（日期/天气/温度行标），纯数据表格更宽敞。
 * 2. 温度改为单行显示（如 11~23°），高度大幅缩减（仅 ~96dp）。
 * 3. 支持轻触在【三日对比】与【逐小时走势】间直接切换。
 */
public class WeatherCardView extends View {
    public static final int MODE_DAILY = 0;   // 三日对比卡片
    public static final int MODE_HOURLY = 1;  // 逐小时走势卡片

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rectF = new RectF();

    private int cardMode = MODE_DAILY;
    private int panelAlphaPercent = 100;
    private int customTextColor = Color.WHITE;
    private boolean customBold = true;

    private String cityName = "北京";
    private String currentSummary = "天气加载中...";
    private String rawJsonData;
    private boolean isOffline = false;

    // 解析后的每日数据
    private final List<DailyItem> dailyItems = new ArrayList<>();
    // 解析后的逐小时数据
    private final List<HourlyItem> hourlyItems = new ArrayList<>();

    public static class DailyItem {
        public String dateStr;    // "10/1"
        public String sublabel;   // "昨", "今", "明"
        public String weatherDesc;// "晴"
        public String icon;       // "☀️"
        public int maxTemp;       // 23
        public int minTemp;       // 11
        public boolean isToday;
    }

    public static class HourlyItem {
        public String timeStr;    // "12:00"
        public String sublabel;   // "前", "现", "+1h"
        public String weatherDesc;// "多云"
        public String icon;       // "⛅"
        public int temp;          // 20
        public boolean isNow;
    }

    public interface OnModeChangeListener {
        void onModeChanged(int newMode);
    }

    private OnModeChangeListener modeChangeListener;

    public WeatherCardView(Context context) {
        super(context);
        init();
    }

    public WeatherCardView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setClickable(true);
        setFocusable(true);
    }

    public void setOnModeChangeListener(OnModeChangeListener listener) {
        this.modeChangeListener = listener;
    }

    public int getCardMode() {
        return cardMode;
    }

    public void setCardMode(int mode) {
        if (this.cardMode != mode) {
            this.cardMode = mode;
            invalidate();
            if (modeChangeListener != null) {
                modeChangeListener.onModeChanged(mode);
            }
        }
    }

    public void toggleMode() {
        setCardMode(cardMode == MODE_DAILY ? MODE_HOURLY : MODE_DAILY);
    }

    public void setPanelAlpha(int percent) {
        this.panelAlphaPercent = Math.max(10, Math.min(100, percent));
        invalidate();
    }

    public void setTextColor(int color) {
        this.customTextColor = color;
        invalidate();
    }

    public void setTextBold(boolean bold) {
        this.customBold = bold;
        invalidate();
    }

    public void setCityName(String name) {
        this.cityName = (name != null && !name.trim().isEmpty()) ? name.trim() : "本地";
        invalidate();
    }

    /**
     * 更新天气数据并解析成卡片表格数据
     */
    public void updateWeatherData(String jsonStr, boolean offline) {
        this.rawJsonData = jsonStr;
        this.isOffline = offline;
        parseJson(jsonStr);
        invalidate();
    }

    private void parseJson(String jsonStr) {
        dailyItems.clear();
        hourlyItems.clear();
        if (jsonStr == null || jsonStr.trim().isEmpty()) {
            currentSummary = cityName + " · 等待拉取天气";
            return;
        }

        try {
            JSONObject root = new JSONObject(jsonStr);
            JSONObject current = root.optJSONObject("current");
            JSONObject daily = root.optJSONObject("daily");
            JSONObject hourly = root.optJSONObject("hourly");

            // 1. 顶部摘要解析
            if (current != null) {
                double temp = current.optDouble("temperature_2m", 0);
                double appTemp = current.optDouble("apparent_temperature", temp);
                int hum = current.optInt("relative_humidity_2m", 0);
                int code = current.optInt("weather_code", 0);

                currentSummary = String.format(Locale.getDefault(),
                        "📍 %s · %s %.0f℃ %s · 体感 %.0f℃ · 湿度 %d%%",
                        cityName, WeatherManager.getWeatherIcon(code), temp,
                        WeatherManager.getWeatherDesc(code), appTemp, hum);
            } else {
                currentSummary = "📍 " + cityName + " · 天气概况";
            }

            // 2. 三日表格数据解析 (昨、今、明)
            if (daily != null) {
                JSONArray timeArr = daily.optJSONArray("time");
                JSONArray maxArr = daily.optJSONArray("temperature_2m_max");
                JSONArray minArr = daily.optJSONArray("temperature_2m_min");
                JSONArray codeArr = daily.optJSONArray("weather_code");

                String[] sublabels = new String[]{"昨", "今", "明", "后"};
                int count = (timeArr != null) ? Math.min(3, timeArr.length()) : 0;
                for (int i = 0; i < count; i++) {
                    DailyItem item = new DailyItem();
                    String tStr = timeArr.optString(i, "");
                    item.dateStr = formatMonthDay(tStr);
                    item.sublabel = (i < sublabels.length) ? sublabels[i] : "";
                    item.isToday = (i == 1);
                    int code = (codeArr != null) ? codeArr.optInt(i, 0) : 0;
                    item.weatherDesc = WeatherManager.getWeatherDesc(code);
                    item.icon = WeatherManager.getWeatherIcon(code);
                    item.maxTemp = (maxArr != null) ? (int) Math.round(maxArr.optDouble(i, 0)) : 0;
                    item.minTemp = (minArr != null) ? (int) Math.round(minArr.optDouble(i, 0)) : 0;
                    dailyItems.add(item);
                }
            }

            // 3. 逐小时表格数据解析 (前一小时、现在、未来三小时)
            if (hourly != null) {
                JSONArray hTime = hourly.optJSONArray("time");
                JSONArray hTemp = hourly.optJSONArray("temperature_2m");
                JSONArray hCode = hourly.optJSONArray("weather_code");

                if (hTime != null && hTemp != null && hTime.length() >= 16) {
                    int nowIdx = 12;
                    String currTime = (current != null) ? current.optString("time", "") : "";
                    if (currTime.length() >= 13) {
                        String currPrefix = currTime.substring(0, 13);
                        for (int k = 0; k < hTime.length(); k++) {
                            if (hTime.optString(k, "").startsWith(currPrefix)) {
                                nowIdx = k;
                                break;
                            }
                        }
                    }

                    int[] offsets = new int[]{-1, 0, 1, 2, 3};
                    String[] subs = new String[]{"前", "现", "+1h", "+2h", "+3h"};
                    for (int o = 0; o < offsets.length; o++) {
                        int idx = nowIdx + offsets[o];
                        if (idx >= 0 && idx < hTime.length()) {
                            HourlyItem item = new HourlyItem();
                            String fullT = hTime.optString(idx, "");
                            item.timeStr = extractHourMinute(fullT);
                            item.sublabel = subs[o];
                            item.isNow = (offsets[o] == 0);
                            int c = (hCode != null) ? hCode.optInt(idx, 0) : 0;
                            item.weatherDesc = WeatherManager.getWeatherDesc(c);
                            item.icon = WeatherManager.getWeatherIcon(c);
                            item.temp = (int) Math.round(hTemp.optDouble(idx, 0));
                            hourlyItems.add(item);
                        }
                    }
                }
            }

        } catch (Exception e) {
            currentSummary = cityName + " · 数据解析异常 (点击刷新)";
        }
    }

    private String formatMonthDay(String dateStr) {
        if (dateStr == null || dateStr.length() < 10) return dateStr;
        try {
            String[] parts = dateStr.split("-");
            if (parts.length >= 3) {
                int m = Integer.parseInt(parts[1]);
                int d = Integer.parseInt(parts[2]);
                return m + "/" + d;
            }
        } catch (Exception ignored) { }
        return dateStr;
    }

    private String extractHourMinute(String isoTime) {
        if (isoTime == null) return "--:--";
        int tIdx = isoTime.indexOf('T');
        if (tIdx >= 0 && tIdx + 6 <= isoTime.length()) {
            return isoTime.substring(tIdx + 1, tIdx + 6);
        }
        return isoTime;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int defWidth = Math.round(290 * density);
        int defHeight = Math.round(96 * density);

        int width = resolveSize(defWidth, widthMeasureSpec);
        int height = resolveSize(defHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float cornerRadius = 11 * density;

        // 1. 卡片外框与半透明背景
        int alpha255 = Math.round(255 * (panelAlphaPercent / 100f));
        rectF.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.88f), 15, 20, 32));
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        // 卡片边框
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.1f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.22f), 255, 255, 255));
        canvas.drawRoundRect(rectF, cornerRadius, cornerRadius, paint);

        // 2. 紧凑顶部栏 (Header ~25dp)
        float headerH = 25 * density;
        drawHeader(canvas, w, headerH, density, alpha255);

        // 顶部栏与表格之间的水平分割线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.9f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.18f), 255, 255, 255));
        canvas.drawLine(6 * density, headerH, w - 6 * density, headerH, paint);

        // 3. 表格区域（纯数据列，无第一列文字）
        float tableTop = headerH + 2 * density;
        float tableBottom = h - 4 * density;
        float tableLeft = 6 * density;
        float tableRight = w - 6 * density;

        if (cardMode == MODE_DAILY) {
            drawDailyTable(canvas, tableLeft, tableTop, tableRight, tableBottom, density, alpha255);
        } else {
            drawHourlyTable(canvas, tableLeft, tableTop, tableRight, tableBottom, density, alpha255);
        }
    }

    private void drawHeader(Canvas canvas, int w, float headerH, float density, int alpha255) {
        paint.setStyle(Paint.Style.FILL);
        paint.setFakeBoldText(true);
        paint.setTextSize(11.5f * density);
        paint.setColor(Color.argb(alpha255, 240, 245, 255));

        // 左侧实时概况文本
        Paint.FontMetrics fm = paint.getFontMetrics();
        float textY = (headerH - (fm.bottom + fm.top)) / 2f;
        canvas.drawText(currentSummary, 9 * density, textY, paint);

        // 右侧模式指示器胶囊
        String badgeText = (cardMode == MODE_DAILY) ? "📅 三日" : "⏱️ 逐小时";
        paint.setTextSize(10f * density);
        float badgeTextW = paint.measureText(badgeText);
        float badgePadH = 6 * density;
        float badgeW = badgeTextW + badgePadH * 2;
        float badgeH = 17 * density;
        float badgeRight = w - 8 * density;
        float badgeLeft = badgeRight - badgeW;
        float badgeTop = (headerH - badgeH) / 2f;

        RectF badgeRect = new RectF(badgeLeft, badgeTop, badgeRight, badgeTop + badgeH);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.22f), 66, 183, 255));
        canvas.drawRoundRect(badgeRect, 8 * density, 8 * density, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.9f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.5f), 100, 210, 255));
        canvas.drawRoundRect(badgeRect, 8 * density, 8 * density, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(alpha255, 215, 240, 255));
        Paint.FontMetrics bFm = paint.getFontMetrics();
        float bTextY = badgeTop + (badgeH - (bFm.bottom + bFm.top)) / 2f;
        canvas.drawText(badgeText, badgeLeft + badgePadH, bTextY, paint);
    }

    /**
     * 紧凑三日对比表格（无首列，单行温度）：
     * 3 列：[10/1 昨]  [10/2 今(高亮)]  [10/3 明]
     * 行1: 日期与副标 (10/2 今)
     * 行2: 天气图标与简述 (⛅ 多云)
     * 行3: 单行温度 (11~21°)
     */
    private void drawDailyTable(Canvas canvas, float left, float top, float right, float bottom, float density, int alpha255) {
        float tableW = right - left;
        float tableH = bottom - top;
        int count = Math.min(3, dailyItems.size());
        if (count == 0) return;

        float colW = tableW / 3f;
        float rowH = tableH / 3f;
        float r0Bottom = top + rowH;
        float r1Bottom = r0Bottom + rowH;

        // 1. 高亮“今天”所在的列 (index 1)
        if (dailyItems.size() >= 2) {
            float todayLeft = left + 1 * colW;
            float todayRight = todayLeft + colW;
            rectF.set(todayLeft, top, todayRight, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.16f), 66, 183, 255));
            canvas.drawRoundRect(rectF, 5 * density, 5 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.40f), 100, 210, 255));
            canvas.drawRoundRect(rectF, 5 * density, 5 * density, paint);
        }

        // 2. 内部水平分割线 (细线)
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.14f), 255, 255, 255));
        canvas.drawLine(left, r0Bottom, right, r0Bottom, paint);
        canvas.drawLine(left, r1Bottom, right, r1Bottom, paint);

        // 3. 列间垂直分隔线
        for (int i = 1; i < 3; i++) {
            float x = left + i * colW;
            canvas.drawLine(x, top, x, bottom, paint);
        }

        // 4. 绘制各日数据（纯单行）
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            DailyItem item = dailyItems.get(i);
            float cx = left + i * colW + colW / 2f;

            // 行 1: 日期 + 昨/今/明 (单行: 10/2 今)
            float cy0 = top + rowH / 2f;
            paint.setTextSize(11.5f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isToday ? Color.argb(alpha255, 120, 220, 255) : Color.argb(alpha255, 235, 240, 255));
            String dateLabel = item.dateStr + " " + item.sublabel;
            drawTextCenteredAt(canvas, dateLabel, cx, cy0);

            // 行 2: 天气图标 + 状况 (单行: ⛅ 晴间多云)
            float cy1 = r0Bottom + rowH / 2f;
            paint.setTextSize(11f * density);
            paint.setFakeBoldText(false);
            paint.setColor(Color.argb(alpha255, 245, 245, 255));
            String weatherLabel = item.icon + " " + item.weatherDesc;
            drawTextCenteredAt(canvas, weatherLabel, cx, cy1);

            // 行 3: 单行温度 (单行: 11~23°)
            float cy2 = r1Bottom + rowH / 2f;
            paint.setTextSize(12f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isToday ? Color.argb(alpha255, 255, 215, 100) : Color.argb(alpha255, 255, 190, 110));
            String tempLabel = item.minTemp + "~" + item.maxTemp + "°";
            drawTextCenteredAt(canvas, tempLabel, cx, cy2);
        }
    }

    /**
     * 紧凑逐小时走势表格（无首列，单行温度）：
     * 5 列：[12:00 前]  [13:00 现(高亮)]  [14:00]  [15:00]  [16:00]
     * 行1: 时间
     * 行2: 天气
     * 行3: 单行温度
     */
    private void drawHourlyTable(Canvas canvas, float left, float top, float right, float bottom, float density, int alpha255) {
        float tableW = right - left;
        float tableH = bottom - top;
        int count = Math.min(5, hourlyItems.size());
        if (count == 0) return;

        float colW = tableW / 5f;
        float rowH = tableH / 3f;
        float r0Bottom = top + rowH;
        float r1Bottom = r0Bottom + rowH;

        // 1. 高亮“现在”所在的列 (index 1)
        if (hourlyItems.size() >= 2) {
            float nowLeft = left + 1 * colW;
            float nowRight = nowLeft + colW;
            rectF.set(nowLeft, top, nowRight, bottom);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.16f), 66, 183, 255));
            canvas.drawRoundRect(rectF, 5 * density, 5 * density, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f * density);
            paint.setColor(Color.argb(Math.round(alpha255 * 0.40f), 100, 210, 255));
            canvas.drawRoundRect(rectF, 5 * density, 5 * density, paint);
        }

        // 2. 水平分割线
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(0.8f * density);
        paint.setColor(Color.argb(Math.round(alpha255 * 0.14f), 255, 255, 255));
        canvas.drawLine(left, r0Bottom, right, r0Bottom, paint);
        canvas.drawLine(left, r1Bottom, right, r1Bottom, paint);

        // 3. 列间垂直分隔线
        for (int i = 1; i < 5; i++) {
            float x = left + i * colW;
            canvas.drawLine(x, top, x, bottom, paint);
        }

        // 4. 绘制逐小时列数据（纯单行）
        paint.setStyle(Paint.Style.FILL);
        for (int i = 0; i < count; i++) {
            HourlyItem item = hourlyItems.get(i);
            float cx = left + i * colW + colW / 2f;

            // 行 1: 时间 (如 13:00 现 或 12:00 前)
            float cy0 = top + rowH / 2f;
            paint.setTextSize(10.5f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isNow ? Color.argb(alpha255, 120, 220, 255) : Color.argb(alpha255, 235, 240, 255));
            String timeText;
            if (item.isNow) {
                timeText = item.timeStr + " 现";
            } else if (i == 0) {
                timeText = item.timeStr + " 前";
            } else {
                timeText = item.timeStr;
            }
            drawTextCenteredAt(canvas, timeText, cx, cy0);

            // 行 2: 天气图标 + 简短天气 (如 ☀️晴 或 ⛅多云)
            float cy1 = r0Bottom + rowH / 2f;
            paint.setTextSize(10.5f * density);
            paint.setFakeBoldText(false);
            paint.setColor(Color.argb(alpha255, 245, 245, 255));
            drawTextCenteredAt(canvas, item.icon + item.weatherDesc, cx, cy1);

            // 行 3: 单行温度 (如 20°)
            float cy2 = r1Bottom + rowH / 2f;
            paint.setTextSize(12f * density);
            paint.setFakeBoldText(true);
            paint.setColor(item.isNow ? Color.argb(alpha255, 255, 215, 100) : Color.argb(alpha255, 120, 220, 255));
            drawTextCenteredAt(canvas, item.temp + "°", cx, cy2);
        }
    }

    private void drawTextCenteredAt(Canvas canvas, String text, float centerX, float centerY) {
        if (text == null || text.isEmpty()) return;
        Paint.FontMetrics fm = paint.getFontMetrics();
        float baseline = centerY - (fm.bottom + fm.top) / 2f;
        float w = paint.measureText(text);
        canvas.drawText(text, centerX - w / 2f, baseline, paint);
    }
}

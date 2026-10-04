package com.example.clocklauncher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.TrafficStats;
import android.os.BatteryManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * 待机桌面时钟核心组件：
 * 默认显示大屏时钟、实时日期、电池电量、实时网速、天气卡片、CPU每核心监控。
 * 支持防烧屏微漂移、左侧上下滑动调亮度、双击切换夜间模式、长按打开桌面设置。
 * 支持右滑/左滑/点击底部提示呼出应用列表。
 */
public class StandbyClockView extends FrameLayout {
    private static final String TAG = "StandbyClockView";
    private static final long BURN_IN_MOVE_INTERVAL_MS = 90_000L;
    private static final long BURN_IN_ANIMATION_MS = 28_000L;

    public interface OnClockActionListener {
        void onOpenAppDrawer();
        void onOpenSettings();
    }

    private TextView clockView;
    private TextView dateView;
    private TextView batteryView;
    private TextView networkView;
    private WeatherCardView weatherCardView;
    private CpuMonitorView cpuMonitorView;
    private TextView hintView;
    private FrameLayout bottomDockBar;
    private View dimOverlayView;

    private Handler handler;
    private SimpleDateFormat formatter;
    private SharedPreferences prefs;
    private final Random random = new Random();

    private float brightness = -1f;
    private float touchDownX;
    private float touchDownY;
    private float brightnessStartValue;
    private boolean brightnessGesture;
    private boolean gestureDetermined;
    private boolean movedDuringTouch;
    private boolean nightModeActive = false;
    private Boolean manualNightOverride = null;
    private Boolean lastScheduleState = null;
    private float savedNormalBrightness = -1f;
    private long lastTapTime = 0L;

    private boolean showDate = true;
    private boolean showBattery = true;
    private boolean showNetwork = true;
    private boolean showCpu = true;
    private boolean showWeather = true;
    private String cachedWeatherJson;

    private long lastRxBytes = -1L;
    private long lastTxBytes = -1L;
    private long lastNetworkSampleMs = -1L;

    private OnClockActionListener actionListener;

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            updateTime();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable burnInMover = new Runnable() {
        @Override public void run() {
            moveClockSlightly();
            handler.postDelayed(this, BURN_IN_MOVE_INTERVAL_MS);
        }
    };

    private final Runnable batteryUpdater = new Runnable() {
        @Override public void run() {
            updateBattery();
            handler.postDelayed(this, 60_000L);
        }
    };

    private final Runnable networkUpdater = new Runnable() {
        @Override public void run() {
            updateNetworkSpeed();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable cpuUpdater = new Runnable() {
        @Override public void run() {
            if (cpuMonitorView != null && showCpu) cpuMonitorView.sample();
            handler.postDelayed(this, 1000L);
        }
    };

    private final Runnable weatherUpdater = new Runnable() {
        @Override public void run() {
            if (showWeather) refreshWeather(false);
            handler.postDelayed(this, 30 * 60 * 1000L);
        }
    };

    private final Runnable longPressSettingsRunnable = new Runnable() {
        @Override
        public void run() {
            if (actionListener != null) {
                actionListener.onOpenSettings();
            }
        }
    };

    public StandbyClockView(@NonNull Context context) {
        this(context, null);
    }

    public StandbyClockView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public StandbyClockView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnClockActionListener(OnClockActionListener listener) {
        this.actionListener = listener;
    }

    private void init() {
        handler = new Handler(Looper.getMainLooper());
        prefs = getContext().getSharedPreferences(ClockPrefs.NAME, Context.MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);
        setBackgroundColor(Color.BLACK);

        buildViews();
        applySettings();
    }

    private void buildViews() {
        // 1. 时钟主文字
        clockView = new TextView(getContext());
        clockView.setGravity(Gravity.CENTER);
        clockView.setIncludeFontPadding(false);
        clockView.setSingleLine(true);
        clockView.setText("12:34:56");
        clockView.setPadding(dp(12), dp(8), dp(12), dp(8));
        addView(clockView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        // 2. 日期文本
        dateView = new TextView(getContext());
        dateView.setSingleLine(true);
        dateView.setText("0000-00-00 星期一");
        dateView.setTextColor(Color.argb(215, 255, 255, 255));
        dateView.setTextSize(20);
        dateView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        dateView.setGravity(Gravity.CENTER);
        dateView.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(dateView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // 3. 电量文本
        batteryView = new TextView(getContext());
        batteryView.setSingleLine(true);
        batteryView.setText("电量 --%");
        batteryView.setTextColor(Color.argb(195, 255, 255, 255));
        batteryView.setTextSize(17);
        batteryView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        batteryView.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(batteryView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // 4. 网速文本
        networkView = new TextView(getContext());
        networkView.setText(networkSpeedText("0 B/s", "0 B/s"));
        networkView.setTextColor(Color.argb(185, 255, 255, 255));
        networkView.setTextSize(15);
        networkView.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        networkView.setSingleLine(false);
        networkView.setLines(2);
        networkView.setIncludeFontPadding(false);
        networkView.setPadding(dp(8), dp(4), dp(8), dp(4));
        addView(networkView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // 5. 天气卡片
        weatherCardView = new WeatherCardView(getContext());
        addView(weatherCardView, new LayoutParams(dp(290), dp(96)));
        weatherCardView.setOnClickListener(v -> cycleWeatherMode());
        weatherCardView.setOnLongClickListener(v -> {
            Toast.makeText(getContext(), "正在刷新天气数据...", Toast.LENGTH_SHORT).show();
            refreshWeather(true);
            return true;
        });

        // 6. CPU 曲线监控
        cpuMonitorView = new CpuMonitorView(getContext());
        addView(cpuMonitorView, new LayoutParams(dp(140), dp(220)));

        // 7. 底部手势与导航操作栏
        bottomDockBar = new FrameLayout(getContext());
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.argb(140, 20, 24, 34));
        barBg.setCornerRadius(dp(20));
        barBg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        bottomDockBar.setBackground(barBg);
        bottomDockBar.setPadding(dp(16), dp(8), dp(16), dp(8));

        hintView = new TextView(getContext());
        hintView.setText("👉 右滑显示应用列表  |  长按桌面设置");
        hintView.setTextColor(Color.argb(200, 255, 255, 255));
        hintView.setTextSize(13);
        hintView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        hintView.setGravity(Gravity.CENTER);

        bottomDockBar.addView(hintView, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        LayoutParams dockParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(42), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dockParams.bottomMargin = dp(24);
        addView(bottomDockBar, dockParams);

        bottomDockBar.setOnClickListener(v -> {
            if (actionListener != null) {
                actionListener.onOpenAppDrawer();
            }
        });

        // 8. 夜间模式纯黑微调遮罩
        dimOverlayView = new View(getContext()) {
            @Override
            public boolean dispatchTouchEvent(MotionEvent event) {
                return false;
            }
        };
        dimOverlayView.setBackgroundColor(Color.BLACK);
        dimOverlayView.setVisibility(View.GONE);
        addView(dimOverlayView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    public void applySettings() {
        ClockPrefs.ensureDefaults(prefs);

        try {
            clockView.setTextColor(Color.parseColor(ClockPrefs.getDesktopTextColor(prefs)));
        } catch (IllegalArgumentException e) {
            clockView.setTextColor(Color.WHITE);
        }

        int shadow;
        try {
            shadow = Color.parseColor(ClockPrefs.getDesktopShadowColor(prefs));
        } catch (IllegalArgumentException e) {
            shadow = Color.BLACK;
        }
        clockView.setShadowLayer(10f, 4f, 4f, shadow);
        clockView.setTextSize(ClockPrefs.getDesktopTextSize(prefs));
        clockView.setTypeface(Typeface.DEFAULT, ClockPrefs.isDesktopBold(prefs) ? Typeface.BOLD : Typeface.NORMAL);

        showDate = prefs.getBoolean(DesktopConfig.KEY_SHOW_DATE, true);
        showBattery = ClockPrefs.showDesktopBattery(prefs);
        showNetwork = ClockPrefs.showDesktopNetwork(prefs);
        showCpu = ClockPrefs.showDesktopCpu(prefs);
        showWeather = prefs.getBoolean(DesktopConfig.KEY_SHOW_WEATHER, true);

        dateView.setVisibility(showDate ? View.VISIBLE : View.GONE);
        batteryView.setVisibility(showBattery ? View.VISIBLE : View.GONE);
        networkView.setVisibility(showNetwork ? View.VISIBLE : View.GONE);
        cpuMonitorView.setVisibility(showCpu ? View.VISIBLE : View.GONE);
        weatherCardView.setVisibility(showWeather ? View.VISIBLE : View.GONE);

        applyTextStyle(dateView, DesktopConfig.KEY_DATE_COLOR, DesktopConfig.KEY_DATE_SIZE,
                DesktopConfig.KEY_DATE_BOLD, "#D2FFFFFF", 20, false);
        applyTextStyle(batteryView, DesktopConfig.KEY_BATTERY_COLOR, DesktopConfig.KEY_BATTERY_SIZE,
                DesktopConfig.KEY_BATTERY_BOLD, "#B9FFFFFF", 18, false);
        applyTextStyle(networkView, DesktopConfig.KEY_NETWORK_COLOR, DesktopConfig.KEY_NETWORK_SIZE,
                DesktopConfig.KEY_NETWORK_BOLD, "#B9FFFFFF", 15, true);

        weatherCardView.setPanelAlpha(prefs.getInt(DesktopConfig.KEY_WEATHER_ALPHA, 100));
        weatherCardView.setCityName(prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京"));
        weatherCardView.setCardMode(prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, WeatherCardView.MODE_DAILY));
        try {
            weatherCardView.setTextColor(Color.parseColor(prefs.getString(DesktopConfig.KEY_WEATHER_COLOR, "#D2FFFFFF")));
        } catch (Exception ignored) {
            weatherCardView.setTextColor(Color.WHITE);
        }
        weatherCardView.setTextBold(prefs.getBoolean(DesktopConfig.KEY_WEATHER_BOLD, true));
        weatherCardView.setTextScale(prefs.getInt(DesktopConfig.KEY_WEATHER_TEXT_SCALE, 100) / 100f);

        cpuMonitorView.setPanelAlpha(prefs.getInt(DesktopConfig.KEY_CPU_ALPHA, 100));

        updateDate();
        updateBattery();
        resetNetworkSample();
        updateWeatherDisplay();

        savedNormalBrightness = prefs.getFloat(DesktopConfig.KEY_NORMAL_BRIGHTNESS, DesktopConfig.DEFAULT_NORMAL_BRIGHTNESS);
        boolean isNight = determineNightModeActive();
        applyNightModeState(isNight, false);

        String format = ClockPrefs.getDesktopFormat(prefs);
        try {
            formatter = ClockPrefs.createFormatter(format);
        } catch (IllegalArgumentException e) {
            formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_DESKTOP_FORMAT);
        }

        requestLayout();
        post(() -> {
            requestLayoutResponsive();
            moveClockSlightly();
        });
    }

    public void onResume() {
        applySettings();
        handler.removeCallbacks(ticker);
        handler.post(ticker);
        handler.removeCallbacks(batteryUpdater);
        if (showBattery) handler.post(batteryUpdater);
        handler.removeCallbacks(networkUpdater);
        if (showNetwork) handler.post(networkUpdater);
        handler.removeCallbacks(cpuUpdater);
        if (showCpu) handler.post(cpuUpdater);
        handler.removeCallbacks(weatherUpdater);
        if (showWeather) {
            refreshWeather(false);
            handler.postDelayed(weatherUpdater, 30 * 60 * 1000L);
        }
        handler.removeCallbacks(burnInMover);
        handler.postDelayed(burnInMover, 10_000L);
    }

    public void onPause() {
        handler.removeCallbacks(ticker);
        handler.removeCallbacks(burnInMover);
        handler.removeCallbacks(batteryUpdater);
        handler.removeCallbacks(networkUpdater);
        handler.removeCallbacks(cpuUpdater);
        handler.removeCallbacks(weatherUpdater);
        handler.removeCallbacks(longPressSettingsRunnable);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            requestLayoutResponsive();
        }
    }

    /**
     * 响应式布局自适应：完美支持竖屏手机与横屏底座模式，
     * 若用户在排版模式中自定义了位置，则精准还原用户自定义坐标。
     */
    private void requestLayoutResponsive() {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        // 若存在自定义自由拖拽排版，则精准还原组件位置
        if (DesktopConfig.hasPosition(prefs, DesktopConfig.COMPONENT_CLOCK)) {
            restoreCustomPosition(clockView, DesktopConfig.COMPONENT_CLOCK);
            restoreCustomPosition(dateView, DesktopConfig.COMPONENT_DATE);
            restoreCustomPosition(batteryView, DesktopConfig.COMPONENT_BATTERY);
            restoreCustomPosition(networkView, DesktopConfig.COMPONENT_NETWORK);
            restoreCustomPosition(cpuMonitorView, DesktopConfig.COMPONENT_CPU);
            restoreCustomPosition(weatherCardView, DesktopConfig.COMPONENT_WEATHER);

            int dockW = getViewWidth(bottomDockBar);
            setChildBounds(bottomDockBar, (w - dockW) / 2, h - dp(64), dockW, dp(42));
            return;
        }

        boolean isPortrait = w < h;

        int prefWeatherW = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 300));
        int prefWeatherH = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96));
        int prefCpuW = dp(prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140));
        int prefCpuH = dp(prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 220));

        if (isPortrait) {
            // ============ 竖屏布局 (标准手机主屏) ============
            // 1. 顶部天气卡片 (顶栏居中偏上)
            int cardW = Math.min(w - dp(32), prefWeatherW);
            int cardH = prefWeatherH;
            setChildBounds(weatherCardView, (w - cardW) / 2, dp(54), cardW, cardH);

            // 2. 电量与实时网速（天气卡片下方并排状态条）
            int statusTop = dp(54) + cardH + dp(10);
            setChildBounds(batteryView, dp(20), statusTop, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setChildBounds(networkView, w - dp(180), statusTop - dp(4), dp(160), LayoutParams.WRAP_CONTENT);

            // 3. 中心巨型时钟与日期
            int clockY = Math.round(h * 0.40f) - dp(40);
            setChildBounds(clockView, (w - getViewWidth(clockView)) / 2, clockY, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            int dateY = clockY + getViewHeight(clockView) + dp(8);
            setChildBounds(dateView, (w - getViewWidth(dateView)) / 2, dateY, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            // 4. CPU曲线面板 (紧随日期下方，居中轻量展示)
            int cpuW = Math.min(w - dp(48), prefCpuW);
            int cpuH = Math.min(dp(260), prefCpuH);
            int cpuY = dateY + getViewHeight(dateView) + dp(16);
            if (cpuY + cpuH < h - dp(90)) {
                setChildBounds(cpuMonitorView, (w - cpuW) / 2, cpuY, cpuW, cpuH);
            } else {
                setChildBounds(cpuMonitorView, (w - cpuW) / 2, h - dp(80) - cpuH, cpuW, cpuH);
            }

            // 5. 底部右滑提示胶囊
            int dockW = getViewWidth(bottomDockBar);
            int dockH = dp(42);
            setChildBounds(bottomDockBar, (w - dockW) / 2, h - dp(64), dockW, dockH);
        } else {
            // ============ 横屏布局 (桌面待机时钟) ============
            // 1. 左上角：天气卡片
            int cardW = prefWeatherW;
            int cardH = prefWeatherH;
            setChildBounds(weatherCardView, dp(24), dp(18), cardW, cardH);

            // 2. 右上角：日期与电量
            setChildBounds(dateView, w - getViewWidth(dateView) - dp(24), dp(18), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            setChildBounds(batteryView, w - getViewWidth(batteryView) - dp(24), dp(54), LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            // 3. 正中央：大时钟
            setChildBounds(clockView, (w - getViewWidth(clockView)) / 2, (h - getViewHeight(clockView)) / 2, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);

            // 4. 左侧中间偏下：CPU监控
            int cpuW = prefCpuW;
            int cpuH = prefCpuH;
            setChildBounds(cpuMonitorView, dp(24), (h - cpuH) / 2 + dp(20), cpuW, cpuH);

            // 5. 右下角：网速
            setChildBounds(networkView, w - dp(180), h - dp(70), dp(160), LayoutParams.WRAP_CONTENT);

            // 6. 底部中央：导航与操作提示
            int dockW = getViewWidth(bottomDockBar);
            setChildBounds(bottomDockBar, (w - dockW) / 2, h - dp(56), dockW, dp(42));
        }
    }

    private void restoreCustomPosition(View view, String component) {
        if (view == null || !DesktopConfig.hasPosition(prefs, component)) return;
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        int w = getWidth();
        int h = getHeight();
        float maxX = Math.max(0, w - vw);
        float maxY = Math.max(0, h - vh);
        float ratioX = clamp(DesktopConfig.getX(prefs, component), 0f, 1f);
        float ratioY = clamp(DesktopConfig.getY(prefs, component), 0f, 1f);
        int targetX = Math.round(ratioX * maxX);
        int targetY = Math.round(ratioY * maxY);

        int reqW = LayoutParams.WRAP_CONTENT;
        int reqH = LayoutParams.WRAP_CONTENT;
        if (view == weatherCardView) {
            reqW = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 290));
            reqH = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96));
        } else if (view == cpuMonitorView) {
            reqW = dp(prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140));
            reqH = dp(prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 220));
        } else if (view == networkView) {
            reqW = dp(160);
        }
        setChildBounds(view, targetX, targetY, reqW, reqH);
    }

    private void setChildBounds(View view, int x, int y, int width, int height) {
        if (view == null) return;
        LayoutParams lp = (LayoutParams) view.getLayoutParams();
        if (lp == null) lp = new LayoutParams(width, height);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.max(0, x);
        lp.topMargin = Math.max(0, y);
        if (width != LayoutParams.WRAP_CONTENT && width != LayoutParams.MATCH_PARENT) {
            lp.width = width;
        }
        if (height != LayoutParams.WRAP_CONTENT && height != LayoutParams.MATCH_PARENT) {
            lp.height = height;
        }
        lp.rightMargin = 0;
        lp.bottomMargin = 0;
        view.setLayoutParams(lp);
    }

    private void updateTime() {
        if (formatter == null) formatter = ClockPrefs.createFormatter(ClockPrefs.DEFAULT_DESKTOP_FORMAT);
        clockView.setText(formatter.format(new Date()));
        checkNightModeSchedule();
    }

    private void updateDate() {
        if (dateView == null) return;
        dateView.setText(new SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINA).format(new Date()));
    }

    private void updateBattery() {
        if (batteryView == null || !showBattery) return;
        Intent battery = getContext().registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) {
            batteryView.setText("电量 --%");
            return;
        }
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int percent = scale > 0 && level >= 0 ? Math.round(level * 100f / scale) : -1;
        boolean charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
        String text = (charging ? "⚡ " : "") + "电量 " + (percent >= 0 ? percent + "%" : "--%");
        batteryView.setText(text);
    }

    private void resetNetworkSample() {
        lastRxBytes = TrafficStats.getTotalRxBytes();
        lastTxBytes = TrafficStats.getTotalTxBytes();
        lastNetworkSampleMs = System.currentTimeMillis();
        if (networkView != null && showNetwork) networkView.setText(networkSpeedText("0 B/s", "0 B/s"));
    }

    private void updateNetworkSpeed() {
        if (networkView == null || !showNetwork) return;
        long rxBytes = TrafficStats.getTotalRxBytes();
        long txBytes = TrafficStats.getTotalTxBytes();
        long now = System.currentTimeMillis();
        if (rxBytes == TrafficStats.UNSUPPORTED || txBytes == TrafficStats.UNSUPPORTED) {
            networkView.setText(networkSpeedText("--", "--"));
            return;
        }
        if (lastRxBytes < 0L || lastTxBytes < 0L || lastNetworkSampleMs <= 0L
                || rxBytes < lastRxBytes || txBytes < lastTxBytes) {
            lastRxBytes = rxBytes;
            lastTxBytes = txBytes;
            lastNetworkSampleMs = now;
            networkView.setText(networkSpeedText("0 B/s", "0 B/s"));
            return;
        }
        long elapsedMs = Math.max(1L, now - lastNetworkSampleMs);
        double downloadBytesPerSecond = (rxBytes - lastRxBytes) * 1000d / elapsedMs;
        double uploadBytesPerSecond = (txBytes - lastTxBytes) * 1000d / elapsedMs;
        networkView.setText(networkSpeedText(
                formatSpeed(downloadBytesPerSecond),
                formatSpeed(uploadBytesPerSecond)));
        lastRxBytes = rxBytes;
        lastTxBytes = txBytes;
        lastNetworkSampleMs = now;
    }

    private String networkSpeedText(String download, String upload) {
        return String.format(Locale.getDefault(), "↓ %9s\n↑ %9s", download, upload);
    }

    private String formatSpeed(double bytesPerSecond) {
        double speed = Math.max(0d, bytesPerSecond);
        if (speed < 1024d) return String.format(Locale.getDefault(), "%.0f B/s", speed);
        speed /= 1024d;
        if (speed < 1024d) return String.format(Locale.getDefault(), speed < 10d ? "%.1f K/s" : "%.0f K/s", speed);
        speed /= 1024d;
        if (speed < 1024d) return String.format(Locale.getDefault(), speed < 10d ? "%.1f M/s" : "%.0f M/s", speed);
        speed /= 1024d;
        return String.format(Locale.getDefault(), speed < 10d ? "%.1f G/s" : "%.0f G/s", speed);
    }

    private void applyTextStyle(TextView view, String colorKey, String sizeKey, String boldKey,
                                String defaultColor, int defaultSize, boolean monospace) {
        try { view.setTextColor(Color.parseColor(prefs.getString(colorKey, defaultColor))); }
        catch (Exception ignored) { view.setTextColor(Color.WHITE); }
        view.setTextSize(Math.max(10, Math.min(72, prefs.getInt(sizeKey, defaultSize))));
        boolean bold = prefs.getBoolean(boldKey, true);
        view.setTypeface(monospace ? Typeface.MONOSPACE : Typeface.DEFAULT,
                bold ? Typeface.BOLD : Typeface.NORMAL);
        if (view != networkView) {
            view.setSingleLine(true);
            view.setMaxLines(1);
        }
    }

    private void moveClockSlightly() {
        if (getWidth() <= 0 || getHeight() <= 0) return;
        animateBurnInSafely(clockView, dp(24), dp(24), dp(8));
        animateBurnInSafely(dateView, dp(16), dp(16), dp(5));
        animateBurnInSafely(batteryView, dp(14), dp(14), dp(4));
        animateBurnInSafely(networkView, dp(14), dp(12), dp(4));
        animateBurnInSafely(weatherCardView, dp(16), dp(16), dp(5));
        if (cpuMonitorView != null && showCpu) {
            animateBurnInSafely(cpuMonitorView, dp(12), dp(18), dp(4));
        }
    }

    private void animateBurnInSafely(View view, int desiredMaxX, int desiredMaxY, int step) {
        if (view == null || view.getVisibility() != View.VISIBLE) return;
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        if (vw <= 0 || vh <= 0) return;

        float curTransX = view.getTranslationX();
        float curTransY = view.getTranslationY();
        if (Math.abs(curTransX) > desiredMaxX * 2f) view.setTranslationX(0f);
        if (Math.abs(curTransY) > desiredMaxY * 2f) view.setTranslationY(0f);

        float targetX = nextSafeOffset(curTransX, -desiredMaxX, desiredMaxX, step);
        float targetY = nextSafeOffset(curTransY, -desiredMaxY, desiredMaxY, step);
        view.animate().translationX(targetX).translationY(targetY)
                .setDuration(BURN_IN_ANIMATION_MS).start();
    }

    private float nextSafeOffset(float current, float min, float max, int step) {
        if (min > max) return 0f;
        float clamped = clamp(current, min, max);
        float target = clamped + randomOffset(Math.max(1, step));
        return clamp(target, min, max);
    }

    private int getViewWidth(View view) {
        if (view == null) return 0;
        if (view instanceof TextView && view != networkView) {
            TextView tv = (TextView) view;
            CharSequence cs = tv.getText();
            if (cs != null && cs.length() > 0) {
                float textW = tv.getPaint().measureText(cs.toString());
                int paddingW = tv.getCompoundPaddingLeft() + tv.getCompoundPaddingRight();
                return (int) Math.ceil(textW + paddingW);
            }
        }
        int w = view.getWidth();
        if (w > 0) return w;
        w = view.getMeasuredWidth();
        if (w > 0) return w;
        if (getWidth() > 0 && getHeight() > 0) {
            view.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
            return view.getMeasuredWidth();
        }
        return 0;
    }

    private int getViewHeight(View view) {
        if (view == null) return 0;
        if (view instanceof TextView && view != networkView) {
            TextView tv = (TextView) view;
            Paint.FontMetrics fm = tv.getPaint().getFontMetrics();
            float textH = fm.bottom - fm.top;
            int paddingH = tv.getCompoundPaddingTop() + tv.getCompoundPaddingBottom();
            return (int) Math.ceil(textH + paddingH);
        }
        int h = view.getHeight();
        if (h > 0) return h;
        h = view.getMeasuredHeight();
        if (h > 0) return h;
        if (getWidth() > 0 && getHeight() > 0) {
            view.measure(
                    MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.AT_MOST),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.AT_MOST));
            return view.getMeasuredHeight();
        }
        return 0;
    }

    private void cycleWeatherMode() {
        if (weatherCardView == null) return;
        int currentMode = weatherCardView.getCardMode();
        int nextMode = (currentMode == WeatherCardView.MODE_DAILY) ? WeatherCardView.MODE_HOURLY : WeatherCardView.MODE_DAILY;
        weatherCardView.setCardMode(nextMode);
        prefs.edit().putInt(DesktopConfig.KEY_WEATHER_MODE, nextMode).apply();
        String modeName = (nextMode == WeatherCardView.MODE_DAILY) ? "三日对比卡片" : "逐小时走势卡片";
        Toast.makeText(getContext(), "切换为: " + modeName, Toast.LENGTH_SHORT).show();
    }

    private void updateWeatherDisplay() {
        if (!showWeather || weatherCardView == null) return;
        int mode = prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, WeatherCardView.MODE_DAILY);
        String city = prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京");
        weatherCardView.setCityName(city);
        weatherCardView.setCardMode(mode);
        if (cachedWeatherJson != null) {
            weatherCardView.updateWeatherData(cachedWeatherJson, false);
        } else {
            String cache = prefs.getString(DesktopConfig.KEY_WEATHER_DATA_CACHE, null);
            if (cache != null) {
                cachedWeatherJson = cache;
                weatherCardView.updateWeatherData(cachedWeatherJson, false);
            } else {
                weatherCardView.updateWeatherData(null, false);
            }
        }
    }

    private void refreshWeather(boolean force) {
        if (!showWeather) return;
        float lat = prefs.getFloat(DesktopConfig.KEY_WEATHER_LAT, 39.9042f);
        float lon = prefs.getFloat(DesktopConfig.KEY_WEATHER_LON, 116.4074f);
        WeatherManager.fetchWeather(getContext(), lat, lon, force, new WeatherManager.WeatherCallback() {
            @Override
            public void onSuccess(String json) {
                cachedWeatherJson = json;
                updateWeatherDisplay();
                if (force) Toast.makeText(getContext(), "天气已更新", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(String message) {
                if (cachedWeatherJson == null && weatherCardView != null) {
                    weatherCardView.updateWeatherData(null, true);
                }
                if (force) Toast.makeText(getContext(), message, Toast.LENGTH_SHORT).show();
            }
        });
    }

    /** 左侧亮度手势感应区宽度：取屏宽 1/6 与 80dp 的较大值，保证单手也能轻松够到。 */
    private int brightnessZoneWidth() {
        return Math.max(dp(80), getWidth() / 6);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (ev.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                // 在拦截阶段就记录起点：子 View（天气卡片等）可能吞掉 DOWN，
                // 那样 onTouchEvent 永远收不到 DOWN，亮度手势就会失效。
                touchDownX = ev.getX();
                touchDownY = ev.getY();
                brightnessStartValue = currentBrightness();
                brightnessGesture = false;
                gestureDetermined = false;
                movedDuringTouch = false;
                break;

            case MotionEvent.ACTION_MOVE:
                if (!gestureDetermined && touchDownX <= brightnessZoneWidth()) {
                    float dx = Math.abs(ev.getX() - touchDownX);
                    float dy = Math.abs(ev.getY() - touchDownY);
                    // 大幅上滑优先判定为「呼出应用列表」，不在左区抢事件
                    boolean upwardFling = (ev.getY() - touchDownY) < -dp(70);
                    // 起手就是纵向滑动 => 判定为调节亮度，从子 View 手里抢过事件流
                    if (!upwardFling && dy > touchSlop && dy > dx * 1.2f) {
                        brightnessGesture = true;
                        gestureDetermined = true;
                        handler.removeCallbacks(longPressSettingsRunnable);
                        getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    }
                }
                break;

            default:
                break;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int leftZone = brightnessZoneWidth();
        int touchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                touchDownX = event.getX();
                touchDownY = event.getY();
                brightnessStartValue = currentBrightness();
                brightnessGesture = false;
                gestureDetermined = false;
                movedDuringTouch = false;
                handler.postDelayed(longPressSettingsRunnable, 800L);
                return true;

            case MotionEvent.ACTION_MOVE:
                float dx = event.getX() - touchDownX;
                float dy = event.getY() - touchDownY;

                if (!gestureDetermined) {
                    if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx) * 1.2f && touchDownX <= leftZone) {
                        brightnessGesture = true;
                        gestureDetermined = true;
                        handler.removeCallbacks(longPressSettingsRunnable);
                        getParent().requestDisallowInterceptTouchEvent(true);
                    } else if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                        brightnessGesture = false;
                        gestureDetermined = true;
                        handler.removeCallbacks(longPressSettingsRunnable);
                        getParent().requestDisallowInterceptTouchEvent(false);
                    }
                }

                if (brightnessGesture) {
                    float delta = (touchDownY - event.getY()) / Math.max(1f, getHeight());
                    setBrightness(clamp(brightnessStartValue + delta, 0.01f, 1f));
                    movedDuringTouch = true;
                    return true;
                }

                if (Math.abs(dx) > dp(16) || Math.abs(dy) > dp(16)) {
                    movedDuringTouch = true;
                    handler.removeCallbacks(longPressSettingsRunnable);
                }

                // 向上滑动快捷呼出应用列表
                if (dy < -dp(70) && Math.abs(dy) > Math.abs(dx) * 1.3f) {
                    if (actionListener != null) {
                        actionListener.onOpenAppDrawer();
                    }
                    return true;
                }
                return true;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                handler.removeCallbacks(longPressSettingsRunnable);
                float totalDx = event.getX() - touchDownX;
                float totalDy = event.getY() - touchDownY;

                if (!brightnessGesture && event.getAction() == MotionEvent.ACTION_UP) {
                    // 1. 判断是否为水平右滑手势 (向右滑动 或 向左滑动超过阈值)
                    if (Math.abs(totalDx) > dp(50) && Math.abs(totalDx) > Math.abs(totalDy) * 1.2f) {
                        if (actionListener != null) {
                            actionListener.onOpenAppDrawer();
                        }
                        return true;
                    }

                    // 2. 双击切换夜间模式
                    if (!movedDuringTouch) {
                        long now = SystemClock.uptimeMillis();
                        if (now - lastTapTime < 350L) {
                            lastTapTime = 0L;
                            toggleNightModeManual();
                        } else {
                            lastTapTime = now;
                        }
                    }
                }
                brightnessGesture = false;
                gestureDetermined = false;
                getParent().requestDisallowInterceptTouchEvent(false);
                return true;

            default:
                return super.onTouchEvent(event);
        }
    }

    private boolean determineNightModeActive() {
        if (manualNightOverride != null) return manualNightOverride;
        if (prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, false)) return true;
        return isWithinAutoSchedule();
    }

    private boolean isWithinAutoSchedule() {
        if (!prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, false)) return false;
        String start = prefs.getString(DesktopConfig.KEY_NIGHT_MODE_START, DesktopConfig.DEFAULT_NIGHT_START);
        String end = prefs.getString(DesktopConfig.KEY_NIGHT_MODE_END, DesktopConfig.DEFAULT_NIGHT_END);
        return DesktopConfig.isTimeInRange(start, end, Calendar.getInstance());
    }

    private float getNightBrightness() {
        int percent = prefs.getInt(DesktopConfig.KEY_NIGHT_MODE_BRIGHTNESS, DesktopConfig.DEFAULT_NIGHT_BRIGHTNESS);
        return clamp(percent / 100f, 0.01f, 1f);
    }

    private void applyNightModeState(boolean night, boolean showToast) {
        this.nightModeActive = night;
        if (night) {
            float cur = currentBrightness();
            if (cur > 0.05f) {
                savedNormalBrightness = cur;
                prefs.edit().putFloat(DesktopConfig.KEY_NORMAL_BRIGHTNESS, savedNormalBrightness).apply();
            }
            float nightBrightness = getNightBrightness();
            setBrightnessInternal(nightBrightness);
            if (dimOverlayView != null) {
                boolean extraDim = prefs.getBoolean(DesktopConfig.KEY_NIGHT_EXTRA_DIM, DesktopConfig.DEFAULT_NIGHT_EXTRA_DIM);
                if (extraDim) {
                    int depth = prefs.getInt(DesktopConfig.KEY_NIGHT_EXTRA_DIM_DEPTH, DesktopConfig.DEFAULT_NIGHT_EXTRA_DIM_DEPTH);
                    dimOverlayView.setAlpha(clamp(depth / 100f, 0.1f, 0.95f));
                    dimOverlayView.setVisibility(View.VISIBLE);
                } else {
                    dimOverlayView.setVisibility(View.GONE);
                }
            }
            if (showToast) {
                Toast.makeText(getContext(), "🌙 已开启夜间模式（亮度最小）", Toast.LENGTH_SHORT).show();
            }
        } else {
            float restore = (savedNormalBrightness > 0.05f) ? savedNormalBrightness
                    : prefs.getFloat(DesktopConfig.KEY_NORMAL_BRIGHTNESS, DesktopConfig.DEFAULT_NORMAL_BRIGHTNESS);
            setBrightnessInternal(clamp(restore, 0.05f, 1f));
            if (dimOverlayView != null) {
                dimOverlayView.setVisibility(View.GONE);
            }
            if (showToast) {
                Toast.makeText(getContext(), "☀️ 已退出夜间模式", Toast.LENGTH_SHORT).show();
            }
        }
        updateHintText();
    }

    private void toggleNightModeManual() {
        if (nightModeActive) {
            manualNightOverride = false;
            prefs.edit().putBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, false).apply();
            applyNightModeState(false, true);
        } else {
            manualNightOverride = true;
            prefs.edit().putBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, true).apply();
            applyNightModeState(true, true);
        }
    }

    private void checkNightModeSchedule() {
        boolean autoScheduleNow = isWithinAutoSchedule();
        if (lastScheduleState == null) {
            lastScheduleState = autoScheduleNow;
        } else if (lastScheduleState != autoScheduleNow) {
            lastScheduleState = autoScheduleNow;
            manualNightOverride = null;
        }
        boolean shouldBeNight = determineNightModeActive();
        if (shouldBeNight != nightModeActive) {
            applyNightModeState(shouldBeNight, true);
        }
    }

    private void updateHintText() {
        if (hintView == null) return;
        int percent = Math.round(currentBrightness() * 100);
        if (nightModeActive) {
            hintView.setText("🌙 夜间待机 (" + Math.max(1, percent) + "%) · 双击退出 · 右滑应用列表");
        } else {
            hintView.setText("👉 右滑应用列表  ·  左侧上下滑动调亮度 (" + Math.max(1, percent) + "%)  ·  长按设置");
        }
    }

    /** 供设置面板等外部读取当前亮度百分比。 */
    public int getBrightnessPercent() {
        return Math.max(1, Math.round(currentBrightness() * 100));
    }

    /** 供设置面板直接设定亮度。 */
    public void setBrightnessPercent(int percent) {
        float value = clamp(percent / 100f, 0.01f, 1f);
        if (nightModeActive) {
            prefs.edit().putInt(DesktopConfig.KEY_NIGHT_MODE_BRIGHTNESS, Math.max(1, percent)).apply();
            setBrightnessInternal(value);
        } else {
            setBrightness(value);
        }
    }

    private float currentBrightness() {
        if (brightness > 0f) return brightness;
        if (getContext() instanceof Activity) {
            float sb = ((Activity) getContext()).getWindow().getAttributes().screenBrightness;
            if (sb > 0f) return sb;
        }
        return 0.7f;
    }

    private void setBrightness(float value) {
        setBrightnessInternal(value);
        if (!nightModeActive) {
            savedNormalBrightness = value;
            prefs.edit().putFloat(DesktopConfig.KEY_NORMAL_BRIGHTNESS, value).apply();
        }
    }

    private void setBrightnessInternal(float value) {
        brightness = value;
        if (getContext() instanceof Activity) {
            Activity a = (Activity) getContext();
            WindowManager.LayoutParams attrs = a.getWindow().getAttributes();
            attrs.screenBrightness = value;
            a.getWindow().setAttributes(attrs);
        }
        updateHintText();
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private float randomOffset(int max) {
        if (max <= 0) return 0f;
        return random.nextInt(max * 2 + 1) - max;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

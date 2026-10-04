package com.example.clocklauncher;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 全屏可视化自由拖拽排版编辑器：
 * 用户可随意用手指拖动各组件到任意理想位置，实时显示金黄色辅助拖动边框。
 * 顶部常驻浮动控制栏：支持「恢复默认」与「完成保存」。
 */
public class DesktopLayoutEditActivity extends AppCompatActivity {
    private FrameLayout root;
    private TextView clockView;
    private TextView dateView;
    private TextView batteryView;
    private TextView networkView;
    private WeatherCardView weatherCardView;
    private CpuMonitorView cpuMonitorView;
    private TextView tipView;
    private FrameLayout editToolbar;

    private SharedPreferences prefs;
    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);

        buildUi();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 21) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.setStatusBarColor(Color.BLACK);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private void buildUi() {
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#080C14"));

        // 1. 时钟组件
        clockView = new TextView(this);
        clockView.setGravity(Gravity.CENTER);
        clockView.setIncludeFontPadding(false);
        clockView.setSingleLine(true);
        clockView.setText(new SimpleDateFormat(ClockPrefs.getDesktopFormat(prefs), Locale.getDefault()).format(new Date()));
        try {
            clockView.setTextColor(Color.parseColor(ClockPrefs.getDesktopTextColor(prefs)));
        } catch (Exception e) {
            clockView.setTextColor(Color.WHITE);
        }
        clockView.setTextSize(ClockPrefs.getDesktopTextSize(prefs));
        clockView.setTypeface(Typeface.DEFAULT, ClockPrefs.isDesktopBold(prefs) ? Typeface.BOLD : Typeface.NORMAL);
        clockView.setPadding(dp(12), dp(8), dp(12), dp(8));
        root.addView(clockView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 2. 日期组件
        dateView = new TextView(this);
        dateView.setSingleLine(true);
        dateView.setText(new SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINA).format(new Date()));
        dateView.setTextSize(prefs.getInt(DesktopConfig.KEY_DATE_SIZE, 22));
        dateView.setTypeface(Typeface.DEFAULT, prefs.getBoolean(DesktopConfig.KEY_DATE_BOLD, true) ? Typeface.BOLD : Typeface.NORMAL);
        try { dateView.setTextColor(Color.parseColor(prefs.getString(DesktopConfig.KEY_DATE_COLOR, "#D2FFFFFF"))); }
        catch (Exception ignored) { dateView.setTextColor(Color.WHITE); }
        dateView.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(dateView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 3. 电量组件
        batteryView = new TextView(this);
        batteryView.setSingleLine(true);
        batteryView.setText("⚡ 电量 98%");
        batteryView.setTextSize(prefs.getInt(DesktopConfig.KEY_BATTERY_SIZE, 18));
        batteryView.setTypeface(Typeface.DEFAULT, prefs.getBoolean(DesktopConfig.KEY_BATTERY_BOLD, true) ? Typeface.BOLD : Typeface.NORMAL);
        try { batteryView.setTextColor(Color.parseColor(prefs.getString(DesktopConfig.KEY_BATTERY_COLOR, "#B9FFFFFF"))); }
        catch (Exception ignored) { batteryView.setTextColor(Color.WHITE); }
        batteryView.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(batteryView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT));

        // 4. 网速组件
        networkView = new TextView(this);
        networkView.setText("↓ 2.4 M/s\n↑ 180 K/s");
        networkView.setTextSize(prefs.getInt(DesktopConfig.KEY_NETWORK_SIZE, 16));
        networkView.setTypeface(Typeface.MONOSPACE, prefs.getBoolean(DesktopConfig.KEY_NETWORK_BOLD, true) ? Typeface.BOLD : Typeface.NORMAL);
        try { networkView.setTextColor(Color.parseColor(prefs.getString(DesktopConfig.KEY_NETWORK_COLOR, "#B9FFFFFF"))); }
        catch (Exception ignored) { networkView.setTextColor(Color.WHITE); }
        networkView.setPadding(dp(8), dp(4), dp(8), dp(4));
        root.addView(networkView, new FrameLayout.LayoutParams(dp(160), FrameLayout.LayoutParams.WRAP_CONTENT));

        // 5. 天气卡片
        weatherCardView = new WeatherCardView(this);
        weatherCardView.setPanelAlpha(prefs.getInt(DesktopConfig.KEY_WEATHER_ALPHA, 100));
        weatherCardView.setCityName(prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京"));
        weatherCardView.setCardMode(prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, WeatherCardView.MODE_DAILY));
        String cache = prefs.getString(DesktopConfig.KEY_WEATHER_DATA_CACHE, null);
        weatherCardView.updateWeatherData(cache, false);
        int weatherW = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 300));
        int weatherH = dp(prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96));
        root.addView(weatherCardView, new FrameLayout.LayoutParams(weatherW, weatherH));

        // 6. CPU 曲线监控
        cpuMonitorView = new CpuMonitorView(this);
        cpuMonitorView.setPanelAlpha(prefs.getInt(DesktopConfig.KEY_CPU_ALPHA, 100));
        cpuMonitorView.sample();
        int cpuW = dp(prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140));
        int cpuH = dp(prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 220));
        root.addView(cpuMonitorView, new FrameLayout.LayoutParams(cpuW, cpuH));

        // 7. 顶部操作工具栏
        buildEditToolbar();

        setContentView(root);

        root.post(this::setupDraggableLayout);
    }

    private void buildEditToolbar() {
        editToolbar = new FrameLayout(this);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.argb(225, 24, 30, 44));
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), Color.argb(120, 255, 204, 68));
        editToolbar.setBackground(bg);
        editToolbar.setPadding(dp(12), dp(6), dp(12), dp(6));

        Button resetBtn = editButton("🔄 恢复默认");
        resetBtn.setTextColor(Color.parseColor("#FF9999"));
        Button doneBtn = editButton("✅ 完成保存");
        doneBtn.setTextColor(Color.parseColor("#70D8A5"));

        FrameLayout.LayoutParams resetLp = new FrameLayout.LayoutParams(dp(110), dp(42), Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams doneLp = new FrameLayout.LayoutParams(dp(110), dp(42), Gravity.END | Gravity.CENTER_VERTICAL);
        editToolbar.addView(resetBtn, resetLp);
        editToolbar.addView(doneBtn, doneLp);

        FrameLayout.LayoutParams toolbarLp = new FrameLayout.LayoutParams(dp(250), dp(54), Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        toolbarLp.topMargin = dp(28);
        root.addView(editToolbar, toolbarLp);

        resetBtn.setOnClickListener(v -> {
            DesktopConfig.resetAllPositions(prefs);
            Toast.makeText(this, "已重置为系统默认排版", Toast.LENGTH_SHORT).show();
            setupDraggableLayout();
        });

        doneBtn.setOnClickListener(v -> {
            saveAllPositions();
            Toast.makeText(this, "排版已保存", Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private Button editButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setTextSize(13);
        b.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        b.setBackgroundColor(Color.TRANSPARENT);
        return b;
    }

    private void setupDraggableLayout() {
        int w = root.getWidth();
        int h = root.getHeight();
        if (w <= 0 || h <= 0) return;

        // 若已有保存坐标则精准还原，否则给出一套互不重叠的默认摆放建议
        int weatherH = getViewHeight(weatherCardView);
        int clockH = getViewHeight(clockView);

        positionView(weatherCardView, DesktopConfig.COMPONENT_WEATHER, dp(24), dp(20));
        positionView(cpuMonitorView, DesktopConfig.COMPONENT_CPU, dp(24), dp(20) + weatherH + dp(16));
        positionView(clockView, DesktopConfig.COMPONENT_CLOCK,
                (w - getViewWidth(clockView)) / 2, Math.max(dp(20), (h - clockH) / 2));
        positionView(dateView, DesktopConfig.COMPONENT_DATE,
                (w - getViewWidth(dateView)) / 2, Math.max(dp(20), (h - clockH) / 2) + clockH + dp(4));
        positionView(batteryView, DesktopConfig.COMPONENT_BATTERY, w - getViewWidth(batteryView) - dp(24), dp(20));
        positionView(networkView, DesktopConfig.COMPONENT_NETWORK, w - dp(184), h - dp(96));

        attachDrag(clockView, DesktopConfig.COMPONENT_CLOCK);
        attachDrag(dateView, DesktopConfig.COMPONENT_DATE);
        attachDrag(batteryView, DesktopConfig.COMPONENT_BATTERY);
        attachDrag(networkView, DesktopConfig.COMPONENT_NETWORK);
        attachDrag(weatherCardView, DesktopConfig.COMPONENT_WEATHER);
        attachDrag(cpuMonitorView, DesktopConfig.COMPONENT_CPU);

        editToolbar.bringToFront();
    }

    private void positionView(View view, String component, int defaultX, int defaultY) {
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        int maxX = Math.max(0, root.getWidth() - vw);
        int maxY = Math.max(0, root.getHeight() - vh);

        int targetX;
        int targetY;
        if (DesktopConfig.hasPosition(prefs, component)) {
            targetX = Math.round(clamp(DesktopConfig.getX(prefs, component), 0f, 1f) * maxX);
            targetY = Math.round(clamp(DesktopConfig.getY(prefs, component), 0f, 1f) * maxY);
        } else {
            targetX = clampInt(defaultX, 0, maxX);
            targetY = clampInt(defaultY, 0, maxY);
        }
        setBasePosition(view, targetX, targetY);
    }

    private void attachDrag(View view, String component) {
        if (view == null) return;

        GradientDrawable outline = new GradientDrawable();
        outline.setColor(Color.argb(35, 255, 204, 68));
        outline.setStroke(dp(1), Color.argb(200, 255, 204, 68));
        outline.setCornerRadius(dp(8));
        view.setBackground(outline);

        view.setOnTouchListener(new View.OnTouchListener() {
            float downRawX, downRawY, startX, startY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = baseX(v);
                        startY = baseY(v);
                        v.bringToFront();
                        if (editToolbar != null) editToolbar.bringToFront();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        int vw = getViewWidth(v);
                        int vh = getViewHeight(v);
                        float maxX = Math.max(0, root.getWidth() - vw);
                        float maxY = Math.max(0, root.getHeight() - vh);
                        float newX = clamp(startX + event.getRawX() - downRawX, 0, maxX);
                        float newY = clamp(startY + event.getRawY() - downRawY, 0, maxY);
                        setBasePosition(v, newX, newY);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        // 拖动结束即落盘全部组件坐标，避免出现“只有部分组件被自定义”的中间态
                        saveAllPositions();
                        return true;

                    default:
                        return true;
                }
            }
        });
    }

    private void saveAllPositions() {
        saveComponentPosition(clockView, DesktopConfig.COMPONENT_CLOCK);
        saveComponentPosition(dateView, DesktopConfig.COMPONENT_DATE);
        saveComponentPosition(batteryView, DesktopConfig.COMPONENT_BATTERY);
        saveComponentPosition(networkView, DesktopConfig.COMPONENT_NETWORK);
        saveComponentPosition(weatherCardView, DesktopConfig.COMPONENT_WEATHER);
        saveComponentPosition(cpuMonitorView, DesktopConfig.COMPONENT_CPU);
    }

    private void saveComponentPosition(View view, String component) {
        if (view == null || root == null || root.getWidth() <= 0 || root.getHeight() <= 0) return;
        int vw = getViewWidth(view);
        int vh = getViewHeight(view);
        float maxX = Math.max(1, root.getWidth() - vw);
        float maxY = Math.max(1, root.getHeight() - vh);
        float xRatio = clamp(baseX(view) / maxX, 0f, 1f);
        float yRatio = clamp(baseY(view) / maxY, 0f, 1f);
        DesktopConfig.savePosition(prefs, component, xRatio, yRatio);
    }

    private void setBasePosition(View view, float x, float y) {
        if (view == null) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        if (lp == null) lp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.leftMargin = Math.round(x);
        lp.topMargin = Math.round(y);
        view.setLayoutParams(lp);
    }

    private float baseX(View view) {
        if (view == null) return 0f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        return lp != null ? lp.leftMargin : 0f;
    }

    private float baseY(View view) {
        if (view == null) return 0f;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) view.getLayoutParams();
        return lp != null ? lp.topMargin : 0f;
    }

    private int getViewWidth(View view) {
        if (view == null) return 0;
        int w = view.getWidth();
        if (w > 0) return w;
        if (view.getLayoutParams() != null && view.getLayoutParams().width > 0) {
            return view.getLayoutParams().width;
        }
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return view.getMeasuredWidth();
    }

    private int getViewHeight(View view) {
        if (view == null) return 0;
        int h = view.getHeight();
        if (h > 0) return h;
        if (view.getLayoutParams() != null && view.getLayoutParams().height > 0) {
            return view.getLayoutParams().height;
        }
        view.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        return view.getMeasuredHeight();
    }

    private float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private int clampInt(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

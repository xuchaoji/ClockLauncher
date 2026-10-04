package com.example.clocklauncher;

import android.app.AlertDialog;
import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import java.util.Locale;

/**
 * 手机默认桌面 Launcher 主入口：
 * 1. 默认展示 StandbyClockView (待机桌面时钟，Page 1)
 * 2. 支持手指右滑（或左滑/上滑/底部点击）无缝呼出 AppDrawerView (应用列表，Page 0)
 * 3. 承接系统 HOME 键与返回键行为：在应用列表按返回或HOME自动回退至时钟，在时钟界面不退出
 * 4. 内置一键“设为系统默认桌面”完整引导与状态检测
 * 5. 支持长按呼出桌面时钟全套定制设置 (颜色、组件开关、天气城市、夜间模式)
 */
public class LauncherActivity extends AppCompatActivity {
    private static final int REQUEST_ROLE_HOME = 1001;

    private ViewPager2 viewPager;
    private StandbyClockView standbyClockView;
    private AppDrawerView appDrawerView;
    private SharedPreferences prefs;

    private final BroadcastReceiver packageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (appDrawerView != null) {
                appDrawerView.loadInstalledApps();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindow();

        prefs = getSharedPreferences(ClockPrefs.NAME, MODE_PRIVATE);
        ClockPrefs.ensureDefaults(prefs);

        standbyClockView = new StandbyClockView(this);
        appDrawerView = new AppDrawerView(this);

        setupListeners();
        setupViewPager();
    }

    private void configureWindow() {
        Window window = getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 21) {
            window.getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    private void setupListeners() {
        standbyClockView.setOnClockActionListener(new StandbyClockView.OnClockActionListener() {
            @Override
            public void onOpenAppDrawer() {
                // 平滑滑动至应用列表 (Page 0)
                if (viewPager != null) {
                    viewPager.setCurrentItem(0, true);
                }
            }

            @Override
            public void onOpenSettings() {
                showDesktopSettingsDialog();
            }
        });

        appDrawerView.setOnDrawerActionListener(new AppDrawerView.OnDrawerActionListener() {
            @Override
            public void onBackToClock() {
                // 平滑回退至待机桌面时钟 (Page 1)
                if (viewPager != null) {
                    viewPager.setCurrentItem(1, true);
                }
            }

            @Override
            public void onOpenSettings() {
                showDesktopSettingsDialog();
            }

            @Override
            public void onSetDefaultLauncher() {
                requestSetDefaultLauncher();
            }
        });
    }

    private void setupViewPager() {
        viewPager = new ViewPager2(this);
        viewPager.setOrientation(ViewPager2.ORIENTATION_HORIZONTAL);
        viewPager.setAdapter(new LauncherPagerAdapter());

        // 默认显示 Page 1: 待机桌面时钟
        viewPager.setCurrentItem(1, false);

        setContentView(viewPager);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (standbyClockView != null) {
            standbyClockView.onResume();
        }

        try {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_ADDED);
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
            filter.addDataScheme("package");
            registerReceiver(packageReceiver, filter);
        } catch (Exception ignored) { }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (standbyClockView != null) {
            standbyClockView.onPause();
        }
        try {
            unregisterReceiver(packageReceiver);
        } catch (Exception ignored) { }
    }

    /**
     * 系统 HOME 键行为承接：
     * 如果用户在应用列表或其他界面点击 HOME 键，平滑回退至待机桌面时钟
     */
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (viewPager != null && viewPager.getCurrentItem() != 1) {
            viewPager.setCurrentItem(1, true);
        }
        if (appDrawerView != null) {
            appDrawerView.clearSearch();
        }
    }

    /**
     * 系统返回键拦截：
     * 1. 若当前在应用列表 (Page 0)，按下返回键平滑回到桌面时钟 (Page 1)
     * 2. 若当前在待机时钟 (Page 1)，按返回键不退出桌面
     */
    @Override
    public void onBackPressed() {
        if (viewPager != null && viewPager.getCurrentItem() == 0) {
            viewPager.setCurrentItem(1, true);
        }
        // 已经在时钟主界面，不作任何退出操作，维持桌面常驻
    }

    /**
     * 设为系统默认桌面请求
     */
    public void requestSetDefaultLauncher() {
        boolean isDefault = isDefaultLauncher();
        if (isDefault) {
            Toast.makeText(this, "当前已是系统默认桌面！", Toast.LENGTH_SHORT).show();
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            RoleManager roleManager = getSystemService(RoleManager.class);
            if (roleManager != null && roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                if (!roleManager.isRoleHeld(RoleManager.ROLE_HOME)) {
                    Intent roleIntent = roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME);
                    startActivityForResult(roleIntent, REQUEST_ROLE_HOME);
                    return;
                }
            }
        }

        // 降级引导至系统默认应用/主屏幕设置
        try {
            Intent intent = new Intent(Settings.ACTION_HOME_SETTINGS);
            startActivity(intent);
        } catch (Exception e1) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS);
                startActivity(intent);
            } catch (Exception e2) {
                Toast.makeText(this, "请在系统“设置 -> 应用 -> 默认应用”中选择本桌面", Toast.LENGTH_LONG).show();
            }
        }
    }

    public boolean isDefaultLauncher() {
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_HOME);
        ResolveInfo resolveInfo = getPackageManager().resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY);
        return resolveInfo != null && resolveInfo.activityInfo != null
                && getPackageName().equals(resolveInfo.activityInfo.packageName);
    }

    /**
     * 桌面时钟全套定制对话框
     */
    private void showDesktopSettingsDialog() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        layout.setPadding(pad, pad, pad, pad);
        scroll.addView(layout);

        // 标题与默认桌面状态
        TextView tvTitle = new TextView(this);
        tvTitle.setText("⏰ 待机桌面设置");
        tvTitle.setTextSize(18);
        tvTitle.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tvTitle.setTextColor(Color.WHITE);
        layout.addView(tvTitle);

        boolean isDefault = isDefaultLauncher();
        TextView tvDefaultStatus = new TextView(this);
        tvDefaultStatus.setText(isDefault ? "状态：✅ 已是系统默认桌面" : "状态：⚠️ 当前尚未设为默认桌面 (点击设置)");
        tvDefaultStatus.setTextColor(isDefault ? Color.parseColor("#70D8A5") : Color.parseColor("#FFC857"));
        tvDefaultStatus.setTextSize(13);
        tvDefaultStatus.setPadding(0, dp(6), 0, dp(12));
        tvDefaultStatus.setOnClickListener(v -> requestSetDefaultLauncher());
        layout.addView(tvDefaultStatus);

        // 1. 组件显示开关
        addSectionHeader(layout, "组件显示开关");
        CheckBox cbDate = addCheckBox(layout, "显示日期与星期", prefs.getBoolean(DesktopConfig.KEY_SHOW_DATE, true));
        CheckBox cbBattery = addCheckBox(layout, "显示电池电量", ClockPrefs.showDesktopBattery(prefs));
        CheckBox cbNetwork = addCheckBox(layout, "显示实时网速", ClockPrefs.showDesktopNetwork(prefs));
        CheckBox cbCpu = addCheckBox(layout, "显示 CPU 曲线监控", ClockPrefs.showDesktopCpu(prefs));
        CheckBox cbWeather = addCheckBox(layout, "显示天气卡片", prefs.getBoolean(DesktopConfig.KEY_SHOW_WEATHER, true));

        // 2. 时钟样式定制
        addSectionHeader(layout, "时钟样式与字体");
        TextView tvSizeLabel = new TextView(this);
        int curSize = ClockPrefs.getDesktopTextSize(prefs);
        tvSizeLabel.setText(String.format(Locale.getDefault(), "时钟字号：%d sp", curSize));
        tvSizeLabel.setTextColor(Color.WHITE);
        layout.addView(tvSizeLabel);

        SeekBar sbSize = new SeekBar(this);
        sbSize.setMax(140);
        sbSize.setProgress(curSize - 40);
        sbSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                tvSizeLabel.setText(String.format(Locale.getDefault(), "时钟字号：%d sp", progress + 40));
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        layout.addView(sbSize);

        CheckBox cbBold = addCheckBox(layout, "粗体显示时钟", ClockPrefs.isDesktopBold(prefs));

        // 3. 颜色选择按钮
        LinearLayout colorRow = new LinearLayout(this);
        colorRow.setOrientation(LinearLayout.HORIZONTAL);
        colorRow.setPadding(0, dp(8), 0, dp(8));

        TextView btnColor = new TextView(this);
        btnColor.setText("🎨 选择文字颜色");
        btnColor.setTextColor(Color.WHITE);
        btnColor.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnColor.setBackgroundColor(Color.argb(90, 255, 255, 255));
        btnColor.setOnClickListener(v -> {
            ColorPickerDialog.show(this, "选择时钟颜色", ClockPrefs.getDesktopTextColor(prefs), false, colorHex -> {
                prefs.edit().putString(ClockPrefs.KEY_DESKTOP_TEXT_COLOR, colorHex).apply();
                if (standbyClockView != null) standbyClockView.applySettings();
                Toast.makeText(this, "已更新文字颜色: " + colorHex, Toast.LENGTH_SHORT).show();
            });
        });
        colorRow.addView(btnColor);

        TextView btnShadow = new TextView(this);
        btnShadow.setText("🌑 选择阴影颜色");
        btnShadow.setTextColor(Color.WHITE);
        btnShadow.setPadding(dp(12), dp(8), dp(12), dp(8));
        btnShadow.setBackgroundColor(Color.argb(90, 255, 255, 255));
        LinearLayout.LayoutParams shLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        shLp.leftMargin = dp(12);
        colorRow.addView(btnShadow, shLp);
        btnShadow.setOnClickListener(v -> {
            ColorPickerDialog.show(this, "选择阴影颜色", ClockPrefs.getDesktopShadowColor(prefs), true, colorHex -> {
                prefs.edit().putString(ClockPrefs.KEY_DESKTOP_SHADOW_COLOR, colorHex).apply();
                if (standbyClockView != null) standbyClockView.applySettings();
                Toast.makeText(this, "已更新阴影颜色", Toast.LENGTH_SHORT).show();
            });
        });
        layout.addView(colorRow);

        // 4. 天气城市配置
        addSectionHeader(layout, "天气城市设置");
        EditText etCity = new EditText(this);
        etCity.setHint("输入城市名称 (如 北京, 上海, 南京)");
        etCity.setTextColor(Color.WHITE);
        etCity.setHintTextColor(Color.argb(120, 255, 255, 255));
        etCity.setText(prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京"));
        layout.addView(etCity);

        // 5. 夜间模式
        addSectionHeader(layout, "夜间模式与护眼");
        CheckBox cbAutoNight = addCheckBox(layout, "定时开启夜间纯黑模式 (22:00 ~ 07:00)",
                prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, false));
        CheckBox cbExtraDim = addCheckBox(layout, "夜间极暗防刺眼微调",
                prefs.getBoolean(DesktopConfig.KEY_NIGHT_EXTRA_DIM, true));

        new AlertDialog.Builder(this)
                .setView(scroll)
                .setPositiveButton("保存并应用", (dialog, which) -> {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(DesktopConfig.KEY_SHOW_DATE, cbDate.isChecked());
                    editor.putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_BATTERY, cbBattery.isChecked());
                    editor.putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_NETWORK, cbNetwork.isChecked());
                    editor.putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_CPU, cbCpu.isChecked());
                    editor.putBoolean(DesktopConfig.KEY_SHOW_WEATHER, cbWeather.isChecked());
                    editor.putInt(ClockPrefs.KEY_DESKTOP_TEXT_SIZE, sbSize.getProgress() + 40);
                    editor.putBoolean(ClockPrefs.KEY_DESKTOP_BOLD, cbBold.isChecked());
                    editor.putBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, cbAutoNight.isChecked());
                    editor.putBoolean(DesktopConfig.KEY_NIGHT_EXTRA_DIM, cbExtraDim.isChecked());

                    String city = etCity.getText().toString().trim();
                    if (!city.isEmpty()) {
                        editor.putString(DesktopConfig.KEY_WEATHER_CITY, city);
                        WeatherManager.searchCity(city, new WeatherManager.CitySearchCallback() {
                            @Override
                            public void onSuccess(String cityName, float lat, float lon) {
                                prefs.edit().putFloat(DesktopConfig.KEY_WEATHER_LAT, lat)
                                        .putFloat(DesktopConfig.KEY_WEATHER_LON, lon).apply();
                                if (standbyClockView != null) standbyClockView.applySettings();
                            }
                            @Override
                            public void onError(String message) { }
                        });
                    }
                    editor.apply();

                    if (standbyClockView != null) {
                        standbyClockView.applySettings();
                    }
                    Toast.makeText(this, "桌面设置已保存", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("设为默认桌面", (dialog, which) -> requestSetDefaultLauncher())
                .setNegativeButton("取消", null)
                .show();
    }

    private void addSectionHeader(LinearLayout layout, String title) {
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(Color.parseColor("#64B5F6"));
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setPadding(0, dp(14), 0, dp(4));
        layout.addView(tv);
    }

    private CheckBox addCheckBox(LinearLayout layout, String text, boolean checked) {
        CheckBox cb = new CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Color.WHITE);
        cb.setChecked(checked);
        cb.setPadding(0, dp(4), 0, dp(4));
        layout.addView(cb);
        return cb;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ================== ViewPager2 Adapter ==================
    private class LauncherPagerAdapter extends RecyclerView.Adapter<LauncherPagerAdapter.PageViewHolder> {
        @NonNull
        @Override
        public PageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            FrameLayout container = new FrameLayout(parent.getContext());
            container.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            View content = (viewType == 0) ? appDrawerView : standbyClockView;
            if (content.getParent() != null) {
                ((ViewGroup) content.getParent()).removeView(content);
            }
            container.addView(content, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
            return new PageViewHolder(container);
        }

        @Override
        public void onBindViewHolder(@NonNull PageViewHolder holder, int position) { }

        @Override
        public int getItemCount() {
            return 2;
        }

        @Override
        public int getItemViewType(int position) {
            return position;
        }

        class PageViewHolder extends RecyclerView.ViewHolder {
            public PageViewHolder(@NonNull View itemView) {
                super(itemView);
            }
        }
    }
}

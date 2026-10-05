package com.example.clocklauncher;

import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * 待机桌面「个性化设置中心」。
 *
 * 设计原则：分类归口，拒绝平铺。
 * 主界面只展示 7 个分类入口卡片，每个入口再进入独立的功能面板：
 *
 *   1. ⏰ 时钟样式       — 文字颜色 / 阴影颜色 / 字号 / 粗体 / 时间格式
 *   2. 📊 状态组件       — 日期 / 电量 / 网速 的开关、颜色、字号、粗体
 *   3. 💻 CPU 监控面板   — 开关、面板宽高、背景透明度
 *   4. 🌤️ 天气组件       — 城市绑定、数据刷新、卡片模式、文字与卡片外观
 *   5. 🌙 夜间模式       — 手动/定时、时段、最低亮度、极暗遮罩深度
 *   6. 🎛️ 排版与预设     — 可视化拖拽排版、恢复默认、预设保存/应用/导入导出
 *   7. ⭐ 默认桌面       — 系统默认桌面状态检测与一键设置
 */
public final class DesktopSettingsDialog {

    /** 设置中心与宿主 Activity 的交互回调。 */
    public interface Host {
        /** 任意设置项变更后调用，用于实时刷新桌面组件。 */
        void onSettingsChanged();

        boolean isDefaultLauncher();

        void requestSetDefaultLauncher();

        void openLayoutEditor();

        /** 读取当前屏幕亮度百分比（1~100）。 */
        int getBrightnessPercent();

        /** 直接设定屏幕亮度百分比（1~100），会切回手动模式。 */
        void setBrightnessPercent(int percent);

        /** 让桌面重新按当前亮度模式接管亮度（注册/注销环境光监听）。 */
        void refreshBrightnessStatus();

        /** 亮度模式与当前环境光的文字摘要。 */
        String getBrightnessStatusText();

        /** 本机是否有环境光传感器。 */
        boolean hasLightSensor();
    }

    // ===== 配色 =====
    private static final int COLOR_DIALOG_BG = 0xFF11151E;
    private static final int COLOR_CARD_BG = 0xFF1A2130;
    private static final int COLOR_CARD_STROKE = 0x33FFFFFF;
    private static final int COLOR_ACCENT = 0xFF64B5F6;
    private static final int COLOR_TEXT = 0xFFF2F5FA;
    private static final int COLOR_TEXT_DIM = 0xB3C2CCDE;
    private static final int COLOR_GOOD = 0xFF70D8A5;
    private static final int COLOR_WARN = 0xFFFFC857;

    // ===== 时间格式预设 =====
    private static final String[] TIME_FORMAT_PRESETS = new String[]{
            "HH:mm:ss", "HH:mm", "hh:mm:ss", "hh:mm",
            "HH:mm:ss EEE", "MM-dd HH:mm", "yyyy-MM-dd HH:mm:ss"
    };
    private static final String[] TIME_FORMAT_LABELS = new String[]{
            "24小时 时:分:秒", "24小时 时:分", "12小时 时:分:秒", "12小时 时:分",
            "时:分:秒 + 星期", "月-日 时:分", "年-月-日 时:分:秒"
    };

    private final Context context;
    private final SharedPreferences prefs;
    private final Host host;

    private DesktopSettingsDialog(Context context, SharedPreferences prefs, Host host) {
        this.context = context;
        this.prefs = prefs;
        this.host = host;
    }

    public static void show(Context context, SharedPreferences prefs, Host host) {
        ClockPrefs.ensureDefaults(prefs);
        new DesktopSettingsDialog(context, prefs, host).showCategoryMenu();
    }

    // ==================================================================
    //  主界面：分类入口
    // ==================================================================
    private void showCategoryMenu() {
        LinearLayout column = column();

        column.addView(titleView("⏰ 待机桌面设置"));
        column.addView(tipView("按功能归类，点进对应分类再进行细调。所有修改即时生效。"));
        column.addView(space(6));

        boolean isDefault = host.isDefaultLauncher();
        TextView defaultBadge = statusView(isDefault
                ? "✅ 已是系统默认桌面"
                : "⚠️ 尚未设为系统默认桌面 · 点此设置");
        defaultBadge.setTextColor(isDefault ? COLOR_GOOD : COLOR_WARN);
        defaultBadge.setOnClickListener(v -> host.requestSetDefaultLauncher());
        column.addView(defaultBadge);

        column.addView(space(8));

        addEntry(column, "⏰", "时钟样式与时间格式",
                ClockPrefs.getDesktopFormat(prefs) + " · " + ClockPrefs.getDesktopTextSize(prefs) + "sp"
                        + (ClockPrefs.isDesktopBold(prefs) ? " · 粗体" : ""),
                this::showClockStyleDialog);

        addEntry(column, "📊", "状态组件（日期 / 电量 / 网速）",
                summarizeStates(
                        stateLabel("日期", prefs.getBoolean(DesktopConfig.KEY_SHOW_DATE, true)),
                        stateLabel("电量", ClockPrefs.showDesktopBattery(prefs)),
                        stateLabel("网速", ClockPrefs.showDesktopNetwork(prefs))),
                this::showStatusComponentsDialog);

        addEntry(column, "💻", "CPU 性能监控面板",
                stateLabel("面板", ClockPrefs.showDesktopCpu(prefs))
                        + " · " + prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140)
                        + "×" + prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 220)
                        + "dp · 透明度 " + prefs.getInt(DesktopConfig.KEY_CPU_ALPHA, 100) + "%",
                this::showCpuDialog);

        addEntry(column, "🌤️", "天气组件与城市",
                stateLabel("天气", prefs.getBoolean(DesktopConfig.KEY_SHOW_WEATHER, true))
                        + " · " + prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京")
                        + " · " + weatherModeLabel(prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, 0)),
                this::showWeatherDialog);

        addEntry(column, "🌙", "夜间模式与护眼",
                nightModeSummary(),
                this::showNightModeDialog);

        addEntry(column, "🖐️", "手势与屏幕亮度",
                "亮度 " + host.getBrightnessPercent() + "% · 左侧上下滑动调亮度",
                this::showGestureDialog);

        addEntry(column, "🎛️", "排版布局与预设",
                DesktopConfig.hasPosition(prefs, DesktopConfig.COMPONENT_CLOCK)
                        ? "已启用自定义排版 · 预设 " + DesktopConfig.presetNames(prefs).size() + " 个"
                        : "系统自适应排版 · 预设 " + DesktopConfig.presetNames(prefs).size() + " 个",
                this::showLayoutDialog);

        addEntry(column, "⭐", "默认桌面与系统",
                host.isDefaultLauncher() ? "本应用已是系统主屏幕" : "点击将本应用设为系统主屏幕",
                host::requestSetDefaultLauncher);

        column.addView(space(10));

        LinearLayout actions = row();
        actions.addView(button("恢复全部默认设置", false, v -> confirmRestoreDefaults()));
        column.addView(actions);

        showDarkDialog("待机桌面设置", column, null, null);
    }

    // ==================================================================
    //  1. 时钟样式
    // ==================================================================
    private void showClockStyleDialog() {
        LinearLayout column = column();
        column.addView(titleView("⏰ 时钟样式与时间格式"));

        column.addView(sectionView("文字颜色与阴影"));
        column.addView(colorRow(
                "文字颜色", ClockPrefs.getDesktopTextColor(prefs), false,
                hex -> {
                    prefs.edit().putString(ClockPrefs.KEY_DESKTOP_TEXT_COLOR, hex).apply();
                    refresh();
                }));
        column.addView(colorRow(
                "阴影颜色", ClockPrefs.getDesktopShadowColor(prefs), true,
                hex -> {
                    prefs.edit().putString(ClockPrefs.KEY_DESKTOP_SHADOW_COLOR, hex).apply();
                    refresh();
                }));

        column.addView(sectionView("字号与字重"));
        column.addView(seekRow("时钟字号", 36, 180,
                ClockPrefs.getDesktopTextSize(prefs), "sp", value -> {
                    prefs.edit().putInt(ClockPrefs.KEY_DESKTOP_TEXT_SIZE, value).apply();
                    refresh();
                }));
        column.addView(checkRow("时钟粗体显示", ClockPrefs.isDesktopBold(prefs), checked -> {
            prefs.edit().putBoolean(ClockPrefs.KEY_DESKTOP_BOLD, checked).apply();
            refresh();
        }));

        column.addView(sectionView("时间格式"));
        column.addView(tipView("选择预设格式，或在下方自定义（毫秒会被自动剔除）。"));
        Spinner spinner = new Spinner(context);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, TIME_FORMAT_LABELS);
        spinner.setAdapter(adapter);
        int presetIndex = indexOfFormat(ClockPrefs.getDesktopFormat(prefs));
        if (presetIndex >= 0) spinner.setSelection(presetIndex);
        spinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putString(ClockPrefs.KEY_DESKTOP_FORMAT, TIME_FORMAT_PRESETS[position]).apply();
                refresh();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        column.addView(spinner);

        EditText formatEdit = editRow("自定义格式（如 HH:mm:ss）", InputType.TYPE_CLASS_TEXT,
                ClockPrefs.getDesktopFormat(prefs));
        column.addView(formatEdit);
        LinearLayout formatActions = row();
        formatActions.addView(button("应用自定义格式", true, v -> {
            String pattern = formatEdit.getText().toString().trim();
            try {
                ClockPrefs.validateFormat(pattern);
                prefs.edit().putString(ClockPrefs.KEY_DESKTOP_FORMAT,
                        ClockPrefs.stripMilliseconds(pattern)).apply();
                refresh();
                toast("时间格式已应用：" + ClockPrefs.getDesktopFormat(prefs));
            } catch (IllegalArgumentException e) {
                toast("时间格式非法，请检查（例如 HH:mm:ss）");
            }
        }));
        column.addView(formatActions);

        showDarkDialog("时钟样式", column, null, null);
    }

    // ==================================================================
    //  2. 状态组件（日期 / 电量 / 网速）
    // ==================================================================
    private void showStatusComponentsDialog() {
        LinearLayout column = column();
        column.addView(titleView("📊 状态组件样式"));
        column.addView(tipView("日期、电量与网速可分别开关并单独调色、调字号。"));

        // ---- 日期 ----
        column.addView(sectionView("📅 日期与星期"));
        column.addView(checkRow("显示日期组件", prefs.getBoolean(DesktopConfig.KEY_SHOW_DATE, true), checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_SHOW_DATE, checked).apply();
            refresh();
        }));
        column.addView(colorRow("日期颜色", prefs.getString(DesktopConfig.KEY_DATE_COLOR, "#D2FFFFFF"), true,
                hex -> {
                    prefs.edit().putString(DesktopConfig.KEY_DATE_COLOR, hex).apply();
                    refresh();
                }));
        column.addView(seekRow("日期字号", 12, 48, prefs.getInt(DesktopConfig.KEY_DATE_SIZE, 22), "sp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_DATE_SIZE, value).apply();
                    refresh();
                }));
        column.addView(checkRow("日期粗体", prefs.getBoolean(DesktopConfig.KEY_DATE_BOLD, true), checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_DATE_BOLD, checked).apply();
            refresh();
        }));

        // ---- 电量 ----
        column.addView(sectionView("🔋 电池电量"));
        column.addView(checkRow("显示电量组件", ClockPrefs.showDesktopBattery(prefs), checked -> {
            prefs.edit().putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_BATTERY, checked).apply();
            refresh();
        }));
        column.addView(colorRow("电量颜色", prefs.getString(DesktopConfig.KEY_BATTERY_COLOR, "#B9FFFFFF"), true,
                hex -> {
                    prefs.edit().putString(DesktopConfig.KEY_BATTERY_COLOR, hex).apply();
                    refresh();
                }));
        column.addView(seekRow("电量字号", 12, 48, prefs.getInt(DesktopConfig.KEY_BATTERY_SIZE, 18), "sp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_BATTERY_SIZE, value).apply();
                    refresh();
                }));
        column.addView(checkRow("电量粗体", prefs.getBoolean(DesktopConfig.KEY_BATTERY_BOLD, true), checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_BATTERY_BOLD, checked).apply();
            refresh();
        }));

        // ---- 网速 ----
        column.addView(sectionView("📶 实时上下行网速"));
        column.addView(checkRow("显示网速组件", ClockPrefs.showDesktopNetwork(prefs), checked -> {
            prefs.edit().putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_NETWORK, checked).apply();
            refresh();
        }));
        column.addView(colorRow("网速颜色", prefs.getString(DesktopConfig.KEY_NETWORK_COLOR, "#B9FFFFFF"), true,
                hex -> {
                    prefs.edit().putString(DesktopConfig.KEY_NETWORK_COLOR, hex).apply();
                    refresh();
                }));
        column.addView(seekRow("网速字号", 10, 40, prefs.getInt(DesktopConfig.KEY_NETWORK_SIZE, 16), "sp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_NETWORK_SIZE, value).apply();
                    refresh();
                }));
        column.addView(checkRow("网速粗体（等宽字体）", prefs.getBoolean(DesktopConfig.KEY_NETWORK_BOLD, true),
                checked -> {
                    prefs.edit().putBoolean(DesktopConfig.KEY_NETWORK_BOLD, checked).apply();
                    refresh();
                }));

        showDarkDialog("状态组件样式", column, null, null);
    }

    // ==================================================================
    //  3. CPU 监控面板
    // ==================================================================
    private void showCpuDialog() {
        LinearLayout column = column();
        column.addView(titleView("💻 CPU 性能监控面板"));
        column.addView(tipView("实时展示每核心频率（或占用率）曲线与 CPU 温度；"
                + "部分 ROM 屏蔽 /proc/stat 时会自动切换为频率模式。"));

        column.addView(checkRow("显示 CPU 监控面板", ClockPrefs.showDesktopCpu(prefs), checked -> {
            prefs.edit().putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_CPU, checked).apply();
            refresh();
        }));

        column.addView(sectionView("面板尺寸与外观"));
        column.addView(seekRow("面板宽度", 100, 400, prefs.getInt(DesktopConfig.KEY_CPU_WIDTH, 140), "dp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_CPU_WIDTH, value).apply();
                    refresh();
                }));
        column.addView(seekRow("面板高度", 120, 460, prefs.getInt(DesktopConfig.KEY_CPU_HEIGHT, 220), "dp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_CPU_HEIGHT, value).apply();
                    refresh();
                }));
        column.addView(seekRow("背景透明度", 10, 100, prefs.getInt(DesktopConfig.KEY_CPU_ALPHA, 100), "%",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_CPU_ALPHA, value).apply();
                    refresh();
                }));

        showDarkDialog("CPU 监控面板", column, null, null);
    }

    // ==================================================================
    //  4. 天气组件
    // ==================================================================
    private void showWeatherDialog() {
        LinearLayout column = column();
        column.addView(titleView("🌤️ 天气组件与城市"));
        column.addView(tipView("数据来源 Open-Meteo，免密钥；20 分钟内自动走本地缓存。"));

        column.addView(checkRow("显示天气卡片", prefs.getBoolean(DesktopConfig.KEY_SHOW_WEATHER, true), checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_SHOW_WEATHER, checked).apply();
            refresh();
        }));

        column.addView(sectionView("城市与数据"));
        EditText cityEdit = editRow("城市名称（如 北京 / 上海 / 深圳）", InputType.TYPE_CLASS_TEXT,
                prefs.getString(DesktopConfig.KEY_WEATHER_CITY, "北京"));
        column.addView(cityEdit);

        float lat = prefs.getFloat(DesktopConfig.KEY_WEATHER_LAT, 39.9042f);
        float lon = prefs.getFloat(DesktopConfig.KEY_WEATHER_LON, 116.4074f);
        TextView coordView = statusView(String.format(Locale.getDefault(),
                "当前绑定坐标：%.4f, %.4f", lat, lon));
        column.addView(coordView);

        LinearLayout cityActions = row();
        cityActions.addView(button("查询并绑定城市", true, v -> {
            String city = cityEdit.getText().toString().trim();
            if (city.isEmpty()) {
                toast("请输入城市名称，例如：北京");
                return;
            }
            toast("正在查询 " + city + " 的经纬度...");
            WeatherManager.searchCity(city, new WeatherManager.CitySearchCallback() {
                @Override
                public void onSuccess(String cityName, float latitude, float longitude) {
                    prefs.edit()
                            .putString(DesktopConfig.KEY_WEATHER_CITY, cityName)
                            .putFloat(DesktopConfig.KEY_WEATHER_LAT, latitude)
                            .putFloat(DesktopConfig.KEY_WEATHER_LON, longitude)
                            .apply();
                    cityEdit.setText(cityName);
                    coordView.setText(String.format(Locale.getDefault(),
                            "当前绑定坐标：%.4f, %.4f", latitude, longitude));
                    toast("已绑定 " + cityName);
                    refresh();
                    WeatherManager.fetchWeather(context, latitude, longitude, true,
                            new WeatherManager.WeatherCallback() {
                                @Override public void onSuccess(String json) { refresh(); }
                                @Override public void onError(String message) { }
                            });
                }

                @Override
                public void onError(String message) {
                    toast(message);
                }
            });
        }));
        cityActions.addView(button("立即刷新缓存", false, v -> {
            float curLat = prefs.getFloat(DesktopConfig.KEY_WEATHER_LAT, 39.9042f);
            float curLon = prefs.getFloat(DesktopConfig.KEY_WEATHER_LON, 116.4074f);
            toast("正在获取最新天气...");
            WeatherManager.fetchWeather(context, curLat, curLon, true, new WeatherManager.WeatherCallback() {
                @Override
                public void onSuccess(String json) {
                    refresh();
                    toast("天气已更新");
                }

                @Override
                public void onError(String message) {
                    toast("更新失败：" + message);
                }
            });
        }));
        column.addView(cityActions);

        column.addView(sectionView("默认卡片模式"));
        Spinner modeSpinner = new Spinner(context);
        String[] modes = new String[]{"📅 三日对比卡片（昨 / 今 / 明）", "⏱️ 逐小时走势卡片（前 / 现 / 未来）"};
        modeSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, modes));
        modeSpinner.setSelection(Math.max(0, Math.min(1, prefs.getInt(DesktopConfig.KEY_WEATHER_MODE, 0))));
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(DesktopConfig.KEY_WEATHER_MODE, position).apply();
                refresh();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        column.addView(modeSpinner);
        column.addView(tipView("提示：在桌面上轻触天气卡片可随时临时切换两种模式。"));

        column.addView(sectionView("卡片外观"));
        column.addView(colorRow("卡片文字颜色", prefs.getString(DesktopConfig.KEY_WEATHER_COLOR, "#D2FFFFFF"), true,
                hex -> {
                    prefs.edit().putString(DesktopConfig.KEY_WEATHER_COLOR, hex).apply();
                    refresh();
                }));
        column.addView(checkRow("卡片文字粗体", prefs.getBoolean(DesktopConfig.KEY_WEATHER_BOLD, true), checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_WEATHER_BOLD, checked).apply();
            refresh();
        }));
        column.addView(seekRow("卡片宽度", 180, 520, prefs.getInt(DesktopConfig.KEY_WEATHER_WIDTH, 300), "dp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_WEATHER_WIDTH, value).apply();
                    refresh();
                }));
        column.addView(seekRow("卡片高度", 72, 240, prefs.getInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96), "dp",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_WEATHER_HEIGHT, value).apply();
                    refresh();
                }));
        column.addView(seekRow("卡片背景透明度", 10, 100, prefs.getInt(DesktopConfig.KEY_WEATHER_ALPHA, 100), "%",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_WEATHER_ALPHA, value).apply();
                    refresh();
                }));
        column.addView(seekRow("卡片内文字缩放", 70, 150, prefs.getInt(DesktopConfig.KEY_WEATHER_TEXT_SCALE, 100), "%",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_WEATHER_TEXT_SCALE, value).apply();
                    refresh();
                }));

        showDarkDialog("天气组件", column, null, null);
    }

    // ==================================================================
    //  5. 夜间模式与护眼
    // ==================================================================
    private void showNightModeDialog() {
        LinearLayout column = column();
        column.addView(titleView("🌙 夜间模式与护眼"));
        column.addView(tipView("夜间模式会立即把屏幕亮度压到最低，并可叠加纯黑遮罩，适合床头摆钟。"));

        boolean manual = prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, false);
        boolean auto = prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, false);
        boolean scheduledNow = DesktopConfig.isTimeInRange(
                prefs.getString(DesktopConfig.KEY_NIGHT_MODE_START, DesktopConfig.DEFAULT_NIGHT_START),
                prefs.getString(DesktopConfig.KEY_NIGHT_MODE_END, DesktopConfig.DEFAULT_NIGHT_END),
                java.util.Calendar.getInstance());

        TextView stateView = statusView("当前状态："
                + (manual ? "手动开启中" : (auto ? (scheduledNow ? "定时时段内（已生效）" : "定时已开启（当前不在时段）") : "未开启")));
        stateView.setTextColor((manual || (auto && scheduledNow)) ? COLOR_GOOD : COLOR_TEXT_DIM);
        column.addView(stateView);

        column.addView(sectionView("开关"));
        column.addView(checkRow("手动开启夜间模式", manual, checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, checked).apply();
            refresh();
            showNightModeDialog();
        }));
        column.addView(checkRow("定时开启夜间模式", auto, checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, checked).apply();
            refresh();
        }));

        column.addView(sectionView("定时时段"));
        LinearLayout timeRow = row();
        EditText startEdit = editRow("开始 HH:mm",
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_TEXT_VARIATION_NORMAL,
                prefs.getString(DesktopConfig.KEY_NIGHT_MODE_START, DesktopConfig.DEFAULT_NIGHT_START));
        EditText endEdit = editRow("结束 HH:mm",
                InputType.TYPE_CLASS_DATETIME | InputType.TYPE_TEXT_VARIATION_NORMAL,
                prefs.getString(DesktopConfig.KEY_NIGHT_MODE_END, DesktopConfig.DEFAULT_NIGHT_END));
        addWeighted(timeRow, startEdit, 1f);
        addWeighted(timeRow, endEdit, 1f);
        column.addView(timeRow);
        column.addView(button("应用时段", true, v -> {
            String start = startEdit.getText().toString().trim();
            String end = endEdit.getText().toString().trim();
            if (!isValidTime(start) || !isValidTime(end)) {
                toast("时间格式应为 HH:mm，例如 22:00");
                return;
            }
            prefs.edit()
                    .putString(DesktopConfig.KEY_NIGHT_MODE_START, start)
                    .putString(DesktopConfig.KEY_NIGHT_MODE_END, end)
                    .apply();
            refresh();
            toast("夜间时段已更新为 " + start + " ~ " + end);
        }));

        column.addView(sectionView("亮度与遮罩"));
        column.addView(seekRow("夜间最低亮度", 1, 60, prefs.getInt(DesktopConfig.KEY_NIGHT_MODE_BRIGHTNESS,
                DesktopConfig.DEFAULT_NIGHT_BRIGHTNESS), "%", value -> {
            prefs.edit().putInt(DesktopConfig.KEY_NIGHT_MODE_BRIGHTNESS, value).apply();
            refresh();
        }));
        column.addView(checkRow("叠加极暗纯黑遮罩", prefs.getBoolean(DesktopConfig.KEY_NIGHT_EXTRA_DIM,
                DesktopConfig.DEFAULT_NIGHT_EXTRA_DIM), checked -> {
            prefs.edit().putBoolean(DesktopConfig.KEY_NIGHT_EXTRA_DIM, checked).apply();
            refresh();
        }));
        column.addView(seekRow("遮罩深度", 10, 95, prefs.getInt(DesktopConfig.KEY_NIGHT_EXTRA_DIM_DEPTH,
                DesktopConfig.DEFAULT_NIGHT_EXTRA_DIM_DEPTH), "%", value -> {
            prefs.edit().putInt(DesktopConfig.KEY_NIGHT_EXTRA_DIM_DEPTH, value).apply();
            refresh();
        }));
        column.addView(tipView("快捷操作：在桌面空白处双击即可立即切换夜间模式。"));

        showDarkDialog("夜间模式", column, null, null);
    }

    // ==================================================================
    //  6. 手势与屏幕亮度
    // ==================================================================
    private void showGestureDialog() {
        LinearLayout column = column();
        column.addView(titleView("🖐️ 手势与屏幕亮度"));
        column.addView(tipView("亮度按窗口生效，只影响本桌面，不会改动系统全局亮度。"));

        // ---------- 亮度模式 ----------
        column.addView(sectionView("亮度模式"));
        final TextView modeStatus = statusView(host.getBrightnessStatusText());
        modeStatus.setTextColor(COLOR_ACCENT);
        column.addView(modeStatus);

        Spinner modeSpinner = new Spinner(context);
        final String[] modeNames = new String[]{
                "✋ 手动亮度（手势 / 滑块）",
                "🔆 自动亮度（跟随环境光）",
                "📱 跟随系统亮度"
        };
        modeSpinner.setAdapter(new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_dropdown_item, modeNames));
        modeSpinner.setSelection(Math.max(0, Math.min(2,
                prefs.getInt(DesktopConfig.KEY_BRIGHTNESS_MODE, DesktopConfig.BRIGHTNESS_MODE_MANUAL))));
        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                prefs.edit().putInt(DesktopConfig.KEY_BRIGHTNESS_MODE, position).apply();
                refresh();
                host.refreshBrightnessStatus();
                modeStatus.setText(host.getBrightnessStatusText());
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        column.addView(modeSpinner);
        column.addView(tipView("自动亮度按环境光对数映射到亮度，再做低通滤波避免闪烁；"
                + "夜间模式期间自动亮度让位，退出后自动恢复。"));
        if (!host.hasLightSensor()) {
            TextView noSensor = statusView("⚠️ 本机未检测到环境光传感器，自动亮度不可用，将保持手动亮度。");
            noSensor.setTextColor(COLOR_WARN);
            column.addView(noSensor);
        }

        // ---------- 自动亮度范围 ----------
        column.addView(sectionView("自动亮度范围"));
        column.addView(seekRow("最暗时亮度（0 lux）", 1, 60,
                prefs.getInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MIN, DesktopConfig.DEFAULT_AUTO_BRIGHTNESS_MIN), "%",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MIN, value).apply();
                    // 保证最暗档不超过最亮档
                    int max = prefs.getInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MAX,
                            DesktopConfig.DEFAULT_AUTO_BRIGHTNESS_MAX);
                    if (max <= value) {
                        prefs.edit().putInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MAX,
                                Math.min(100, value + 10)).apply();
                    }
                    refresh();
                }));
        column.addView(seekRow("最亮时亮度（约 3000 lux）", 20, 100,
                prefs.getInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MAX, DesktopConfig.DEFAULT_AUTO_BRIGHTNESS_MAX), "%",
                value -> {
                    prefs.edit().putInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MAX, value).apply();
                    int min = prefs.getInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MIN,
                            DesktopConfig.DEFAULT_AUTO_BRIGHTNESS_MIN);
                    if (min >= value) {
                        prefs.edit().putInt(DesktopConfig.KEY_AUTO_BRIGHTNESS_MIN,
                                Math.max(1, value - 10)).apply();
                    }
                    refresh();
                }));

        // ---------- 手动亮度 ----------
        column.addView(sectionView("手动亮度"));
        column.addView(tipView("在桌面左侧约 1/6 宽度的区域内上下滑动，即可快速无级调节；"
                + "主动调节会自动切回手动模式。"));
        final int[] brightnessHolder = new int[]{host.getBrightnessPercent()};
        TextView valueView = statusView("当前亮度：" + brightnessHolder[0] + "%");
        valueView.setTextColor(COLOR_ACCENT);
        column.addView(valueView);

        LinearLayout sliderBox = new LinearLayout(context);
        sliderBox.setOrientation(LinearLayout.VERTICAL);
        SeekBar seek = new SeekBar(context);
        seek.setMax(99);
        seek.setProgress(Math.max(0, Math.min(99, brightnessHolder[0] - 1)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) return;
                int percent = progress + 1;
                brightnessHolder[0] = percent;
                valueView.setText("当前亮度：" + percent + "%");
                host.setBrightnessPercent(percent);
                host.refreshBrightnessStatus();
                modeStatus.setText(host.getBrightnessStatusText());
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        sliderBox.addView(seek);
        column.addView(sliderBox);

        LinearLayout quickRow = row();
        quickRow.addView(button("🔆 100%", false, v -> {
            host.setBrightnessPercent(100);
            seek.setProgress(99);
        }));
        quickRow.addView(button("🔅 50%", false, v -> {
            host.setBrightnessPercent(50);
            seek.setProgress(49);
        }));
        quickRow.addView(button("🌙 10%", false, v -> {
            host.setBrightnessPercent(10);
            seek.setProgress(9);
        }));
        column.addView(quickRow);

        column.addView(sectionView("桌面手势一览"));
        column.addView(tipView("· 右滑 / 左滑 / 上滑：呼出应用列表"));
        column.addView(tipView("· 左侧上下滑动：调节屏幕亮度（自动切回手动）"));
        column.addView(tipView("· 双击桌面空白处：快速切换夜间模式"));
        column.addView(tipView("· 长按桌面空白处：打开本设置中心"));
        column.addView(tipView("· 轻触天气卡片：切换三日 / 逐小时视图"));

        AlertDialog dialog = showDarkDialog("手势与屏幕亮度", column, null, null);
        // 打开期间定时刷新环境光读数，方便边看边调
        if (dialog != null) {
            final android.os.Handler handler = new android.os.Handler(android.os.Looper.getMainLooper());
            final Runnable tick = new Runnable() {
                @Override
                public void run() {
                    host.refreshBrightnessStatus();
                    modeStatus.setText(host.getBrightnessStatusText());
                    handler.postDelayed(this, 800L);
                }
            };
            dialog.setOnDismissListener(d -> handler.removeCallbacks(tick));
            handler.postDelayed(tick, 800L);
        }
    }

    // ==================================================================
    //  7. 排版布局与预设
    // ==================================================================
    private void showLayoutDialog() {
        LinearLayout column = column();
        column.addView(titleView("🎛️ 排版布局与预设"));
        column.addView(tipView("默认为系统自适应排版；如需完全自由摆放，可进入可视化拖拽排版模式。"));

        boolean custom = DesktopConfig.hasPosition(prefs, DesktopConfig.COMPONENT_CLOCK);
        TextView stateView = statusView(custom ? "当前排版：自由拖拽（自定义坐标）" : "当前排版：系统自适应");
        stateView.setTextColor(custom ? COLOR_ACCENT : COLOR_TEXT_DIM);
        column.addView(stateView);

        column.addView(button("🖐️ 进入可视化拖拽排版", true, v -> {
            host.openLayoutEditor();
        }));
        column.addView(button("🔄 恢复系统默认排版", false, v -> {
            DesktopConfig.resetAllPositions(prefs);
            refresh();
            toast("已恢复系统自适应排版");
        }));
        column.addView(tipView("进入排版模式后，可直接拖动时钟、日期、电量、网速、天气、CPU 面板到任意位置，点击「完成保存」生效。"));

        column.addView(sectionView("配置预设"));
        List<String> names = DesktopConfig.presetNames(prefs);
        column.addView(statusView(names.isEmpty() ? "暂无已保存的预设" : "已保存：" + join(names)));

        column.addView(button("💾 将当前样式保存为新预设", false, v ->
                promptText("保存预设", "预设名称", "我的桌面样式", name -> {
                    try {
                        DesktopConfig.savePreset(prefs, name);
                        toast("预设「" + name + "」已保存");
                        showLayoutDialog();
                    } catch (Exception e) {
                        toast("保存失败：" + e.getMessage());
                    }
                })));

        if (!names.isEmpty()) {
            Spinner spinner = new Spinner(context);
            spinner.setAdapter(new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_dropdown_item, names));
            column.addView(spinner);

            column.addView(button("✨ 应用选中预设", true, v -> {
                int index = spinner.getSelectedItemPosition();
                if (index < 0 || index >= names.size()) return;
                try {
                    DesktopConfig.applyPreset(prefs, index);
                    refresh();
                    toast("已应用预设「" + names.get(index) + "」");
                } catch (Exception e) {
                    toast("应用失败：" + e.getMessage());
                }
            }));

            LinearLayout presetActions = row();
            presetActions.addView(button("重命名", false, v -> {
                int index = spinner.getSelectedItemPosition();
                if (index < 0 || index >= names.size()) return;
                promptText("重命名预设", "新名称", names.get(index), newName -> {
                    try {
                        DesktopConfig.renamePreset(prefs, index, newName);
                        toast("已重命名");
                        showLayoutDialog();
                    } catch (Exception e) {
                        toast("重命名失败：" + e.getMessage());
                    }
                });
            }));
            presetActions.addView(button("删除", false, v -> {
                int index = spinner.getSelectedItemPosition();
                if (index < 0 || index >= names.size()) return;
                new AlertDialog.Builder(context)
                        .setTitle("删除预设")
                        .setMessage("确定删除「" + names.get(index) + "」？")
                        .setPositiveButton("删除", (d, w) -> {
                            try {
                                DesktopConfig.deletePreset(prefs, index);
                                toast("已删除");
                                showLayoutDialog();
                            } catch (Exception e) {
                                toast("删除失败：" + e.getMessage());
                            }
                        })
                        .setNegativeButton("取消", null)
                        .show();
            }));
            column.addView(presetActions);
        }

        column.addView(sectionView("备份与迁移"));
        LinearLayout backupActions = row();
        backupActions.addView(button("📋 导出配置 JSON", false, v -> {
            try {
                String json = DesktopConfig.exportAll(prefs);
                copyToClipboard("桌面配置", json);
                toast("配置 JSON 已复制到剪贴板");
            } catch (Exception e) {
                toast("导出失败：" + e.getMessage());
            }
        }));
        backupActions.addView(button("📥 导入配置 JSON", false, v ->
                promptText("导入配置", "粘贴配置 JSON", "", json -> {
                    if (json == null || json.trim().isEmpty()) {
                        toast("内容为空");
                        return;
                    }
                    try {
                        DesktopConfig.importAll(prefs, json.trim());
                        refresh();
                        toast("配置已导入");
                    } catch (Exception e) {
                        toast("导入失败：JSON 格式有误");
                    }
                })));
        column.addView(backupActions);

        showDarkDialog("排版布局与预设", column, null, null);
    }

    // ==================================================================
    //  通用 UI 构件
    // ==================================================================
    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(18), dp(16), dp(18), dp(16));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_DIALOG_BG);
        bg.setCornerRadius(dp(18));
        layout.setBackground(bg);
        return layout;
    }

    private TextView titleView(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(COLOR_TEXT);
        tv.setTextSize(18);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setPadding(0, 0, 0, dp(6));
        return tv;
    }

    private TextView sectionView(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(COLOR_ACCENT);
        tv.setTextSize(14);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setPadding(0, dp(16), 0, dp(6));
        return tv;
    }

    private TextView tipView(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(COLOR_TEXT_DIM);
        tv.setTextSize(12);
        tv.setLineSpacing(dp(2), 1f);
        return tv;
    }

    private TextView statusView(String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextColor(COLOR_TEXT_DIM);
        tv.setTextSize(13);
        tv.setPadding(0, dp(4), 0, dp(4));
        return tv;
    }

    private View space(int dpValue) {
        View v = new View(context);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(dpValue)));
        return v;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(0, dp(4), 0, dp(4));
        return layout;
    }

    private LinearLayout.LayoutParams wrap(float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.rightMargin = dp(4);
        lp.leftMargin = dp(4);
        return lp;
    }

    /** 按权重把子视图横向排布到一行中，便于并排展示两个输入框。 */
    private void addWeighted(LinearLayout parent, View child, float weight) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, weight);
        lp.leftMargin = dp(4);
        lp.rightMargin = dp(4);
        parent.addView(child, lp);
    }

    /** 分类入口卡片。 */
    private void addEntry(LinearLayout parent, String icon, String title, String summary, Runnable onClick) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(14), dp(12), dp(12), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD_BG);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), COLOR_CARD_STROKE);
        card.setBackground(bg);

        TextView iconView = new TextView(context);
        iconView.setText(icon);
        iconView.setTextSize(22);
        iconView.setGravity(Gravity.CENTER);
        card.addView(iconView, new LinearLayout.LayoutParams(dp(38), ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout textCol = new LinearLayout(context);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setPadding(dp(6), 0, dp(6), 0);

        TextView titleView = new TextView(context);
        titleView.setText(title);
        titleView.setTextColor(COLOR_TEXT);
        titleView.setTextSize(15);
        titleView.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        textCol.addView(titleView);

        TextView summaryView = new TextView(context);
        summaryView.setText(summary);
        summaryView.setTextColor(COLOR_TEXT_DIM);
        summaryView.setTextSize(12);
        summaryView.setPadding(0, dp(2), 0, 0);
        textCol.addView(summaryView);

        card.addView(textCol, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView arrow = new TextView(context);
        arrow.setText("›");
        arrow.setTextColor(COLOR_TEXT_DIM);
        arrow.setTextSize(20);
        card.addView(arrow);

        card.setOnClickListener(v -> onClick.run());

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        parent.addView(card, lp);
    }

    private CheckBox checkRow(String text, boolean checked, Consumer<Boolean> onChange) {
        CheckBox cb = new CheckBox(context);
        cb.setText(text);
        cb.setTextColor(COLOR_TEXT);
        cb.setTextSize(14);
        cb.setChecked(checked);
        cb.setPadding(0, dp(6), 0, dp(6));
        cb.setOnCheckedChangeListener((buttonView, isChecked) -> onChange.accept(isChecked));
        return cb;
    }

    /** 带数值回显的滑动条。 */
    private View seekRow(String label, int min, int max, int value, String unit, IntConsumer onChange) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(6), 0, dp(2));

        TextView valueLabel = new TextView(context);
        int clamped = Math.max(min, Math.min(max, value));
        valueLabel.setText(String.format(Locale.getDefault(), "%s：%d%s", label, clamped, unit));
        valueLabel.setTextColor(COLOR_TEXT);
        valueLabel.setTextSize(13);
        box.addView(valueLabel);

        SeekBar seek = new SeekBar(context);
        seek.setMax(max - min);
        seek.setProgress(clamped - min);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int current = min + progress;
                valueLabel.setText(String.format(Locale.getDefault(), "%s：%d%s", label, current, unit));
                if (fromUser) onChange.accept(current);
            }

            @Override public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });
        box.addView(seek);
        return box;
    }

    private EditText editRow(String hint, int inputType, String value) {
        EditText edit = new EditText(context);
        edit.setHint(hint);
        edit.setText(value);
        edit.setInputType(inputType);
        edit.setTextColor(COLOR_TEXT);
        edit.setHintTextColor(0x80C2CCDE);
        edit.setTextSize(14);
        edit.setSingleLine(true);
        edit.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFF1E2635);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), COLOR_CARD_STROKE);
        edit.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        edit.setLayoutParams(lp);
        return edit;
    }

    /** 颜色选择按钮行，右侧显示当前色值。 */
    private View colorRow(String label, String currentColor, boolean allowAlpha, Consumer<String> onPicked) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        layout.setPadding(dp(12), dp(10), dp(12), dp(10));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(COLOR_CARD_BG);
        bg.setCornerRadius(dp(10));
        bg.setStroke(dp(1), COLOR_CARD_STROKE);
        layout.setBackground(bg);

        TextView nameView = new TextView(context);
        nameView.setText("🎨 " + label);
        nameView.setTextColor(COLOR_TEXT);
        nameView.setTextSize(14);
        layout.addView(nameView, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final String[] holder = new String[]{currentColor};
        TextView valueView = new TextView(context);
        valueView.setText(currentColor);
        valueView.setTextColor(COLOR_TEXT_DIM);
        valueView.setTextSize(12);
        valueView.setPadding(dp(8), 0, 0, 0);
        layout.addView(valueView);

        View swatch = new View(context);
        try {
            GradientDrawable dot = new GradientDrawable();
            dot.setShape(GradientDrawable.OVAL);
            dot.setColor(Color.parseColor(currentColor));
            dot.setStroke(dp(1), 0x66FFFFFF);
            swatch.setBackground(dot);
        } catch (Exception ignored) {
            swatch.setBackgroundColor(Color.GRAY);
        }
        LinearLayout.LayoutParams swatchLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        swatchLp.leftMargin = dp(8);
        layout.addView(swatch, swatchLp);

        layout.setOnClickListener(v -> ColorPickerDialog.show(context, label, holder[0], allowAlpha, hex -> {
            holder[0] = hex;
            valueView.setText(hex);
            try {
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL);
                dot.setColor(Color.parseColor(hex));
                dot.setStroke(dp(1), 0x66FFFFFF);
                swatch.setBackground(dot);
            } catch (Exception ignored) { }
            onPicked.accept(hex);
        }));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        layout.setLayoutParams(lp);
        return layout;
    }

    private View button(String text, boolean primary, View.OnClickListener listener) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(13);
        tv.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(dp(10), dp(11), dp(10), dp(11));

        GradientDrawable bg = new GradientDrawable();
        if (primary) {
            bg.setColor(0x3364B5F6);
            bg.setStroke(dp(1), 0x9964B5F6);
            tv.setTextColor(0xFFBBDEFB);
        } else {
            bg.setColor(0x1FFFFFFF);
            bg.setStroke(dp(1), COLOR_CARD_STROKE);
            tv.setTextColor(COLOR_TEXT);
        }
        bg.setCornerRadius(dp(10));
        tv.setBackground(bg);
        tv.setOnClickListener(listener);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        tv.setLayoutParams(lp);
        return tv;
    }

    // ==================================================================
    //  对话框基础设施
    // ==================================================================
    private AlertDialog showDarkDialog(String title, View content, DialogInterface.OnClickListener positive,
                                       String positiveText) {
        // 手势屏蔽：长按桌面弹出的瞬间，手指抬起的那一下不应误触到分类卡片
        TouchGuardLayout guard = new TouchGuardLayout(context);
        guard.addView(content, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(context);
        scroll.addView(guard, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        if (positiveText != null) {
            builder.setPositiveButton(positiveText, positive);
        }
        builder.setNegativeButton("关闭", null);
        builder.setView(scroll);
        AlertDialog dialog = builder.create();

        dialog.setOnShowListener(d -> {
            guard.arm();
            Window window = dialog.getWindow();
            if (window == null) return;
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    Math.min(dp(520), (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94f)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
            if (Build.VERSION.SDK_INT >= 21) {
                window.setStatusBarColor(Color.TRANSPARENT);
            }
            if (dialog.getButton(AlertDialog.BUTTON_NEGATIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(COLOR_ACCENT);
            }
            if (positiveText != null && dialog.getButton(AlertDialog.BUTTON_POSITIVE) != null) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(COLOR_ACCENT);
            }
        });

        dialog.show();
        guard.arm();
        // show() 之后再设置一次，确保 window 已创建
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    Math.min(dp(520), (int) (context.getResources().getDisplayMetrics().widthPixels * 0.94f)),
                    WindowManager.LayoutParams.WRAP_CONTENT);
        }
        return dialog;
    }

    private void promptText(String title, String hint, String initial, Consumer<String> onConfirm) {
        EditText edit = editRow(hint, InputType.TYPE_CLASS_TEXT, initial);
        LinearLayout box = column();
        box.addView(titleView(title));
        box.addView(edit);
        showDarkDialog(title, box, (d, which) ->
                onConfirm.accept(edit.getText().toString().trim()), "确定");
    }

    private void confirmRestoreDefaults() {
        new AlertDialog.Builder(context)
                .setTitle("恢复全部默认设置")
                .setMessage("将重置时钟样式、组件开关、天气外观、夜间模式与自定义排版，此操作不可撤销。")
                .setPositiveButton("恢复默认", (d, which) -> {
                    resetAllToDefaults();
                    refresh();
                    toast("已恢复全部默认设置");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void resetAllToDefaults() {
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(ClockPrefs.KEY_DESKTOP_TEXT_COLOR, ClockPrefs.DEFAULT_DESKTOP_TEXT_COLOR);
        editor.putString(ClockPrefs.KEY_DESKTOP_SHADOW_COLOR, ClockPrefs.DEFAULT_DESKTOP_SHADOW_COLOR);
        editor.putInt(ClockPrefs.KEY_DESKTOP_TEXT_SIZE, ClockPrefs.DEFAULT_DESKTOP_TEXT_SIZE);
        editor.putString(ClockPrefs.KEY_DESKTOP_FORMAT, ClockPrefs.DEFAULT_DESKTOP_FORMAT);
        editor.putBoolean(ClockPrefs.KEY_DESKTOP_BOLD, false);
        editor.putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_BATTERY, true);
        editor.putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_NETWORK, true);
        editor.putBoolean(ClockPrefs.KEY_DESKTOP_SHOW_CPU, true);

        editor.putBoolean(DesktopConfig.KEY_SHOW_DATE, true);
        editor.putString(DesktopConfig.KEY_DATE_COLOR, "#D2FFFFFF");
        editor.putInt(DesktopConfig.KEY_DATE_SIZE, 22);
        editor.putBoolean(DesktopConfig.KEY_DATE_BOLD, true);
        editor.putString(DesktopConfig.KEY_BATTERY_COLOR, "#B9FFFFFF");
        editor.putInt(DesktopConfig.KEY_BATTERY_SIZE, 18);
        editor.putBoolean(DesktopConfig.KEY_BATTERY_BOLD, true);
        editor.putString(DesktopConfig.KEY_NETWORK_COLOR, "#B9FFFFFF");
        editor.putInt(DesktopConfig.KEY_NETWORK_SIZE, 16);
        editor.putBoolean(DesktopConfig.KEY_NETWORK_BOLD, true);

        editor.putInt(DesktopConfig.KEY_CPU_WIDTH, 140);
        editor.putInt(DesktopConfig.KEY_CPU_HEIGHT, 220);
        editor.putInt(DesktopConfig.KEY_CPU_ALPHA, 100);

        editor.putBoolean(DesktopConfig.KEY_SHOW_WEATHER, true);
        editor.putString(DesktopConfig.KEY_WEATHER_COLOR, "#D2FFFFFF");
        editor.putBoolean(DesktopConfig.KEY_WEATHER_BOLD, true);
        editor.putInt(DesktopConfig.KEY_WEATHER_WIDTH, 300);
        editor.putInt(DesktopConfig.KEY_WEATHER_HEIGHT, 96);
        editor.putInt(DesktopConfig.KEY_WEATHER_ALPHA, 100);
        editor.putInt(DesktopConfig.KEY_WEATHER_TEXT_SCALE, 100);
        editor.putInt(DesktopConfig.KEY_WEATHER_MODE, 0);

        editor.putBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, false);
        editor.putBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, false);
        editor.putString(DesktopConfig.KEY_NIGHT_MODE_START, DesktopConfig.DEFAULT_NIGHT_START);
        editor.putString(DesktopConfig.KEY_NIGHT_MODE_END, DesktopConfig.DEFAULT_NIGHT_END);
        editor.putInt(DesktopConfig.KEY_NIGHT_MODE_BRIGHTNESS, DesktopConfig.DEFAULT_NIGHT_BRIGHTNESS);
        editor.putBoolean(DesktopConfig.KEY_NIGHT_EXTRA_DIM, DesktopConfig.DEFAULT_NIGHT_EXTRA_DIM);
        editor.putInt(DesktopConfig.KEY_NIGHT_EXTRA_DIM_DEPTH, DesktopConfig.DEFAULT_NIGHT_EXTRA_DIM_DEPTH);

        editor.apply();
        DesktopConfig.resetAllPositions(prefs);
        ClockPrefs.ensureDefaults(prefs);
    }

    // ==================================================================
    //  工具方法
    // ==================================================================
    private void refresh() {
        host.onSettingsChanged();
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    private void copyToClipboard(String label, String text) {
        ClipboardManager cm = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText(label, text));
        }
    }

    private static int indexOfFormat(String pattern) {
        if (pattern == null) return -1;
        for (int i = 0; i < TIME_FORMAT_PRESETS.length; i++) {
            if (TIME_FORMAT_PRESETS[i].equals(pattern)) return i;
        }
        return -1;
    }

    private static boolean isValidTime(String text) {
        if (text == null) return false;
        String[] parts = text.trim().split(":");
        if (parts.length != 2) return false;
        try {
            int h = Integer.parseInt(parts[0].trim());
            int m = Integer.parseInt(parts[1].trim());
            return h >= 0 && h <= 23 && m >= 0 && m <= 59;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static String stateLabel(String name, boolean enabled) {
        return name + (enabled ? "开" : "关");
    }

    private static String summarizeStates(String... parts) {
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (sb.length() > 0) sb.append(" · ");
            sb.append(part);
        }
        return sb.toString();
    }

    private static String weatherModeLabel(int mode) {
        return mode == WeatherCardView.MODE_HOURLY ? "逐小时卡片" : "三日对比卡片";
    }

    private String nightModeSummary() {
        boolean manual = prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_MANUAL, false);
        boolean auto = prefs.getBoolean(DesktopConfig.KEY_NIGHT_MODE_AUTO, false);
        if (manual) return "手动开启中 · 亮度最低";
        if (auto) {
            return "定时 " + prefs.getString(DesktopConfig.KEY_NIGHT_MODE_START, DesktopConfig.DEFAULT_NIGHT_START)
                    + " ~ " + prefs.getString(DesktopConfig.KEY_NIGHT_MODE_END, DesktopConfig.DEFAULT_NIGHT_END);
        }
        return "未开启 · 双击桌面可快速切换";
    }

    private static String join(List<String> names) {
        StringBuilder sb = new StringBuilder();
        for (String name : names) {
            if (sb.length() > 0) sb.append("、");
            sb.append(name);
        }
        return sb.toString();
    }

    private int dp(int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}

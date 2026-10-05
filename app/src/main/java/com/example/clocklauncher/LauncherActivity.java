package com.example.clocklauncher;

import android.app.role.RoleManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;


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
    /** 待机桌面时钟页（默认页，全屏）。 */
    private static final int PAGE_CLOCK = 1;
    /** 应用列表页（显示状态栏）。 */
    private static final int PAGE_DRAWER = 0;
    /** 应用列表页状态栏配色，与列表头部渐变起始色保持一致。 */
    private static final int DRAWER_STATUS_BAR_COLOR = 0xFF1B2232;

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
            window.setNavigationBarColor(Color.BLACK);
        }
        // 允许窗口铺满刘海/挖孔区域，否则竖屏顶部(或横屏侧边)会留下一条 108px 的非内容带，
        // 桌面就不是真正的"全屏"了。
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }
        applySystemUiForPage(PAGE_CLOCK);
    }

    /**
     * 按当前页切换系统栏显示策略：
     * · 待机时钟页（默认桌面）：真·全屏，隐藏状态栏与导航栏，时钟完全铺满整块屏幕
     * · 应用列表页：显示状态栏（内容仍绘制到状态栏下方，由列表头部留出让位空间）
     */
    private void applySystemUiForPage(int page) {
        if (Build.VERSION.SDK_INT < 21) return;
        Window window = getWindow();
        View decor = window.getDecorView();
        if (page == PAGE_CLOCK) {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.setStatusBarColor(Color.TRANSPARENT);
            window.setNavigationBarColor(Color.TRANSPARENT);
        } else {
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            window.setStatusBarColor(DRAWER_STATUS_BAR_COLOR);
            window.setNavigationBarColor(Color.BLACK);
        }
    }

    /** 状态栏高度（像素）：优先取系统资源，取不到时退回 24dp。 */
    private int statusBarHeightPx() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resId > 0) {
            return getResources().getDimensionPixelSize(resId);
        }
        return Math.round(24 * getResources().getDisplayMetrics().density);
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

        // 页面切换时同步系统栏策略：时钟页全屏、应用列表页显示状态栏
        viewPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override
            public void onPageSelected(int position) {
                applySystemUiForPage(position);
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                // 拖动结束时再校正一次，避免滑动过程中状态栏闪烁残留
                if (state == ViewPager2.SCROLL_STATE_IDLE && viewPager != null) {
                    applySystemUiForPage(viewPager.getCurrentItem());
                }
            }
        });

        // 默认显示 Page 1: 待机桌面时钟
        viewPager.setCurrentItem(PAGE_CLOCK, false);

        setContentView(viewPager);

        // 应用列表头部按真实状态栏高度留出让位空间（横竖屏/刘海屏都适配）
        if (appDrawerView != null) {
            appDrawerView.setStatusBarInset(statusBarHeightPx());
        }
        applySystemUiForPage(PAGE_CLOCK);
    }

    /**
     * 沉浸式全屏会被系统在弹出状态栏后清除（IMMERSIVE_STICKY 需要重新声明），
     * 重新拿到焦点时按当前页再校正一次。
     */
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && viewPager != null) {
            applySystemUiForPage(viewPager.getCurrentItem());
        }
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
     * 呼出「个性化设置中心」。
     * 主界面只列分类入口，具体设置项在各自分类面板内，避免一次性平铺所有选项。
     */
    private void showDesktopSettingsDialog() {
        DesktopSettingsDialog.show(this, prefs, new DesktopSettingsDialog.Host() {
            @Override
            public void onSettingsChanged() {
                if (standbyClockView != null) {
                    standbyClockView.applySettings();
                }
            }

            @Override
            public boolean isDefaultLauncher() {
                return LauncherActivity.this.isDefaultLauncher();
            }

            @Override
            public void requestSetDefaultLauncher() {
                LauncherActivity.this.requestSetDefaultLauncher();
            }

            @Override
            public void openLayoutEditor() {
                startActivity(new Intent(LauncherActivity.this, DesktopLayoutEditActivity.class));
            }

            @Override
            public int getBrightnessPercent() {
                return standbyClockView != null ? standbyClockView.getBrightnessPercent() : 70;
            }

            @Override
            public void setBrightnessPercent(int percent) {
                if (standbyClockView != null) {
                    standbyClockView.setBrightnessPercent(percent);
                }
            }

            @Override
            public void refreshBrightnessStatus() {
                if (standbyClockView != null) {
                    standbyClockView.reapplyBrightnessMode();
                }
            }

            @Override
            public String getBrightnessStatusText() {
                return standbyClockView != null ? standbyClockView.brightnessStatusText() : "亮度信息不可用";
            }

            @Override
            public boolean hasLightSensor() {
                return standbyClockView != null && standbyClockView.hasLightSensor();
            }
        });
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

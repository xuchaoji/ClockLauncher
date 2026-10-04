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

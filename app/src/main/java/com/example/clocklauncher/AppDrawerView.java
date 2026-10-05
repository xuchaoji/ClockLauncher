package com.example.clocklauncher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 现代全量应用抽屉：
 * 1. 自动扫描全量可启动应用，中文拼音/拼音首字母/英文智能排序
 * 2. 顶部实时搜索过滤栏 + 快捷返回时钟按钮
 * 3. 响应式自适应网格展示 (竖屏4列，横屏6-7列)
 * 4. 支持点击快速启动、长按呼出应用管理（系统详情、卸载、复制包名）
 * 5. 顶部快捷栏支持一键直达“系统设置”、“设为默认桌面”与“待机时钟设置”
 */
public class AppDrawerView extends LinearLayout {
    public interface OnDrawerActionListener {
        void onBackToClock();
        void onOpenSettings();
        void onSetDefaultLauncher();
    }

    public static class AppItem {
        public String label;
        public String packageName;
        public String activityName;
        public Drawable icon;
        public Intent launchIntent;
        public boolean isSystemApp;
        public String searchKey;
    }

    private EditText searchEdit;
    private TextView countView;
    private TextView btnClearSearch;
    private RecyclerView recyclerView;
    private ProgressBar loadingBar;
    private TextView emptyView;

    private final List<AppItem> allApps = new ArrayList<>();
    private final List<AppItem> filteredApps = new ArrayList<>();
    private AppAdapter adapter;
    private GridLayoutManager gridLayoutManager;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private OnDrawerActionListener actionListener;

    private float touchDownX;
    private float touchDownY;

    /** 状态栏高度（像素）；应用列表页会显示状态栏，头部需按此留出让位空间。 */
    private int statusBarInset = 0;
    private LinearLayout headerContainer;

    /**
     * 设置状态栏让位高度。应用列表页显示状态栏时，头部内容需要下移，
     * 否则搜索框会被状态栏压住。
     */
    public void setStatusBarInset(int insetPx) {
        this.statusBarInset = Math.max(0, insetPx);
        if (headerContainer != null) {
            headerContainer.setPadding(dp(16), statusBarInset + dp(12), dp(16), dp(10));
        }
    }

    public AppDrawerView(@NonNull Context context) {
        this(context, null);
    }

    public AppDrawerView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public AppDrawerView(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public void setOnDrawerActionListener(OnDrawerActionListener listener) {
        this.actionListener = listener;
    }

    private void init() {
        setOrientation(VERTICAL);
        setBackgroundColor(Color.parseColor("#0C101A")); // 现代极简深色桌面背景

        buildHeader();
        buildContent();
        loadInstalledApps();
    }

    private void buildHeader() {
        // 顶部搜索与操作容器
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(VERTICAL);
        header.setPadding(dp(16), statusBarInset > 0 ? statusBarInset + dp(12) : dp(44), dp(16), dp(10));
        GradientDrawable headerBg = new GradientDrawable();
        headerBg.setColors(new int[]{Color.parseColor("#1B2232"), Color.parseColor("#0C101A")});
        header.setBackground(headerBg);
        headerContainer = header;

        // 第 1 行：返回时钟按钮 + 搜索框 + 菜单
        LinearLayout searchRow = new LinearLayout(getContext());
        searchRow.setOrientation(HORIZONTAL);
        searchRow.setGravity(Gravity.CENTER_VERTICAL);

        // 返回时钟胶囊按钮
        TextView btnBack = new TextView(getContext());
        btnBack.setText("◀ 时钟");
        btnBack.setTextColor(Color.WHITE);
        btnBack.setTextSize(14);
        btnBack.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        btnBack.setGravity(Gravity.CENTER);
        btnBack.setPadding(dp(12), dp(8), dp(12), dp(8));
        GradientDrawable backBg = new GradientDrawable();
        backBg.setColor(Color.argb(90, 255, 255, 255));
        backBg.setCornerRadius(dp(16));
        btnBack.setBackground(backBg);
        btnBack.setOnClickListener(v -> {
            hideKeyboard();
            if (actionListener != null) actionListener.onBackToClock();
        });
        searchRow.addView(btnBack, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        // 搜索输入框包裹容器
        FrameLayout searchContainer = new FrameLayout(getContext());
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.argb(130, 32, 40, 58));
        searchBg.setCornerRadius(dp(20));
        searchBg.setStroke(dp(1), Color.argb(60, 255, 255, 255));
        searchContainer.setBackground(searchBg);
        searchContainer.setPadding(dp(14), dp(2), dp(14), dp(2));

        searchEdit = new EditText(getContext());
        searchEdit.setHint("🔍 搜索应用...");
        searchEdit.setHintTextColor(Color.argb(140, 200, 210, 230));
        searchEdit.setTextColor(Color.WHITE);
        searchEdit.setTextSize(14);
        searchEdit.setBackground(null);
        searchEdit.setSingleLine(true);
        searchEdit.setPadding(dp(4), dp(8), dp(32), dp(8));
        searchContainer.addView(searchEdit, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL));

        // 搜索清除按钮 (X)
        btnClearSearch = new TextView(getContext());
        btnClearSearch.setText("✕");
        btnClearSearch.setTextColor(Color.argb(180, 255, 255, 255));
        btnClearSearch.setTextSize(14);
        btnClearSearch.setGravity(Gravity.CENTER);
        btnClearSearch.setVisibility(GONE);
        btnClearSearch.setPadding(dp(8), dp(8), dp(8), dp(8));
        btnClearSearch.setOnClickListener(v -> searchEdit.setText(""));
        searchContainer.addView(btnClearSearch, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL));

        LayoutParams searchLp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f);
        searchLp.setMargins(dp(10), 0, dp(10), 0);
        searchRow.addView(searchContainer, searchLp);

        // 快捷选项按钮 (⋮ 菜单)
        TextView btnMenu = new TextView(getContext());
        btnMenu.setText("⚙️");
        btnMenu.setTextSize(18);
        btnMenu.setGravity(Gravity.CENTER);
        btnMenu.setPadding(dp(10), dp(6), dp(10), dp(6));
        GradientDrawable menuBg = new GradientDrawable();
        menuBg.setColor(Color.argb(90, 255, 255, 255));
        menuBg.setCornerRadius(dp(16));
        btnMenu.setBackground(menuBg);
        btnMenu.setOnClickListener(v -> showQuickMenu());
        searchRow.addView(btnMenu, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        header.addView(searchRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        // 第 2 行：应用数量与快捷标签
        LinearLayout subRow = new LinearLayout(getContext());
        subRow.setOrientation(HORIZONTAL);
        subRow.setGravity(Gravity.CENTER_VERTICAL);
        subRow.setPadding(dp(4), dp(10), dp(4), 0);

        countView = new TextView(getContext());
        countView.setText("正在扫描应用...");
        countView.setTextColor(Color.argb(170, 190, 205, 230));
        countView.setTextSize(12);
        subRow.addView(countView, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1.0f));

        // 快捷“设为默认桌面”微胶囊
        TextView chipDefault = new TextView(getContext());
        chipDefault.setText("⭐ 设为默认桌面");
        chipDefault.setTextColor(Color.parseColor("#FFCC44"));
        chipDefault.setTextSize(11);
        chipDefault.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        chipDefault.setPadding(dp(10), dp(4), dp(10), dp(4));
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setColor(Color.argb(50, 255, 204, 68));
        chipBg.setStroke(dp(1), Color.argb(120, 255, 204, 68));
        chipBg.setCornerRadius(dp(12));
        chipDefault.setBackground(chipBg);
        chipDefault.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onSetDefaultLauncher();
        });
        subRow.addView(chipDefault, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        header.addView(subRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        searchEdit.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                String query = s.toString().trim();
                btnClearSearch.setVisibility(TextUtils.isEmpty(query) ? GONE : VISIBLE);
                filterApps(query);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
    }

    private void buildContent() {
        FrameLayout contentContainer = new FrameLayout(getContext());

        recyclerView = new RecyclerView(getContext());
        recyclerView.setClipToPadding(false);
        recyclerView.setPadding(dp(12), dp(12), dp(12), dp(36));
        int initialSpan = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE ? 6 : 4;
        gridLayoutManager = new GridLayoutManager(getContext(), initialSpan);
        recyclerView.setLayoutManager(gridLayoutManager);

        adapter = new AppAdapter();
        recyclerView.setAdapter(adapter);
        contentContainer.addView(recyclerView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        loadingBar = new ProgressBar(getContext());
        FrameLayout.LayoutParams pbLp = new FrameLayout.LayoutParams(
                dp(48), dp(48), Gravity.CENTER);
        contentContainer.addView(loadingBar, pbLp);

        emptyView = new TextView(getContext());
        emptyView.setText("未找到匹配的应用");
        emptyView.setTextColor(Color.argb(160, 255, 255, 255));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setVisibility(GONE);
        contentContainer.addView(emptyView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));

        addView(contentContainer, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1.0f));
    }

    public void loadInstalledApps() {
        loadingBar.setVisibility(VISIBLE);
        executor.execute(() -> {
            PackageManager pm = getContext().getPackageManager();
            Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
            mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);

            List<ResolveInfo> resolveInfos = pm.queryIntentActivities(mainIntent, 0);
            List<AppItem> list = new ArrayList<>();
            String myPkg = getContext().getPackageName();

            for (ResolveInfo ri : resolveInfos) {
                if (ri.activityInfo == null) continue;
                String pkg = ri.activityInfo.packageName;
                if (pkg.equals(myPkg)) continue; // 过滤自身桌面入口，避免递归套娃

                AppItem item = new AppItem();
                item.packageName = pkg;
                item.activityName = ri.activityInfo.name;
                CharSequence labelCs = ri.loadLabel(pm);
                item.label = (labelCs != null && labelCs.length() > 0) ? labelCs.toString() : pkg;
                try {
                    item.icon = ri.loadIcon(pm);
                } catch (Exception e) {
                    item.icon = pm.getDefaultActivityIcon();
                }
                item.launchIntent = pm.getLaunchIntentForPackage(pkg);
                if (item.launchIntent != null) {
                    item.launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                }
                item.isSystemApp = (ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                item.searchKey = item.label.toLowerCase(Locale.getDefault()) + " " + pkg.toLowerCase(Locale.getDefault());
                list.add(item);
            }

            // 中文汉字拼音排序
            Collator collator = Collator.getInstance(Locale.CHINA);
            Collections.sort(list, (a, b) -> collator.compare(a.label, b.label));

            mainHandler.post(() -> {
                allApps.clear();
                allApps.addAll(list);
                filterApps(searchEdit != null ? searchEdit.getText().toString().trim() : "");
                loadingBar.setVisibility(GONE);
            });
        });
    }

    private void filterApps(String query) {
        filteredApps.clear();
        if (TextUtils.isEmpty(query)) {
            filteredApps.addAll(allApps);
            countView.setText(String.format(Locale.getDefault(), "共 %d 款应用", allApps.size()));
        } else {
            String lower = query.toLowerCase(Locale.getDefault());
            for (AppItem app : allApps) {
                if (app.searchKey.contains(lower)) {
                    filteredApps.add(app);
                }
            }
            countView.setText(String.format(Locale.getDefault(), "已筛选出 %d 款应用 (总共 %d 款)", filteredApps.size(), allApps.size()));
        }
        emptyView.setVisibility(filteredApps.isEmpty() ? VISIBLE : GONE);
        adapter.notifyDataSetChanged();
    }

    public void clearSearch() {
        if (searchEdit != null) {
            searchEdit.setText("");
        }
        hideKeyboard();
    }

    private void showQuickMenu() {
        String[] options = new String[]{
                "⭐ 设为系统默认桌面",
                "⚙️ 待机时钟外观与组件设置",
                "📱 打开系统设置",
                "🔄 刷新应用列表"
        };
        new AlertDialog.Builder(getContext())
                .setTitle("桌面快捷选项")
                .setItems(options, (dialog, which) -> {
                    switch (which) {
                        case 0:
                            if (actionListener != null) actionListener.onSetDefaultLauncher();
                            break;
                        case 1:
                            if (actionListener != null) actionListener.onOpenSettings();
                            break;
                        case 2:
                            openSystemSettings();
                            break;
                        case 3:
                            loadInstalledApps();
                            Toast.makeText(getContext(), "已重新扫描应用", Toast.LENGTH_SHORT).show();
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAppDetailDialog(AppItem item) {
        String[] actions = new String[]{
                "🚀 打开应用",
                "ℹ️ 系统应用信息",
                "🗑️ 卸载此应用",
                "📋 复制应用包名"
        };

        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle(item.label)
                .setIcon(item.icon)
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0:
                            launchApp(item);
                            break;
                        case 1:
                            openAppDetails(item.packageName);
                            break;
                        case 2:
                            uninstallApp(item.packageName);
                            break;
                        case 3:
                            copyToClipboard(item.packageName);
                            break;
                    }
                })
                .setNegativeButton("取消", null)
                .create();
        dialog.show();
    }

    private void launchApp(AppItem item) {
        if (item.launchIntent != null) {
            try {
                getContext().startActivity(item.launchIntent);
            } catch (Exception e) {
                Toast.makeText(getContext(), "启动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getContext(), "该应用无启动入口", Toast.LENGTH_SHORT).show();
        }
    }

    private void openAppDetails(String packageName) {
        try {
            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(Uri.parse("package:" + packageName));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开应用设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void uninstallApp(String packageName) {
        try {
            Intent intent = new Intent(Intent.ACTION_DELETE);
            intent.setData(Uri.parse("package:" + packageName));
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法卸载系统核心应用", Toast.LENGTH_SHORT).show();
        }
    }

    private void copyToClipboard(String text) {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("package", text));
            Toast.makeText(getContext(), "已复制包名: " + text, Toast.LENGTH_SHORT).show();
        }
    }

    private void openSystemSettings() {
        try {
            Intent intent = new Intent(Settings.ACTION_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getContext().startActivity(intent);
        } catch (Exception e) {
            Toast.makeText(getContext(), "无法打开系统设置", Toast.LENGTH_SHORT).show();
        }
    }

    private void hideKeyboard() {
        if (searchEdit != null && getContext() instanceof Activity) {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(searchEdit.getWindowToken(), 0);
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        if (w > 0 && gridLayoutManager != null) {
            int span = Math.max(4, w / dp(82));
            gridLayoutManager.setSpanCount(span);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    // ================== RecyclerView Adapter ==================
    private class AppAdapter extends RecyclerView.Adapter<AppViewHolder> {
        @NonNull
        @Override
        public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            LinearLayout itemLayout = new LinearLayout(getContext());
            itemLayout.setOrientation(VERTICAL);
            itemLayout.setGravity(Gravity.CENTER_HORIZONTAL);
            itemLayout.setPadding(dp(6), dp(10), dp(6), dp(10));

            // 点击水波纹反馈
            GradientDrawable ripple = new GradientDrawable();
            ripple.setColor(Color.TRANSPARENT);
            ripple.setCornerRadius(dp(14));
            itemLayout.setBackground(ripple);

            ImageView iconView = new ImageView(getContext());
            iconView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            LayoutParams iconLp = new LayoutParams(dp(54), dp(54));
            iconLp.bottomMargin = dp(6);
            itemLayout.addView(iconView, iconLp);

            TextView labelView = new TextView(getContext());
            labelView.setGravity(Gravity.CENTER);
            labelView.setTextColor(Color.argb(235, 240, 245, 255));
            labelView.setTextSize(12);
            labelView.setSingleLine(true);
            labelView.setEllipsize(TextUtils.TruncateAt.END);
            labelView.setPadding(dp(2), 0, dp(2), 0);
            itemLayout.addView(labelView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

            return new AppViewHolder(itemLayout, iconView, labelView);
        }

        @Override
        public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
            AppItem app = filteredApps.get(position);
            holder.labelView.setText(app.label);
            holder.iconView.setImageDrawable(app.icon);

            holder.itemView.setOnClickListener(v -> {
                hideKeyboard();
                launchApp(app);
            });

            holder.itemView.setOnLongClickListener(v -> {
                hideKeyboard();
                showAppDetailDialog(app);
                return true;
            });
        }

        @Override
        public int getItemCount() {
            return filteredApps.size();
        }
    }

    private static class AppViewHolder extends RecyclerView.ViewHolder {
        ImageView iconView;
        TextView labelView;

        public AppViewHolder(@NonNull View itemView, ImageView iconView, TextView labelView) {
            super(itemView);
            this.iconView = iconView;
            this.labelView = labelView;
        }
    }
}

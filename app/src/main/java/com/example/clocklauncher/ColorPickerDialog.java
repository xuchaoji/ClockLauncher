package com.example.clocklauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public final class ColorPickerDialog {
    public interface OnColorPickedListener {
        void onColorPicked(String colorText);
    }

    private ColorPickerDialog() { }

    public static void show(Context context, String title, String initialColorText, boolean allowAlpha,
                            OnColorPickedListener listener) {
        int initialColor;
        try {
            initialColor = Color.parseColor(initialColorText.trim());
        } catch (Exception e) {
            initialColor = allowAlpha ? Color.argb(255, 0, 0, 0) : Color.WHITE;
        }
        final String originalColorText = colorToString(initialColor, allowAlpha);
        // 用户是否真正动过取色控件：没动过就按原值返回，避免「点开又确定」把颜色改掉
        final boolean[] userChanged = new boolean[]{false};

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(context, 18);
        root.setPadding(padding, padding, padding, 0);

        TextView preview = new TextView(context);
        preview.setGravity(android.view.Gravity.CENTER);
        preview.setTextSize(16);
        preview.setTextColor(Color.WHITE);
        preview.setText("当前颜色");
        preview.setPadding(0, dp(context, 12), 0, dp(context, 12));
        root.addView(preview, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ColorPaletteView paletteView = new ColorPaletteView(context, initialColor);
        // 色域高度自适应：横屏（桌面底座模式）窗口很矮，固定 220dp 会把
        // 快捷色块和滑块全挤到屏幕外，这里按屏幕高度与朝向共同决定。
        int screenH = context.getResources().getDisplayMetrics().heightPixels;
        boolean landscape = screenH < context.getResources().getDisplayMetrics().widthPixels;
        int paletteH = landscape
                ? Math.max(dp(context, 90), Math.round(screenH * 0.24f))
                : Math.min(dp(context, 220), Math.round(screenH * 0.30f));
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, paletteH);
        paletteParams.topMargin = dp(context, 8);
        root.addView(paletteView, paletteParams);

        // 常用颜色快捷色块：黑色阴影要换成彩色阴影时，一次点击即可
        LinearLayout swatchRow = new LinearLayout(context);
        swatchRow.setOrientation(LinearLayout.HORIZONTAL);
        swatchRow.setPadding(0, dp(context, 10), 0, 0);
        final int[] presetColors = new int[]{
                Color.WHITE, 0xFF000000, 0xFFFF3B30, 0xFFFF9500, 0xFFFFCC00,
                0xFF34C759, 0xFF00C7BE, 0xFF32ADE6, 0xFF5856D6, 0xFFFF2D55
        };
        for (int preset : presetColors) {
            View dot = new View(context);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(preset);
            dotBg.setStroke(dp(context, 1), 0x66FFFFFF);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dp(context, 26), dp(context, 26));
            dotLp.rightMargin = dp(context, 8);
            swatchRow.addView(dot, dotLp);
        }
        root.addView(swatchRow, matchWrap());

        final TextView brightnessLabel = label(context, "亮度：" + Math.round(paletteView.getValue() * 100) + "%");
        root.addView(brightnessLabel);
        SeekBar brightnessSeek = new SeekBar(context);
        brightnessSeek.setMax(100);
        brightnessSeek.setProgress(Math.round(paletteView.getValue() * 100));
        root.addView(brightnessSeek, matchWrap());

        TextView alphaLabel = label(context, "透明度：100%");
        SeekBar alphaSeek = new SeekBar(context);
        if (allowAlpha) {
            root.addView(alphaLabel);
            alphaSeek.setMax(255);
            alphaSeek.setProgress(Color.alpha(initialColor));
            root.addView(alphaSeek, matchWrap());
        }

        TextView valueText = label(context, "");
        root.addView(valueText);

        final ColorState state = new ColorState(initialColor, allowAlpha);
        final UiRefresher refresher = new UiRefresher(preview, valueText, alphaLabel, allowAlpha, state);

        // 色块点击后同步 SeekBar 与预览
        for (int i = 0; i < swatchRow.getChildCount(); i++) {
            final View dot = swatchRow.getChildAt(i);
            final int preset = presetColors[i];
            dot.setOnClickListener(v -> {
                userChanged[0] = true;
                paletteView.applyColor(preset);
                brightnessSeek.setProgress(Math.round(paletteView.getValue() * 100));
                brightnessLabel.setText("亮度：" + Math.round(paletteView.getValue() * 100) + "%");
                state.setRgb(paletteView.getColor());
                refresher.forceRefresh();
            });
        }

        // 在调色板上取色时，如果原本亮度为 0（纯黑），自动抬到满亮度，
        // 否则 HSVToColor(h, s, 0) 永远是黑色，用户会以为「只能选黑色」。
        paletteView.setOnValueBumpedListener(value -> {
            brightnessSeek.setProgress(Math.round(value * 100));
            brightnessLabel.setText("亮度：" + Math.round(value * 100) + "%");
        });

        paletteView.setOnColorChangedListener(color -> {
            userChanged[0] = true;
            state.setRgb(color);
            refresher.requestLightRefresh();
        });
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                paletteView.setValue(progress / 100f, false);
                brightnessLabel.setText("亮度：" + progress + "%");
                if (fromUser) userChanged[0] = true;
                state.setRgb(paletteView.getColor());
                refresher.requestLightRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                paletteView.rebuildBitmapAsync();
                refresher.forceRefresh();
            }
        });
        alphaSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) userChanged[0] = true;
                state.setAlpha(progress);
                refresher.requestLightRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { refresher.forceRefresh(); }
        });
        refresher.forceRefresh();

        // 放进 ScrollView，保证横竖屏下所有控件都可滚动到达；
        // 外层再套手势屏蔽容器，防止打开弹窗的那次触摸把颜色"隔空"点掉。
        TouchGuardLayout guard = new TouchGuardLayout(context);
        guard.addView(root, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(guard, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(scrollView)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (d, which) -> listener.onColorPicked(
                        userChanged[0] ? colorToString(state.getColor(), allowAlpha) : originalColorText))
                .create();
        dialog.setOnShowListener(d -> guard.arm());
        dialog.show();
        guard.arm();
    }

    private static class ColorState {
        private final boolean allowAlpha;
        private int color;

        ColorState(int color, boolean allowAlpha) {
            this.allowAlpha = allowAlpha;
            this.color = allowAlpha ? color : Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        }

        int getColor() {
            return color;
        }

        void setRgb(int rgb) {
            int alpha = allowAlpha ? Color.alpha(color) : 255;
            color = Color.argb(alpha, Color.red(rgb), Color.green(rgb), Color.blue(rgb));
        }

        void setAlpha(int alpha) {
            if (allowAlpha) {
                color = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
            }
        }
    }

    private static class UiRefresher implements Runnable {
        private final TextView preview;
        private final TextView valueText;
        private final TextView alphaLabel;
        private final boolean allowAlpha;
        private final ColorState state;
        private boolean scheduled;

        UiRefresher(TextView preview, TextView valueText, TextView alphaLabel, boolean allowAlpha, ColorState state) {
            this.preview = preview;
            this.valueText = valueText;
            this.alphaLabel = alphaLabel;
            this.allowAlpha = allowAlpha;
            this.state = state;
        }

        void requestLightRefresh() {
            if (!scheduled) {
                scheduled = true;
                if (Build.VERSION.SDK_INT >= 16) {
                    preview.postOnAnimation(this);
                } else {
                    preview.postDelayed(this, 16L);
                }
            }
        }

        void forceRefresh() {
            preview.removeCallbacks(this);
            scheduled = false;
            run();
        }

        @Override public void run() {
            scheduled = false;
            int color = state.getColor();
            preview.setBackgroundColor(color);
            preview.setTextColor(isDark(color) ? Color.WHITE : Color.BLACK);
            String text = colorToString(color, allowAlpha);
            preview.setText(text);
            valueText.setText("颜色值：" + text);
            if (allowAlpha) alphaLabel.setText("透明度：" + Math.round(Color.alpha(color) * 100f / 255f) + "%");
        }
    }

    private static TextView label(Context context, String text) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setPadding(0, dp(context, 10), 0, dp(context, 4));
        return tv;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static String colorToString(int color, boolean includeAlpha) {
        if (includeAlpha) {
            return String.format(Locale.US, "#%02X%02X%02X%02X", Color.alpha(color), Color.red(color), Color.green(color), Color.blue(color));
        }
        return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
    }

    private static boolean isDark(int color) {
        return Color.red(color) * 0.299 + Color.green(color) * 0.587 + Color.blue(color) * 0.114 < 150;
    }

    private static int dp(Context context, int value) {
        return (int) (value * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class ColorPaletteView extends View {
        interface OnColorChangedListener { void onColorChanged(int color); }
        /** 在调色板上取色导致亮度被自动抬起时回调，用于同步亮度 SeekBar。 */
        interface OnValueBumpedListener { void onValueBumped(float value); }

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        private float hue;
        private float saturation;
        private float value;
        private OnColorChangedListener listener;
        private OnValueBumpedListener valueBumpedListener;

        ColorPaletteView(Context context, int initialColor) {
            super(context);
            float[] hsv = new float[3];
            Color.colorToHSV(initialColor, hsv);
            hue = hsv[0];
            saturation = hsv[1];
            value = hsv[2];
        }

        void setOnColorChangedListener(OnColorChangedListener listener) {
            this.listener = listener;
        }

        void setOnValueBumpedListener(OnValueBumpedListener listener) {
            this.valueBumpedListener = listener;
        }

        /** 由外部（快捷色块）直接设定颜色，会同步 V 并通知。 */
        void applyColor(int color) {
            float[] hsv = new float[3];
            Color.colorToHSV(color, hsv);
            hue = hsv[0];
            saturation = hsv[1];
            value = hsv[2];
            invalidate();
            notifyColor();
        }

        float getValue() {
            return value;
        }

        void setValue(float value, boolean rebuildNow) {
            this.value = Math.max(0f, Math.min(1f, value));
            if (rebuildNow) {
                rebuildBitmap();
            }
            invalidate();
            notifyColor();
        }

        void rebuildBitmapAsync() {
            post(this::rebuildBitmapAndInvalidate);
        }

        private void rebuildBitmapAndInvalidate() {
            rebuildBitmap();
            invalidate();
        }

        int getColor() {
            return Color.HSVToColor(new float[]{hue, saturation, value});
        }

        @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            rebuildBitmap();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (bitmap != null) {
                // 亮度只用来「压暗」色域的观感，保留 35% 下限，
                // 否则亮度为 0（纯黑初始色）时整个调色板会完全看不见。
                float previewAlpha = 0.35f + 0.65f * value;
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(),
                        Math.round(previewAlpha * 255), Canvas.ALL_SAVE_FLAG);
                canvas.drawBitmap(bitmap, 0, 0, null);
                canvas.restore();
            }
            float x = hue / 360f * Math.max(1, getWidth() - 1);
            float y = saturation * Math.max(1, getHeight() - 1);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(getContext(), 2));
            paint.setColor(Color.WHITE);
            canvas.drawCircle(x, y, dp(getContext(), 9), paint);
            paint.setColor(Color.BLACK);
            canvas.drawCircle(x, y, dp(getContext(), 11), paint);
            paint.setStyle(Paint.Style.FILL);
        }

        @Override public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE || event.getAction() == MotionEvent.ACTION_UP) {
                hue = clamp(event.getX() / Math.max(1, getWidth() - 1), 0f, 1f) * 360f;
                saturation = clamp(event.getY() / Math.max(1, getHeight() - 1), 0f, 1f);
                // 关键修复：亮度为 0 时取任何色相都会得到黑色。
                // 用户在色域上点选即代表「我要这个颜色」，此处把亮度抬到满值。
                if (value <= 0.05f) {
                    value = 1f;
                    if (valueBumpedListener != null) valueBumpedListener.onValueBumped(value);
                }
                invalidate();
                notifyColor();
                return true;
            }
            return super.onTouchEvent(event);
        }

        private void notifyColor() {
            if (listener != null) listener.onColorChanged(getColor());
        }

        private void rebuildBitmap() {
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) return;
            int[] pixels = new int[w * h];
            float[] hsv = new float[3];
            hsv[2] = 1f;
            for (int y = 0; y < h; y++) {
                hsv[1] = y / (float) Math.max(1, h - 1);
                for (int x = 0; x < w; x++) {
                    hsv[0] = x * 360f / Math.max(1, w - 1);
                    pixels[y * w + x] = Color.HSVToColor(hsv);
                }
            }
            bitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888);
        }

        private float clamp(float value, float min, float max) {
            return Math.max(min, Math.min(max, value));
        }
    }
}

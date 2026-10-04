package com.example.clocklauncher;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Build;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
        LinearLayout.LayoutParams paletteParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 220));
        paletteParams.topMargin = dp(context, 12);
        root.addView(paletteView, paletteParams);

        TextView brightnessLabel = label(context, "亮度");
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

        paletteView.setOnColorChangedListener(color -> {
            state.setRgb(color);
            refresher.requestLightRefresh();
        });
        brightnessSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                paletteView.setValue(progress / 100f, false);
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
                state.setAlpha(progress);
                refresher.requestLightRefresh();
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { refresher.forceRefresh(); }
        });
        refresher.forceRefresh();

        new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("取消", null)
                .setPositiveButton("确定", (dialog, which) -> listener.onColorPicked(colorToString(state.getColor(), allowAlpha)))
                .show();
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

        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap bitmap;
        private float hue;
        private float saturation;
        private float value;
        private OnColorChangedListener listener;

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
                canvas.saveLayerAlpha(0, 0, getWidth(), getHeight(), Math.round(value * 255), Canvas.ALL_SAVE_FLAG);
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

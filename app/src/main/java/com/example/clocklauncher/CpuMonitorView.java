package com.example.clocklauncher;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.Log;
import android.view.View;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * CPU 每核心监控曲线。
 *
 * 关键前提：很多 ROM（如 EMUI）对普通应用屏蔽了 {@code /proc/stat}，
 * 此时**无法取得真实 CPU 占用率**，只能读到 cpufreq 频率。
 * 因此这里按「能测到什么就显示什么」分两种模式，并在面板上明确标注：
 *
 *  1. 利用率模式：{@code /proc/stat} 可读 → 显示真实占用率（数字 + 曲线）
 *  2. 频率模式：  /proc/stat 不可读 → 显示真实频率（{@code 1.6/1.9G} + 频率曲线），
 *                绝不把频率包装成"占用率"，避免误读
 *
 * 频率优先用 {@code cpufreq/stats/time_in_state} 差分求窗口平均频率（平滑、无采样混叠），
 * 读不到时退回 {@code scaling_cur_freq} 的瞬时值。
 */
public class CpuMonitorView extends View {
    private static final int HISTORY = 60;
    private static final String TAG = "FloatingClockCpu";

    private static final int SOURCE_UNKNOWN = -1;
    private static final int SOURCE_STAT = 0;
    private static final int SOURCE_TIME_IN_STATE = 1;
    private static final int SOURCE_CUR_FREQ = 2;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final List<float[]> history = new ArrayList<>();
    private int panelAlphaPercent = 100;
    private int dataSource = SOURCE_UNKNOWN;
    private String statusText = "初始化";

    /** /proc/stat 上一次的 {total, idle} 累计值 */
    private long[][] lastStats;

    /** time_in_state 上一次快照 */
    private List<long[]> tisTimes;

    /** 频率模式下每核心当前频率（kHz），用于文字显示 */
    private double[] curFreqKhz;

    private int coreCount;
    private float cpuTemperatureC = Float.NaN;
    private String cpuTemperatureSource;
    private int[] coreOrder;
    private long[] coreMaxFreqs;
    private boolean[] coreIsBig;
    private boolean topologyLogged;

    public CpuMonitorView(Context context) {
        super(context);
        paint.setStrokeCap(Paint.Cap.ROUND);
    }

    public void setPanelAlpha(int percent) {
        panelAlphaPercent = Math.max(10, Math.min(100, percent));
        invalidate();
    }

    public void sample() {
        if (dataSource == SOURCE_UNKNOWN) detectDataSource();
        sampleCpuTemperature();

        float[] values;
        switch (dataSource) {
            case SOURCE_STAT:
                values = sampleFromStat();
                break;
            case SOURCE_TIME_IN_STATE:
                values = sampleFromTimeInState();
                break;
            default:
                values = sampleFromCurFreq();
                break;
        }
        if (values == null || values.length == 0) {
            if (coreCount <= 0) statusText = "采样中";
            invalidate();
            return;
        }
        coreCount = values.length;
        ensureCoreInfo();
        history.add(values);
        while (history.size() > HISTORY) history.remove(0);
        updateAccessibilitySummary(values);
        logSample(values);
        invalidate();
    }

    // ---------------------------------------------------------------- 数据源探测

    private void detectDataSource() {
        if (readTextFile("/proc/stat") != null) {
            dataSource = SOURCE_STAT;
        } else if (readTextFile("/sys/devices/system/cpu/cpu0/cpufreq/stats/time_in_state") != null) {
            dataSource = SOURCE_TIME_IN_STATE;
        } else {
            dataSource = SOURCE_CUR_FREQ;
        }
        Log.i(TAG, "CPU 数据源：" + sourceLabel()
                + "（/proc/stat " + (dataSource == SOURCE_STAT ? "可读" : "不可读") + "）");
    }

    private String sourceLabel() {
        switch (dataSource) {
            case SOURCE_STAT: return "/proc/stat 真实占用率";
            case SOURCE_TIME_IN_STATE: return "频率监控（time_in_state 窗口平均）";
            default: return "频率监控（瞬时频率）";
        }
    }

    private String sourceHint() {
        switch (dataSource) {
            case SOURCE_STAT: return "真实占用率";
            case SOURCE_TIME_IN_STATE: return "频率曲线（系统屏蔽 /proc/stat）";
            default: return "频率曲线（系统屏蔽 /proc/stat）";
        }
    }

    private boolean isFreqMode() {
        return dataSource != SOURCE_STAT;
    }

    // ---------------------------------------------------------------- 三级采样

    /** 1) /proc/stat 累计计数差分 = 真实占用率。 */
    private float[] sampleFromStat() {
        long[][] stats = readProcStat();
        if (stats == null || stats.length == 0) return null;
        if (lastStats == null || lastStats.length != stats.length) {
            lastStats = stats;
            return null;
        }
        float[] values = new float[stats.length];
        for (int i = 0; i < stats.length; i++) {
            long totalDelta = Math.max(1, stats[i][0] - lastStats[i][0]);
            long idleDelta = Math.max(0, stats[i][1] - lastStats[i][1]);
            values[i] = clamp01(1f - idleDelta / (float) totalDelta);
        }
        lastStats = stats;
        return values;
    }

    /**
     * 2) time_in_state 差分求窗口平均频率，再归一化到 0..1 作为曲线高度。
     * 注意：这是**频率**，不是占用率。
     */
    private float[] sampleFromTimeInState() {
        int count = Runtime.getRuntime().availableProcessors();
        if (count <= 0) return null;
        List<long[]> freqs = new ArrayList<>();
        List<long[]> times = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long[][] table = readTimeInState(i);
            if (table == null) return null;
            freqs.add(table[0]);
            times.add(table[1]);
        }
        if (tisTimes == null || tisTimes.size() != times.size()) {
            tisTimes = times;
            return null;
        }
        float[] values = new float[count];
        double[] khz = new double[count];
        for (int i = 0; i < count; i++) {
            long[] f = freqs.get(i);
            long min = Long.MAX_VALUE;
            long max = 0L;
            for (long v : f) {
                if (v > 0) {
                    min = Math.min(min, v);
                    max = Math.max(max, v);
                }
            }
            double avg = averageFreq(f, tisTimes.get(i), times.get(i));
            khz[i] = avg;
            values[i] = (min == Long.MAX_VALUE || max <= min || avg <= 0)
                    ? 0f : clamp01((float) ((avg - min) / (double) (max - min)));
        }
        tisTimes = times;
        curFreqKhz = khz;
        return values;
    }

    /** 3) 兜底：直接用 scaling_cur_freq 的瞬时频率。 */
    private float[] sampleFromCurFreq() {
        int count = Runtime.getRuntime().availableProcessors();
        if (count <= 0) return null;
        float[] values = new float[count];
        double[] khz = new double[count];
        boolean any = false;
        for (int i = 0; i < count; i++) {
            long cur = readLongFile(freqPath(i, "scaling_cur_freq"));
            long max = readLongFile(freqPath(i, "cpuinfo_max_freq"));
            long min = readLongFile(freqPath(i, "cpuinfo_min_freq"));
            if (cur > 0 && max > 0) {
                if (min <= 0 || min >= max) min = 0;
                khz[i] = cur;
                values[i] = clamp01((cur - min) / (float) (max - min));
                any = true;
            }
        }
        curFreqKhz = khz;
        return any ? values : null;
    }

    /** 按各频率档位驻留时间加权，得到窗口内的平均频率（kHz）。 */
    private double averageFreq(long[] freqs, long[] prev, long[] now) {
        double weighted = 0d;
        double total = 0d;
        int n = Math.min(freqs.length, Math.min(prev.length, now.length));
        for (int i = 0; i < n; i++) {
            long delta = now[i] - prev[i];
            if (delta < 0) delta = 0; // 计数器被重置
            weighted += delta * (double) freqs[i];
            total += delta;
        }
        return total > 0d ? weighted / total : 0d;
    }

    // ---------------------------------------------------------------- 读取工具

    private long[][] readProcStat() {
        List<long[]> result = new ArrayList<>();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader("/proc/stat"));
            String line;
            while ((line = br.readLine()) != null) {
                if (!line.startsWith("cpu")) continue;
                String[] parts = line.trim().split("\\s+");
                if (parts.length < 5 || parts[0].length() <= 3) continue;
                if (!Character.isDigit(parts[0].charAt(3))) continue;
                long user = parse(parts, 1);
                long nice = parse(parts, 2);
                long system = parse(parts, 3);
                long idle = parse(parts, 4);
                long iowait = parse(parts, 5);
                long irq = parse(parts, 6);
                long softirq = parse(parts, 7);
                long steal = parse(parts, 8);
                long idleAll = idle + iowait;
                long total = user + nice + system + idle + iowait + irq + softirq + steal;
                result.add(new long[]{total, idleAll});
            }
        } catch (Throwable ignored) {
            return null;
        } finally {
            close(br);
        }
        return result.isEmpty() ? null : result.toArray(new long[result.size()][]);
    }

    /** 返回 {freqs, times}；读不到返回 null。 */
    private long[][] readTimeInState(int core) {
        String content = readTextFile("/sys/devices/system/cpu/cpu" + core + "/cpufreq/stats/time_in_state");
        if (content == null) return null;
        List<Long> freqs = new ArrayList<>();
        List<Long> times = new ArrayList<>();
        for (String line : content.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\s+");
            if (parts.length < 2) continue;
            try {
                freqs.add(Long.parseLong(parts[0]));
                times.add(Long.parseLong(parts[1]));
            } catch (NumberFormatException ignored) {
                // 跳过表头等非数字行
            }
        }
        if (freqs.isEmpty()) return null;
        long[] freqArr = new long[freqs.size()];
        long[] timeArr = new long[times.size()];
        for (int i = 0; i < freqs.size(); i++) {
            freqArr[i] = freqs.get(i);
            timeArr[i] = times.get(i);
        }
        return new long[][]{freqArr, timeArr};
    }

    private String freqPath(int core, String name) {
        return "/sys/devices/system/cpu/cpu" + core + "/cpufreq/" + name;
    }

    private String readTextFile(String path) {
        BufferedReader br = null;
        try {
            File file = new File(path);
            if (!file.exists() || !file.canRead()) return null;
            br = new BufferedReader(new FileReader(file));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
            return sb.length() == 0 ? null : sb.toString();
        } catch (Throwable ignored) {
            return null;
        } finally {
            close(br);
        }
    }

    private long readLongFile(String path) {
        String content = readTextFile(path);
        if (content == null) return 0L;
        try {
            return Long.parseLong(content.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    /**
     * 从 thermal_zone 中挑选 CPU 相关传感器。部分设备提供多个核心/簇温度，
     * 这里显示其中最高值，便于及时反映最热核心；过滤明显异常的传感器数值。
     */
    private void sampleCpuTemperature() {
        File thermalRoot = new File("/sys/class/thermal");
        File[] zones = thermalRoot.listFiles();
        float hottest = Float.NaN;
        String hottestType = null;
        if (zones != null) {
            for (File zone : zones) {
                if (zone == null || !zone.getName().startsWith("thermal_zone")) continue;
                String typeContent = readTextFile(new File(zone, "type").getAbsolutePath());
                if (typeContent == null) continue;
                String type = typeContent.trim().toLowerCase(Locale.US);
                if (!isCpuThermalType(type)) continue;
                long raw = readLongFile(new File(zone, "temp").getAbsolutePath());
                float value = normalizeTemperature(raw);
                if (Float.isNaN(value)) continue;
                if (Float.isNaN(hottest) || value > hottest) {
                    hottest = value;
                    hottestType = typeContent.trim();
                }
            }
        }
        cpuTemperatureC = hottest;
        cpuTemperatureSource = hottestType;
    }

    private boolean isCpuThermalType(String type) {
        if (type == null || type.isEmpty()) return false;
        if (type.contains("cpu") || type.contains("cpuss") || type.contains("soc")) return true;
        return type.contains("ap") && (type.contains("therm") || type.contains("temp"));
    }

    private float normalizeTemperature(long raw) {
        if (raw <= 0L) return Float.NaN;
        double value = raw;
        while (value > 200d) value /= 1000d;
        if (value < -20d || value > 150d) return Float.NaN;
        return (float) value;
    }

    private String temperatureText() {
        if (Float.isNaN(cpuTemperatureC)) return "温度 --";
        return String.format(Locale.getDefault(), "温度 %.1f℃", cpuTemperatureC);
    }

    private void close(BufferedReader br) {
        try { if (br != null) br.close(); } catch (Exception ignored) { }
    }

    private long parse(String[] parts, int index) {
        if (index >= parts.length) return 0L;
        try { return Long.parseLong(parts[index]); } catch (Exception e) { return 0L; }
    }

    private float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    // ---------------------------------------------------------------- 大小核识别

    private void ensureCoreInfo() {
        if (coreCount <= 0) return;
        if (coreOrder != null && coreOrder.length == coreCount
                && coreMaxFreqs != null && coreMaxFreqs.length == coreCount) {
            return;
        }
        coreMaxFreqs = new long[coreCount];
        for (int i = 0; i < coreCount; i++) {
            long max = readLongFile(freqPath(i, "cpuinfo_max_freq"));
            if (max <= 0) max = readLongFile(freqPath(i, "scaling_max_freq"));
            coreMaxFreqs[i] = max;
        }
        classifyBigLittle();
        coreOrder = new int[coreCount];
        for (int i = 0; i < coreCount; i++) coreOrder[i] = i;
        for (int i = 0; i < coreCount - 1; i++) {
            for (int j = i + 1; j < coreCount; j++) {
                if (coreMaxFreqs[coreOrder[j]] > coreMaxFreqs[coreOrder[i]]) {
                    int t = coreOrder[i];
                    coreOrder[i] = coreOrder[j];
                    coreOrder[j] = t;
                }
            }
        }
        logTopology();
    }

    /** 按最大频率把核心分成大核 / 小核两组，取相邻频率档位之间相对落差最大的位置作为分界。 */
    private void classifyBigLittle() {
        coreIsBig = new boolean[coreCount];
        long maxFreq = 0;
        for (long f : coreMaxFreqs) maxFreq = Math.max(maxFreq, f);
        if (maxFreq <= 0) {
            for (int i = 0; i < coreCount; i++) coreIsBig[i] = true;
            return;
        }
        List<Long> levels = new ArrayList<>();
        for (long f : coreMaxFreqs) {
            if (f <= 0 || levels.contains(f)) continue;
            levels.add(f);
        }
        for (int i = 0; i < levels.size() - 1; i++) {
            for (int j = i + 1; j < levels.size(); j++) {
                if (levels.get(j) > levels.get(i)) {
                    long t = levels.get(i);
                    levels.set(i, levels.get(j));
                    levels.set(j, t);
                }
            }
        }
        long bigThreshold = maxFreq;
        if (levels.size() >= 2) {
            double bestGap = -1d;
            int split = 0;
            for (int i = 0; i < levels.size() - 1; i++) {
                double gap = (levels.get(i) - levels.get(i + 1)) / (double) levels.get(i);
                if (gap > bestGap) {
                    bestGap = gap;
                    split = i;
                }
            }
            bigThreshold = levels.get(split);
        }
        for (int i = 0; i < coreCount; i++) {
            coreIsBig[i] = coreMaxFreqs[i] <= 0 || coreMaxFreqs[i] >= bigThreshold;
        }
    }

    private void logTopology() {
        if (topologyLogged) return;
        topologyLogged = true;
        StringBuilder big = new StringBuilder();
        StringBuilder small = new StringBuilder();
        for (int i = 0; i < coreCount; i++) {
            StringBuilder target = coreIsBig[i] ? big : small;
            if (target.length() > 0) target.append(',');
            target.append('C').append(i);
        }
        Log.i(TAG, "CPU拓扑：大核[" + big + "] 小核[" + small + "] 频率档位=" + levelsSummary());
    }

    private String levelsSummary() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < coreCount; i++) {
            if (i > 0) sb.append(' ');
            sb.append('C').append(i).append('=').append(freqText(i));
        }
        return sb.toString();
    }

    private String freqText(int core) {
        if (coreMaxFreqs == null || core < 0 || core >= coreMaxFreqs.length) return "?";
        long freq = coreMaxFreqs[core];
        if (freq <= 0) return "?";
        return ghz(freq) + "G";
    }

    private String ghz(double khz) {
        return String.format(Locale.US, "%.1f", khz / 1_000_000d);
    }

    private boolean isBig(int core) {
        return coreIsBig == null || core < 0 || core >= coreIsBig.length || coreIsBig[core];
    }

    private String buildTitle() {
        return "CPU";
    }

    // ---------------------------------------------------------------- 无障碍与日志

    private void updateAccessibilitySummary(float[] values) {
        if (values == null || values.length == 0) {
            setContentDescription("CPU监控：无数据");
            return;
        }
        setContentDescription(buildSummary(values));
    }

    private void logSample(float[] values) {
        Log.i(TAG, buildSummary(values));
    }

    private String buildSummary(float[] values) {
        StringBuilder sb = new StringBuilder("CPU监控[")
                .append(dataSource == SOURCE_STAT ? "占用率"
                        : dataSource == SOURCE_TIME_IN_STATE ? "频率/窗口平均" : "频率/瞬时")
                .append("]：");
        for (int i = 0; i < Math.min(values.length, 8); i++) {
            if (i > 0) sb.append(' ');
            sb.append('C').append(i).append('=');
            if (isFreqMode() && curFreqKhz != null && i < curFreqKhz.length && curFreqKhz[i] > 0) {
                sb.append(String.format(Locale.US, "%.2fG", curFreqKhz[i] / 1_000_000d));
            } else {
                sb.append(Math.round(values[i] * 100)).append('%');
            }
        }
        sb.append(' ').append(temperatureText());
        if (cpuTemperatureSource != null) sb.append('(').append(cpuTemperatureSource).append(')');
        return sb.toString();
    }

    // ---------------------------------------------------------------- 绘制

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float density = getResources().getDisplayMetrics().density;
        Log.i(TAG, "面板尺寸：" + Math.round(w / density) + "dp x " + Math.round(h / density) + "dp");
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(Math.round(72 * panelAlphaPercent / 100f), 18, 24, 36));
        canvas.drawRoundRect(0, 0, w, h, dp(14), dp(14), paint);

        if (coreCount > 0) ensureCoreInfo();

        paint.setColor(Color.argb(215, 255, 255, 255));
        paint.setTextSize(dp(11));
        paint.setFakeBoldText(true);
        canvas.drawText(buildTitle(), dp(9), dp(14), paint);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setColor(temperatureColor());
        canvas.drawText(temperatureText(), w - dp(9), dp(14), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setFakeBoldText(false);

        if (coreCount <= 0) {
            paint.setTextSize(dp(10));
            paint.setColor(Color.argb(160, 255, 255, 255));
            canvas.drawText(statusText, dp(9), dp(36), paint);
            return;
        }

        // /proc/stat 不可读时必须标明是估算值，避免误读成真实利用率
        boolean showHint = dataSource != SOURCE_STAT;
        if (showHint) {
            paint.setTextSize(dp(8));
            paint.setColor(Color.argb(150, 255, 255, 255));
            canvas.drawText(sourceHint(), dp(9), dp(24), paint);
        }

        // 大核全宽正常大小；小核半宽、两个一行紧凑排布
        List<Integer> bigCores = new ArrayList<>();
        List<Integer> smallCores = new ArrayList<>();
        for (int i = 0; i < coreCount; i++) {
            int core = coreOrder == null ? i : coreOrder[i];
            if (isBig(core)) bigCores.add(core); else smallCores.add(core);
        }
        int smallRows = (smallCores.size() + 1) / 2;
        int totalRows = bigCores.size() + smallRows;
        if (totalRows <= 0) return;

        float left = dp(6);
        float contentW = w - dp(12);
        float top = showHint ? dp(31) : dp(20);
        float gap = dp(4);
        float available = h - top - dp(8) - gap * Math.max(0, totalRows - 1);
        float unit = available / Math.max(1f, bigCores.size() * 2f + smallRows);
        float bigH = Math.max(dp(16), unit * 2f);
        float smallH = Math.max(dp(12), unit);

        float y = top;
        for (int core : bigCores) {
            drawCore(canvas, core, left, y, contentW, bigH, true);
            y += bigH + gap;
        }
        float smallW = (contentW - gap) / 2f;
        for (int i = 0; i < smallCores.size(); i += 2) {
            drawCore(canvas, smallCores.get(i), left, y, smallW, smallH, false);
            if (i + 1 < smallCores.size()) {
                drawCore(canvas, smallCores.get(i + 1), left + smallW + gap, y, smallW, smallH, false);
            }
            y += smallH + gap;
        }
    }

    private void drawCore(Canvas canvas, int core, float x, float y, float width, float height, boolean wide) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.argb(42, 255, 255, 255));
        canvas.drawRoundRect(x, y, x + width, y + height, dp(6), dp(6), paint);

        float current = latestValue(core);
        paint.setTextSize(wide ? dp(9) : dp(8));
        paint.setColor(Color.argb(200, 255, 255, 255));
        // 占用率模式显示「上限 百分比」；频率模式显示「当前/上限」——不把频率冒充成占用率
        String label;
        if (isFreqMode()) {
            double cur = (curFreqKhz != null && core < curFreqKhz.length) ? curFreqKhz[core] : 0d;
            long max = (coreMaxFreqs != null && core < coreMaxFreqs.length) ? coreMaxFreqs[core] : 0L;
            label = (cur > 0 ? ghz(cur) : "?") + "/" + (max > 0 ? ghz(max) : "?") + "G";
        } else {
            label = freqText(core) + " " + Math.round(current * 100) + "%";
        }
        canvas.drawText(label, x + dp(4), y + dp(10), paint);

        if (history.size() < 2) return;
        float graphLeft = x + dp(3);
        float graphRight = x + width - dp(3);
        float graphTop = y + dp(13);
        float graphBottom = y + height - dp(3);
        if (graphRight - graphLeft <= dp(4) || graphBottom - graphTop <= dp(3)) return;

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.3f));
        paint.setColor(colorForCore(core));
        int n = Math.min(history.size(), HISTORY);
        float lastX = graphLeft;
        float lastY = graphBottom - valueAt(0, core) * (graphBottom - graphTop);
        for (int i = 1; i < n; i++) {
            float px = graphLeft + (graphRight - graphLeft) * i / (HISTORY - 1);
            float py = graphBottom - valueAt(i, core) * (graphBottom - graphTop);
            canvas.drawLine(lastX, lastY, px, py, paint);
            lastX = px;
            lastY = py;
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private float valueAt(int historyIndex, int core) {
        if (historyIndex < 0 || historyIndex >= history.size()) return 0f;
        float[] values = history.get(historyIndex);
        return core < values.length ? values[core] : 0f;
    }

    private float latestValue(int core) {
        if (history.isEmpty()) return 0f;
        float[] values = history.get(history.size() - 1);
        return core < values.length ? values[core] : 0f;
    }

    private int temperatureColor() {
        if (Float.isNaN(cpuTemperatureC)) return Color.argb(150, 255, 255, 255);
        if (cpuTemperatureC >= 80f) return Color.rgb(255, 90, 90);
        if (cpuTemperatureC >= 65f) return Color.rgb(255, 190, 90);
        return Color.rgb(120, 230, 190);
    }

    private int colorForCore(int core) {
        int[] colors = new int[]{
                Color.rgb(92, 225, 230), Color.rgb(255, 209, 102), Color.rgb(255, 111, 145), Color.rgb(152, 245, 142),
                Color.rgb(170, 140, 255), Color.rgb(255, 159, 67), Color.rgb(72, 219, 251), Color.rgb(29, 209, 161)
        };
        return colors[core % colors.length];
    }

    private int dp(float value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}

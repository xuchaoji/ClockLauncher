package com.example.clocklauncher;

import android.content.Context;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.widget.FrameLayout;

/**
 * 弹出瞬间的手势屏蔽容器。
 *
 * 背景：弹窗是在用户手指仍按在屏幕上（点击/长按触发）时创建的，
 * 系统会把这次手势剩余的 UP/CANCEL 事件投递到新出现的窗口上。
 * 结果就是——点「阴影颜色」打开取色器时，手指所在位置会立刻被当成一次取色，
 * 用户还没看清界面颜色就被改掉了（长按打开设置中心时也会误点进第一个分类）。
 *
 * 这里在弹窗显示后的极短时间内吞掉所有触摸事件，等手指抬起后再放行。
 */
public class TouchGuardLayout extends FrameLayout {
    /** 弹窗出现后屏蔽触摸的时长（毫秒），足以覆盖残留的 UP 事件。 */
    private static final long GUARD_DURATION_MS = 320L;

    private long armedAt = 0L;

    public TouchGuardLayout(Context context) {
        super(context);
    }

    /** 从现在起屏蔽触摸 GUARD_DURATION_MS 毫秒。 */
    public void arm() {
        armedAt = SystemClock.uptimeMillis() + GUARD_DURATION_MS;
    }

    public boolean isArmed() {
        return SystemClock.uptimeMillis() >= armedAt;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isArmed()) {
            // 吞掉残留事件，避免弹窗内容被"隔空点击"
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }
}

package com.example.molegame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;

/**
 * The four round steering buttons (up / down / left / right) drawn at the bottom
 * of the screen. Each pointer is mapped to the nearest button, so sliding a thumb
 * between buttons steers smoothly and two thumbs can press two at once.
 */
public class PadView extends View {

    /**
     * Four steering buttons in a row: left, up, down and right. The left button lets
     * the mole back out of a dead end or walk around an obstacle he slipped past.
     */
    public static final int UP = 0, DOWN = 1, LEFT = 2, RIGHT = 3;

    /** Notified when a direction is pressed or released. */
    public interface Listener { void onPad(int dir, boolean down); }

    private Listener listener;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arrow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final int[] heldCount = new int[4];
    private final long[] holdStart = new long[4];

    /**
     * Button centres as fractions of the pad area: four round buttons in one row,
     * ordered left / up / down / right so the arrows read naturally.
     */
    private static final float[][] POS = {
            {0.38f, 0.50f},   // up
            {0.54f, 0.50f},   // down
            {0.22f, 0.50f},   // left
            {0.70f, 0.50f},   // right
    };
    private float radius = 0f, cx = 0f, cy = 0f, cw = 0f, ch = 0f;

    public PadView(Context c) { super(c); setup(); }

    private void setup() {
        setClickable(true);
        arrow.setStyle(Paint.Style.STROKE);
        arrow.setStrokeCap(Paint.Cap.ROUND);
        arrow.setStrokeJoin(Paint.Join.ROUND);
    }

    public void setListener(Listener l) { this.listener = l; }
    public boolean isHeld(int dir) { return heldCount[dir] > 0; }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cw = w; ch = h;
        // anchor the pad cluster around its own middle
        cx = w * 0.5f; cy = h * 0.5f;
        // Four buttons in a row, each one large enough that a child never has to aim.
        radius = Math.min(w * 0.068f, h * 0.34f);
    }

    private float centerX(int dir) { return cx + (POS[dir][0] - 0.5f) * cw; }
    private float centerY(int dir) { return cy + (POS[dir][1] - 0.5f) * ch; }

    private static final int[] SHOWN = {LEFT, UP, DOWN, RIGHT};

    @Override protected void onDraw(Canvas c) {
        for (int d : SHOWN) drawButton(c, d);
    }

    private void drawButton(Canvas c, int d) {
        boolean held = heldCount[d] > 0;
        float x = centerX(d), y = centerY(d);
        float r = radius;
        float press = held ? 0.9f : 1f;
        float rr = r * press;

        // soft drop shadow
        p.setShader(null);
        p.setColor(Color.argb(held ? 60 : 110, 0, 0, 0));
        c.drawOval(x - rr, y - rr + (held ? 3 : 8), x + rr, y + rr + (held ? 3 : 12), p);

        // body: brushed dirt-brown disc with a lit top
        p.setShader(new RadialGradient(x, y - rr * 0.35f, rr * 1.5f,
                new int[]{Color.rgb(226, 176, 130), Color.rgb(158, 104, 66), Color.rgb(96, 60, 38)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr, p);
        p.setShader(null);

        // rim
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(3f, rr * 0.09f));
        p.setColor(held ? Color.argb(255, 255, 226, 150) : Color.argb(220, 60, 40, 28));
        c.drawCircle(x, y, rr - p.getStrokeWidth() * 0.5f, p);
        p.setStyle(Paint.Style.FILL);

        // inner face
        p.setColor(held ? Color.argb(90, 255, 240, 200) : Color.argb(45, 255, 255, 255));
        c.drawCircle(x, y, rr * 0.78f, p);

        // arrow glyph
        arrow.setColor(held ? Color.rgb(255, 248, 232) : Color.rgb(250, 240, 226));
        arrow.setStrokeWidth(Math.max(5f, rr * 0.14f));
        float s = rr * 0.42f;
        float ux = 0, uy = 0;
        if (d == UP) { ux = 0; uy = -1; }
        else if (d == DOWN) { ux = 0; uy = 1; }
        else if (d == LEFT) { ux = -1; uy = 0; }
        else { ux = 1; uy = 0; }            // RIGHT

        // shaft
        c.drawLine(x - ux * s, y - uy * s, x + ux * s * 0.6f, y + uy * s * 0.6f, arrow);
        // head
        float hx = x + ux * s, hy = y + uy * s;
        float px1 = -uy, py1 = ux;
        path.reset();
        path.moveTo(hx - ux * s * 0.75f + px1 * s * 0.62f, hy - uy * s * 0.75f + py1 * s * 0.62f);
        path.lineTo(hx, hy);
        path.lineTo(hx - ux * s * 0.75f - px1 * s * 0.62f, hy - uy * s * 0.75f - py1 * s * 0.62f);
        c.drawPath(path, arrow);
    }

    // ---------------------------------------------------------------- input
    /**
     * Direction under a finger. Any touch on the pad counts: we take the closest
     * button, which keeps the controls usable for small hands that miss slightly.
     */
    private int nearest(float tx, float ty) {
        int best = -1;
        float bd = Float.MAX_VALUE;
        for (int d : SHOWN) {
            float dx = tx - centerX(d), dy = ty - centerY(d);
            float dist = dx * dx + dy * dy;
            if (dist < bd) { bd = dist; best = d; }
        }
        return best;
    }

    @Override public boolean performClick() { return super.performClick(); }

    @Override public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                assign(e, e.getActionIndex());
                break;
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < e.getPointerCount(); i++) assign(e, i);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                release(e, e.getActionIndex());
                break;
            case MotionEvent.ACTION_CANCEL:
                java.util.Arrays.fill(activeDir, -1);
                java.util.Arrays.fill(pointerIds, -1);
                for (int d : SHOWN) while (heldCount[d] > 0) setDown(d, false);
                break;
            default:
                break;
        }
        invalidate();
        return true;
    }

    /** Map one pointer index to the button currently under its finger. */
    private void assign(MotionEvent e, int idx) {
        int id = e.getPointerId(idx);
        int slot = slotFor(id, true);
        if (slot < 0) return;
        int d = nearest(e.getX(idx), e.getY(idx));
        int prev = activeDir[slot];
        if (prev == d) return;
        if (prev >= 0) setDown(prev, false);
        activeDir[slot] = d;
        if (d >= 0) setDown(d, true);
    }

    private void release(MotionEvent e, int idx) {
        int id = e.getPointerId(idx);
        int slot = slotFor(id, false);
        if (slot < 0) return;
        int prev = activeDir[slot];
        if (prev >= 0) setDown(prev, false);
        activeDir[slot] = -1;
        pointerIds[slot] = -1;
    }

    /** Press / release a direction using a small reference count. */
    private void setDown(int d, boolean down) {
        if (d < 0 || d > 3) return;
        if (down) {
            if (heldCount[d] == 0) {
                holdStart[d] = System.currentTimeMillis();
                if (listener != null) listener.onPad(d, true);
            }
            heldCount[d]++;
        } else {
            if (heldCount[d] > 0) heldCount[d]--;
            if (heldCount[d] == 0 && listener != null) listener.onPad(d, false);
        }
    }

    // fixed-size table of live pointers -> button they hold
    private static final int MAX_PTR = 4;
    private final int[] pointerIds = {-1, -1, -1, -1};
    private final int[] activeDir = {-1, -1, -1, -1};

    private int slotFor(int id, boolean allocate) {
        for (int i = 0; i < MAX_PTR; i++) if (pointerIds[i] == id) return i;
        if (!allocate) return -1;
        for (int i = 0; i < MAX_PTR; i++) {
            if (pointerIds[i] == -1) { pointerIds[i] = id; return i; }
        }
        return -1;
    }


    /** Axis helpers used by the game view. */
    public float axisX() {
        float v = 0;
        if (heldCount[LEFT] > 0) v -= 1;
        if (heldCount[RIGHT] > 0) v += 1;
        return v;
    }

    public float axisY() {
        float v = 0;
        if (heldCount[UP] > 0) v -= 1;
        if (heldCount[DOWN] > 0) v += 1;
        return v;
    }
}

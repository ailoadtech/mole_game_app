package com.example.molegame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** The finish: the mole's friend, a mouse, waiting in her hole. */
public class MouseFriend {

    /** Drawn size of the mouse relative to one tile. */
    public static final float MOUSE_SCALE = 1.9f;

    public static final int X = C.MOUSE_HOLE_X;
    public static final int Y = C.MOUSE_HOLE_ROW;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    /** True when the mole is close enough to see her waving. */
    public void draw(Canvas c, float camX, float camY, float t, boolean happy) {
        float T = C.TILE;
        float sx = (X - camX) * T;
        float sy = (Y - camY) * T;
        if (sx < -T * 5 || sx > c.getWidth() + T * 5) return;

        // She is drawn larger than a tile so she is easy to spot from far away.
        c.save();
        c.scale(MOUSE_SCALE, MOUSE_SCALE, sx, sy);

        // her hole: dark opening with an excavated mound
        p.setColor(Color.rgb(20, 14, 12));
        c.drawOval(sx - T * 0.9f, sy - T * 0.7f, sx + T * 0.9f, sy + T * 0.8f, p);
        p.setColor(Color.rgb(150, 110, 74));
        c.drawOval(sx - T * 1.35f, sy + T * 0.55f, sx + T * 1.35f, sy + T * 1.05f, p);

        // little party flag on the mound
        p.setColor(Color.rgb(120, 90, 60));
        p.setStrokeWidth(3f);
        c.drawLine(sx + T * 1.0f, sy + T * 0.6f, sx + T * 1.0f, sy - T * 0.5f, p);
        p.setStrokeWidth(1f);
        p.setColor(Color.rgb(232, 76, 110));
        path.reset();
        path.moveTo(sx + T * 1.0f, sy - T * 0.5f);
        path.lineTo(sx + T * 1.7f, sy - T * 0.32f + (float) Math.sin(t * 5f) * 3f);
        path.lineTo(sx + T * 1.0f, sy - T * 0.12f);
        path.close();
        c.drawPath(path, p);

        float bob = (float) Math.sin(t * (happy ? 9f : 3f)) * (happy ? 5f : 2f);
        c.save();
        c.translate(sx, sy + bob);

        // tail
        p.setColor(Color.rgb(226, 168, 176));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        path.moveTo(T * 0.35f, T * 0.3f);
        path.quadTo(T * 0.95f, T * 0.5f, T * 0.7f + (happy ? 10 : 0), T * 0.05f);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);

        // body (soft grey)
        p.setColor(Color.rgb(186, 186, 196));
        c.drawOval(-T * 0.42f, -T * 0.28f, T * 0.42f, T * 0.5f, p);
        p.setColor(Color.rgb(222, 222, 230));
        c.drawOval(-T * 0.26f, -T * 0.02f, T * 0.26f, T * 0.44f, p);

        // ears
        p.setColor(Color.rgb(176, 176, 188));
        c.drawCircle(-T * 0.3f, -T * 0.42f, T * 0.22f, p);
        c.drawCircle(T * 0.24f, -T * 0.42f, T * 0.22f, p);
        p.setColor(Color.rgb(244, 186, 198));
        c.drawCircle(-T * 0.3f, -T * 0.42f, T * 0.12f, p);
        c.drawCircle(T * 0.24f, -T * 0.42f, T * 0.12f, p);

        // head
        p.setColor(Color.rgb(198, 198, 208));
        c.drawOval(-T * 0.34f, -T * 0.52f, T * 0.34f, T * 0.06f, p);
        // snout + nose
        p.setColor(Color.rgb(226, 226, 236));
        path.reset();
        path.moveTo(-T * 0.1f, -T * 0.28f);
        path.lineTo(T * 0.1f, -T * 0.28f);
        path.lineTo(0, -T * 0.02f);
        path.close();
        c.drawPath(path, p);
        p.setColor(Color.rgb(236, 130, 150));
        c.drawCircle(0, -T * 0.04f, T * 0.055f, p);

        // eyes (happy = closed arcs)
        p.setColor(Color.rgb(28, 26, 30));
        if (happy) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3f);
            c.drawArc(-T * 0.24f, -T * 0.44f, -T * 0.08f, -T * 0.28f, 20, 140, false, p);
            c.drawArc(T * 0.08f, -T * 0.44f, T * 0.24f, -T * 0.28f, 20, 140, false, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            c.drawCircle(-T * 0.16f, -T * 0.34f, T * 0.055f, p);
            c.drawCircle(T * 0.16f, -T * 0.34f, T * 0.055f, p);
            p.setColor(Color.rgb(255, 255, 255));
            c.drawCircle(-T * 0.145f, -T * 0.355f, T * 0.02f, p);
            c.drawCircle(T * 0.175f, -T * 0.355f, T * 0.02f, p);
        }
        // whiskers
        p.setColor(Color.rgb(250, 250, 250));
        p.setStrokeWidth(1.6f);
        for (int s = -1; s <= 1; s += 2) {
            c.drawLine(s * T * 0.06f, -T * 0.1f, s * T * 0.5f, -T * 0.18f, p);
            c.drawLine(s * T * 0.06f, -T * 0.05f, s * T * 0.52f, -T * 0.02f, p);
        }
        p.setStrokeWidth(1f);

        // waving paw when she is glad
        if (happy) {
            p.setColor(Color.rgb(206, 206, 216));
            c.save();
            c.rotate((float) Math.sin(t * 12f) * 0.5f - 0.6f, T * 0.3f, -T * 0.1f);
            c.drawOval(T * 0.24f, -T * 0.5f, T * 0.42f, -T * 0.05f, p);
            c.restore();
        }
        c.restore();
        c.restore();

        // hearts when reunited
        if (happy) {
            for (int i = 0; i < 3; i++) {
                float ph = (t * 0.9f + i * 0.33f) % 1f;
                float hx = sx + (i - 1) * T * 0.5f + (float) Math.sin(ph * 6f) * 8f;
                float hy = sy - T * 0.9f - ph * T * 1.4f;
                drawHeart(c, hx, hy, 8f + 4f * (1 - ph), 1 - ph);
            }
        }
    }

    private void drawHeart(Canvas c, float cx, float cy, float s, float alpha) {
        p.setColor(Color.argb((int) (220 * alpha), 236, 74, 110));
        path.reset();
        path.moveTo(cx, cy + s * 0.8f);
        path.cubicTo(cx - s * 1.4f, cy - s * 0.3f, cx - s * 0.4f, cy - s * 1.2f, cx, cy - s * 0.4f);
        path.cubicTo(cx + s * 0.4f, cy - s * 1.2f, cx + s * 1.4f, cy - s * 0.3f, cx, cy + s * 0.8f);
        c.drawPath(path, p);
    }
}

package com.example.molegame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** On-screen HUD: the horizontal red/green power bar at the top right. */
public class Hud {

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF r = new RectF();

    public Hud() {
        text.setTextAlign(Paint.Align.LEFT);
    }

    /**
     * The power bar in the top-right corner and nothing else: green when strong,
     * sliding to red as the mole runs out of energy. No labels or numbers - the
     * colour alone tells a young player everything they need.
     */
    public void draw(Canvas c, float power01, boolean lowWarn, float t) {
        float w = c.getWidth(), h = c.getHeight();
        float density = Math.max(1f, w / 900f);
        float bw = Math.min(w * 0.34f, 260 * density);
        float bh = Math.max(14f, 20 * density);
        float bx = w - bw - 14 * density;
        float by = 12 * density;

        // frame
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(200, 24, 20, 18));
        r.set(bx - 3, by - 3, bx + bw + 3, by + bh + 3);
        c.drawRoundRect(r, bh, bh, p);
        p.setColor(Color.argb(230, 226, 214, 196));
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(2.5f);
        c.drawRoundRect(r, bh, bh, p);
        p.setStyle(Paint.Style.FILL);

        // fill: gradient from red (left/low) to green (right/full)
        float fw = bw * Math.max(0f, Math.min(1f, power01));
        if (fw > 1) {
            android.graphics.Shader sh = new android.graphics.LinearGradient(
                    bx, 0, bx + bw, 0,
                    new int[]{Color.rgb(226, 46, 38), Color.rgb(240, 176, 40), Color.rgb(74, 200, 74)},
                    new float[]{0f, 0.5f, 1f}, android.graphics.Shader.TileMode.CLAMP);
            p.setShader(sh);
            r.set(bx, by, bx + fw, by + bh);
            c.drawRoundRect(r, bh * 0.5f, bh * 0.5f, p);
            p.setShader(null);
        }

        // pulse a warning outline when nearly empty
        if (lowWarn) {
            int a = (int) (120 + 110 * Math.sin(t * 9f));
            p.setColor(Color.argb(Math.min(255, a), 255, 60, 40));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4f);
            c.drawRoundRect(bx - 6, by - 6, bx + bw + 6, by + bh + 6, bh, bh, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(1f);
        }

    }

    /** A soft strip of text near the top-left explaining what to do. */
    public void hintBar(Canvas c, String msg, float alpha01) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha01)));
        float density = Math.max(1f, c.getWidth() / 900f);
        float size = Math.max(15f, 17 * density);
        text.setTextSize(size);
        text.setTextAlign(Paint.Align.LEFT);
        text.setFakeBoldText(true);
        float tw = text.measureText(msg);
        float bx = 12 * density, by = 12 * density;
        p.setColor(Color.argb((int) (a * 0.62f), 14, 11, 10));
        r.set(bx, by, bx + tw + 22 * density, by + size + 18 * density);
        c.drawRoundRect(r, 12, 12, p);
        text.setColor(Color.argb(a, 255, 246, 226));
        text.setShadowLayer(3, 0, 1, Color.argb(a, 0, 0, 0));
        c.drawText(msg, bx + 11 * density, by + size + 7 * density, text);
        text.setShadowLayer(0, 0, 0, 0);
        text.setFakeBoldText(false);
    }

    /**
     * Round speaker button in the top-left corner. Tap it (the game view handles the
     * hit test) to enable or disable the background music. When off, a red slash is
     * drawn across the speaker so the state is obvious at a glance.
     */
    public void drawSoundButton(Canvas c, boolean enabled) {
        float w = c.getWidth(), h = c.getHeight();
        float density = Math.max(1f, w / 900f);
        float d = Math.min(w * 0.085f, h * 0.06f);          // button diameter
        d = Math.max(d, 34f * density);
        float cx = 12 * density + d * 0.5f;
        float cy = 12 * density + d * 0.5f;

        // disc
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(200, 24, 20, 18));
        c.drawCircle(cx, cy, d * 0.5f, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f, 2.2f * density));
        p.setColor(Color.argb(230, 226, 214, 196));
        c.drawCircle(cx, cy, d * 0.5f, p);
        p.setStyle(Paint.Style.FILL);

        float s = d * 0.34f;                                  // glyph size
        // speaker body: rectangle + triangle cone
        p.setColor(enabled ? Color.rgb(255, 246, 226) : Color.rgb(170, 160, 150));
        r.set(cx - s * 0.75f, cy - s * 0.3f, cx - s * 0.3f, cy + s * 0.3f);
        c.drawRoundRect(r, 2, 2, p);
        android.graphics.Path cone = new android.graphics.Path();
        cone.moveTo(cx - s * 0.3f, cy - s * 0.15f);
        cone.lineTo(cx + s * 0.25f, cy - s * 0.7f);
        cone.lineTo(cx + s * 0.25f, cy + s * 0.7f);
        cone.lineTo(cx - s * 0.3f, cy + s * 0.15f);
        cone.close();
        c.drawPath(cone, p);

        if (enabled) {
            // sound waves
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(2f, s * 0.14f));
            p.setColor(Color.rgb(255, 226, 120));
            c.drawArc(cx + s * 0.1f, cy - s * 0.5f, cx + s * 0.85f, cy + s * 0.5f, -55, 110, false, p);
            c.drawArc(cx + s * 0.1f, cy - s * 0.85f, cx + s * 1.25f, cy + s * 0.85f, -50, 100, false, p);
            p.setStyle(Paint.Style.FILL);
        } else {
            // red mute slash
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(3f, s * 0.22f));
            p.setColor(Color.rgb(236, 74, 74));
            c.drawLine(cx - s * 0.85f, cy + s * 0.85f, cx + s * 0.95f, cy - s * 0.85f, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeWidth(1f);
        }
    }

    /** Top-of-screen banner used for hints and the win message. */
    public void banner(Canvas c, String title, String sub, float alpha01) {
        int a = (int) (255 * Math.max(0f, Math.min(1f, alpha01)));
        float w = c.getWidth(), h = c.getHeight();
        p.setColor(Color.argb((int) (a * 0.72f), 12, 10, 10));
        c.drawRect(0, 0, w, h, p);

        text.setTextAlign(Paint.Align.CENTER);
        text.setColor(Color.argb(a, 255, 246, 232));
        text.setShadowLayer(6, 0, 2, Color.argb(a, 0, 0, 0));
        float ts = Math.min(w * 0.09f, 72f);
        text.setTextSize(ts);
        c.drawText(title, w / 2f, h * 0.42f, text);
        if (sub != null) {
            text.setTextSize(ts * 0.42f);
            text.setColor(Color.argb(a, 226, 214, 200));
            c.drawText(sub, w / 2f, h * 0.42f + ts * 0.85f, text);
        }
        text.setShadowLayer(0, 0, 0, 0);
        text.setTextAlign(Paint.Align.LEFT);
    }
}

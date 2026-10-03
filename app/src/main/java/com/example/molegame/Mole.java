package com.example.molegame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

/** The digging mole: position in tile units, facing, dig animation. */
public class Mole {

    /** Drawn width of the mole relative to one tile. */
    public static final float MOLE_SCALE = 1.4f;

    public float x = C.START_X;      // tile coordinates (centre)
    public float y = C.START_Y;
    public int dirX = 1, dirY = 0;   // last movement direction
    public float animTime = 0f;      // accumulates while digging
    public boolean moving = false;
    public float power = C.MAX_POWER;
    public int diamonds = 0;
    public int food = 0;
    public int dugTiles = 0;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();

    public void draw(Canvas c, float camX, float camY) {
        float T = C.TILE;
        float sx = (x - camX) * T;
        float sy = (y - camY) * T;
        // The mole is deliberately bigger than the tunnel he digs: he presses the
        // earth as he pushes through, which reads much better on a phone screen.
        float size = T * MOLE_SCALE;
        float dig = moving ? (float) Math.sin(animTime * 22f) : 0f;

        c.save();
        c.translate(sx, sy);
        float ang = (float) Math.toDegrees(Math.atan2(dirY, dirX));
        c.rotate(ang);
        // When heading left the rotation puts the belly on top: mirror across the
        // travel axis so the mole always lies on his belly either way.
        if (dirX < 0) c.scale(1f, -1f);

        // ---- dirt spray in front of the nose while digging
        if (moving) {
            p.setColor(Color.argb(150, 120, 82, 50));
            for (int i = 0; i < 6; i++) {
                float a = (i / 6f - 0.5f) * 1.3f;
                float d = size * (0.55f + 0.35f * ((i * 37 + (int) (animTime * 30)) % 5) / 5f);
                c.drawCircle((float) Math.cos(a) * d, (float) Math.sin(a) * d,
                        2.4f + (i % 3), p);
            }
        }

        // ---- tail
        p.setColor(Color.rgb(96, 72, 58));
        p.setStrokeWidth(5f);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        path.reset();
        path.moveTo(-size * 0.42f, 0);
        path.quadTo(-size * 0.66f, -6 - dig * 3, -size * 0.78f, 4 + dig * 4);
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);

        // ---- body (velvet grey-brown)
        p.setColor(Color.rgb(92, 78, 70));
        c.drawOval(-size * 0.5f, -size * 0.36f, size * 0.42f, size * 0.36f, p);
        p.setColor(Color.rgb(122, 106, 94));
        c.drawOval(-size * 0.42f, -size * 0.26f, size * 0.34f, size * 0.24f, p);
        // pinkish belly
        p.setColor(Color.rgb(176, 140, 130));
        c.drawOval(-size * 0.3f, -size * 0.1f, size * 0.24f, size * 0.3f, p);

        // ---- head / snout pointing in travel direction
        p.setColor(Color.rgb(104, 88, 78));
        c.drawOval(size * 0.06f, -size * 0.3f, size * 0.56f, size * 0.28f, p);
        p.setColor(Color.rgb(226, 150, 160));
        c.drawCircle(size * 0.56f + dig * 2f, 0, size * 0.11f, p);       // nose tip

        // ---- pink digging claws at the front
        p.setColor(Color.rgb(238, 176, 186));
        float clawSwing = dig * 0.16f;
        for (int s = -1; s <= 1; s += 2) {
            c.save();
            c.rotate(s * (0.5f + clawSwing));
            c.drawOval(size * 0.26f, s * size * 0.2f - size * 0.09f,
                    size * 0.46f, s * size * 0.2f + size * 0.09f, p);
            c.restore();
        }
        // hind feet
        p.setColor(Color.rgb(214, 156, 166));
        c.drawOval(-size * 0.44f, -size * 0.34f, -size * 0.26f, -size * 0.2f, p);
        c.drawOval(-size * 0.44f, size * 0.2f, -size * 0.26f, size * 0.34f, p);

        // ---- tiny eyes and whiskers
        p.setColor(Color.rgb(20, 18, 18));
        c.drawCircle(size * 0.3f, -size * 0.12f, size * 0.045f, p);
        c.drawCircle(size * 0.3f, size * 0.1f, size * 0.045f, p);
        p.setColor(Color.rgb(240, 236, 230));
        c.drawCircle(size * 0.315f, -size * 0.13f, size * 0.015f, p);
        c.drawCircle(size * 0.315f, size * 0.09f, size * 0.015f, p);
        // buck teeth
        p.setColor(Color.rgb(250, 246, 236));
        c.drawRect(size * 0.48f, -size * 0.06f, size * 0.56f, size * 0.06f, p);

        c.restore();
    }

    /** Facing direction as a GameView DIR_* id. */
    public int heading() {
        if (dirX > 0) return GameView.DIR_R;
        if (dirX < 0) return GameView.DIR_L;
        if (dirY > 0) return GameView.DIR_D;
        if (dirY < 0) return GameView.DIR_U;
        return 0;
    }

    /** Facing angle in degrees, used for effects. */
    public float angleDeg() { return (float) Math.toDegrees(Math.atan2(dirY, dirX)); }
}

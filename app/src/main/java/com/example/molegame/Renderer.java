package com.example.molegame;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * Draws the world: sky band with changing scenery (dark forest -> city park ->
 * countryside), soil, tunnels, rocks, diamonds, water and all collectibles.
 */
public class Renderer {

    /** Collectibles are drawn larger than one tile so they read on a phone. */
    private static final float ITEM_SCALE = 1.6f;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint px = new Paint();            // non-aliasing for tiles
    private final Path path = new Path();
    private final RectF r = new RectF();

    // cached sky shader per band
    private Shader skyForest, skyPark, skyCountry;
    private int skyPx = -1;   // height in pixels the sky gradients were built for

    Renderer() {
        px.setAntiAlias(false);
        px.setFilterBitmap(false);
    }

    /** Camera in tile units (top-left of viewport). */
    public void draw(Canvas c, World w, float camX, float camY, float viewW, float viewH,
                     float timeSec, boolean levelWon) {
        int T = C.TILE;
        drawSky(c, w, camX, camY, viewW, viewH, timeSec);
        drawUnderground(c, w, camX, camY, viewW, viewH, timeSec);
        drawSurfaceLine(c, w, camX, camY, viewW, viewH);
    }

    // ---------------------------------------------------------------- sky
    private void drawSky(Canvas c, World w, float camX, float camY, float vw, float vh, float t) {
        float horizonPx = (C.SURFACE_ROWS - camY) * C.TILE;   // approx ground line
        if (horizonPx < 0) horizonPx = 0;
        if (skyPx != (int) Math.max(1, vh) || skyForest == null) buildSky((int) Math.max(1, vh));

        // Fill the whole viewport with the current band's sky first: this guarantees
        // there is never an unlit (black) area above the ground, whatever the camera
        // position or screen size. The per-band zones are then painted on top so the
        // scenery visibly changes while digging to the right.
        int hereBand = w.bandAt(camX + vw / (2f * C.TILE));
        Shader[] sh = {skyForest, skyPark, skyCountry};
        p.setShader(sh[hereBand]);
        c.drawRect(0, 0, vw, vh, p);
        p.setShader(null);

        float[] bounds = {C.BAND_FOREST_END, C.BAND_PARK_END};
        for (int b = 0; b < 3; b++) {
            if (b == hereBand) continue;
            float x0 = (b == 0 ? -40f : bounds[b - 1]) - camX;
            float x1 = (b == 2 ? w.w + 40f : bounds[b]) - camX;
            float px0 = x0 * C.TILE, px1 = x1 * C.TILE;
            if (px1 < 0 || px0 > vw) continue;
            p.setShader(sh[b]);
            c.save();
            c.clipRect(px0, 0, px1, vh);
            c.drawRect(px0, 0, px1, vh, p);
            c.restore();
        }
        p.setShader(null);

        drawClouds(c, w, camX, camY, vw, vh, t);
        drawBandContent(c, w, camX, camY, vw, vh, t);
    }

    private void buildSky(int hpx) {
        // Even the dark forest needs a readable horizon: the lowest stop is a dusk blue
        // so trees near the ground are silhouetted against light, not lost in black.
        skyForest = new LinearGradient(0, 0, 0, hpx,
                new int[]{Color.rgb(26, 32, 56), Color.rgb(58, 68, 96), Color.rgb(126, 132, 140)},
                new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP);
        skyPark = new LinearGradient(0, 0, 0, hpx,
                new int[]{Color.rgb(96, 150, 200), Color.rgb(160, 200, 232), Color.rgb(214, 232, 244)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        skyCountry = new LinearGradient(0, 0, 0, hpx,
                new int[]{Color.rgb(126, 192, 238), Color.rgb(186, 224, 246), Color.rgb(236, 246, 250)},
                new float[]{0f, 0.6f, 1f}, Shader.TileMode.CLAMP);
        skyPx = hpx;
    }

    private void drawClouds(Canvas c, World w, float camX, float camY, float vw, float vh, float t) {
        int b = w.bandAt(camX + vw / 2f);
        p.setColor(b == World.SCENERY_FOREST ? Color.argb(60, 200, 205, 220)
                : Color.argb(210, 255, 255, 255));
        for (int[] cl : w.clouds) {
            float x = (cl[0] - camX) * C.TILE + (float) Math.sin(t * 0.12f + cl[0]) * 12f;
            float y = (cl[1] - camY) * C.TILE * 0.35f + 10f;
            if (x < -200 || x > vw + 200) continue;
            float s = cl[2];
            c.drawOval(x, y, x + s * 6, y + s * 2.2f, p);
            c.drawOval(x + s * 1.5f, y - s * 0.8f, x + s * 4.2f, y + s * 1.6f, p);
        }
    }

    /** Scenery specific to each band: dark forest roots, park + skyline, corn fields. */
    private void drawBandContent(Canvas c, World w, float camX, float camY, float vw, float vh, float t) {
        float T = C.TILE;
        // far skyline behind the park band
        for (int[] bd : w.buildings) {
            float x = (bd[0] - camX) * T;
            if (x > vw + 200 || x + bd[1] * T < -200) continue;
            int b = w.bandAt(bd[0]);
            if (b == World.SCENERY_COUNTRY && bd[0] > C.BAND_PARK_END + 6) continue;
            float bw = bd[1] * T, bh = bd[2] * T;
            float base = (surfacePx(w, bd[0] + bd[1] / 2f) - camY * T) - 6;
            int[] cols = {Color.rgb(70, 82, 104), Color.rgb(88, 98, 120), Color.rgb(56, 66, 88)};
            p.setColor(cols[bd[3]]);
            c.drawRect(x, base - bh, x + bw, base, p);
            // windows
            p.setColor(Color.argb(180, 255, 232, 150));
            for (float wy = base - bh + 10; wy < base - 12; wy += 22) {
                for (float wx = x + 8; wx < x + bw - 12; wx += 20) {
                    c.drawRect(wx, wy, wx + 8, wy + 11, p);
                }
            }
        }

        // grass strip along the surface
        for (int x = (int) Math.max(1, camX - 1); x < Math.min(w.w, camX + vw / T + 2); x++) {
            int b = w.bandAt(x);
            int gc = b == World.SCENERY_FOREST ? Color.rgb(30, 54, 34)
                    : b == World.SCENERY_PARK ? Color.rgb(74, 150, 66)
                    : Color.rgb(112, 168, 62);
            p.setColor(gc);
            float top = surfacePx(w, x) - camY * T;
            c.drawRect((x - camX) * T, top - 8, (x - camX + 1) * T, top + 3, p);
        }

        // trees
        for (int[] tr : w.trees) {
            float x = (tr[0] - camX) * T;
            if (x < -4 * T || x > vw + 4 * T) continue;
            drawTree(c, x, surfacePx(w, tr[0]) - camY * T, tr[1] * T, tr[2], w.bandAt(tr[0]), t);
        }
        // corn
        for (int[] cn : w.corn) {
            float x = (cn[0] - camX) * T;
            if (x < -T || x > vw + T) continue;
            drawCorn(c, x, surfacePx(w, cn[0]) - camY * T, cn[1] * T, t);
        }
        // tufts + flowers
        for (int[] g : w.grassTufts) {
            float x = (g[0] - camX) * T;
            if (x < -T || x > vw + T) continue;
            drawTuft(c, x, surfacePx(w, g[0]) - camY * T, g[1], w.bandAt(g[0]));
        }
        for (int[] f : w.flowers) {
            float x = (f[0] - camX) * T;
            if (x < -T || x > vw + T) continue;
            drawFlower(c, x, surfacePx(w, f[0]) - camY * T, f[1], f[2]);
        }
    }

    private float surfacePx(World w, float xTile) {
        int xi = Math.max(1, Math.min(w.w - 2, (int) xTile));
        return w.surfaceRow[xi] * (float) C.TILE;
    }

    private void drawTree(Canvas c, float x, float groundY, float h, int kind, int band, float t) {
        float sway = (float) Math.sin(t * 0.7f + x * 0.01f) * (kind == 2 ? 3f : 1.5f);
        // trunk
        p.setColor(kind == 2 ? Color.rgb(122, 88, 58) : Color.rgb(86, 60, 42));
        c.drawRect(x - 5, groundY - h, x + 5, groundY + 2, p);
        // visible root plate gripping the earth
        p.setColor(Color.rgb(48, 34, 26));
        path.reset();
        path.moveTo(x - 22, groundY + 3);
        path.quadTo(x - 12, groundY - 10, x, groundY - 8);
        path.quadTo(x + 12, groundY - 10, x + 22, groundY + 3);
        path.close();
        c.drawPath(path, p);

        if (kind == 0) {                                  // conifer
            p.setColor(band == World.SCENERY_FOREST ? Color.rgb(30, 66, 44) : Color.rgb(44, 112, 60));
            for (int i = 0; i < 3; i++) {
                float yy = groundY - h * (0.35f + i * 0.22f);
                float ww = h * (0.36f - i * 0.08f);
                path.reset();
                path.moveTo(x - ww + sway, yy);
                path.lineTo(x + ww + sway, yy);
                path.lineTo(x + sway, yy - h * 0.42f);
                path.close();
                c.drawPath(path, p);
            }
        } else {                                          // broadleaf blob
            p.setColor(kind == 2 ? Color.rgb(70, 140, 60)
                    : (band == World.SCENERY_FOREST ? Color.rgb(38, 78, 52) : Color.rgb(64, 138, 66)));
            float cy = groundY - h * 0.92f + Math.abs(sway) * 0.3f;
            c.drawCircle(x + sway, cy, h * 0.34f, p);
            c.drawCircle(x + sway - h * 0.24f, cy + h * 0.12f, h * 0.24f, p);
            c.drawCircle(x + sway + h * 0.24f, cy + h * 0.10f, h * 0.26f, p);
        }
    }

    private void drawCorn(Canvas c, float x, float gy, float h, float t) {
        float sway = (float) Math.sin(t * 1.1f + x * 0.05f) * 2.5f;
        p.setColor(Color.rgb(96, 150, 60));
        p.setStrokeWidth(3.5f);
        c.drawLine(x, gy, x + sway, gy - h, p);
        c.drawLine(x, gy - h * 0.45f, x - 12 + sway * 0.5f, gy - h * 0.75f, p);
        c.drawLine(x, gy - h * 0.55f, x + 12 + sway * 0.5f, gy - h * 0.85f, p);
        p.setColor(Color.rgb(226, 190, 70));
        c.drawOval(x + sway - 4, gy - h * 0.8f, x + sway + 4, gy - h * 0.55f, p);
        p.setStrokeWidth(1f);
    }

    private void drawTuft(Canvas c, float x, float gy, int kind, int band) {
        p.setColor(band == World.SCENERY_COUNTRY ? Color.rgb(130, 176, 70) : Color.rgb(60, 128, 56));
        p.setStrokeWidth(2.4f);
        for (int i = -2; i <= 2; i++) {
            c.drawLine(x + i * 3, gy, x + i * 4.5f, gy - 9 - Math.abs(kind) * 3, p);
        }
        p.setStrokeWidth(1f);
    }

    private void drawFlower(Canvas c, float x, float gy, float size, int colorIdx) {
        int[] fc = {Color.rgb(240, 90, 110), Color.rgb(250, 210, 80), Color.rgb(200, 120, 230), Color.rgb(255, 255, 255)};
        p.setColor(Color.rgb(70, 130, 60));
        p.setStrokeWidth(2f);
        c.drawLine(x, gy, x, gy - size * 1.6f, p);
        p.setStrokeWidth(1f);
        p.setColor(fc[colorIdx % fc.length]);
        for (int i = 0; i < 5; i++) {
            double a = i * Math.PI * 2 / 5;
            c.drawCircle(x + (float) Math.cos(a) * size * 0.55f,
                    gy - size * 1.6f + (float) Math.sin(a) * size * 0.55f, size * 0.42f, p);
        }
        p.setColor(Color.rgb(250, 200, 60));
        c.drawCircle(x, gy - size * 1.6f, size * 0.32f, p);
    }

    /** Dark soil crust + a thin grass edge so the ground line reads clearly. */
    private void drawSurfaceLine(Canvas c, World w, float camX, float camY, float vw, float vh) {
        float T = C.TILE;
        int x0 = Math.max(1, (int) Math.floor(camX) - 1);
        int x1 = Math.min(w.w - 1, (int) Math.ceil(camX + vw / T) + 2);
        path.reset();
        boolean first = true;
        for (int x = x0; x <= x1; x++) {
            float px = (x - camX) * T;
            float py = surfacePx(w, x) - camY * T;
            if (first) { path.moveTo(px, py); first = false; } else path.lineTo(px, py);
        }
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(4f);
        p.setColor(Color.argb(230, 36, 62, 34));
        c.drawPath(path, p);
        p.setStrokeWidth(2f);
        p.setColor(Color.argb(150, 150, 190, 120));
        c.drawPath(path, p);
        p.setStyle(Paint.Style.FILL);
        p.setStrokeWidth(1f);
    }

    // ------------------------------------------------------- underground
    private void drawUnderground(Canvas c, World w, float camX, float camY, float vw, float vh, float t) {
        float T = C.TILE;
        int x0 = Math.max(0, (int) Math.floor(camX) - 1);
        int x1 = Math.min(w.w, (int) Math.ceil(camX + vw / T) + 1);
        // Only underground rows are drawn here. Above the surface the world stores
        // EMPTY tiles (open sky); painting those would fill the sky band with the
        // dark tunnel colour and hide the scenery, so we clamp to GROUND_ROW.
        int y0 = Math.max(C.GROUND_ROW, (int) Math.floor(camY) - 1);
        int y1 = Math.min(w.h, (int) Math.ceil(camY + vh / T) + 1);

        for (int y = y0; y < y1; y++) {
            for (int x = x0; x < x1; x++) {
                int tile = w.get(x, y);
                float sx = (x - camX) * T, sy = (y - camY) * T;
                switch (tile) {
                    case C.EMPTY:
                        drawTunnel(c, sx, sy, x, y);
                        break;
                    case C.SOIL:
                        drawSoil(c, sx, sy, x, y);
                        break;
                    case C.ROCK:
                        drawRock(c, sx, sy, x, y);
                        break;
                    case C.DIAMOND:
                        drawDiamond(c, sx, sy, x, y, t);
                        break;
                    case C.WATER:
                        drawWater(c, sx, sy, x, y, t);
                        break;
                    case C.BEDROCK:
                        p.setColor(Color.rgb(24, 22, 26));
                        c.drawRect(sx, sy, sx + T, sy + T, p);
                        break;
                    case C.ROOT:
                        drawSoil(c, sx, sy, x, y);
                        drawRoot(c, sx, sy, w, x, y);
                        break;
                    case C.CARROT:
                        drawSoil(c, sx, sy, x, y);
                        drawCarrot(c, sx, sy);
                        break;
                    case C.POTATO:
                        drawSoil(c, sx, sy, x, y);
                        drawPotato(c, sx, sy);
                        break;
                    case C.BUG:
                        drawSoil(c, sx, sy, x, y);
                        drawBug(c, sx, sy, t);
                        break;
                    default:
                        break;
                }
            }
        }
    }

    private void drawTunnel(Canvas c, float sx, float sy, int tx, int ty) {
        // excavated area: dark cave air with soft lit edges
        p.setColor(Color.rgb(28, 20, 16));
        c.drawRect(sx, sy, sx + C.TILE, sy + C.TILE, p);
        int h1 = hash(tx, ty);
        p.setColor(Color.argb(70, 92, 62, 44));
        if ((h1 & 3) == 0) c.drawCircle(sx + 12, sy + 26, 3.5f, p);
        if ((h1 & 7) == 3) c.drawRect(sx + 22, sy + 8, sx + 30, sy + 13, p);
    }

    private void drawSoil(Canvas c, float sx, float sy, int tx, int ty) {
        int h1 = hash(tx, ty);
        int depth = Math.max(0, Math.min(6, (ty - C.GROUND_ROW) / 3));
        int base = mix(Color.rgb(126, 84, 52), Color.rgb(74, 48, 32), depth / 6f);
        p.setColor(base);
        c.drawRect(sx, sy, sx + C.TILE, sy + C.TILE, p);
        // speckles / pebbles so the earth reads as textured
        p.setColor(mix(base, Color.rgb(40, 26, 18), 0.55f));
        c.drawRect(sx + (h1 % 22) + 3, sy + ((h1 >> 4) % 22) + 3,
                sx + (h1 % 22) + 9, sy + ((h1 >> 4) % 22) + 9, p);
        p.setColor(mix(base, Color.rgb(206, 168, 122), 0.45f));
        c.drawRect(sx + ((h1 >> 8) % 20) + 6, sy + ((h1 >> 12) % 20) + 16,
                sx + ((h1 >> 8) % 20) + 10, sy + ((h1 >> 12) % 20) + 20, p);
        // subtle top highlight on the first soil row of a column
        if (ty >= C.GROUND_ROW && ty < C.GROUND_ROW + 2) {
            p.setColor(Color.argb(50, 240, 220, 180));
            c.drawRect(sx, sy, sx + C.TILE, sy + 3, p);
        }
    }

    private void drawRock(Canvas c, float sx, float sy, int tx, int ty) {
        int h1 = hash(tx, ty);
        p.setColor(Color.rgb(104, 104, 110));
        r.set(sx + 1, sy + 1, sx + C.TILE - 1, sy + C.TILE - 1);
        c.drawRoundRect(r, 8, 8, p);
        p.setColor(Color.rgb(140, 140, 148));
        c.drawRoundRect(sx + 4, sy + 4, sx + C.TILE - 8, sy + C.TILE - 12, 7, 7, p);
        p.setColor(Color.rgb(74, 74, 80));
        c.drawLine(sx + 8, sy + 26, sx + 20 + (h1 % 8), sy + 34, p);
        c.drawLine(sx + 26, sy + 8, sx + 32, sy + 18, p);
    }

    /** A glittering blue sphere set in a rock housing - the obstacle is round now. */
    private void drawDiamond(Canvas c, float sx, float sy, int tx, int ty, float t) {
        drawRock(c, sx, sy, tx, ty);
        float cx = sx + C.TILE / 2f, cy = sy + C.TILE / 2f;
        float rad = C.TILE * 0.34f + (float) Math.sin(t * 2.4f + (tx + ty)) * 1.2f;

        // soft shadow under the ball so it reads as a solid sphere
        p.setShader(null);
        p.setColor(Color.argb(90, 10, 10, 16));
        c.drawCircle(cx + 2f, cy + 3f, rad, p);

        // body: light from the upper left fading to deep blue at the edge
        p.setShader(new android.graphics.RadialGradient(
                cx - rad * 0.35f, cy - rad * 0.35f, rad * 1.6f,
                new int[]{Color.rgb(226, 252, 255), Color.rgb(120, 214, 240), Color.rgb(34, 96, 150)},
                new float[]{0f, 0.55f, 1f}, android.graphics.Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, rad, p);
        p.setShader(null);

        // rim and specular highlight
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(1.6f);
        p.setColor(Color.argb(150, 255, 255, 255));
        c.drawCircle(cx, cy, rad - 0.8f, p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.argb(230, 255, 255, 255));
        c.drawCircle(cx - rad * 0.38f, cy - rad * 0.42f, rad * 0.20f, p);
        p.setColor(Color.argb(150, 255, 255, 255));
        c.drawCircle(cx + rad * 0.30f, cy + rad * 0.40f, rad * 0.10f, p);
    }

    private void drawWater(Canvas c, float sx, float sy, int tx, int ty, float t) {
        p.setColor(Color.rgb(38, 92, 150));
        c.drawRect(sx, sy, sx + C.TILE, sy + C.TILE, p);
        p.setColor(Color.argb(150, 120, 190, 240));
        for (int i = 0; i < 2; i++) {
            float yy = sy + 12 + i * 14 + (float) Math.sin(t * 2f + tx + i) * 2f;
            path.reset();
            path.moveTo(sx + 3, yy);
            path.quadTo(sx + 13, yy - 5, sx + 22, yy);
            path.quadTo(sx + 31, yy + 5, sx + C.TILE - 3, yy);
            c.drawLine(sx + 3, yy, sx + C.TILE - 3, yy, p);
        }
    }

    private void drawRoot(Canvas c, float sx, float sy, World w, int tx, int ty) {
        float T = C.TILE;
        c.save();
        c.scale(ITEM_SCALE, ITEM_SCALE, sx + T * 0.5f, sy + T * 0.5f);
        p.setColor(Color.rgb(126, 88, 52));
        p.setStrokeWidth(4.5f);
        boolean upOpen = !C.isCollectible(w.get(tx, ty - 1)) && w.get(tx, ty - 1) != C.ROCK;
        c.drawLine(sx + 20, sy + (upOpen ? 0 : 6), sx + 20, sy + 40, p);
        p.setStrokeWidth(2.6f);
        c.drawLine(sx + 20, sy + 14, sx + 6, sy + 26, p);
        c.drawLine(sx + 20, sy + 24, sx + 34, sy + 34, p);
        p.setStrokeWidth(1f);
    
        c.restore();}

    private void drawCarrot(Canvas c, float sx, float sy) {
        float T = C.TILE;
        c.save();
        c.scale(ITEM_SCALE, ITEM_SCALE, sx + T * 0.5f, sy + T * 0.5f);
        // leafy top at the tile, orange body buried below
        p.setColor(Color.rgb(232, 122, 30));
        path.reset();
        path.moveTo(sx + 12, sy + 10);
        path.lineTo(sx + 28, sy + 10);
        path.lineTo(sx + 20, sy + 36);
        path.close();
        c.drawPath(path, p);
        p.setColor(Color.rgb(60, 140, 60));
        p.setStrokeWidth(3f);
        c.drawLine(sx + 20, sy + 10, sx + 12, sy + 1, p);
        c.drawLine(sx + 20, sy + 10, sx + 20, sy, p);
        c.drawLine(sx + 20, sy + 10, sx + 28, sy + 1, p);
        p.setStrokeWidth(1f);
    
        c.restore();}

    private void drawPotato(Canvas c, float sx, float sy) {
        float T = C.TILE;
        c.save();
        c.scale(ITEM_SCALE, ITEM_SCALE, sx + T * 0.5f, sy + T * 0.5f);
        p.setColor(Color.rgb(178, 132, 74));
        c.drawOval(sx + 7, sy + 13, sx + 33, sy + 30, p);
        p.setColor(Color.rgb(140, 100, 56));
        c.drawCircle(sx + 15, sy + 20, 2.2f, p);
        c.drawCircle(sx + 25, sy + 24, 2f, p);
        c.drawCircle(sx + 22, sy + 17, 1.6f, p);
    
        c.restore();}

    private void drawBug(Canvas c, float sx, float sy, float t) {
        float T = C.TILE;
        c.save();
        c.scale(ITEM_SCALE, ITEM_SCALE, sx + T * 0.5f, sy + T * 0.5f);
        float wig = (float) Math.sin(t * 8f + sx) * 2f;
        p.setColor(Color.rgb(60, 130, 70));
        c.drawCircle(sx + 20, sy + 22 + wig * 0.2f, 8f, p);
        p.setColor(Color.rgb(40, 96, 48));
        c.drawCircle(sx + 20, sy + 14 + wig * 0.4f, 5f, p);
        p.setColor(Color.rgb(30, 30, 30));
        p.setStrokeWidth(1.4f);
        c.drawLine(sx + 12, sy + 20, sx + 6, sy + 14 + wig, p);
        c.drawLine(sx + 28, sy + 20, sx + 34, sy + 14 - wig, p);
        c.drawLine(sx + 12, sy + 26, sx + 6, sy + 32 - wig, p);
        c.drawLine(sx + 28, sy + 26, sx + 34, sy + 32 + wig, p);
        p.setStrokeWidth(1f);
    
        c.restore();}

    // ----------------------------------------------------------- helpers
    private static int hash(int x, int y) {
        int h = x * 374761393 + y * 668265263;
        h = (h ^ (h >> 13)) * 1274126177;
        return Math.abs(h ^ (h >> 16));
    }

    private static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int rr = (int) (Color.red(a) * (1 - t) + Color.red(b) * t);
        int gg = (int) (Color.green(a) * (1 - t) + Color.green(b) * t);
        int bb = (int) (Color.blue(a) * (1 - t) + Color.blue(b) * t);
        return Color.rgb(rr, gg, bb);
    }
}

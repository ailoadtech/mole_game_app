package com.example.molegame;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Procedurally generated underground world: soil, rock fields, diamond veins,
 * water pockets, roots hanging from the surface and buried vegetables.
 */
public final class World {

    public final int w = C.W;
    public final int h = C.H;
    public final byte[] tiles = new byte[w * h];

    /** Per-column surface row (first solid ground row). */
    public final int[] surfaceRow = new int[w];

    /** Which scenery band a column belongs to: 0 forest, 1 park, 2 country. */
    public static final int SCENERY_FOREST = 0, SCENERY_PARK = 1, SCENERY_COUNTRY = 2;
    public final int[] band = new int[w];

    // Decor data produced at generation time (world tile coordinates)
    public int[][] trees = new int[0][];      // {x, heightTiles, kind}
    public int[][] flowers = new int[0][];    // {x, sizePx, colorIdx}
    public int[][] corn = new int[0][];       // {x, heightTiles}
    public int[][] buildings = new int[0][];  // {x, widthTiles, heightTiles, colorIdx}
    public int[][] clouds = new int[0][];     // {x, y, scale}
    public int[][] grassTufts = new int[0][]; // {x, kind}

    public World(long seed) {
        Random r = new Random(seed);
        generate(r);
    }

    public int get(int x, int y) {
        if (x < 0 || y < 0 || x >= w || y >= h) return C.BEDROCK;
        return tiles[y * w + x] & 0xFF;
    }

    public void set(int x, int y, int t) {
        if (x < 0 || y < 0 || x >= w || y >= h) return;
        tiles[y * w + x] = (byte) t;
    }

    public int bandAt(float xTile) {
        if (xTile < C.BAND_FOREST_END) return SCENERY_FOREST;
        if (xTile < C.BAND_PARK_END) return SCENERY_PARK;
        return SCENERY_COUNTRY;
    }

    // ------------------------------------------------------------------
    private void generate(Random r) {
        // 1. bands + rolling surface
        for (int x = 0; x < w; x++) {
            float wave = (float) Math.sin(x * 0.09f) * 0.9f + (float) Math.sin(x * 0.031f + 1.7f) * 1.6f;
            // Never shallower than GROUND_ROW + 2: the mole is clamped to GROUND_ROW and
            // below, so the whole sky band always stays out of reach.
            // Surface wobbles between GROUND_ROW+1 and GROUND_ROW+4, always below the
            // mole's ceiling row so he can never break through into the sky.
            int row = C.GROUND_ROW + 2 + Math.round(wave);
            surfaceRow[x] = Math.max(C.GROUND_ROW + 1, Math.min(C.GROUND_ROW + 4, row));
            band[x] = bandAt(x);
        }

        // 2. fill soil / sky
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                set(x, y, y < surfaceRow[x] ? C.EMPTY : C.SOIL);
            }
        }

        // 3. unbreakable frame around the world (left wall, sky top, floor)
        for (int y = 0; y < h; y++) { set(0, y, C.BEDROCK); set(w - 1, y, C.BEDROCK); }
        for (int x = 0; x < w; x++) { set(x, 0, C.BEDROCK); }
        for (int x = 0; x < w; x++) { set(x, h - 1, C.BEDROCK); }

        // 4. obstacle blobs: rock fields, diamond veins, water pockets.
        //    Kept small and shallow so a young player can always steer round them.
        placeBlobs(r, 8, 2, 3, 2, 8, C.ROCK, 10, w - 12);
        placeBlobs(r, 5, 1, 2, 4, 11, C.DIAMOND, 26, w - 24);
        placeBlobs(r, 3, 1, 2, 3, 10, C.WATER, 18, w - 16);

        // 5. a couple of rock seams to dig around - short, so they read as a puzzle
        addRockWall(r, 40, 3);
        addRockWall(r, 78, 3);

        // keep the start pocket and the finish hole clear
        protectAround((int) C.START_X, (int) C.START_Y, 3);
        protectAround(C.MOUSE_HOLE_X, C.MOUSE_HOLE_ROW, 4);

        // 5b. keep the approach to the mouse hole free of obstacles so simply
        //     holding RIGHT through the last stretch always reaches her
        clearFinishApproach();

        // 5c. guarantee the whole level is solvable
        ensureConnectivity(r);

        // 6. collectibles
        scatterFood(r);
        hangRoots(r);

        // 7. above-ground decoration
        buildScenery(r);
    }


    private void placeBlobs(Random r, int count, int minR, int maxR,
                            int minDepth, int maxDepth, int tile, int xFrom, int xTo) {
        for (int i = 0; i < count; i++) {
            int cx = xFrom + r.nextInt(Math.max(1, xTo - xFrom));
            int depth = minDepth + r.nextInt(Math.max(1, maxDepth - minDepth + 1));
            int cy = Math.min(h - 3, Math.max(C.GROUND_ROW + 3, surfaceRow[Math.min(w - 1, cx)] + depth));
            int rad = minR + r.nextInt(Math.max(1, maxR - minR + 1));
            for (int y = cy - rad; y <= cy + rad; y++) {
                for (int x = cx - rad; x <= cx + rad; x++) {
                    if (x <= 1 || x >= w - 2 || y <= 0 || y >= h - 2) continue;
                    double d = Math.hypot(x - cx, (y - cy) * 1.25);
                    if (d > rad + 0.35) continue;
                    if (get(x, y) != C.SOIL) continue;
                    set(x, y, tile);
                }
            }
        }
    }

    /**
     * A vertical rock seam the mole has to dig around. Deliberately placed deep
     * so there is always open soil above it near the surface.
     */
    private void addRockWall(Random r, int cx, int halfLen) {
        int mid = C.GROUND_ROW + 8 + r.nextInt(Math.max(1, C.ROWS / 3));
        int top = mid - halfLen;
        int bot = mid + halfLen;
        for (int y = top; y <= bot; y++) {
            for (int dx = 0; dx <= (r.nextFloat() < 0.35f ? 1 : 0); dx++) {
                int x = cx + dx;
                if (x > 1 && x < w - 2 && y > C.GROUND_ROW && y < h - 2 && get(x, y) == C.SOIL) {
                    set(x, y, C.ROCK);
                }
            }
        }
    }


    private static boolean isHard(int t) {
        return t == C.ROCK || t == C.DIAMOND || t == C.WATER || t == C.BEDROCK;
    }

    /** Remove unbreakable tiles in a box so the mole and its friend fit. */
    private void protectAround(int cx, int cy, int rad) {
        for (int y = cy - rad; y <= cy + rad; y++) {
            for (int x = cx - rad; x <= cx + rad; x++) {
                if (x < 1 || x >= w - 1 || y <= C.GROUND_ROW || y >= h - 2) continue;
                if (isHard(get(x, y))) set(x, y, C.SOIL);
            }
        }
    }

    private void scatterFood(Random r) {
        // Sparse enough that digging a level costs real energy: the player has to
        // steer onto the food instead of refilling automatically on the way past.
        // Depths are measured from the soil surface. The mole works a few rows down,
        // so food has to sit where he can reach it instead of buried far below.
        putRandom(r, 10, C.CARROT, 3, 7);
        putRandom(r, 8, C.POTATO, 4, 9);
        putRandom(r, 6, C.BUG, 3, 8);
    }

    /** Drops `n` collectibles into plain soil between the given depths. */
    private void putRandom(Random r, int n, int tile, int minDepth, int maxDepth) {
        int placed = 0, tries = 0;
        while (placed < n && tries < n * 400) {
            tries++;
            int x = 6 + r.nextInt(w - 12);
            int depth = minDepth + r.nextInt(Math.max(1, maxDepth - minDepth + 1));
            int y = surfaceRow[x] + depth;
            if (y > h - 3 || y < C.GROUND_ROW) continue;
            if (get(x, y) != C.SOIL) continue;
            set(x, y, tile);
            placed++;
        }
    }


    /** Roots dangle from the surface into the upper soil; moles love them. */
    private void hangRoots(Random r) {
        int x = 4;
        while (x < w - 6) {
            if (r.nextFloat() < 0.34f) {
                int len = 6 + r.nextInt(7);       // long enough to reach the dig band
                int top = surfaceRow[x];
                for (int i = 0; i < len; i++) {
                    int rx = x + (i > 1 && r.nextFloat() < 0.4f ? r.nextInt(2) : 0);
                    int ry = Math.max(C.GROUND_ROW, top) + i;   // only inside the diggable band
                    if (rx < 1 || rx >= w - 1 || ry > h - 3) break;
                    if (get(rx, ry) == C.SOIL) set(rx, ry, C.ROOT);
                    else if (!isHard(get(rx, ry))) continue;
                    else break;
                }
            }
            x += 2 + r.nextInt(3);
        }
    }


    /**
     * Soften the soil around the finish into plain diggable earth. The last stretch is
     * where a player who only presses RIGHT must succeed, so no rock, diamond or water
     * may sit there, and it spans the full depth of the underground.
     */
    private void clearFinishApproach() {
        for (int x = C.MOUSE_HOLE_X - 18; x <= w - 2; x++) {   // through to the right edge
            if (x < 1 || x >= w - 1) continue;
            for (int y = C.GROUND_ROW; y < h - 2; y++) {
                int t = get(x, y);
                if (t == C.ROCK || t == C.DIAMOND || t == C.WATER) set(x, y, C.SOIL);
            }
        }
    }

    // -------------------------------------------------------------- solvability
    /**
     * Dig a winding pilot tunnel from the start to the mouse hole, then make sure
     * every remaining diggable pocket is joined to it. Unbreakable tiles are only
     * ever removed where a connection is impossible otherwise, so rock fields and
     * diamond veins stay as obstacles but the level can never be unwinnable.
     */
    private void ensureConnectivity(Random r) {
        // 1. pilot route: gentle S-curve driven by Bresenham-ish segments
        int[] waypoints = {
                (int) C.START_X, (int) C.START_Y,
                28, 19, 50, 12, 70, 21, 92, 14, 112, 22, 132, 15,
                C.MOUSE_HOLE_X, C.MOUSE_HOLE_ROW
        };
        for (int i = 0; i + 3 < waypoints.length; i += 2) {
            carveLine(waypoints[i], waypoints[i + 1], waypoints[i + 2], waypoints[i + 3]);
        }

        // 2. join orphan soil pockets to the main network
        for (int pass = 0; pass < 6; pass++) {
            List<int[]> orphans = findOrphanSeeds();
            if (orphans.isEmpty()) break;
            for (int[] o : orphans) {
                int[] target = nearestOnNetwork(o[0], o[1]);
                if (target != null) carveLine(o[0], o[1], target[0], target[1]);
            }
        }

        // 3. re-validate: everything soft must be reachable
        boolean[] reach = reachable();
        for (int y = C.GROUND_ROW; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int t = get(x, y);
                if (isHard(t) || t == C.EMPTY) continue;
                if (!reach[y * w + x]) {
                    // last resort: drill straight up to open sky above this tile
                    int sx = x;
                    for (int yy = y; yy >= C.GROUND_ROW; yy--) {
                        if (isHard(get(sx, yy)) || get(sx, yy) == C.BEDROCK) set(sx, yy, C.SOIL);
                    }
                }
            }
        }
    }

    /** Soften a straight line into soil, removing hard tiles along the way. */
    private void carveLine(int x0, int y0, int x1, int y1) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        if (steps == 0) steps = 1;
        for (int s = 0; s <= steps; s++) {
            int x = Math.round(x0 + (x1 - x0) * (float) s / steps);
            int y = Math.round(y0 + (y1 - y0) * (float) s / steps);
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 0; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 1 || nx >= w - 1 || ny < C.GROUND_ROW || ny > h - 2) continue;
                    int t = get(nx, ny);
                    if (t == C.EMPTY) continue;                 // already open air
                    if (isHard(t) || C.isCollectible(t)) set(nx, ny, C.SOIL);
                    else set(nx, ny, C.SOIL);                   // keep as soil
                }
            }
        }
    }

    /** Seeds of diggable regions not connected to the start region. */
    private List<int[]> findOrphanSeeds() {
        boolean[] reach = reachable();
        List<int[]> out = new ArrayList<>();
        boolean[] done = new boolean[w * h];
        for (int y = C.GROUND_ROW; y < h - 1; y++) {
            for (int x = 1; x < w - 1; x++) {
                int i = y * w + x;
                int t = get(x, y);
                if (isHard(t) || reach[i] || done[i]) continue;
                // flood this soft region to mark it handled and remember its centre
                ArrayDeque<Integer> q = new ArrayDeque<>();
                q.add(i); done[i] = true;
                int minx = x, maxx = x, miny = y, maxy = y, n = 0;
                while (!q.isEmpty() && n < 4000) {
                    int c = q.poll(); n++;
                    int cx = c % w, cy = c / w;
                    minx = Math.min(minx, cx); maxx = Math.max(maxx, cx);
                    miny = Math.min(miny, cy); maxy = Math.max(maxy, cy);
                    int[] d = {1, -1, 0, 0}, e = {0, 0, 1, -1};
                    for (int k = 0; k < 4; k++) {
                        int ax = cx + d[k], by = cy + e[k];
                        if (ax < 1 || by < C.GROUND_ROW || ax >= w - 1 || by > h - 2) continue;
                        int j = by * w + ax;
                        if (done[j] || isHard(get(ax, by)) || get(ax, by) == C.EMPTY) continue;
                        done[j] = true; q.add(j);
                    }
                }
                if (n >= 4) out.add(new int[]{(minx + maxx) / 2, (miny + maxy) / 2});
            }
        }
        return out;
    }

    /** Closest reachable tile to (x,y), used to stitch pockets together. */
    private int[] nearestOnNetwork(int x, int y) {
        boolean[] reach = reachable();
        int best = -1, bd = Integer.MAX_VALUE;
        for (int rad = 2; rad <= 26 && best < 0; rad++) {
            for (int yy = y - rad; yy <= y + rad; yy++) {
                for (int xx = x - rad; xx <= x + rad; xx++) {
                    if (xx < 1 || yy < C.GROUND_ROW || xx >= w - 1 || yy > h - 2) continue;
                    if (!reach[yy * w + xx]) continue;
                    int d = Math.abs(xx - x) + Math.abs(yy - y);
                    if (d < bd) { bd = d; best = yy * w + xx; }
                }
            }
        }
        if (best < 0) return null;
        return new int[]{best % w, best / w};
    }

    /** Flood fill of all non-hard tiles reachable from the mole's start cell. */
    public boolean[] reachable() {
        boolean[] seen = new boolean[w * h];
        ArrayDeque<Integer> q = new ArrayDeque<>();
        int s = (int) C.START_Y * w + (int) C.START_X;
        if (isHard(get(s % w, s / w))) return seen;
        seen[s] = true; q.add(s);
        int[] dx = {1, -1, 0, 0}, dy = {0, 0, 1, -1};
        while (!q.isEmpty()) {
            int i = q.poll();
            int x = i % w, y = i / w;
            for (int k = 0; k < 4; k++) {
                int nx = x + dx[k], ny = y + dy[k];
                if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                int j = ny * w + nx;
                if (seen[j] || isHard(get(nx, ny))) continue;
                seen[j] = true; q.add(j);
            }
        }
        return seen;
    }

    // ------------------------------------------------------------------
    private void buildScenery(Random r) {
        java.util.List<int[]> tl = new java.util.ArrayList<>();
        java.util.List<int[]> fl = new java.util.ArrayList<>();
        java.util.List<int[]> co = new java.util.ArrayList<>();
        java.util.List<int[]> bu = new java.util.ArrayList<>();
        java.util.List<int[]> cl = new java.util.ArrayList<>();
        java.util.List<int[]> gr = new java.util.ArrayList<>();

        for (int x = 3; x < w - 3; ) {
            int b = band[x];
            int srow = surfaceRow[x];
            if (b == SCENERY_FOREST) {
                int kind = r.nextFloat() < 0.6f ? 0 : 1;             // pine / dark broadleaf
                int ht = 5 + r.nextInt(4);
                tl.add(new int[]{x, ht, kind});
                x += 2 + r.nextInt(2);
            } else if (b == SCENERY_PARK) {
                if (r.nextFloat() < 0.45f) {
                    tl.add(new int[]{x, 4 + r.nextInt(3), 2});        // bright park tree
                    x += 4 + r.nextInt(3);
                } else {
                    fl.add(new int[]{x, 6 + r.nextInt(5), r.nextInt(4)});
                    gr.add(new int[]{x, r.nextInt(2)});
                    x += 2 + r.nextInt(2);
                }
            } else {
                float f = r.nextFloat();
                if (f < 0.5f) {
                    co.add(new int[]{x, 5 + r.nextInt(3)});           // corn stalk
                    gr.add(new int[]{x, 1});
                    x += 1;
                } else if (f < 0.72f) {
                    fl.add(new int[]{x, 6 + r.nextInt(4), r.nextInt(4)});
                    gr.add(new int[]{x, 0});
                    x += 2 + r.nextInt(2);
                } else {
                    gr.add(new int[]{x, r.nextInt(3)});
                    x += 2 + r.nextInt(3);
                }
            }
        }

        // City skyline sits behind the park band, on the horizon.
        for (int bx = (int) C.BAND_FOREST_END - 6; bx < C.BAND_PARK_END + 10; ) {
            int bw = 4 + r.nextInt(5);
            int bh = 5 + r.nextInt(9);
            bu.add(new int[]{bx, bw, bh, r.nextInt(3)});
            bx += bw + 1 + r.nextInt(2);
        }

        for (int i = 0; i < 26; i++) {
            cl.add(new int[]{r.nextInt(w), 1 + r.nextInt(4), 8 + r.nextInt(10)});
        }

        trees = tl.toArray(new int[0][]);
        flowers = fl.toArray(new int[0][]);
        corn = co.toArray(new int[0][]);
        buildings = bu.toArray(new int[0][]);
        clouds = cl.toArray(new int[0][]);
        grassTufts = gr.toArray(new int[0][]);
    }
}

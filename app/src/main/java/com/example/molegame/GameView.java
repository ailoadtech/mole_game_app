package com.example.molegame;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * Game surface: fixed-timestep update loop, tile collision for the mole,
 * power drain/refill, camera follow and rendering.
 */
public class GameView extends SurfaceView implements SurfaceHolder.Callback, Runnable {

    public interface InputSource {
        /** -1..1 on each axis, from the four round buttons. */
        float axisX();
        float axisY();
    }

    private Thread thread;
    private volatile boolean running = false;


    private World world;
    private Renderer renderer;
    private Mole mole;
    private Hud hud;
    private MouseFriend mouse = new MouseFriend();

    private InputSource input;

    // camera in tile units (top-left)
    private float camX, camY;
    private float viewWpx = 800, viewHpx = 450;

    // state machine
    /** Playing, party at the finish, or a short snack nap when the bar empties. */
    public static final int ST_PLAY = 0, ST_WIN = 1, ST_REST = 3;
    private int state = ST_PLAY;
    private float winTimer = 0f;
    private float restTimer = 0f;
    /** Seconds of 'nap' before the mole is fully charged again. */
    private static final float REST_SECONDS = 2.6f;
    private long seed = 1234L;

    private boolean paused = false;

    /** Sound on/off, driven by the speaker button in the top-left corner. */
    public interface SoundListener { void onSoundToggled(boolean enabled); }
    private SoundListener soundListener;
    private boolean soundEnabled = true;

    public void setSoundListener(SoundListener l) { this.soundListener = l; }
    public boolean isSoundEnabled() { return soundEnabled; }

    /** Toggle background sound. Returns the new enabled state. */
    public boolean toggleSound() {
        soundEnabled = !soundEnabled;
        if (soundListener != null) soundListener.onSoundToggled(soundEnabled);
        return soundEnabled;
    }

    public GameView(Context ctx) { this(ctx, null); }
    public GameView(Context ctx, AttributeSet a) {
        super(ctx, a);
        getHolder().addCallback(this);
        setFocusable(true);
        renderer = new Renderer();
        hud = new Hud();
        newLevel(System.currentTimeMillis() % 100000);
    }

    public void setInputSource(InputSource s) { this.input = s; }
    public void setPaused(boolean b) {
        if (paused == b) return;
        paused = b;
        if (pauseListener != null) pauseListener.onPaused(b);
    }

    // ------------------------------------------------------------- lifecycle
    @Override public void surfaceCreated(SurfaceHolder h) {
        viewWpx = getWidth(); viewHpx = getHeight();
        start();
    }
    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) {
        viewWpx = w; viewHpx = hh;     }
    @Override public void surfaceDestroyed(SurfaceHolder h) { stop(); }

    private void start() {
        if (running) return;
        running = true;
        thread = new Thread(this, "molegame-loop");
        thread.start();
    }
    private void stop() {
        running = false;
                try { if (thread != null) thread.join(600); } catch (InterruptedException ignored) {}
        thread = null;
    }

    @Override public void run() {
        final long frameNs = 1000L * 1000L / 60L;
        long next = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            if (now < next) {
                synchronized (this) {
                    try { wait(Math.max(1, (next - now) / 1_000_000L)); }
                    catch (InterruptedException e) { break; }
                }
            }
            if (!running) break;
            Canvas c = null;
            try {
                if (getHolder().getSurface().isValid()) {
                    c = getHolder().lockCanvas();
                    if (c != null) { render(c); }
                }
            } catch (Exception ignored) {
            } finally {
                if (c != null) {
                    try { getHolder().unlockCanvasAndPost(c); } catch (Exception ignored) {}
                }
            }
            next += frameNs;
            if (next < System.nanoTime() - frameNs * 4) next = System.nanoTime();
        }
    }

    // ------------------------------------------------------------- game logic
    private void newLevel(long s) {
        seed = s;
        world = new World(s);
        mole = new Mole();
        state = ST_PLAY;
        winTimer = 0f;
        centerCamera();
            }

    private void centerCamera() {
        camX = mole.x - viewWpx / (float) C.TILE * 0.42f;
        camY = desiredCamY();
        clampCamera();
    }

    /**
     * Camera height that keeps the scenery above ground in view. The mole usually
     * digs several rows below the surface, so a camera centred on him would show only
     * earth; we pull it up until the top of the sky band is visible and let him sit in
     * the lower half of the screen instead.
     */
    private float desiredCamY() {
        float vhTiles = viewHpx / (float) C.TILE;
        // Follow the mole but never scroll past the bottom of the world.
        float maxTop = Math.max(0f, world.h - vhTiles);
        float follow = Math.min(mole.y - vhTiles * 0.68f, maxTop);
        // And never sit so low that the surface and the trees above it disappear.
        float groundLine = Math.min(maxTop, C.GROUND_ROW - 1.2f);
        return Math.max(Math.min(follow, groundLine), 0f);
    }

    private void clampCamera() {
        float vwTiles = viewWpx / (float) C.TILE;
        float vhTiles = viewHpx / (float) C.TILE;
        camX = Math.max(0, Math.min(world.w - vwTiles, camX));
        camY = Math.max(0, Math.min(world.h - vhTiles, camY));
    }

    private void update(float dt) {
        if (paused) return;
        if (state == ST_WIN) { winTimer += dt; return; }
        if (state == ST_REST) {
            restTimer += dt;
            // recover quickly, then carry on playing - no restart needed
            mole.power = Math.min(C.MAX_POWER, mole.power + C.MAX_POWER * dt / REST_SECONDS);
            if (mole.power >= C.MAX_POWER * 0.995f) { mole.power = C.MAX_POWER; state = ST_PLAY; }
            return;
        }

        float ax = 0, ay = 0;
        if (input != null) { ax = input.axisX(); ay = input.axisY(); }
        if (Math.abs(ax) < 0.15f) ax = 0;
        if (Math.abs(ay) < 0.15f) ay = 0;

        if ((ax != 0 || ay != 0) && mole.power > 0f) {
            // Pick a dominant axis. If the current heading is walled off, fall back
            // to whichever perpendicular direction is open so holding one button
            // never leaves the mole stuck against a rock.
            int wantX = ax != 0 ? (int) Math.signum(ax) : 0;
            int wantY = ay != 0 ? (int) Math.signum(ay) : 0;
            int dir = chooseDirection(wantX, wantY);

            if (dir != 0) {
                mole.dirX = dir == DIR_R ? 1 : dir == DIR_L ? -1 : 0;
                mole.dirY = dir == DIR_D ? 1 : dir == DIR_U ? -1 : 0;
            } else if (wantX != 0 && wantY == 0) {
                // Pushing against an obstacle: face that way so it is obvious he is stuck.
                mole.dirX = wantX; mole.dirY = 0;
            }

            boolean moved = stepAlongHeading(dt);
            mole.moving = moved;
            if (moved) {
                mole.animTime += dt;
                digCurrentCell();
                mole.power = Math.max(0f, mole.power - C.MOVE_COST_IDLE * dt);
            } else if (dir != 0) {
                // pressed into a wall: gnaw at it visually but pay nothing extra
                mole.animTime += dt * 0.5f;
            }
        } else {
            mole.moving = false;
        }

        checkFinish();

        // smooth camera follow, biased so the mole sees more of what it digs into
        float vwTiles = viewWpx / (float) C.TILE;
        float vhTiles = viewHpx / (float) C.TILE;
        float targetX = mole.x - vwTiles * 0.42f;
        float targetY = desiredCamY();
        float k = 1f - (float) Math.pow(0.0025, dt);
        camX += (targetX - camX) * k;
        camY += (targetY - camY) * k;
        clampCamera();

        if (mole.power <= 0f && state == ST_PLAY) {
            // A child should not be punished with a game over. The mole simply gets
            // tired, rests for a moment and is topped back up to keep digging.
            state = ST_REST;
            restTimer = 0f;
        }
    }

    /** True for tiles the mole cannot dig: rock, diamond, water and the frame. */
    private boolean hardAt(int x, int y) {
        int t = world.get(x, y);
        return t == C.ROCK || t == C.DIAMOND || t == C.WATER || t == C.BEDROCK;
    }

    /** Heading ids shared with {@link Mole}. */
    public static final int DIR_R = 1, DIR_L = 2, DIR_U = 3, DIR_D = 4;

    /**
     * Every button maps to exactly one direction and nothing else. If an obstacle is
     * in the way the mole halts there; the player decides how to dig around it. Both
     * axes are handled the same way, so there is no hidden auto-steering anywhere.
     */
    private int chooseDirection(int wantX, int wantY) {
        // One button, one direction. The mole only stops when something unbreakable is
        // directly in front of him; digging around it is left entirely to the player.
        int d;
        if (wantY != 0) {
            d = wantY > 0 ? DIR_D : DIR_U;
        } else if (wantX != 0) {
            d = wantX > 0 ? DIR_R : DIR_L;
        } else {
            return 0;
        }
        return canTravel(d) ? d : 0;
    }


    /** Unit vectors for a DIR_* heading id. */
    static int dxOf(int d) { return d == DIR_R ? 1 : d == DIR_L ? -1 : 0; }
    static int dyOf(int d) { return d == DIR_D ? 1 : d == DIR_U ? -1 : 0; }




    /** True when the next cell in direction d is not an unbreakable obstacle. */
    private boolean canTravel(int d) {
        return canTravelFrom(mole.x, mole.y, d);
    }

    /**
     * Can a mole standing at (fx,fy) make progress in direction d? We test the cell
     * it would actually reach after a full tile of travel, not merely the next one:
     * that avoids the classic corner deadlock where moving up looks free but leaves
     * the mole unable to turn right again.
     */
    private boolean canTravelFrom(float fx, float fy, int d) {
        int cx = Math.round(fx), cy = Math.round(fy);
        int nx = cx + dxOf(d), ny = cy + dyOf(d);
        if (!undergroundCell(nx, ny)) return false;           // never break the surface
        return !hardAt(nx, ny);
    }

    /**
     * Cells the mole may occupy. Everything at or below GROUND_ROW is earth; the sky
     * above it belongs to the scenery and is off limits, so the mole can never dig
     * his way out into the open.
     */
    private boolean undergroundCell(int x, int y) {
        return x >= 1 && x <= world.w - 2 && y >= C.GROUND_ROW && y <= world.h - 2;
    }

    /** Move along the current heading and glide onto the tunnel centre line. */
    private boolean stepAlongHeading(float dt) {
        float step = C.SPEED * dt;
        float nx = mole.x + mole.dirX * step;
        float ny = mole.y + mole.dirY * step;
        if (!canTravel(mole.heading())) return false;

        if (mole.dirX != 0) {
            mole.y += (Math.round(mole.y) - mole.y) * 0.35f;   // centre on the row
            mole.x = nx;
        } else {
            mole.x += (Math.round(mole.x) - mole.x) * 0.35f;   // centre on the column
            mole.y = ny;
        }
        return true;
    }

    /** Dig the cell under the nose plus the cell the body sits in: one cost each. */
    private void digCurrentCell() {
        int bx = Math.round(mole.x), by = Math.round(mole.y);
        digTile(bx, by);
        int tx = bx + mole.dirX, ty = by + mole.dirY;
        if (!(tx == bx && ty == by)) digTile(tx, ty);
    }

    private void digTile(int x, int y) {
        int t = world.get(x, y);
        if (!C.isDiggable(t)) return;
        collect(x, y, t);
        world.set(x, y, C.EMPTY);
        mole.dugTiles++;
        mole.power = Math.max(0f, mole.power - C.DIG_COST);
    }

    private void collect(int x, int y, int t) {
        if (t == C.DIAMOND) { mole.diamonds++; return; }
        mole.food++;
        mole.power = Math.min(C.MAX_POWER, mole.power + C.foodValue(t));
    }

    /**
     * The reunion trigger is deliberately forgiving: reaching any tile of her hole
     * counts, so a player who stops slightly early still wins.
     */
    private void checkFinish() {
        float dx = mole.x - MouseFriend.X, dy = mole.y - MouseFriend.Y;
        if (Math.abs(dx) <= 1.6f && Math.abs(dy) <= 1.3f) { state = ST_WIN; winTimer = 0f; }
    }

    // ------------------------------------------------------------ rendering
    private void render(Canvas c) {
        long now = System.nanoTime();
        float dt = lastRender == 0 ? 1 / 60f : Math.min(0.05f, (now - lastRender) / 1e9f);
        lastRender = now;
        if (!paused) timeSec += dt;                        // freeze animations while paused

        update(dt);

        c.drawColor(Color.rgb(18, 14, 12));
        renderer.draw(c, world, camX, camY, viewWpx, viewHpx, timeSec, state == ST_WIN);
        mouse.draw(c, camX, camY, timeSec, state == ST_WIN);
        mole.draw(c, camX, camY);
        hud.draw(c, mole.power / C.MAX_POWER, mole.power < 25f && state == ST_PLAY, timeSec);
        hud.drawSoundButton(c, soundEnabled);

        if (paused && state != ST_WIN) {
            hud.banner(c, "PAUSED", "Tap the screen to continue", 1f);
        } else if (state == ST_WIN) drawWinOverlay(c);
        else if (state == ST_REST) hud.banner(c, "Zzz... the mole takes a snack break",
                "He wakes up strong again in a second!", Math.min(1f, restTimer * 2.5f));
        else drawHint(c);
    }

    private float timeSec = 0f;
    private long lastRender = 0;

    private final android.graphics.Paint op = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);

    /**
     * Friendly signposting for young players: a big arrow near the right edge
     * points to the mouse, with an invitation to dig that way.
     */
    /**
     * A big blinking chevron at the right edge pointing to the mouse. No words - the
     * arrow alone shows a young player which way to keep digging.
     */
    private void drawHint(Canvas c) {
        float distTiles = MouseFriend.X - mole.x;
        if (distTiles <= 0f || timeSec < 3f) return;       // hidden during the intro text
        float T = C.TILE;
        float sy = (MouseFriend.Y - camY) * T;
        float margin = 18f * Math.max(1f, viewWpx / 900f);
        sy = Math.max(margin + sizeHint(), Math.min(viewHpx - margin - sizeHint(), sy));

        // strong blink between dim and bright so it reads against any background
        float blink = 0.45f + 0.55f * (float) Math.max(0f, Math.sin(timeSec * 4.2f));
        float s = sizeHint() * (0.92f + 0.08f * blink);
        float x = viewWpx - margin - s;

        // dark halo first, then the bright arrow, so it stays visible over soil
        op.setColor(Color.argb((int) (150 * blink), 20, 14, 10));
        drawChevron(c, x, sy, s * 1.35f);
        op.setColor(Color.argb((int) (200 + 55 * blink), 255, 226, 120));
        drawChevron(c, x, sy, s);
    }

    /** Arrow size scales with the screen so it is unmistakably large. */
    private float sizeHint() {
        return Math.max(18f, Math.min(viewWpx, viewHpx) * 0.055f);
    }

    private void drawChevron(Canvas c, float tipX, float cy, float s) {
        android.graphics.Path pth = new android.graphics.Path();
        pth.moveTo(tipX - s, cy - s * 0.85f);
        pth.lineTo(tipX + s * 0.45f, cy);
        pth.lineTo(tipX - s, cy + s * 0.85f);
        pth.lineTo(tipX - s * 0.45f, cy);
        pth.close();
        c.drawPath(pth, op);
    }

    private void drawWinOverlay(Canvas c) {
        float a = Math.min(1f, winTimer * 1.4f);
        hud.banner(c, "PARTY TIME!",
                "The mole reached his friend.  Tap the screen to dig again.", a);
        confetti(c, a);
    }


    private final java.util.Random cfRnd = new java.util.Random(7);
    private void confetti(Canvas c, float a) {
        int n = 70;
        for (int i = 0; i < n; i++) {
            float base = cfRnd.nextFloat();
            float speed = 60 + cfRnd.nextFloat() * 160;
            float x = cfRnd.nextFloat() * viewWpx;
            float y = ((timeSec * speed + base * viewHpx) % (viewHpx + 40)) - 20;
            int col = Color.HSVToColor(new float[]{(i * 37 % 360), 0.7f, 0.95f});
            op.setColor(Color.argb((int) (230 * a), Color.red(col), Color.green(col), Color.blue(col)));
            float s = 4 + (i % 4) * 2;
            c.save();
            c.rotate((timeSec * 2 + i) % 6.28f, x, y);
            c.drawRect(x, y, x + s, y + s * 0.6f, op);
            c.restore();
        }
    }

    // ---------------------------------------------------------------- input
    /**
     * Tapping the play area toggles pause. After the party it starts a fresh level
     * instead, so the same gesture always means "go on".
     */
    /** Fraction of the screen height reserved for the steering buttons at the bottom. */
    private static final float PAD_FRACTION = 0.34f;

    /** Fraction of screen width/height used by the sound button hit area. */
    private static final float SOUND_BTN_W = 0.13f;
    private static final float SOUND_BTN_H = 0.085f;

    /** True when a tap at (x,y) landed on the sound button drawn in the top-left. */
    public boolean soundButtonHit(float x, float y) {
        return x <= getWidth() * SOUND_BTN_W && y <= getHeight() * SOUND_BTN_H;
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
            // Taps over the button strip are steering, not pausing.
            if (e.getY() > getHeight() * (1f - PAD_FRACTION)) return true;
            // Top-left speaker button: toggles sound instead of pausing.
            if (soundButtonHit(e.getX(), e.getY())) {
                toggleSound();
                performClick();
                return true;
            }
            if (state == ST_WIN && winTimer > 0.45f) {
                newLevel(seed + 1);
            } else {
                togglePause();
            }
            performClick();
        }
        return true;
    }

    /** Notified whenever the player pauses or resumes by tapping the screen. */
    public interface PauseListener { void onPaused(boolean paused); }

    private PauseListener pauseListener;

    public void setPauseListener(PauseListener l) { this.pauseListener = l; }

    /** Pause / resume. While paused the game freezes, PAUSED is shown and music stops. */
    public void togglePause() {
        setPaused(!paused);
    }

    public boolean isPaused() { return paused; }

    @Override public boolean performClick() { return super.performClick(); }

    /** Restart with a brand new random level (used by the menu button). */
    public void restart() { newLevel(System.currentTimeMillis() % 1000000L); lastRender = 0; }

    /** Reset the current level keeping the same layout. */
    public void resetLevel() { newLevel(seed); }
}

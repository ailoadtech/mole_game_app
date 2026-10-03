package com.example.molegame;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * Single-activity entry point: full-screen game surface plus the round steering
 * buttons overlaid at the bottom of the screen.
 */
public class MainActivity extends Activity implements GameView.InputSource {

    private GameView game;
    private PadView pad;
    private Music music;
    private boolean userPaused = false;      // the player tapped to pause himself

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(android.graphics.Color.rgb(18, 14, 12));

        music = new Music(this);

        game = new GameView(this);
        game.setInputSource(this);
        // Tapping the play area pauses the game, so the music has to stop with it.
        game.setPauseListener(paused -> {
            userPaused = paused;
            if (paused) music.pause(); else music.play();
        });
        // Speaker button in the top-left corner mutes / unmutes the tune.
        game.setSoundListener(enabled -> music.setMuted(!enabled));
        root.addView(game, match());

        pad = new PadView(this);
        // The steering cluster owns a good slice of the bottom of the screen so the
        // round buttons are easy to see and easy to hit.
        int padH = Math.min((int) (getResources().getDisplayMetrics().heightPixels * 0.34f), dp(230));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, padH);
        lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        root.addView(pad, lp);

        setContentView(root);
        hideChrome();
    }

    private FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
    }

    private int dp(float v) { return Math.round(getResources().getDisplayMetrics().density * v); }

    private void hideChrome() {
        View decor = getWindow().getDecorView();
        int f = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN;
        if (Build.VERSION.SDK_INT >= 19) f |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        decor.setSystemUiVisibility(f);
    }

    @Override public void onWindowFocusChanged(boolean has) {
        super.onWindowFocusChanged(has);
        if (has) hideChrome();
    }

    // ---- steering from the round buttons -------------------------------
    @Override public float axisX() { return pad == null ? 0 : pad.axisX(); }
    @Override public float axisY() { return pad == null ? 0 : pad.axisY(); }

    @Override protected void onResume() {
        super.onResume();
        // Stay paused if the player had paused the game himself before leaving.
        game.setPaused(userPaused);
        if (!userPaused) music.play();
        music.setMuted(!game.isSoundEnabled());
        hideChrome();
    }

    @Override protected void onPause() {
        super.onPause();
        game.setPaused(true);
        if (music != null) music.pause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (music != null) music.release();
    }

}

package com.example.molegame;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;

/**
 * Carefree looping background tune. Kept deliberately simple: one MediaPlayer in
 * loop mode, started on resume and stopped when the activity leaves the screen, so
 * no other app's audio is left fighting with it.
 */
public class Music {

    private MediaPlayer mp;
    private boolean wantPlaying = false;
    private boolean muted = false;
    private final Context ctx;

    public Music(Context ctx) { this.ctx = ctx.getApplicationContext(); }

    /** Start (or resume) the loop. Safe to call repeatedly. */
    public void play() {
        wantPlaying = true;
        if (mp == null) {
            mp = new MediaPlayer();
            try {
                android.content.res.Resources r = ctx.getResources();
                int id = r.getIdentifier("music", "raw", ctx.getPackageName());
                if (id == 0) return;                       // no asset bundled: stay silent
                android.content.res.AssetFileDescriptor fd = r.openRawResourceFd(id);
                mp.setDataSource(fd.getFileDescriptor(), fd.getStartOffset(), fd.getLength());
                fd.close();
                mp.setLooping(true);
                mp.setVolume(muted ? 0f : 0.5f, muted ? 0f : 0.5f);
                if (Build.VERSION.SDK_INT >= 21) {
                    mp.setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build());
                }
                mp.prepare();
            } catch (Exception e) {
                release();
                return;                                     // silent failure beats a crash
            }
        }
        try { if (!mp.isPlaying()) mp.start(); } catch (Exception ignored) {}
    }

    /** Stop the music (activity gone, or the player paused the game). */
    public void pause() {
        try { if (mp != null && mp.isPlaying()) mp.pause(); } catch (Exception ignored) {}
    }

    /** Resume only if the game is actually being played again. */
    public void resumeIfWanted() {
        if (wantPlaying) play();
    }

    /** Forget the stored wish - used when leaving the activity entirely. */
    public void stopAll() {
        wantPlaying = false;
        pause();
    }

    /** Mute/unmute without losing the position, used by the sound toggle. */
    public void setMuted(boolean m) {
        muted = m;
        try { if (mp != null) { float v = muted ? 0f : 0.5f; mp.setVolume(v, v); } }
        catch (Exception ignored) {}
    }

    public boolean isMuted() { return muted; }

    public void release() {
        if (mp != null) {
            try { mp.stop(); } catch (Exception ignored) {}
            try { mp.release(); } catch (Exception ignored) {}
            mp = null;
        }
    }

    public boolean isWanted() { return wantPlaying; }
}

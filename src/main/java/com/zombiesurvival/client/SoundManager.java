package com.zombiesurvival.client;

import javax.sound.sampled.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Synthesized sound effects using Java Sound API.
 * All calls are non-blocking (dispatched to a daemon thread pool).
 */
public class SoundManager {

    private static volatile boolean enabled = true;

    private static final ExecutorService pool = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "SoundWorker");
        t.setDaemon(true);
        return t;
    });

    public static void setEnabled(boolean v) { enabled = v; }
    public static boolean isEnabled()        { return enabled; }

    // ── Public sound triggers ─────────────────────────────────────────────────

    /** Dull thud — zombie drains HP */
    public static void playHit() {
        pool.submit(() -> playTone(210, 110, 0.22f));
    }

    /** Chime — medkit collected */
    public static void playMedkit() {
        pool.submit(() -> {
            playTone(523, 75, 0.17f);
            sleep(70);
            playTone(659, 90, 0.17f);
        });
    }

    /** Glint — key / armor / speed boost collected */
    public static void playPickup() {
        pool.submit(() -> {
            playTone(660, 70, 0.16f);
            sleep(65);
            playTone(784, 80, 0.16f);
        });
    }

    /** Descending growl — player infected */
    public static void playInfected() {
        pool.submit(() -> {
            for (int f = 480; f > 110; f -= 45) {
                playTone(f, 45, 0.28f);
                sleep(40);
            }
        });
    }

    /** Rising fanfare — game starts */
    public static void playGameStart() {
        pool.submit(() -> {
            int[] fs = {330, 392, 494, 392, 523};
            for (int f : fs) { playTone(f, 95, 0.2f); sleep(88); }
        });
    }

    /** Victory fanfare / defeat chord */
    public static void playGameOver(boolean survivorsWin) {
        pool.submit(() -> {
            if (survivorsWin) {
                int[] fs = {392, 494, 587, 784};
                for (int f : fs) { playTone(f, 140, 0.22f); sleep(125); }
            } else {
                int[] fs = {300, 250, 200, 145};
                for (int f : fs) { playTone(f, 190, 0.28f); sleep(175); }
            }
        });
    }

    /** Soft tick — second countdown */
    public static void playTick() {
        pool.submit(() -> playTone(880, 55, 0.10f));
    }

    /** Double-pulse warning — 30 s remaining */
    public static void playWarning() {
        pool.submit(() -> {
            playTone(440, 130, 0.22f);
            sleep(90);
            playTone(440, 130, 0.22f);
        });
    }

    // ── Synthesizer core ──────────────────────────────────────────────────────

    /**
     * Generates and plays a mono 16-bit sine tone with attack/release envelope.
     * Silently swallowed if the sound system is unavailable.
     */
    private static void playTone(float freq, int durationMs, float volume) {
        if (!enabled) return;
        try {
            int rate    = 44100;
            int samples = rate * durationMs / 1000;
            byte[] buf  = new byte[samples * 2];

            for (int i = 0; i < samples; i++) {
                double angle = 2.0 * Math.PI * freq * i / rate;
                // Slight harmonics for a warmer sound
                double wave = Math.sin(angle) * 0.60
                            + Math.sin(angle * 2) * 0.25
                            + Math.sin(angle * 3) * 0.15;

                // ADSR-lite: 8 ms attack, last 30 % release
                float env = 1.0f;
                int attack = rate * 8 / 1000;
                if (i < attack) env = (float) i / attack;
                if (i > samples * 0.70f) env = (float)(samples - i) / (samples * 0.30f);

                short s = (short)(wave * 32767 * volume * env);
                buf[i*2]     = (byte)(s & 0xFF);
                buf[i*2 + 1] = (byte)((s >> 8) & 0xFF);
            }

            AudioFormat    fmt  = new AudioFormat(rate, 16, 1, true, false);
            DataLine.Info  info = new DataLine.Info(SourceDataLine.class, fmt);
            SourceDataLine line = (SourceDataLine) AudioSystem.getLine(info);
            line.open(fmt, buf.length);
            line.start();
            line.write(buf, 0, buf.length);
            line.drain();
            line.close();
        } catch (Exception ignored) {}
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}

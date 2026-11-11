package ru.ramauto;

import android.app.Application;
import android.content.ContextWrapper;
import android.media.audiofx.LoudnessEnhancer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Keeps a global (session 0 = output mix) LoudnessEnhancer enabled, the same
 * thing volume-booster apps do, but started at boot by a Magisk module.
 *
 * Run as root:  CLASSPATH=keeper.dex app_process /system/bin ru.ramauto.VolumeKeeper <conf> <log>
 * conf file: one integer = gain in dB (0 = off). Re-read every 5 s.
 */
public final class VolumeKeeper {
    static final int MAX_DB = 15;
    static final long PERIOD_MS = 5000;

    static String confPath = "/data/adb/ramauto_volume/gain_db";
    static String logPath = "/data/adb/ramauto_volume/keeper.log";

    /** Abstraction so the control loop can be tested off-device. */
    interface Effect {
        void setGainMb(int mb) throws Throwable;
        boolean healthy() throws Throwable;
        void release();
    }

    interface EffectFactory {
        Effect create() throws Throwable;
    }

    static final class RealEffect implements Effect {
        private final LoudnessEnhancer le;
        RealEffect() {
            le = new LoudnessEnhancer(0, 0); // priority 0, session 0 = global mix
        }
        public void setGainMb(int mb) {
            le.setTargetGain(mb);
            le.setEnabled(true);
        }
        public boolean healthy() {
            return le.hasControl() && le.getEnabled();
        }
        public void release() {
            try { le.release(); } catch (Throwable ignored) { }
        }
    }

    /** Fake op package name so AudioEffect works outside an app process. */
    static final class FakeContext extends ContextWrapper {
        FakeContext() { super(null); }
        @Override public String getPackageName() { return "com.android.shell"; }
        @Override public String getOpPackageName() { return "com.android.shell"; }
    }

    static void prepareRuntime() {
        try {
            // ActivityThread() creates a Handler, so a main Looper must exist first
            try { android.os.Looper.prepareMainLooper(); } catch (Throwable t) { log("looper: " + t); }
            Class<?> at = Class.forName("android.app.ActivityThread");
            Constructor<?> c = at.getDeclaredConstructor();
            c.setAccessible(true);
            Object thread = c.newInstance();
            Field cur = at.getDeclaredField("sCurrentActivityThread");
            cur.setAccessible(true);
            cur.set(null, thread);

            Application app = new Application();
            Field base = ContextWrapper.class.getDeclaredField("mBase");
            base.setAccessible(true);
            base.set(app, new FakeContext());
            Field init = at.getDeclaredField("mInitialApplication");
            init.setAccessible(true);
            init.set(thread, app);
            log("runtime prepared");
        } catch (Throwable t) {
            Throwable c = t.getCause() != null ? t.getCause() : t;
            log("runtime prepare failed (continuing): " + t + " cause: " + c);
        }
    }

    static int readGainDb(String path, int fallback) {
        BufferedReader r = null;
        try {
            File f = new File(path);
            if (!f.exists()) return fallback;
            r = new BufferedReader(new FileReader(f));
            String line = r.readLine();
            if (line == null) return fallback;
            line = line.trim();
            if (line.isEmpty()) return fallback;
            int v = Integer.parseInt(line);
            if (v < 0) v = 0;
            if (v > MAX_DB) v = MAX_DB;
            return v;
        } catch (Throwable t) {
            return fallback;
        } finally {
            if (r != null) try { r.close(); } catch (Throwable ignored) { }
        }
    }

    static synchronized void log(String msg) {
        try {
            File f = new File(logPath);
            if (f.length() > 64 * 1024) f.delete();
            FileWriter w = new FileWriter(f, true);
            w.write(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " " + msg + "\n");
            w.close();
        } catch (Throwable ignored) { }
    }

    /** Current state of the control loop (package-private for tests). */
    static Effect effect;
    static int appliedDb = -1;
    static int failures;

    /** One iteration; returns true if an effect is active afterwards. */
    static boolean step(EffectFactory factory, int wantDb) {
        if (wantDb <= 0) {
            if (effect != null) {
                effect.release();
                effect = null;
                appliedDb = -1;
                log("gain 0 dB: boost off");
            }
            return false;
        }
        if (effect != null) {
            boolean ok;
            try { ok = effect.healthy(); } catch (Throwable t) { ok = false; }
            if (!ok) {
                log("effect lost control or disabled - recreating");
                effect.release();
                effect = null;
                appliedDb = -1;
            }
        }
        if (effect == null) {
            try {
                if (failures == 0) log("creating LoudnessEnhancer on session 0");
                effect = factory.create();
            } catch (Throwable t) {
                failures++;
                if (failures <= 5 || failures % 60 == 0) log("create failed (#" + failures + "): " + t);
                effect = null;
                return false;
            }
        }
        if (appliedDb != wantDb) {
            try {
                effect.setGainMb(wantDb * 100);
                appliedDb = wantDb;
                failures = 0;
                log("boost on: +" + wantDb + " dB");
            } catch (Throwable t) {
                log("set gain failed: " + t);
                effect.release();
                effect = null;
                appliedDb = -1;
                return false;
            }
        }
        return true;
    }

    public static void main(String[] args) {
        if (args.length > 0) confPath = args[0];
        if (args.length > 1) logPath = args[1];
        log("start pid-file conf=" + confPath);
        prepareRuntime();
        EffectFactory real = new EffectFactory() {
            public Effect create() { return new RealEffect(); }
        };
        while (true) {
            step(real, readGainDb(confPath, 0));
            try { Thread.sleep(PERIOD_MS); } catch (InterruptedException e) { break; }
        }
    }
}

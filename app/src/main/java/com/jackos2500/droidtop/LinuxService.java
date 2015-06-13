package com.jackos2500.droidtop;

import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import android.util.Log;

import static com.jackos2500.droidtop.DroidtopApplication.debug;
import static com.jackos2500.droidtop.DroidtopApplication.debugException;

public class LinuxService extends Service {
    private static final String TAG = "LinuxService";

    private static LinuxService instance;

    private PowerManager.WakeLock wakelock;
    private SharedPreferences preferences;
    private boolean running;
    private LinuxManager linuxManager;
    private LinuxThread thread;

    public static LinuxService getInstance() {
        return instance;
    }
    public LinuxManager getLinuxManager() {
        return linuxManager;
    }

    @Override
    public void onCreate() {
        Log.d(TAG, "onCreate()");
        instance = this;

        preferences = PreferenceManager.getDefaultSharedPreferences(this);

        linuxManager = new LinuxManager(this);
        thread = new LinuxThread();
        PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
        wakelock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, Constants.WAKELOCK);
    }
    @Override
    public int onStartCommand(Intent startIntent, int flags, int startId) {
        Log.d(TAG, "onStartCommand()");

        if (wakelock != null) {
            wakelock.acquire();
            debug(TAG, "wakelock acquired");
        } else {
            debug(TAG, "wakelock is null!");
        }

        if (startIntent != null && startIntent.getBooleanExtra("stop", false)) {
            debug(TAG, "received stop request");
            if (running) {
                if (linuxManager.isBooted()) {
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                linuxManager.shutdown();
                                running = false;
                            } catch (LinuxManager.LinuxException e) {
                                debugException(TAG, e);
                            }
                        }
                    }).start();
                }
            } else {
                debug(TAG, "cant't stop, we are not running!");
            }
            return START_STICKY;
        }

        if (!thread.isAlive()) {
            if (thread.hasRun()) {
                debug(TAG, "thread has completed, restarting...");
                thread = new LinuxThread();
            }
            thread.start();
        } else {
            debug(TAG, "can't start, we are already running!");
        }

        return START_STICKY;
    }
    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy()");

        if (wakelock != null) {
            if (wakelock.isHeld()) {
                wakelock.release();
                debug(TAG, "wakelock released");
            }
        } else {
            debug(TAG, "wakelock is null!");
        }
    }

    private class LinuxThread extends Thread {
        private boolean ran;

        public LinuxThread() {
            super("LinuxThread");
        }

        @Override
        public void run() {
            ran = true;
            running = true;

            boolean audioInitialized = false;
            try {
                if (!linuxManager.isInitialized()) {
                    linuxManager.init();
                }
                linuxManager.boot();
                if (preferences.getBoolean("enable_audio", true)) {
                    audioInitialized = linuxManager.connectAudio();
                }
                Util.runInApplicationThread(LinuxService.this, new Runnable() {
                    @Override
                    public void run() {
                        Intent startIntent = new Intent(LinuxService.this, DroidtopActivity.class);
                        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        startActivity(startIntent);
                    }
                });
            } catch (LinuxManager.LinuxException e) {
                debugException(TAG, e);
                running = false;
            }

            int audioFails = 0;
            while(running) {
                try {
                    if (audioInitialized && linuxManager.isAudioConnected() && audioFails < 10) {
                        if (!linuxManager.audioLoop()) {
                            audioFails++;
                            if (audioFails >= 10) {
                                debug(TAG, "audio failure count has exceeded maximum, closing audio...");
                                linuxManager.closeAudio();
                            }
                        }
                    } else {
                        // JUST SPIN!!! :D
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //} catch (LinuxManager.LinuxException e) {
            //debugException(TAG, e);
            try {
                linuxManager.close(true);
            } catch (LinuxManager.LinuxException e) {
                debugException(TAG, e);
                // we have lost all hope...
                linuxManager = new LinuxManager(LinuxService.this);
            }
            //}
            stopSelf();
        }
        public boolean hasRun() {
            return ran;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}

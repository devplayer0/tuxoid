package com.jackos2500.droidtop;

import android.app.Activity;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import gnu.x11.Display;
import gnu.x11.extension.XTest;

import static com.jackos2500.droidtop.DroidtopApplication.debug;

public class DroidtopActivity extends Activity implements DroidtopSurfaceView.ErrorListener {
    private static final String TAG = "DroidtopActivity";

    private DroidtopSurfaceView surfaceView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        surfaceView = new DroidtopSurfaceView(this, this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (LinuxService.getInstance() != null && LinuxService.getInstance().getLinuxManager().isAudioConnected()) {
            setVolumeControlStream(AudioManager.STREAM_MUSIC);
        }

        setContentView(surfaceView);
    }
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    @Override
    public void onError(int errorCode) {
        debug(TAG, "error connecting to xorg server, error code: "+errorCode);
        finish();
    }
    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "onResume()");
    }
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}

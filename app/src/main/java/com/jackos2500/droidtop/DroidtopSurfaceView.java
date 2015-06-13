package com.jackos2500.droidtop;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.preference.PreferenceManager;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static com.jackos2500.droidtop.DroidtopApplication.debug;

public class DroidtopSurfaceView extends GLSurfaceView {
    private static final String TAG = "DroidtopSurface";

    private ErrorListener listener;
    private Renderer renderer;
    public DroidtopSurfaceView(Context context, ErrorListener listener) {
        super(context);
        this.listener = listener;

        setEGLContextClientVersion(3);

        renderer = new Renderer();
        setRenderer(renderer);
    }

    @Override
    public void onDetachedFromWindow() {
        super.onDetachedFromWindow();

        DroidtopNative.close();
    }
    private class Renderer implements GLSurfaceView.Renderer {
        private boolean initFailed = false;
        //long startTime = 0;
        //int frames = 0;
        @Override
        public void onSurfaceCreated(GL10 unused, EGLConfig config) {
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getContext());
            Point realSize = Util.getScreenDimensions(getContext());
            Point size = Util.getPointResolution(preferences.getString("resolution", (String)Util.getAvailableResolutions(getContext())[0]));
            if (size == null) {
                size = realSize;
            }

            int result = DroidtopNative.init(realSize.x, realSize.y, size.x, size.y);
            if (result != 0) {
                initFailed = true;
                listener.onError(result);
            }
        }
        @Override
        public void onSurfaceChanged(GL10 unused, int width, int height) {
            Log.d(TAG, "onSurfaceChanged("+width+", "+height+")");
        }
        @Override
        public void onDrawFrame(GL10 unused) {
            /*frames++;
            if (System.nanoTime() - startTime >= 1000000000) {
                Log.d("test", "fps: "+frames);
                frames = 0;
                startTime = System.nanoTime();
            }*/

            if (!initFailed) {
                DroidtopNative.update();
            }
        }
    }

    public interface ErrorListener {
        public void onError(int errorCode);
    }
}

package com.jackos2500.droidtop;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import eu.chainfire.libsuperuser.Application;

public class DroidtopApplication extends Application {
    private static final String TAG = "DroidtopApplication";

    public static final boolean IS_DEBUG = true;
    public static void debug(String tag, String msg) {
        if (!IS_DEBUG) {
            return;
        }

        Log.d(tag, msg);
        LauncherActivity.printLog(tag, msg);
    }
    public static void debugException(String tag, Exception ex) {
        if (!IS_DEBUG) {
            return;
        }

        String msg = ex.toString() + "\n";
        for (StackTraceElement el : ex.getStackTrace()) {
            msg += el.toString() + "\n";
        }
        // remove trailing \n
        msg = msg.substring(0, msg.length());
        debug(tag, msg);
    }

    public static String extractAsset(Context context, String name) {
        String loc = "/data/data/" + context.getPackageName() + "/files/"+name;
        File asset = new File(loc);
        if (asset.exists()) {
            return loc;
        }

        debug(TAG, "asset '"+name+"' does not exist, extracting...");
        try {
            AssetManager assets = context.getAssets();
            InputStream in = assets.open(name);
            if (!asset.getParentFile().exists()) {
                asset.getParentFile().mkdirs();
            }
            OutputStream out = new FileOutputStream(asset);

            byte[] buffer = new byte[1024 * 256];
            int read;
            while ((read = in.read(buffer)) > 0) {
                out.write(buffer, 0, read);
            }

            in.close();
            out.close();

            return loc;
        } catch (IOException e) {
            debug(TAG, "failed to extract asset");
            e.printStackTrace();
        }
        return null;
    }
}

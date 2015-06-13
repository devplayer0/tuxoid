package com.jackos2500.droidtop;

import android.content.Context;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import java.lang.reflect.Array;
import java.util.Collections;
import java.util.List;

import static com.jackos2500.droidtop.DroidtopApplication.debug;

public class Util {
    private static final String TAG = "Util";
    public static String listToString(List<String> list) {
        String result = "";
        for (int i = 0; i < list.size(); i++) {
            String item = list.get(i);
            result = result + item + "\n";
        }
        if (result.endsWith("\n")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
    public static void runInApplicationThread(Context context, Runnable r) {
        DroidtopApplication app = (DroidtopApplication)context.getApplicationContext();
        app.runInApplicationThread(r);
    }
    private static Point size;
    public static Point getScreenDimensions(Context context) {
        if (size != null) {
            return size;
        }

        WindowManager wm = (WindowManager)context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();

        size = new Point();
        display.getSize(size);
        if (size.y > size.x) {
            int xCopy = size.x;
            size.x = size.y;
            size.y = xCopy;
        }
        debug(TAG, "screen size: "+size.x+"x"+size.y);
        return size;
    }
    public static Point getPointResolution(String resolutionString) {
        String[] arr = resolutionString.split("x");
        if (arr.length != 2) {
            return null;
        }

        Point size = new Point();
        try {
            size.x = Integer.parseInt(arr[0]);
            size.y = Integer.parseInt(arr[1]);
        } catch (NumberFormatException e) {
            return null;
        }

        return size;
    }
    public static <T> T[] getSubArray(Class<T> type, T[] arr, int start) {
        T[] subArr = (T[])Array.newInstance(type, arr.length - start);
        for (int i = 0; i < subArr.length; i++) {
            subArr[i] = arr[start + i];
        }
        return subArr;
    }
    private static CharSequence[] availableResolutions;
    public static CharSequence[] getAvailableResolutions(Context context) {
        if (availableResolutions != null) {
            return availableResolutions;
        }

        Point realSize = getScreenDimensions(context);
        for (int i = 0; i < Constants.RESOLUTIONS.length; i++) {
            Point size = getPointResolution(Constants.RESOLUTIONS[i]);
            if (size.x <= realSize.x && size.y <= realSize.y) {
                availableResolutions = getSubArray(String.class, Constants.RESOLUTIONS, i);
                break;
            }
        }
        return availableResolutions;
    }
    public static int parseIntDefault(String intString, int defaultValue) {
        try {
            return Integer.parseInt(intString);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

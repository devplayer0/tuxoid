package com.jackos2500.droidtop;

public class DroidtopNative {
    static {
        System.loadLibrary("droidtop");
    }

    public static native int init(int realWidth, int realHeight, int width, int height);
    public static native void update();
    public static native void close();

    public static native boolean grab(int fd);
    public static native boolean ungrab(int fd);
    public static native int openFile(String path);
    public static native int closeFile(int fd);
    public static native boolean isMouse(int fd);
    public static native boolean isKeyboard(int fd);
}

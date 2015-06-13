package com.jackos2500.droidtop;

import android.os.Environment;

import java.io.File;

public class Constants {
    public static final String WAKELOCK = "DroidtopWakelock";

    public static final String MOUNTPOINT = "/data/local/droidtop_sysroot";
    public static final String LOOP_DEV = "/dev/block/loop255";
    public static final String INIT_PROCESS = "/usr/bin/droidtop_init";
    public static final String DEFAULT_HOSTNAME = "android";

    public static final File IMG_DIRECTORY = new File("/storage/emulated/legacy/", "droidtop");
    public static final String IMG_NAME_DEFAULT = "system.img";
    public static final int IMG_SIZE_DEFAULT = 4096;
    public static final String ARCH_URL = "http://archlinuxarm.org/os/ArchLinuxARM-armv7-latest.tar.gz";
    public static final String ARCH_TAR = "arch.tar.gz";

    public static final String DEFAULT_USERNAME = "tuxoid";
    public static final String[] PACKAGES = new String[] { "python2", "python2-pip", "sudo", "xorg-server", "xorg-server-utils", "xorg-apps", "xorg-xinit", "xf86-input-evdev", "ttf-dejavu", "xterm", "openbox", "pulseaudio" };
    public static final String[] RESOLUTIONS = new String[] { "2560x1600", "1920x1440", "1920x1200", "1920x1080", "1600x1200", "1680x1050", "1600x900", "1400x1050", "1440x900", "1280x1024", "1366x768", "1280x800", "1024x768", "1024x600", "800x600" };

    public static final String INPUT_PATH = "/dev/input";
    public static final String INPUT_PREFIX = "event";

    public static final int AUDIO_PORT = 9090;
}

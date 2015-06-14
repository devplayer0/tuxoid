package com.jackos2500.droidtop;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.FileObserver;
import android.preference.PreferenceManager;
import android.util.Log;

import com.jackos2500.droidtop.initservice.DroidtopError;
import com.jackos2500.droidtop.initservice.DroidtopInit;

import org.apache.thrift.TException;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.chainfire.libsuperuser.Shell;

import static com.jackos2500.droidtop.DroidtopApplication.debug;
import static com.jackos2500.droidtop.DroidtopApplication.debugException;

public class LinuxManager {
    private static final String TAG = "LinuxManager";

    private Context context;
    private SharedPreferences preferences;

    private Shell.Interactive rootShell;
    private int exitCode = -1;
    private List<String> output;
    private String busybox;
    private boolean initialized;
    private boolean booted;

    private TTransport initTransport;
    private DroidtopInit.Client initClient;

    private String mouseDev;
    private String keyboardDev;
    private FileObserver inputDevicesObserver = new FileObserver(Constants.INPUT_PATH, FileObserver.CREATE) {
        @Override
        public void onEvent(int event, String fileName) {
            if (fileName == null) {
                return;
            }
            if (!fileName.startsWith(Constants.INPUT_PREFIX)) {
                return;
            }

            if ((event & FileObserver.CREATE) != 0) {
                debug(TAG, "got event device with name: " + fileName);

                if (runCommandWait("chmod 0666 "+Constants.INPUT_PATH+"/"+fileName) != 0) {
                    debug(TAG, "failed to set permissions on event file '"+fileName+"'");
                    return;
                }

                int fd = DroidtopNative.openFile(Constants.INPUT_PATH+"/"+fileName);
                if (fd == -1) {
                    debug(TAG, "failed to open event file '"+fileName+"'");
                    return;
                }

                if (DroidtopNative.isMouse(fd)) {
                    debug(TAG, "got mouse event file '"+fileName+"'");
                    mouseDev = Constants.INPUT_PATH+"/"+fileName;
                }
                if (DroidtopNative.isKeyboard(fd)) {
                    debug(TAG, "got keyboard event file '"+fileName+"'");
                    keyboardDev = Constants.INPUT_PATH+"/"+fileName;
                }

                DroidtopNative.closeFile(fd);
            }
        }
    };

    private Socket audioSocket;
    private BufferedInputStream audioInput;
    private AudioTrack audioTrack;
    private byte[] audioBuffer;
    private boolean audioClosed;

    public LinuxManager(Context context) {
        this.context = context;
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public boolean isInitialized() {
        return initialized;
    }
    public boolean isBooted() {
        return booted;
    }

    private int runCommandWait(final String command) {
        rootShell.addCommand(command, 0, new Shell.OnCommandResultListener() {
            @Override
            public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                LinuxManager.this.exitCode = exitCode;
                LinuxManager.this.output = output;
                Log.d(TAG, "'" + command + "' exit code: " + exitCode);
            }
        });
        rootShell.waitForIdle();
        return exitCode;
    }
    private int busyboxCommandWait(String busyboxCommand) {
        return runCommandWait(busybox + " " + busyboxCommand);
    }
    private int chrootCommandWait(String chrootCommand) {
        return runCommandWait("chroot "+Constants.MOUNTPOINT+" "+chrootCommand);
    }

    private boolean copyFile(String from, String to) {
        return runCommandWait("cp "+from+" "+to) == 0;
    }

    private void getAndTestBusybox() throws LinuxException {
        busybox = DroidtopApplication.extractAsset(context, "busybox");
        if (busybox == null) {
            rootShell.close();
            throw new LinuxException("error extracting busybox");
        }
        if (!new File(busybox).canExecute()) {
            debug(TAG, "busybox is not executable, running chmod...");
            if (runCommandWait("chmod 500 " + busybox) != 0) {
                rootShell.close();
                throw new LinuxException("chmod returned exit code: " + exitCode);
            }
        }
        if (runCommandWait(busybox) != 0) {
            rootShell.close();
            throw new LinuxException("busybox returned exit code: " + exitCode);
        }
    }

    public void init() throws LinuxException {
        if (!Constants.IMG_DIRECTORY.exists()) {
            debug(TAG, "images directory " + Constants.IMG_DIRECTORY.getAbsolutePath() + " does not exist, creating...");
            if (!Constants.IMG_DIRECTORY.mkdir()) {
                throw new LinuxException("failed to create images directory");
            }
        }

        if (!Shell.SU.available()) {
            throw new LinuxException("unable to get root access");
        }
        rootShell = new Shell.Builder()
                .useSU()
                .setWantSTDERR(true)
                .setWatchdogTimeout(0)
                .open(new Shell.OnCommandResultListener() {
                    @Override
                    public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                        LinuxManager.this.exitCode = exitCode;
                    }
                });

        while (exitCode == -1);
        if (exitCode != Shell.OnCommandResultListener.SHELL_RUNNING) {
            rootShell.close();
            throw new LinuxException("error opening root shell!");
        }

        debug(TAG, "root shell started");

        setSELinuxMode(false);

        getAndTestBusybox();
        File[] inputFiles = new File(Constants.INPUT_PATH).listFiles();
        for (File f : inputFiles) {
            inputDevicesObserver.onEvent(FileObserver.CREATE, f.getName());
        }
        inputDevicesObserver.startWatching();
        initialized = true;
    }

    private void cleanChroot() throws LinuxException {
        if (busyboxCommandWait("fuser " + Constants.MOUNTPOINT) == 0) {
            debug(TAG, "mountpoint '" + Constants.MOUNTPOINT + "' is in use, attempting SIGTERM and allowing 10 seconds for shutdown...");
            busyboxCommandWait("fuser -SIGTERM -k " + Constants.MOUNTPOINT);
            try {
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                debugException(TAG, e);
            }
            if (busyboxCommandWait("fuser " + Constants.MOUNTPOINT) == 0) {
                debug(TAG, "mountpoint '" + Constants.MOUNTPOINT + "' still in use, sending SIGKILL...");
                busyboxCommandWait("fuser -SIGKILL -k " + Constants.MOUNTPOINT);
                if (busyboxCommandWait("fuser " + Constants.MOUNTPOINT) == 0) {
                    throw new LinuxException("failed to kill processes in chroot");
                }
            }
        }

        final List<String> toUnmount = new ArrayList<String>();
        rootShell.addCommand("mount", 0, new Shell.OnCommandResultListener() {
            @Override
            public void onCommandResult(int commandCode, int exitCode, List<String> output) {
                for (String line : output) {
                    if (line.contains(Constants.MOUNTPOINT)) {
                        String mountpoint = line.split(" ")[1];
                        toUnmount.add(mountpoint);
                    }
                }
            }
        });
        rootShell.waitForIdle();

        while (toUnmount.size() > 0) {
            String longestLine = "";
            for (String mountpoint : toUnmount) {
                if (longestLine.length() < mountpoint.length()) {
                    longestLine = mountpoint;
                }
            }
            debug(TAG, "unmounting '" + longestLine + "'...");
            unmount(longestLine);
            toUnmount.remove(longestLine);
        }

        busyboxCommandWait("losetup -d " + Constants.LOOP_DEV);
    }

    private void mount(String type, String device, String mountpoint) throws LinuxException {
        if (busyboxCommandWait("mount -t " + type + " " + device + " " + mountpoint) != 0) {
            throw new LinuxException("failed to mount device '" + device + "'" + " of type '" + type + "' to '" + mountpoint + "'");
        }
    }
    private void mountWithOpts(String opts, String type, String device, String mountpoint) throws LinuxException {
        if (busyboxCommandWait("mount -o " + opts + " -t " + type + " " + device + " " + mountpoint) != 0) {
            throw new LinuxException("failed to mount device '" + device + "'" + " of type '" + type + "' to '" + mountpoint + "'");
        }
    }
    private void bindMount(String source, String mountpoint) throws LinuxException {
        if (busyboxCommandWait("mount -o bind " + source + " " + mountpoint) != 0) {
            throw new LinuxException("failed to bind mount '" + source + "' to '" + mountpoint + "'");
        }
    }
    private void unmount(String mountpoint) throws LinuxException {
        if (busyboxCommandWait("umount -f " + mountpoint) != 0) {
            throw new LinuxException("failed to unmount from '" + mountpoint + "'");
        }
    }

    private boolean updatePackages() throws LinuxException {
        if (chrootCommandWait("/usr/bin/pacman -Syu --noconfirm") != 0) {
            String exString = "failed to update packages with pacman";
            if (output.size() > 0) {
                exString = exString + ". output:\n"+Util.listToString(output);
            }
            return false;
        }
        return true;
    }
    private boolean installPackage(String name) throws LinuxException {
        if (chrootCommandWait("/usr/bin/pacman -S --noconfirm " + name) != 0) {
            String exString = "failed to install package '"+name+"' with pacman";
            if (output.size() > 0) {
                exString = exString + ". output:\n"+Util.listToString(output);
            }
            return false;
            //throw new LinuxException(exString);
        }
        return true;
    }

    private void buildExt4Image(int sizeMB) throws LinuxException {
        String imgFile = new File(Constants.IMG_DIRECTORY, preferences.getString("img_name", Constants.IMG_NAME_DEFAULT)).getAbsolutePath();
        if (busyboxCommandWait("dd if=/dev/zero of="+imgFile+" bs=1M count="+sizeMB) != 0) {
            throw new LinuxException("failed to create empty image file '"+imgFile+"' of size "+sizeMB+"MB");
        }
        if (runCommandWait("mke2fs -F -t ext4 " + imgFile) != 0) {
            throw new LinuxException("failed to create ext4 filesystem in image file '"+imgFile+"'");
        }
    }
    private boolean downloadAndVerify(String url, String dst, long progressThresholdKB) throws LinuxException {
        BufferedInputStream in = null;
        FileOutputStream out = null;
        boolean verified = false;
        try {
            debug(TAG, "downloading "+url+"...");

            URLConnection urlConnection = new URL(url).openConnection();
            int length = urlConnection.getContentLength();
            in = new BufferedInputStream(urlConnection.getInputStream());
            out = new FileOutputStream(dst);

            final byte[] buffer = new byte[1024 * 256];
            long totalRead = 0;
            long lastProgess = 0;
            int count;
            while ((count = in.read(buffer, 0, buffer.length)) != -1) {
                totalRead += count;
                out.write(buffer, 0, count);
                if (totalRead - lastProgess >= progressThresholdKB * 1024) {
                    lastProgess = totalRead;

                    String downloaded = length != -1 ? "/"+(length/1024)+"KB " : "";
                    debug(TAG, "downloaded "+totalRead/1024+"KB "+downloaded+" of "+url);
                }
            }

            final byte[] md5Buffer = new byte[32];
            int read = new URL(url+".md5").openStream().read(md5Buffer);
            if (read != 32) {
                throw new IOException("failed to read full md5");
            }
            String md5 = new String(md5Buffer);

            debug(TAG, "verifying "+url+"...");
            if (busyboxCommandWait("md5sum "+dst) != 0) {
                throw new LinuxException("failed to read md5 checksum on downloaded file "+url);
            }
            if (output == null || output.size() == 0) {
                throw new LinuxException("failed to read output from md5sum");
            }

            String downloadedMd5 = output.get(0).substring(0, 32);
            verified = md5.equals(downloadedMd5);
        } catch (IOException e) {
            throw new LinuxException("failed to download file from url "+url);
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {}
            }
            if (out != null) {
                try {
                    out.close();
                } catch (IOException e) {}
            }
            return verified;
        }
    }
    private void extractTar(String tarFile, boolean compressed, String dst) throws LinuxException {
        if (runCommandWait("tar x" + (compressed ? "z" : "") + "f " + tarFile + " -C " + dst) != 0) {
            throw new LinuxException("failed to extract tarfile '"+tarFile+"' to '"+dst+"'");
        }
    }
    private void setupInitialImage() throws LinuxException {
        File tar = new File(Constants.IMG_DIRECTORY, Constants.ARCH_TAR);
        if (!tar.exists()) {
            debug(TAG, "arch linux arm archive does not exist, downloading...");
            if (!downloadAndVerify(Constants.ARCH_URL, tar.getAbsolutePath(), 10 * 1024)) {
                throw new LinuxException("failed to download and verify arch linux arm archive");
            }
        }
        debug(TAG, "extracting gzipped tar archive " + tar.getAbsolutePath() + "...");
        extractTar(tar.getAbsolutePath(), true, Constants.MOUNTPOINT);

        debug(TAG, "cleaning /boot...");
        if (runCommandWait("rm -r "+Constants.MOUNTPOINT+"/boot/*") != 0) {
            throw new LinuxException("failed to clean boot directory from image");
        }

        debug(TAG, "creating mountpoint for android system access...");
        if (runCommandWait("mkdir "+Constants.MOUNTPOINT+"/internal/") != 0) {
            throw new LinuxException("failed to add android internal storage mountpoint in chroot image");
        }

        debug(TAG, "configuring dns");
        String hostname = preferences.getString("hostname", Constants.DEFAULT_HOSTNAME);
        if (runCommandWait("echo \""+hostname+"\" > "+Constants.MOUNTPOINT+"/etc/hostname") != 0) {
            throw new LinuxException("failed to configure system hostname as '"+hostname+"'");
        }
        if (runCommandWait("rm "+Constants.MOUNTPOINT+"/etc/resolv.conf") != 0) {
            throw new LinuxException("failed to clean /etc/resolv.conf in chroot image");
        }
        if (runCommandWait("echo \"nameserver 8.8.8.8\" > "+Constants.MOUNTPOINT+"/etc/resolv.conf") != 0) {
            throw new LinuxException("failed to add 8.8.8.8 to /etc/resolv.conf chroot image");
        }
        if (runCommandWait("echo \"nameserver 8.8.4.4\" >> "+Constants.MOUNTPOINT+"/etc/resolv.conf") != 0) {
            throw new LinuxException("failed to add 8.8.4.4 to /etc/resolv.conf chroot image");
        }
        if (busyboxCommandWait("chmod 644 " + Constants.MOUNTPOINT+"/etc/resolv.conf") != 0){
            throw new LinuxException("failed to set permissions on /etc/resolv.conf");
        }
        if (runCommandWait("echo \"127.0.0.1 localhost\" > "+Constants.MOUNTPOINT+"/etc/hosts") != 0) {
            throw new LinuxException("failed to perform /etc/hosts setup in chroot image");
        }

        debug(TAG, "performing initial droidtop init system installation...");
        String droidtopInit = DroidtopApplication.extractAsset(context, "droidtop_init");
        if (droidtopInit == null) {
            throw new LinuxException("failed to extract Droidtop Linux init binary from apk");
        }
        if (!copyFile(droidtopInit, Constants.MOUNTPOINT+Constants.INIT_PROCESS)) {
            throw new LinuxException("failed to copy Droidtop Linux init binary to chroot image");
        }
        if (busyboxCommandWait("chmod 500 " + Constants.MOUNTPOINT + Constants.INIT_PROCESS) != 0) {
            throw new LinuxException("failed to enable execution for Droidtop Linux init binary in chroot image");
        }
        String blockDevsSymlink = DroidtopApplication.extractAsset(context, "block_symlink");
        if (blockDevsSymlink == null) {
            throw new LinuxException("failed to extract block device symlinker from apk");
        }
        if (!copyFile(blockDevsSymlink, Constants.MOUNTPOINT+"/usr/bin/block_symlink")) {
            throw new LinuxException("failed to copy block device symlinker to chroot image");
        }
        if (busyboxCommandWait("chmod 500 "+Constants.MOUNTPOINT+"/usr/bin/block_symlink") != 0) {
            throw new LinuxException("failed to enable execution for block device symlinker in chroot image");
        }
        String droidtopServices = DroidtopApplication.extractAsset(context, "droidtop_services.tar");
        if (droidtopServices == null) {
            throw new LinuxException("failed to extract Droidtop Linux service definitions archive from apk");
        }
        extractTar(droidtopServices, false, Constants.MOUNTPOINT+"/etc/");
    }
    private void addGroup(int id, String name, String groupFile) throws LinuxException {
        if (runCommandWait("echo \""+name+":x:"+id+":root\" >> "+groupFile) != 0) {
            throw new LinuxException("failed to add group '"+name+"' to /etc/group");
        }
    }
    private void configureImage() throws LinuxException {
        debug(TAG, "updating packages (this will take a while, especially since the arch arm repos are so unreliable)...");

        boolean updateSucceeded = false;
        for (int i = 0; i < 10; i++) {
            updateSucceeded = updatePackages();
            if (!updateSucceeded) {
                debug(TAG, "failed to update packages, retrying... (attempt "+(i+2)+")");
                continue;
            }
            break;
        }
        if (!updateSucceeded) {
            throw new LinuxException("failed to update packages!");
        }

        for (int i = 0; i < Constants.PACKAGES.length; i++) {
            String pkg = Constants.PACKAGES[i];
            debug(TAG, "installing package '"+pkg+"'");

            boolean installSucceeded = false;
            for (int j = 0; j < 10; j++) {
                installSucceeded = installPackage(pkg);
                if (!installSucceeded) {
                    debug(TAG, "failed to install package '"+pkg+"', retrying... (attempt "+(j+2)+")");
                    continue;
                }
                break;
            }
            if (!installSucceeded) {
                throw new LinuxException("failed to update packages!");
            }
        }

        debug(TAG, "performing final droidtop init system installation and configuration...");
        if (chrootCommandWait("/usr/bin/pip2 install thrift") != 0) {
            throw new LinuxException("failed to install required libraries for droidtop init process");
        }
        if (chrootCommandWait("/usr/bin/pip2 install pyinotify") != 0) {
            throw new LinuxException("failed to install required library for a droidtop init server");
        }
        String droidtopModule = DroidtopApplication.extractAsset(context, "droidtop_init_module.tar");
        if (droidtopModule == null) {
            throw new LinuxException("failed to extract Droidtop Linux init module");
        }
        extractTar(droidtopModule, false, Constants.MOUNTPOINT + "/usr/lib/python2.7/site-packages/");

        String username = preferences.getString("username", Constants.DEFAULT_USERNAME);
        debug(TAG, "adding and configuring user "+username);
        if (chrootCommandWait("/usr/bin/useradd -m -G wheel -s /bin/bash " + username) != 0) {
            throw new LinuxException("failed to create user '"+username+"'");
        }
        if (runCommandWait("chroot " + Constants.MOUNTPOINT + " /usr/bin/echo " + username + ":" + username + " | chroot " + Constants.MOUNTPOINT + " /usr/bin/chpasswd") != 0) {
            throw new LinuxException("failed to set password for user '"+username+"'");
        }
        addGroup(3003, "inet", Constants.MOUNTPOINT+"/etc/group");
        addGroup(3004, "net_raw", Constants.MOUNTPOINT+"/etc/group");
        addGroup(1015, "sdcard_rw", Constants.MOUNTPOINT+"/etc/group");
        addGroup(1028, "sdcard_r", Constants.MOUNTPOINT+"/etc/group");
        if (chrootCommandWait("/usr/bin/usermod -aG inet,net_raw,sdcard_rw,sdcard_r "+username) != 0) {
            throw new LinuxException("failed to add user "+username+" to groups inet, net_raw sdcard_rw and sdcard_r");
        }
        if (runCommandWait("echo \"%wheel ALL=(ALL) ALL\" > " + Constants.MOUNTPOINT + "/etc/sudoers.d/wheel") != 0) {
            throw new LinuxException("failed to add user'"+username+"' to sudoers");
        }

        debug(TAG, "configuring xorg...");
        String droidtopDriver = DroidtopApplication.extractAsset(context, "dummy_drv.so");
        if (droidtopDriver == null) {
            throw new LinuxException("failed to extract Droidtop video driver");
        }
        if (!copyFile(droidtopDriver, Constants.MOUNTPOINT+"/usr/lib/xorg/modules/drivers/")) {
            throw new LinuxException("failed to install Droidtop video driver");
        }
        if (runCommandWait("echo \"allowed_users=anybody\" > " + Constants.MOUNTPOINT + "/etc/X11/Xwrapper.config") != 0) {
            throw new LinuxException("failed to configure xorg permissions");
        }
        String xorgConf = DroidtopApplication.extractAsset(context, "xorg.conf");
        if (xorgConf == null) {
            throw new LinuxException("failed to extract xorg.conf");
        }
        if (!copyFile(xorgConf, Constants.MOUNTPOINT+"/etc/X11/")) {
            throw new LinuxException("failed to install xorg.conf");
        }
        if (busyboxCommandWait("sed -i 's/#{user}/"+username+"/g' " + Constants.MOUNTPOINT + "/etc/droidtop_services/01-user/00-xserver.json") != 0) {
            throw new LinuxException("failed to configure xorg server service for user '"+username+"'");
        }
        String xinitrc = DroidtopApplication.extractAsset(context, "xinitrc");
        if (xinitrc == null) {
            throw new LinuxException("failed to extract .xinitrc");
        }
        if (!copyFile(xinitrc, Constants.MOUNTPOINT+"/home/"+username+"/.xinitrc")) {
            throw new LinuxException("failed to install .xinitrc");
        }
        if (chrootCommandWait("/usr/bin/chmod 644 /home/"+username+"/.xinitrc") != 0) {
            throw new LinuxException("failed to set permissions on .xinitrc file");
        }
        if (chrootCommandWait("/usr/bin/chown " + username + " /home/" + username + "/.xinitrc") != 0) {
            throw new LinuxException("failed to change ownership of .xinitrc");
        }
        if (chrootCommandWait("/usr/bin/chgrp "+username+" /home/" + username + "/.xinitrc") != 0) {
            throw new LinuxException("failed to change group of .xinitrc");
        }

        debug(TAG, "configuring audio...");
        if (busyboxCommandWait("sed -i 's/load-module module-console-kit/#load-module module-console-kit/g' "+Constants.MOUNTPOINT+"/etc/pulse/default.pa") != 0) {
            throw new LinuxException("failed to disable pulseaudio module 'console-kit'");
        }
        if (runCommandWait("echo \"load-module module-simple-protocol-tcp rate=" + AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC) + " format=s16le channels=2 source=0 record=true port=" + Constants.AUDIO_PORT + "\" >> " + Constants.MOUNTPOINT + "/etc/pulse/default.pa") != 0) {
            throw new LinuxException("failed to configure audio streaming server");
        }
    }

    private void configureXorgDevices() throws LinuxException {
        if (runCommandWait("[ -f " + Constants.MOUNTPOINT + "/etc/X11/xorg.conf.d/* ]") == 0) {
            if (runCommandWait("rm "+Constants.MOUNTPOINT+"/etc/X11/xorg.conf.d/*") != 0) {
                throw new LinuxException("failed to clean xorg.conf.d directory");
            }
        }

        Point size = Util.getPointResolution(preferences.getString("resolution", (String)Util.getAvailableResolutions(context)[0]));
        if (size == null) {
            size = Util.getScreenDimensions(context);
        }
        debug(TAG, "configuring video with resolution '"+size.x+"x"+size.y+"'");
        String videoTemplate = DroidtopApplication.extractAsset(context, "xorg_video_template.conf");
        if (videoTemplate == null) {
            throw new LinuxException("failed to extract video configuration template");
        }
        if (!copyFile(videoTemplate, Constants.MOUNTPOINT+"/etc/X11/xorg.conf.d/video.conf")) {
            throw new LinuxException("failed to install video configuration template");
        }
        if (busyboxCommandWait("sed -i 's/#{size}/" + size.x+" "+size.y + "/g' " + Constants.MOUNTPOINT + "/etc/X11/xorg.conf.d/video.conf") != 0) {
            throw new LinuxException("failed to change video configuration");
        }

        boolean mouseEnabled = preferences.getBoolean("enable_mouse", true);
        debug(TAG, "configuring mouse with device '"+(mouseEnabled ? mouseDev : null)+"' (null is ok)");
        String mouseTemplate = DroidtopApplication.extractAsset(context, "xorg_mouse_template.conf");
        if (mouseTemplate == null) {
            throw new LinuxException("failed to extract mouse configuration template");
        }
        if (!copyFile(mouseTemplate, Constants.MOUNTPOINT+"/etc/X11/xorg.conf.d/mouse.conf")) {
            throw new LinuxException("failed to install mouse configuration template");
        }
        if (mouseDev != null && mouseEnabled) {
            if (busyboxCommandWait("sed -i 's/null/"+mouseDev.replace("/", "\\/")+"/g' " + Constants.MOUNTPOINT + "/etc/X11/xorg.conf.d/mouse.conf") != 0) {
                throw new LinuxException("failed to change mouse configuration");
            }
        }

        boolean keyboardEnabled = preferences.getBoolean("enable_keyboard", true);
        debug(TAG, "configuring keyboard with device '"+(keyboardEnabled ? keyboardDev : null)+"' (null is ok)");
        String keyboardTemplate = DroidtopApplication.extractAsset(context, "xorg_keyboard_template.conf");
        if (keyboardTemplate == null) {
            throw new LinuxException("failed to extract keyboard configuration template");
        }
        if (!copyFile(keyboardTemplate, Constants.MOUNTPOINT+"/etc/X11/xorg.conf.d/keyboard.conf")) {
            throw new LinuxException("failed to install keyboard configuration template");
        }
        if (keyboardDev != null && keyboardEnabled) {
            if (busyboxCommandWait("sed -i 's/null/"+keyboardDev.replace("/", "\\/")+"/g' "+Constants.MOUNTPOINT+"/etc/X11/xorg.conf.d/keyboard.conf") != 0) {
                throw new LinuxException("failed to change mouse configuration");
            }
        }
    }

    public boolean connectAudio() {
        int sampleRate = AudioTrack.getNativeOutputSampleRate(AudioManager.STREAM_MUSIC);
        debug(TAG, "connecting to PulseAudio on port "+Constants.AUDIO_PORT+" at sample rate of "+sampleRate+"Hz");

        try {
            audioSocket = new Socket("127.0.0.1", Constants.AUDIO_PORT);
            audioInput = new BufferedInputStream(audioSocket.getInputStream());
        } catch (IOException e) {
            debug(TAG, "error connecting to PulseAudio sound server");
            debugException(TAG, e);
            return false;
        }

        int bufferSize = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT);
        audioBuffer = new byte[bufferSize * 8];
        audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
        audioTrack.play();

        return true;
    }
    public boolean audioLoop() {
        if (audioSocket == null || audioInput == null || audioBuffer == null || audioTrack == null) {
            return true;
        }
        if (audioSocket.isClosed()) {
            return true;
        }

        try {
            int read = audioInput.read(audioBuffer, 0, audioBuffer.length / 8);
            int written = audioTrack.write(audioBuffer, 0, read);
            if (written == AudioTrack.ERROR_INVALID_OPERATION) {
                debug(TAG, "error in audio processing loop: ERROR_INVALID_OPERATION");
                return false;
            }
            if (written == AudioTrack.ERROR_BAD_VALUE) {
                debug(TAG, "error in audio processing loop: ERROR_BAD_VALUE");
                return false;
            }
        } catch (IOException e) {
            debug(TAG, "error in audio processing loop");
            debugException(TAG, e);
            return false;
        }
        return true;
    }
    public void closeAudio() {
        if (audioSocket == null || audioInput == null || audioBuffer == null || audioTrack == null || audioClosed) {
            return;
        }
        audioClosed = true;

        audioTrack.stop();
        try {
            audioInput.close();
            audioSocket.close();
        } catch (IOException e) {
            debug(TAG, "error closing audio connection");
            debugException(TAG, e);
        }

        audioTrack = null;
        audioInput = null;
        audioSocket = null;
        audioBuffer = null;
    }
    public boolean isAudioConnected() {
        if (audioSocket == null) {
            return false;
        }
        return audioSocket.isConnected() && !audioClosed;
    }

    private void setSELinuxMode(boolean enforce) throws LinuxException {
        if (runCommandWait("[ -f /system/bin/setenforce ]") == 0) {
            int flag = enforce ? 1 : 0;
            String text = enforce ? "ENFORCING" : "PERMISSIVE";

            debug(TAG, "setting SELinux mode to "+text);
            if (runCommandWait("/system/bin/setenforce "+flag) != 0) {
                throw new LinuxException("failed to set SELinux mode to "+text);
            }
        }
    }

    public void boot() throws LinuxException {
        if (!isInitialized()) {
            throw new LinuxException("init() not called, cannot boot");
        }

        File mnt = new File(Constants.MOUNTPOINT);
        if (!mnt.exists()) {
            debug(TAG, "mountpoint '" + Constants.MOUNTPOINT + "' does not exist, creating...");
            if (runCommandWait("mkdir -p " + Constants.MOUNTPOINT) != 0) {
                throw new LinuxException("failed to create mountpoint");
            }
        }
        cleanChroot();

        if (runCommandWait("[ -b " + Constants.LOOP_DEV + " ]") != 0) {
            debug(TAG, "loop device does not exist, creating...");
            if (busyboxCommandWait("mknod " + Constants.LOOP_DEV + " b 7 255") != 0) {
                throw new LinuxException("failed to create loop device");
            }
        }

        boolean needInstall = false;
        String imgFile = new File(Constants.IMG_DIRECTORY, preferences.getString("img_name", Constants.IMG_NAME_DEFAULT)).getAbsolutePath();
        int imgSize = Integer.parseInt(preferences.getString("img_size", String.valueOf(Constants.IMG_SIZE_DEFAULT)));
        if (runCommandWait("[ -f " + imgFile + " ]") != 0) {
            debug(TAG, "chroot image '"+imgFile+"' doesn't exist, installing Droidtop (Arch) Linux...");
            debug(TAG, "building "+imgSize+"MB disk image and formatting as ext4...");
            needInstall = true;
            buildExt4Image(imgSize);
        }

        debug(TAG, "setting up loop device with image '" + imgFile + "'");
        if (busyboxCommandWait("losetup " + Constants.LOOP_DEV + " " + imgFile) != 0) {
            throw new LinuxException("failed to attach chroot image '" + imgFile + "' to loop device '" + Constants.LOOP_DEV + "'");
        }

        debug(TAG, "mounting filesystems...");
        mount("auto", Constants.LOOP_DEV, Constants.MOUNTPOINT);
        if (needInstall) {
            debug(TAG, "installing from tar archive '" + new File(Constants.IMG_DIRECTORY, Constants.ARCH_TAR) + "' to image file '" + imgFile + "'");
            setupInitialImage();
        }

        bindMount("/dev", Constants.MOUNTPOINT + "/dev");
        mountWithOpts("gid=5,mode=620", "devpts", "devpts", Constants.MOUNTPOINT + "/dev/pts");
        mount("proc", "proc", Constants.MOUNTPOINT + "/proc");
        mount("sysfs", "sysfs", Constants.MOUNTPOINT + "/sys");
        bindMount("/sdcard", Constants.MOUNTPOINT + "/internal");
        if (runCommandWait("chmod 777 " + Constants.MOUNTPOINT + "/internal") != 0) {
            throw new LinuxException("failed to set permissions on android internal storage mountpoint");
        }
        if (chrootCommandWait("/usr/bin/[ -h /dev/fd ]") != 0) {
            if (chrootCommandWait("/usr/bin/ln -s /proc/self/fd /dev/fd") != 0) {
                throw new LinuxException("failed to link /proc/self/fd to /dev/fd in chroot");
            }
        }

        debug(TAG, "enabling network forwarding...");
        if (busyboxCommandWait("sysctl -w net.ipv4.ip_forward=1") != 0) {
            throw new LinuxException("failed to forward network to chroot");
        }

        if (needInstall) {
            configureImage();
        }
        configureXorgDevices();

        debug(TAG, "chrooting and starting init...");
        rootShell.addCommand(busybox + " chroot " + Constants.MOUNTPOINT+" "+Constants.INIT_PROCESS+" -l DEBUG");

        try {
            Thread.sleep(2 * 1000);
        } catch (InterruptedException e) {
            debugException(TAG, e);
        }

        booted = true;

        try {
            debug(TAG, "connecting to init process...");
            initTransport = new TSocket("127.0.0.1", 2500, 5000);
            initTransport.open();

            TProtocol protocol = new TBinaryProtocol(initTransport);
            initClient = new DroidtopInit.Client(protocol);
        } catch (TTransportException e) {
            throw new LinuxException("failed to connect to droidtop_init process");
        }

        try {
            debug(TAG, "retrieving service list from init process");
            Map<String, String> services = initClient.getServices();
            for (Map.Entry<String, String> service : services.entrySet()) {
                debug(TAG, "got service: " + service.getValue() + " with id '" + service.getKey() + "'");
            }

            debug(TAG, "starting xorg server...");
            initClient.startService("xserver");

            Thread.sleep(3 * 1000);
        } catch (DroidtopError e) {
            debug(TAG, "DroidtopError: reason: " + e.getReason() + ", message: " + e.getMsg());
        } catch (Exception e) {
            if (e instanceof LinuxException) {
                throw (LinuxException)e;
            }
            debugException(TAG, e);
        }

        debug(TAG, "BOOTED! :D");
    }

    public String findProcess(String pattern) {
        List<String> output = Shell.SU.run(busybox + " pgrep -f \"" + pattern + "\"");
        if (output != null) {
            if (output.size() < 1) {
                return null;
            }
            String pid = output.get(0).replaceAll("\\s", "");
            return pid;
        }
        return null;
    }

    public void shutdown() throws LinuxException {
        if (!isInitialized()) {
            throw new LinuxException("init() not called, cannot shutdown");
        }
        if (!isBooted()) {
            throw new LinuxException("boot() not called, cannot shutdown");
        }
        debug(TAG, "shutting down...");

        if (findProcess(Constants.INIT_PROCESS) == null) {
            debug(TAG, "droidtop_init process is not running");
        }

        debug(TAG, "disconnecting audio...");
        closeAudio();

        try {
            debug(TAG, "disconnecting from init process...");
            initClient.shutdown();
            initTransport.close();
            try {
                Thread.sleep(3 * 1000);
            } catch (InterruptedException e) {
                debugException(TAG, e);
            }
        } catch (TException e) {
            if (e instanceof DroidtopError) {
                DroidtopError err = (DroidtopError)e;
                debug(TAG, "DroidtopError: reason: " + err.getReason() + ", message: " + err.getMsg());
            } else {
                debugException(TAG, e);
            }
            debug(TAG, "failed to send shutdown() to droidtop_init process, attempting to kill manually...");

            String pid = findProcess(Constants.INIT_PROCESS);
            if (pid != null) {
                debug(TAG, "sending SIGTERM to droidtop_init process with pid " + pid + "...");
                Shell.SU.run(busybox + " kill " + pid);
                try {
                    Thread.sleep(5 * 1000);
                } catch (InterruptedException e2) {
                    debugException(TAG, e2);
                }

                pid = findProcess(Constants.INIT_PROCESS);
                if (pid != null) {
                    debug(TAG, "failed to kill droidtop_init process, sending SIGKILL...");
                    Shell.SU.run(busybox + " kill -KILL " + pid);
                }
            } else {
                debug(TAG, "couldn't find droidtop_init process");
            }
        }

        if (busyboxCommandWait("fuser " + Constants.MOUNTPOINT) == 0) {
            /*debug(TAG, "mountpoint '"+MOUNTPOINT+"' is still in use, sleeping to allow debugging");
            try {
                Thread.sleep(60 * 1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }*/
            debug(TAG, "terminating remaining processes");
            busyboxCommandWait("fuser -SIGTERM -k " + Constants.MOUNTPOINT);
            try {
                Thread.sleep(5 * 1000);
            } catch (InterruptedException e) {
                debugException(TAG, e);
            }
            if (busyboxCommandWait("fuser " + Constants.MOUNTPOINT) == 0) {
                debug(TAG, "failed to terminate remaining processes gracefully, sending SIGKILL...");
                busyboxCommandWait("fuser -SIGKILL -k " + Constants.MOUNTPOINT);
                if (busyboxCommandWait("fuser " + Constants.MOUNTPOINT) == 0) {
                    throw new LinuxException("failed to kill processes in chroot");
                }
            }
        }

        /*debug(TAG, "cleaning "+MOUNTPOINT+"/tmp");
        if (busyboxCommandWait("rm -rf "+MOUNTPOINT+"/tmp") != 0) {
            debug(TAG, "failed to clean "+MOUNTPOINT+"/tmp");
        }*/

        debug(TAG, "unmounting filesystems...");
        unmount(Constants.MOUNTPOINT + "/internal");
        unmount(Constants.MOUNTPOINT + "/dev/pts");
        unmount(Constants.MOUNTPOINT + "/dev");
        unmount(Constants.MOUNTPOINT + "/proc");
        unmount(Constants.MOUNTPOINT + "/sys");
        unmount(Constants.MOUNTPOINT);

        debug(TAG, "tearing down loop device...");
        busyboxCommandWait("losetup -d " + Constants.LOOP_DEV);

        setSELinuxMode(true);

        booted = false;
    }

    public void close(boolean graceful) throws LinuxException {
        if (!isInitialized()) {
            throw new LinuxException("init() not called, cannot close");
        }
        debug(TAG, "closing down...");
        inputDevicesObserver.stopWatching();
        mouseDev = null;
        keyboardDev = null;
        if (graceful) {
            rootShell.close();
            initialized = false;
        } else {
            rootShell.kill();
            booted = false;
            initialized = false;
        }
        audioClosed = false;
        debug(TAG, "SHUTDOWN!");
    }

    public static class LinuxException extends Exception {
        public LinuxException(String msg) {
            super(msg);
        }
    }
}

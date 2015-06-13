package com.jackos2500.droidtop;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Point;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static com.jackos2500.droidtop.DroidtopApplication.debug;


public class LauncherActivity extends Activity {
    private static Handler handler;
    private static String logText = "";
    private static TextView log;
    private static SimpleDateFormat logFormat = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public static void printLog(String tag, String logText) {
        final String temp = "["+logFormat.format(new Date())+" "+tag+"] " + logText + "\n";
        LauncherActivity.logText += temp;
        if (log != null) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    log.append(temp);
                    ScrollView parent = (ScrollView)log.getParent();
                    parent.fullScroll(View.FOCUS_DOWN);
                }
            });
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        handler = new Handler();
        log = (TextView)findViewById(R.id.log_view);
        log.setText(logText);
        ScrollView parent = (ScrollView)log.getParent();
        parent.fullScroll(View.FOCUS_DOWN);
    }
    @Override
    protected void onPause() {
        super.onPause();
        log = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_launcher);

        PreferenceManager.setDefaultValues(this, R.xml.settings, false);
    }

    public void onButtonClick(View button) {
        Intent serviceIntent = new Intent(this, LinuxService.class);
        switch(button.getId()) {
            case R.id.start_button:
                startService(serviceIntent);
                break;
            case R.id.stop_button:
                serviceIntent.putExtra("stop", true);
                startService(serviceIntent);
                break;
            case R.id.gui_button:
                Intent startIntent = new Intent(this, DroidtopActivity.class);
                startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(startIntent);
                break;
            case R.id.clear_button:
                logText = "";
                log.setText("");
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_launcher_activity, menu);
        return true;
    }
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            Intent preferencesIntent = new Intent(this, SettingsActivity.class);
            startActivity(preferencesIntent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}

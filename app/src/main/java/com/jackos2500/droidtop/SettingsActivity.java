package com.jackos2500.droidtop;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;

import java.util.Arrays;
import java.util.List;

public class SettingsActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Display the PreferencesFragment as the main content.
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PreferencesFragment()).commit();
    }
    public static class PreferencesFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);

            // Load the preferences from an XML resource
            addPreferencesFromResource(R.xml.settings);

            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
            CharSequence[] availableResolutions = Util.getAvailableResolutions(getActivity());
            ListPreference resolutionPreference = (ListPreference)findPreference("resolution");
            Preference.OnPreferenceChangeListener resolutionListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary("Screen size of " + newValue);
                    return true;
                }
            };
            resolutionListener.onPreferenceChange(resolutionPreference, preferences.getString("resolution", (String) availableResolutions[0]));
            resolutionPreference.setOnPreferenceChangeListener(resolutionListener);
            resolutionPreference.setDefaultValue(availableResolutions[0]);
            resolutionPreference.setEntries(availableResolutions);
            resolutionPreference.setEntryValues(availableResolutions);

            Preference usernamePreference = findPreference("username");
            Preference.OnPreferenceChangeListener usernameChangeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary("System username will be '" + newValue + "'");
                    return true;
                }
            };
            usernameChangeListener.onPreferenceChange(usernamePreference, preferences.getString("username", Constants.DEFAULT_USERNAME));
            usernamePreference.setOnPreferenceChangeListener(usernameChangeListener);

            final List<String> imgSizesKeys = Arrays.asList(getResources().getStringArray(R.array.img_sizes));
            final String[] imgSizesValues = getResources().getStringArray(R.array.img_sizes_values);
            Preference.OnPreferenceChangeListener imgSizeListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary("System images will be " + imgSizesValues[imgSizesKeys.indexOf((String)newValue)]);
                    return true;
                }
            };
            Preference imgSizePreference = findPreference("img_size");
            imgSizeListener.onPreferenceChange(imgSizePreference, preferences.getString("img_size", String.valueOf(Constants.IMG_SIZE_DEFAULT)));
            imgSizePreference.setOnPreferenceChangeListener(imgSizeListener);

            Preference.OnPreferenceChangeListener imgNameListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary("System images will be called '"+newValue+"'");
                    return true;
                }
            };
            Preference imgNamePreference = findPreference("img_name");
            imgNameListener.onPreferenceChange(imgNamePreference, preferences.getString("img_name", Constants.IMG_NAME_DEFAULT));
            imgNamePreference.setOnPreferenceChangeListener(imgNameListener);

            Preference.OnPreferenceChangeListener hostnameListener = new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    preference.setSummary("Hostname for systems will be '"+newValue+"'");
                    return true;
                }
            };
            Preference hostnamePreference = findPreference("hostname");
            hostnameListener.onPreferenceChange(hostnamePreference, Constants.DEFAULT_HOSTNAME);
            hostnamePreference.setOnPreferenceChangeListener(hostnameListener);
        }
    }
}

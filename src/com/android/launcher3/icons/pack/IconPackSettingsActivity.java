/*
 * Copyright (C) 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.launcher3.icons.pack;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.widget.Toast;
import android.widget.Toolbar;

import androidx.core.view.WindowCompat;
import androidx.fragment.app.FragmentActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;

import java.util.Map;

/**
 * Lets the user pick the icon pack used for the launcher icons. The first entry is always the
 * system icons.
 *
 * <p>Built on a preference screen so the rows use exactly the same typography, height, padding and
 * touch feedback as the rest of the launcher settings and of the system settings.
 */
public class IconPackSettingsActivity extends FragmentActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_activity);

        setActionBar((Toolbar) findViewById(R.id.action_bar));
        WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        setTitle(R.string.icon_pack_title);

        if (savedInstanceState == null) {
            getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.content_frame, new IconPackFragment())
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /** The list of installed icon packs plus the masking switch. */
    public static class IconPackFragment extends PreferenceFragmentCompat {

        private IconPackManager mManager;

        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            Context context = requireContext();
            mManager = IconPackManager.get(context);
            mManager.reloadProviders();

            PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
            screen.setTitle(R.string.icon_pack_title);

            PreferenceCategory category = new PreferenceCategory(context);
            category.setTitle(R.string.icon_pack_list_title);
            screen.addPreference(category);

            String selected = mManager.getSelectedPack();
            category.addPreference(
                    createPackPreference(context, "", getString(R.string.icon_pack_default_label),
                            selected.isEmpty()));

            Map<String, CharSequence> providers = mManager.getProviderNames();
            if (providers.isEmpty()) {
                Preference empty = new Preference(context);
                empty.setTitle(R.string.icon_pack_no_market);
                empty.setEnabled(false);
                empty.setSelectable(false);
                category.addPreference(empty);
            } else {
                for (Map.Entry<String, CharSequence> entry : providers.entrySet()) {
                    category.addPreference(createPackPreference(context, entry.getKey(),
                            entry.getValue().toString(), entry.getKey().equals(selected)));
                }
            }

            SwitchPreferenceCompat mask = new SwitchPreferenceCompat(context);
            mask.setKey("mask_uncovered");
            mask.setTitle(R.string.icon_pack_use_mask);
            mask.setSummary(R.string.icon_pack_use_mask_summary);
            mask.setChecked(mManager.isMaskingEnabled());
            // The value lives in IconPackManager, not in the preference storage.
            mask.setPersistent(false);
            mask.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enabled = (Boolean) newValue;
                if (enabled != mManager.isMaskingEnabled()) {
                    mManager.setMaskingEnabled(enabled);
                    applyAndRestart();
                }
                return true;
            });
            screen.addPreference(mask);

            setPreferenceScreen(screen);
        }

        private Preference createPackPreference(Context context, String packageName, String label,
                boolean isSelected) {
            Preference preference = new Preference(context);
            preference.setTitle(label);
            if (isSelected) {
                preference.setSummary(R.string.icon_pack_selected);
            }
            preference.setPersistent(false);
            preference.setOnPreferenceClickListener(p -> {
                if (!packageName.equals(mManager.getSelectedPack())) {
                    mManager.setSelectedPack(packageName);
                    applyAndRestart();
                }
                return true;
            });
            return preference;
        }

        /** Drops the cached icons and restarts the launcher so that every icon is rebuilt. */
        private void applyAndRestart() {
            requireActivity().deleteDatabase(LauncherFiles.APP_ICONS_DB);
            Toast.makeText(requireContext(), R.string.icon_pack_applying, Toast.LENGTH_SHORT).show();

            // Restarting the process is the only way to make every already cached icon pick up the
            // new pack or the new masking setting.
            new Handler(Looper.getMainLooper()).postDelayed(() -> System.exit(0), 600);
        }
    }
}

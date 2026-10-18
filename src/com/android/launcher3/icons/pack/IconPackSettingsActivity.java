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

import android.app.Activity;
import android.graphics.Insets;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.launcher3.LauncherFiles;
import com.android.launcher3.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Lets the user pick the icon pack used for the launcher icons. The first entry is always the
 * system icons.
 */
public class IconPackSettingsActivity extends Activity {

    private final List<String> mPackages = new ArrayList<>();
    private final List<String> mLabels = new ArrayList<>();
    private IconPackManager mManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTitle(R.string.icon_pack_title);
        mManager = IconPackManager.get(this);
        mManager.reloadProviders();
        buildEntries();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        TextView header = new TextView(this);
        header.setText(R.string.icon_pack_title);
        header.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        int pad = dp(24);
        header.setPadding(pad, dp(20), pad, dp(12));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (mPackages.size() == 1) {
            // Only the "system default" entry: nothing to choose from.
            TextView empty = new TextView(this);
            empty.setText(R.string.icon_pack_no_market);
            empty.setPadding(pad, dp(16), pad, 0);
            root.addView(empty);
        } else {
            ListView list = new ListView(this);
            list.setAdapter(new ArrayAdapter<>(this,
                    android.R.layout.simple_list_item_single_choice, mLabels));
            list.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            list.setItemChecked(selectedIndex(), true);
            list.setDivider(null);
            list.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    onPackSelected(mPackages.get(position));
                }
            });
            root.addView(list, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }

        // The activity is edge to edge, so keep the content out of the status and navigation bars.
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View view, WindowInsets insets) {
                Insets bars = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
                return insets;
            }
        });

        setContentView(root);
    }

    private int dp(int value) {
        return Math.round(getResources().getDisplayMetrics().density * value);
    }

    private void buildEntries() {
        mPackages.clear();
        mLabels.clear();

        mPackages.add("");
        mLabels.add(getString(R.string.icon_pack_default_label));

        Map<String, CharSequence> providers = mManager.getProviderNames();
        for (Map.Entry<String, CharSequence> entry : providers.entrySet()) {
            mPackages.add(entry.getKey());
            mLabels.add(entry.getValue().toString());
        }
    }

    private int selectedIndex() {
        int index = mPackages.indexOf(mManager.getSelectedPack());
        return Math.max(index, 0);
    }

    private void onPackSelected(String packageName) {
        if (packageName.equals(mManager.getSelectedPack())) {
            finish();
            return;
        }
        mManager.setSelectedPack(packageName);

        // Drop the cached icons so they are rebuilt with the pack that is now selected.
        deleteDatabase(LauncherFiles.APP_ICONS_DB);
        Toast.makeText(this, R.string.icon_pack_applying, Toast.LENGTH_SHORT).show();

        // Restart the launcher process, which is the only way to make every already cached icon
        // pick up the new pack.
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override
            public void run() {
                System.exit(0);
            }
        }, 600);
    }
}

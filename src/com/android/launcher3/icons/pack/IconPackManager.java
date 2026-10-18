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

import static com.android.launcher3.util.Executors.MODEL_EXECUTOR;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Handler;
import android.util.Log;

import com.android.launcher3.util.ComponentKey;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

/**
 * Discovers the installed icon packs and resolves an icon for a component through the selected
 * pack.
 *
 * <p>The list of packs is refreshed when packages are added, removed or updated. The selection is
 * stored in shared preferences so that it survives a launcher restart.
 */
public class IconPackManager extends BroadcastReceiver {
    private static final String TAG = "IconPackManager";

    private static final String PREFS_NAME = "icon_pack_prefs";
    private static final String KEY_SELECTED = "selected_pack";
    private static final String KEY_MASK = "mask_uncovered";

    /** Intents that icon pack apps use to advertise themselves. */
    private static final String[] ICON_INTENT_ACTIONS = new String[]{
            "com.fede.launcher.THEME_ICONPACK",
            "com.anddoes.launcher.THEME",
            "com.novalauncher.THEME",
            "com.teslacoilsw.launcher.THEME",
            "com.gau.go.launcherex.theme",
            "org.adw.launcher.THEMES",
            "net.oneplus.launcher.icons.ACTION_PICK_ICON",
            "org.adw.launcher.icons.ACTION_PICK_ICON"
    };

    private static IconPackManager sInstance;

    public static synchronized IconPackManager get(Context context) {
        if (sInstance == null) {
            sInstance = new IconPackManager(context.getApplicationContext());
        }
        return sInstance;
    }

    private final Context mContext;
    private final Map<String, IconPack> mProviders = new HashMap<>();
    private final Handler mHandler = new Handler(MODEL_EXECUTOR.getLooper());

    private IconPackManager(Context context) {
        mContext = context;
        reloadProviders();

        IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        context.registerReceiver(this, filter, null, mHandler);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getData() == null) {
            return;
        }
        String pkg = intent.getData().getEncodedSchemeSpecificPart();
        if (pkg == null) {
            return;
        }
        Log.d(TAG, "Package changed: " + intent.getAction() + " " + pkg);

        // Drop the cached resources of the changed pack, then rebuild the provider list.
        synchronized (this) {
            mProviders.remove(pkg);
        }
        reloadProviders();
    }

    public synchronized void reloadProviders() {
        PackageManager pm = mContext.getPackageManager();
        Set<ResolveInfo> info = new HashSet<>();
        for (String action : ICON_INTENT_ACTIONS) {
            info.addAll(pm.queryIntentActivities(new Intent(action), PackageManager.GET_META_DATA));
        }

        // Remove packs that are gone.
        for (String packageName : new HashSet<>(mProviders.keySet())) {
            boolean found = false;
            for (ResolveInfo ri : info) {
                if (ri.activityInfo.packageName.equals(packageName)) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                mProviders.remove(packageName);
            }
        }

        // Add the new ones.
        for (ResolveInfo ri : info) {
            String packageName = ri.activityInfo.packageName;
            if (!mProviders.containsKey(packageName)) {
                ApplicationInfo ai = ri.activityInfo.applicationInfo;
                CharSequence label = ai.loadLabel(pm);
                mProviders.put(packageName, new IconPack(ai, label));
            }
        }

        // The selected pack may have been uninstalled: fall back to the system icons.
        String selected = getSelectedPack();
        if (!selected.isEmpty() && !mProviders.containsKey(selected)) {
            Log.e(TAG, "Selected icon pack " + selected + " is gone, resetting to the default");
            setSelectedPack("");
        }
    }

    /** Package names of the installed packs, mapped to their user visible label. */
    public synchronized Map<String, CharSequence> getProviderNames() {
        Map<String, CharSequence> titles = new HashMap<>();
        for (Map.Entry<String, IconPack> pack : mProviders.entrySet()) {
            titles.put(pack.getKey(), pack.getValue().getTitle());
        }
        return titles;
    }

    public String getSelectedPack() {
        return prefs().getString(KEY_SELECTED, "");
    }

    public void setSelectedPack(String packageName) {
        prefs().edit().putString(KEY_SELECTED, packageName == null ? "" : packageName).apply();
    }

    /**
     * Whether apps the pack has no drawable for should still get the pack's mask/back/upon layers
     * applied to their original icon.
     */
    public boolean isMaskingEnabled() {
        // Off by default: packs usually draw their back layer for their own icons, so putting it
        // behind a system icon looks like a coloured ring around it.
        return prefs().getBoolean(KEY_MASK, false);
    }

    public void setMaskingEnabled(boolean enabled) {
        prefs().edit().putBoolean(KEY_MASK, enabled).apply();
    }

    private SharedPreferences prefs() {
        return mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Creates a resolver for a component, or {@code null} when the selected pack does not cover it
     * (so that the caller can fall back to the system icon).
     */
    public synchronized IconResolver resolve(ComponentKey key) {
        String packPackage = getSelectedPack();
        if (packPackage.isEmpty()) {
            return null;
        }
        IconPack pack = mProviders.get(packPackage);
        if (pack == null) {
            return null;
        }
        try {
            IconPack.Data data = pack.getData(mContext.getPackageManager());
            if (data.drawables.containsKey(key.componentName)) {
                int drawableId = pack.getDrawableId(mContext.getPackageManager(),
                        key.componentName);
                if (drawableId != 0) {
                    return new IconResolverExternal(mContext.getPackageManager(), pack.getAi(),
                            drawableId, data.calendarPrefix.get(key.componentName));
                }
            }
            if (data.hasMasking() && isMaskingEnabled()) {
                return new IconResolverMasked(mContext, data, pack.getAi(), key.hashCode());
            }
        } catch (PackageManager.NameNotFoundException | XmlPullParserException | IOException e) {
            Log.e(TAG, "Failed to read icon pack " + packPackage, e);
        }
        return null;
    }
}

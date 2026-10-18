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
package com.android.launcher3.model;

import static com.android.launcher3.LauncherSettings.Favorites.CONTAINER_DESKTOP;
import static com.android.launcher3.LauncherSettings.Favorites.ITEM_TYPE_APPLICATION;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.launcher3.InvariantDeviceProfile;
import com.android.launcher3.LauncherModel.ModelUpdateTask;
import com.android.launcher3.model.data.AppInfo;
import com.android.launcher3.model.data.ItemInfo;
import com.android.launcher3.model.data.WorkspaceItemInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Places every installed app that has no shortcut yet onto the workspace, so that the home screen
 * becomes a full list of apps and no app is only reachable through the app drawer.
 *
 * <p>The task is idempotent: packages that already have a workspace shortcut (or that were placed
 * by an earlier run, remembered in shared preferences) are skipped. Free cells are found by
 * scanning row-major from the last used screen, and a new screen is appended when the grid is
 * full.
 */
public class WorkspaceAutoPopulateTask implements ModelUpdateTask {

    private static final String PREFS_NAME = "auto_populate_workspace";
    private static final String KEY_PLACED = "placed_packages";

    @Nullable
    private final String mOnlyPackage;

    public WorkspaceAutoPopulateTask() {
        this(null);
    }

    /** Populates a single package only, used when an app is installed while the launcher runs. */
    public WorkspaceAutoPopulateTask(@NonNull String packageName) {
        mOnlyPackage = packageName;
    }

    @Override
    public void execute(@NonNull ModelTaskController taskController,
            @NonNull BgDataModel dataModel, @NonNull AllAppsList allAppsList) {
        Context context = taskController.getContext();
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        Set<String> placed = new HashSet<>(prefs.getStringSet(KEY_PLACED, Set.of()));

        InvariantDeviceProfile idp = InvariantDeviceProfile.INSTANCE.get(context);
        int numColumns = Math.max(1, idp.numColumns);
        int numRows = Math.max(1, idp.numRows);

        Set<String> onWorkspace = new HashSet<>();
        Set<Long> occupied = new HashSet<>();
        int lastScreen = 0;
        synchronized (dataModel) {
            for (ItemInfo info : dataModel.itemsIdMap) {
                ComponentName cn = info.getTargetComponent();
                if (info.itemType == ITEM_TYPE_APPLICATION && cn != null) {
                    onWorkspace.add(cn.flattenToString());
                }
                if (info.container != CONTAINER_DESKTOP) {
                    continue;
                }
                lastScreen = Math.max(lastScreen, info.screenId);
                for (int x = info.cellX; x < info.cellX + Math.max(1, info.spanX); x++) {
                    for (int y = info.cellY; y < info.cellY + Math.max(1, info.spanY); y++) {
                        occupied.add(cellKey(info.screenId, x, y));
                    }
                }
            }
        }

        List<WorkspaceItemInfo> added = new ArrayList<>();
        int screenId = lastScreen;
        int cellX = 0;
        int cellY = 0;
        for (AppInfo app : allAppsList.data) {
            ComponentName cn = app.componentName;
            if (cn == null) {
                continue;
            }
            if (mOnlyPackage != null && !mOnlyPackage.equals(cn.getPackageName())) {
                continue;
            }
            String key = cn.flattenToString();
            if (onWorkspace.contains(key) || placed.contains(key)) {
                continue;
            }
            onWorkspace.add(key);
            placed.add(key);

            // Advance to the next free cell.
            while (cellY < numRows && occupied.contains(cellKey(screenId, cellX, cellY))) {
                if (++cellX >= numColumns) {
                    cellX = 0;
                    cellY++;
                }
            }
            if (cellY >= numRows) {
                screenId++;
                cellX = 0;
                cellY = 0;
            }
            occupied.add(cellKey(screenId, cellX, cellY));

            WorkspaceItemInfo item = app.makeWorkspaceItem(context);
            item.container = CONTAINER_DESKTOP;
            item.screenId = screenId;
            item.cellX = cellX;
            item.cellY = cellY;
            taskController.getModelWriter().addItemToDatabase(item, item.container, screenId,
                    cellX, cellY);
            added.add(item);

            if (++cellX >= numColumns) {
                cellX = 0;
                cellY++;
            }
        }

        prefs.edit().putStringSet(KEY_PLACED, placed).apply();
        if (!added.isEmpty()) {
            taskController.bindUpdatedWorkspaceItems(added);
        }
    }

    private static long cellKey(int screenId, int cellX, int cellY) {
        return ((long) screenId << 32) | ((long) cellX << 16) | cellY;
    }
}

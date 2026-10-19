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
        synchronized (dataModel) {
            for (ItemInfo info : dataModel.itemsIdMap) {
                ComponentName cn = info.getTargetComponent();
                if (info.itemType == ITEM_TYPE_APPLICATION && cn != null) {
                    onWorkspace.add(cn.flattenToString());
                }
                if (info.container != CONTAINER_DESKTOP) {
                    continue;
                }
                for (int x = info.cellX; x < info.cellX + Math.max(1, info.spanX); x++) {
                    for (int y = info.cellY; y < info.cellY + Math.max(1, info.spanY); y++) {
                        occupied.add(cellKey(info.screenId, x, y));
                    }
                }
            }
        }

        List<WorkspaceItemInfo> added = new ArrayList<>();
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

            // 从第一屏开始按「从上到下、从左到右」找第一个空位：
            // 之前是从最后一屏开始扫的，于是前几屏里微件旁边空出来的格永远不会被用到，
            // 新装的应用只能跑到最后一屏/新的一屏去。
            int[] freeCell = findFirstFreeCell(occupied, numColumns, numRows);
            int screenId = freeCell[0];
            int cellX = freeCell[1];
            int cellY = freeCell[2];
            occupied.add(cellKey(screenId, cellX, cellY));

            WorkspaceItemInfo item = app.makeWorkspaceItem(context);
            item.container = CONTAINER_DESKTOP;
            item.screenId = screenId;
            item.cellX = cellX;
            item.cellY = cellY;
            taskController.getModelWriter().addItemToDatabase(item, item.container, screenId,
                    cellX, cellY);
            added.add(item);

        }

        prefs.edit().putStringSet(KEY_PLACED, placed).apply();
        if (!added.isEmpty()) {
            taskController.bindUpdatedWorkspaceItems(added);
        }
    }

    /**
     * 返回第一个空位：屏幕从小到大，屏幕内从上到下、从左到右。
     * 所有已有屏幕都满时，返回下一屏的 (0, 0)。
     */
    private static int[] findFirstFreeCell(Set<Long> occupied, int numColumns, int numRows) {
        for (int screenId = 0; ; screenId++) {
            for (int cellY = 0; cellY < numRows; cellY++) {
                for (int cellX = 0; cellX < numColumns; cellX++) {
                    if (!occupied.contains(cellKey(screenId, cellX, cellY))) {
                        return new int[]{screenId, cellX, cellY};
                    }
                }
            }
        }
    }

    private static long cellKey(int screenId, int cellX, int cellY) {
        return ((long) screenId << 32) | ((long) cellX << 16) | cellY;
    }
}

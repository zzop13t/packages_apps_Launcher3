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
package com.android.launcher3.icons;

import android.content.Context;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

import com.android.launcher3.icons.pack.IconPackManager;
import com.android.launcher3.icons.pack.IconResolver;
import com.android.launcher3.util.ComponentKey;

/** Resolves an icon through the selected icon pack, or {@code null} when the pack has none. */
class ThirdPartyIconUtils {

    static Drawable getByKey(Context context, ComponentKey key, int iconDpi,
            IconResolver.DefaultDrawableProvider fallback) {
        IconResolver resolver = IconPackManager.get(context).resolve(key);
        if (resolver == null) {
            return null;
        }
        return wrapAsAdaptive(resolver.getIcon(iconDpi, fallback));
    }

    /**
     * Icon packs ship plain bitmaps, and the launcher wraps every non adaptive icon into an
     * adaptive one with a solid background plate. Claiming to be adaptive avoids the plate, but the
     * adaptive drawing would also clip the artwork to the launcher icon shape. {@link
     * IconPackDrawable} reports as adaptive and draws the pack artwork unchanged.
     */
    static Drawable wrapAsAdaptive(Drawable icon) {
        if (icon == null || icon instanceof AdaptiveIconDrawable) {
            return icon;
        }
        return new IconPackDrawable(icon);
    }
}

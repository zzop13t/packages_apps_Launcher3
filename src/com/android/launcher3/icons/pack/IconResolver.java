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

import android.graphics.drawable.Drawable;

/**
 * Resolves a single icon of an icon pack. Instances are short lived: the strategy can change when
 * the selected pack is updated or uninstalled, so they must not be cached by callers.
 */
public interface IconResolver {

    /**
     * Loads the icon of the pack.
     *
     * @param iconDpi  positive density, or non-positive to get the drawable at full scale
     * @param fallback used when the pack has no usable drawable for the component
     * @return the pack drawable, or {@code null} when the pack does not cover the component
     */
    Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback);

    interface DefaultDrawableProvider {
        Drawable get();
    }
}

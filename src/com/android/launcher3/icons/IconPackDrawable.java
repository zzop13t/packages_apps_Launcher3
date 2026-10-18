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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * Adaptive icon that draws an icon pack drawable as it is.
 *
 * <p>The launcher only leaves non adaptive icons alone if they are already an
 * {@link AdaptiveIconDrawable}: else it wraps them with a solid background plate. Reporting as an
 * adaptive icon avoids that plate, but the adaptive draw path would also clip the artwork to the
 * icon shape of the launcher. Icon packs ship their own shaped artwork, so the drawing is done here
 * directly instead.
 */
public class IconPackDrawable extends AdaptiveIconDrawable {

    private static final Path NO_MASK = new Path();

    private final Drawable mSource;

    public IconPackDrawable(@NonNull Drawable source) {
        // The artwork is also the foreground layer: the launcher draws the layers directly (and
        // ignores draw()) when icons are rendered full bleed. The background stays transparent so
        // no plate is added.
        super(new ColorDrawable(Color.TRANSPARENT), source);
        mSource = source;
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect bounds = getBounds();
        mSource.setBounds(bounds.left, bounds.top, bounds.right, bounds.bottom);
        mSource.draw(canvas);
    }

    /** An empty mask keeps the icon from getting a shadow in the shape of the launcher icon. */
    @Override
    public Path getIconMask() {
        return NO_MASK;
    }
}

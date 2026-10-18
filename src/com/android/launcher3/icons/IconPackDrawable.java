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

import android.content.res.Resources;
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
 * directly instead. Implementing {@link SelfShapedIcon} also tells the icon loader not to clip it
 * with the launcher icon shape.
 */
public class IconPackDrawable extends AdaptiveIconDrawable implements SelfShapedIcon {

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
        // Match what BaseIconFactory does when it rasterises a non adaptive icon: the artwork is
        // inset to ICON_VISIBLE_AREA_FACTOR of the icon size. Drawing it edge to edge here would
        // make the icon look bigger during the launch/close transition than when it settles.
        Rect bounds = getBounds();
        float insetFraction = (1f - IconNormalizer.ICON_VISIBLE_AREA_FACTOR) / 2f;
        int insetX = Math.round(bounds.width() * insetFraction);
        int insetY = Math.round(bounds.height() * insetFraction);
        mSource.setBounds(bounds.left + insetX, bounds.top + insetY,
                bounds.right - insetX, bounds.bottom - insetY);
        mSource.draw(canvas);
    }

    /** An empty mask keeps the icon from getting a shadow in the shape of the launcher icon. */
    @Override
    public Path getIconMask() {
        return NO_MASK;
    }

    /**
     * Cloning through {@link AdaptiveIconDrawable}'s constant state would produce a plain
     * AdaptiveIconDrawable: the pack artwork would then be drawn inside the adaptive mask (i.e. the
     * launcher icon shape) and the transition would show a different shape than the settled icon.
     * Keep the class by providing our own constant state.
     */
    @Override
    public ConstantState getConstantState() {
        return new ConstantState() {
            @Override
            public Drawable newDrawable() {
                return new IconPackDrawable(cloneSource());
            }

            @Override
            public Drawable newDrawable(Resources res) {
                return new IconPackDrawable(cloneSource());
            }

            @Override
            public int getChangingConfigurations() {
                return 0;
            }
        };
    }

    private Drawable cloneSource() {
        ConstantState state = mSource.getConstantState();
        return state != null ? state.newDrawable() : mSource;
    }
}

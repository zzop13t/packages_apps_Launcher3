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

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import com.android.launcher3.icons.BaseIconFactory;
import com.android.launcher3.icons.LauncherIcons;

/**
 * Builds an icon by drawing the original icon, scaling it, then applying the pack's mask, back and
 * upon layers. Used by packs that do not ship a drawable per app but restyle all icons.
 */
public class IconResolverMasked implements IconResolver {
    private final Context mContext;
    private final IconPack.Data mData;
    private final ApplicationInfo mPackInfo;
    private final int mHashCode;

    private final Canvas mCanvas = new Canvas();
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    IconResolverMasked(Context context, IconPack.Data data, ApplicationInfo packInfo,
            int hashCode) {
        mContext = context;
        mData = data;
        mPackInfo = packInfo;
        mHashCode = hashCode & 0xFFFF;
    }

    @Override
    public Drawable getIcon(int iconDpi, DefaultDrawableProvider fallback) {
        Drawable icon = fallback.get();
        if (icon == null) {
            return null;
        }

        LauncherIcons li = LauncherIcons.obtain(mContext);
        PackageManager pm = mContext.getPackageManager();
        try {
            Resources res = pm.getResourcesForApplication(mPackInfo);

            // Rasterise the icon at the launcher icon size. Do not wrap it into an adaptive
            // plate and do not add a shadow: both would draw a visible ring around the icon,
            // which then shows through everything the pack masks.
            BaseIconFactory.IconOptions options = new BaseIconFactory.IconOptions()
                    .setWrapNonAdaptiveIcon(false)
                    .setAddShadows(false)
                    .setBitmapGenerationMode(BaseIconFactory.MODE_DEFAULT);
            Bitmap iconBm = li.createBadgedIconBitmap(icon, options).icon;

            scaleBitmap(iconBm, mData.scale);

            if (!mData.iconMasks.isEmpty()) {
                int iconMask = mData.iconMasks.get(mHashCode % mData.iconMasks.size());
                maskBitmap(iconBm, res.getDrawableForDensity(iconMask, iconDpi, null));
            }

            if (!mData.iconBacks.isEmpty()) {
                int iconBack = mData.iconBacks.get(mHashCode % mData.iconBacks.size());
                backBitmap(iconBm, res.getDrawableForDensity(iconBack, iconDpi, null));
            }

            // Render the overlay on top, staying inside the icon bounds.
            if (!mData.iconUpons.isEmpty()) {
                int iconUpon = mData.iconUpons.get(mHashCode % mData.iconUpons.size());
                uponBitmap(iconBm, res.getDrawableForDensity(iconUpon, iconDpi, null));
            }

            li.recycle();
            return new BitmapDrawable(mContext.getResources(), iconBm);
        } catch (PackageManager.NameNotFoundException | Resources.NotFoundException e) {
            android.util.Log.w("IconPack", "Failed to build masked icon", e);
        }

        li.recycle();
        return icon;
    }

    private void scaleBitmap(Bitmap bitmap, float scale) {
        if (scale != 1f) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap,
                    (int) (bitmap.getWidth() * scale),
                    (int) (bitmap.getHeight() * scale),
                    true);

            float move = 0.5f * (1f - scale);

            Matrix matrix = new Matrix();
            matrix.postTranslate(move * bitmap.getWidth(), move * bitmap.getHeight());

            bitmap.eraseColor(Color.TRANSPARENT);
            mCanvas.setBitmap(bitmap);
            mCanvas.drawBitmap(scaled, matrix, null);

            scaled.recycle();
        }
    }

    private void maskBitmap(Bitmap bitmap, Drawable mask) {
        if (mask != null) {
            Bitmap maskBm = Bitmap.createBitmap(
                    bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

            mCanvas.setBitmap(maskBm);
            mask.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            mask.draw(mCanvas);

            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OUT));
            mCanvas.setBitmap(bitmap);
            mCanvas.drawBitmap(maskBm, 0f, 0f, mPaint);

            maskBm.recycle();
        }
    }

    @SuppressLint("WrongConstant")
    private void backBitmap(Bitmap bitmap, Drawable back) {
        if (back != null) {
            Bitmap backBm = Bitmap.createBitmap(
                    bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

            mCanvas.setBitmap(backBm);
            back.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            back.draw(mCanvas);

            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_OVER));
            mCanvas.setBitmap(bitmap);
            mCanvas.drawBitmap(backBm, 0f, 0f, mPaint);

            backBm.recycle();
        }
    }

    private void uponBitmap(Bitmap bitmap, Drawable upon) {
        if (upon != null) {
            Bitmap uponBm = Bitmap.createBitmap(
                    bitmap.getWidth(), bitmap.getHeight(), Bitmap.Config.ARGB_8888);

            mCanvas.setBitmap(uponBm);
            upon.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight());
            upon.draw(mCanvas);

            mPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
            mCanvas.setBitmap(bitmap);
            mCanvas.drawBitmap(uponBm, 0f, 0f, mPaint);

            uponBm.recycle();
        }
    }
}

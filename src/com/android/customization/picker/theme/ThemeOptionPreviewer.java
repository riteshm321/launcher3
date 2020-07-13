/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.customization.picker.theme;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.customization.model.theme.ThemeBundle;
import com.android.customization.model.theme.ThemeBundle.PreviewInfo;
import com.android.customization.picker.TimeTicker;
import com.android.wallpaper.R;

import java.text.DateFormat;
import java.text.FieldPosition;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** A class to load the {@link ThemeBundle} preview to the view. */
class ThemeOptionPreviewer implements LifecycleObserver {

    /**
     * Maps which icon from ResourceConstants#ICONS_FOR_PREVIEW to use for each icon in the
     * top bar (fake "status bar") of the cover page.
     */
    private static final int [] sTopBarIconToPreviewIcon = new int [] { 0, 6, 7 };

    private int[] mShapeIconIds = {
            R.id.shape_preview_icon_0, R.id.shape_preview_icon_1,
            R.id.shape_preview_icon_2, R.id.shape_preview_icon_3
    };

    private final Context mContext;

    private View mContentView;
    private TextView mClock;
    private TimeTicker mTicker;

    ThemeOptionPreviewer(Lifecycle lifecycle, Context context, ViewGroup previewContainer) {
        lifecycle.addObserver(this);

        mContext = context;
        mContentView = LayoutInflater.from(context).inflate(
                R.layout.theme_preview_content_v2, previewContainer);
        mClock = mContentView.findViewById(R.id.theme_preview_clock);
        updateTime();
    }

    /** Loads the Theme option into the container view. */
    public void setThemeBundle(ThemeBundle themeBundle) {
        PreviewInfo previewInfo = themeBundle.getPreviewInfo();
        setHeadlineFont(previewInfo.headlineFontFamily);
        setTopBarIcons(previewInfo.icons);
        setShapeIcons(previewInfo.shapeAppIcons);
        setQsbRadius(previewInfo.bottomSheeetCornerRadius);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    @MainThread
    public void onResume() {
        mTicker = TimeTicker.registerNewReceiver(mContext, this::updateTime);
        updateTime();
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @MainThread
    public void onPause() {
        if (mContext != null) {
            mContext.unregisterReceiver(mTicker);
        }
    }

    private void setHeadlineFont(Typeface headlineFont) {
        mClock.setTypeface(headlineFont);

        // Update other text style here.
    }

    private void setTopBarIcons(List<Drawable> icons) {
        ViewGroup iconsContainer = mContentView.findViewById(R.id.theme_preview_top_bar_icons);
        for (int i = 0; i < iconsContainer.getChildCount(); i++) {
            int iconIndex = sTopBarIconToPreviewIcon[i];
            if (iconIndex < icons.size()) {
                ((ImageView) iconsContainer.getChildAt(i))
                        .setImageDrawable(icons.get(iconIndex).getConstantState()
                                .newDrawable().mutate());
            } else {
                iconsContainer.getChildAt(i).setVisibility(View.GONE);
            }
        }
    }

    private void setShapeIcons(List<Drawable> icons) {
        for (int i = 0; i < mShapeIconIds.length && i < icons.size(); i++) {
            ImageView iconView = mContentView.findViewById(mShapeIconIds[i]);
            iconView.setBackground(icons.get(i));
        }
    }

    private void setQsbRadius(int cornerRadius) {
        View qsb = mContentView.findViewById(R.id.theme_qsb);
        if (qsb != null && qsb.getVisibility() == View.VISIBLE) {
            if (qsb.getBackground() instanceof GradientDrawable) {
                GradientDrawable bg = (GradientDrawable) qsb.getBackground();
                float radius = useRoundedQSB(cornerRadius)
                        ? (float) qsb.getLayoutParams().height / 2 : cornerRadius;
                bg.setCornerRadii(new float[]{
                        radius, radius, radius, radius,
                        radius, radius, radius, radius});
            }
        }
    }

    private void updateTime() {
        if (mClock != null) {
            mClock.setText(getFormattedTime());
        }
    }

    private String getFormattedTime() {
        DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);
        StringBuffer time = new StringBuffer();
        FieldPosition amPmPosition = new FieldPosition(DateFormat.Field.AM_PM);
        df.format(Calendar.getInstance(TimeZone.getDefault()).getTime(), time, amPmPosition);
        if (amPmPosition.getBeginIndex() > 0) {
            time.delete(amPmPosition.getBeginIndex(), amPmPosition.getEndIndex());
        }
        return time.toString();
    }

    private boolean useRoundedQSB(int cornerRadius) {
        return cornerRadius >= mContext.getResources().getDimensionPixelSize(
                R.dimen.roundCornerThreshold);
    }
}

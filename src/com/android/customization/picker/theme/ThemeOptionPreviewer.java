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
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.cardview.widget.CardView;
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
    // Maps which icon from ResourceConstants#ICONS_FOR_PREVIEW.
    private static final int ICON_WIFI = 0;
    private static final int ICON_BLUETOOTH = 1;
    private static final int ICON_FLASHLIGHT = 3;
    private static final int ICON_AUTO_ROTATE = 4;
    private static final int ICON_CELLULAR_SIGNAL = 6;
    private static final int ICON_BATTERY = 7;

    // Icons in the top bar (fake "status bar") with the particular order.
    private static final int [] sTopBarIconToPreviewIcon = new int [] {
            ICON_WIFI, ICON_CELLULAR_SIGNAL, ICON_BATTERY };

    // Ids of app icon shape preview.
    private int[] mShapeAppIconIds = {
            R.id.shape_preview_icon_0, R.id.shape_preview_icon_1,
            R.id.shape_preview_icon_2, R.id.shape_preview_icon_3
    };
    private int[] mShapeIconAppNameIds = {
            R.id.shape_preview_icon_app_name_0, R.id.shape_preview_icon_app_name_1,
            R.id.shape_preview_icon_app_name_2, R.id.shape_preview_icon_app_name_3
    };

    // Ids of color/icons section.
    private int[][] mColorTileIconIds = {
            new int[] { R.id.preview_color_qs_0_icon, ICON_WIFI},
            new int[] { R.id.preview_color_qs_1_icon, ICON_BLUETOOTH},
            new int[] { R.id.preview_color_qs_2_icon, ICON_FLASHLIGHT},
            new int[] { R.id.preview_color_qs_3_icon, ICON_AUTO_ROTATE},
    };
    private int[] mColorTileIds = {
            R.id.preview_color_qs_0_bg, R.id.preview_color_qs_1_bg,
            R.id.preview_color_qs_2_bg, R.id.preview_color_qs_3_bg
    };
    private int[] mColorButtonIds = {
            R.id.preview_check_selected, R.id.preview_radio_selected, R.id.preview_toggle_selected
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
        setAppIconShape(previewInfo.shapeAppIcons);
        setColorAndIconsSection(previewInfo.icons, previewInfo.shapeDrawable,
                previewInfo.resolveAccentColor(mContext.getResources()));
        setColorAndIconsBoxRadius(previewInfo.bottomSheeetCornerRadius);
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

        TextView date = mContentView.findViewById(R.id.smart_space_date);
        date.setTypeface(headlineFont);
        // TODO(chihhangchuang): Use real date.
        date.setText("Friday, Nov 12");

        // TODO(chihhangchuang): Query the app name for icon shapes, we can get package name from
        // res/values/override.xml to query the app name.
        for (int id : mShapeIconAppNameIds) {
            TextView appName = mContentView.findViewById(id);
            appName.setTypeface(headlineFont);
        }

        TextView colorIconsSectionTitle = mContentView.findViewById(R.id.color_icons_section_title);
        colorIconsSectionTitle.setTypeface(headlineFont);
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

    private void setAppIconShape(List<Drawable> appIcons) {
        for (int i = 0; i < mShapeAppIconIds.length && i < appIcons.size(); i++) {
            ImageView iconView = mContentView.findViewById(mShapeAppIconIds[i]);
            iconView.setBackground(appIcons.get(i));
        }
    }

    private void setColorAndIconsSection(List<Drawable> icons, Drawable shapeDrawable,
                                         int accentColor) {
        // Set QS icons and background.
        for (int i = 0; i < mColorTileIconIds.length && i < icons.size(); i++) {
            Drawable icon = icons.get(mColorTileIconIds[i][1]).getConstantState()
                    .newDrawable().mutate();
            Drawable bgShape = shapeDrawable.getConstantState().newDrawable();
            bgShape.setTint(accentColor);

            ImageView bg = mContentView.findViewById(mColorTileIds[i]);
            bg.setImageDrawable(bgShape);
            ImageView fg = mContentView.findViewById(mColorTileIconIds[i][0]);
            fg.setImageDrawable(icon);
        }

        // Set color for Buttons (CheckBox, RadioButton, and Switch).
        ColorStateList tintList = getColorStateList(accentColor);
        for (int mColorButtonId : mColorButtonIds) {
            CompoundButton button = mContentView.findViewById(mColorButtonId);
            button.setButtonTintList(tintList);
            if (button instanceof Switch) {
                ((Switch) button).setThumbTintList(tintList);
                ((Switch) button).setTrackTintList(tintList);
            }
        }
    }

    private void setColorAndIconsBoxRadius(int cornerRadius) {
        ((CardView) mContentView.findViewById(R.id.color_icons_section)).setRadius(cornerRadius);
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

    private ColorStateList getColorStateList(int accentColor) {
        int controlGreyColor = mContext.getColor(R.color.control_grey);
        return new ColorStateList(
                new int[][]{
                        new int[]{android.R.attr.state_selected},
                        new int[]{android.R.attr.state_checked},
                        new int[]{-android.R.attr.state_enabled},
                },
                new int[] {
                        accentColor,
                        accentColor,
                        controlGreyColor
                }
        );
    }
}

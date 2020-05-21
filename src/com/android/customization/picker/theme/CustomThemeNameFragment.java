/*
 * Copyright (C) 2019 The Android Open Source Project
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

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.customization.model.theme.ThemeBundle.PreviewInfo;
import com.android.customization.picker.WallpaperPreviewer;
import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.widget.WallpaperColorsLoader;

/** Fragment of naming a custom theme. */
public class CustomThemeNameFragment extends CustomThemeStepFragment {

    public static CustomThemeNameFragment newInstance(CharSequence toolbarTitle, int position,
            int titleResId) {
        CustomThemeNameFragment fragment = new CustomThemeNameFragment();
        Bundle arguments = AppbarFragment.createArguments(toolbarTitle);
        arguments.putInt(ARG_KEY_POSITION, position);
        arguments.putInt(ARG_KEY_TITLE_RES_ID, titleResId);
        fragment.setArguments(arguments);
        return fragment;
    }

    private EditText mNameEditor;
    private ImageView mWallpaperImage;
    private WallpaperInfo mCurrentHomeWallpaper;
    private ThemeOptionPreviewer mThemeOptionPreviewer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = super.onCreateView(inflater, container, savedInstanceState);
        mTitle = view.findViewById(R.id.component_options_title);
        mTitle.setText(mTitleResId);
        mNameEditor = view.findViewById(R.id.custom_theme_name);
        mNameEditor.setText(mCustomThemeManager.getOriginalTheme().getTitle());
        CurrentWallpaperInfoFactory currentWallpaperFactory = InjectorProvider.getInjector()
                .getCurrentWallpaperFactory(getActivity().getApplicationContext());

        // Set wallpaper background.
        mWallpaperImage = view.findViewById(R.id.wallpaper_preview_image);
        final WallpaperPreviewer wallpaperPreviewer = new WallpaperPreviewer(
                getLifecycle(),
                getActivity(),
                mWallpaperImage,
                view.findViewById(R.id.wallpaper_preview_surface));
        currentWallpaperFactory.createCurrentWallpaperInfos(
                (homeWallpaper, lockWallpaper, presentationMode) -> {
                    mCurrentHomeWallpaper = homeWallpaper;
                    wallpaperPreviewer.setWallpaper(homeWallpaper);
                    updateThemePreviewColorPerWallpaper();
                }, false);

        // Set theme option.
        mThemeOptionPreviewer = new ThemeOptionPreviewer(
                getLifecycle(),
                getContext(),
                view.findViewById(R.id.theme_preview_container));
        PreviewInfo previewInfo = mCustomThemeManager.buildCustomThemePreviewInfo(getContext());
        mThemeOptionPreviewer.setPreviewInfo(previewInfo);

        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                wallpaperPreviewer.updatePreviewCardRadius();
                updateThemePreviewColorPerWallpaper();
                view.removeOnLayoutChangeListener(this);
            }
        });
        return view;
    }

    private void updateThemePreviewColorPerWallpaper() {
        if (mCurrentHomeWallpaper != null && mWallpaperImage.getMeasuredWidth() > 0
                && mWallpaperImage.getMeasuredHeight() > 0) {
            WallpaperColorsLoader.getWallpaperColors(
                    getContext(),
                    mCurrentHomeWallpaper.getThumbAsset(getContext()),
                    mWallpaperImage.getMeasuredWidth(),
                    mWallpaperImage.getMeasuredHeight(),
                    mThemeOptionPreviewer::updateColorForLauncherWidgets);
        }
    }

    @Override
    protected int getFragmentLayoutResId() {
        return R.layout.fragment_custom_theme_name;
    }

    public String getThemeName() {
        return mNameEditor.getText().toString();
    }
}

/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.customization.picker.grid;

import static android.app.Activity.RESULT_OK;

import static com.android.customization.picker.ViewOnlyFullPreviewActivity.SECTION_GRID;
import static com.android.customization.picker.grid.GridFullPreviewFragment.EXTRA_GRID_OPTION;
import static com.android.customization.picker.grid.GridFullPreviewFragment.EXTRA_GRID_USES_SURFACE_VIEW;
import static com.android.customization.picker.grid.GridFullPreviewFragment.EXTRA_WALLPAPER_INFO;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.customization.picker.ViewOnlyFullPreviewActivity;
import com.android.customization.picker.WallpaperPreviewer;
import com.android.customization.widget.OptionSelectorController;
import com.android.wallpaper.R;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.widget.BottomActionBar;

import com.bumptech.glide.Glide;

import java.util.List;

/**
 * Fragment that contains the UI for selecting and applying a GridOption.
 */
public class GridFragment extends AppbarFragment {

    private static final int FULL_PREVIEW_REQUEST_CODE = 1000;

    private static final String TAG = "GridFragment";

    /**
     * Interface to be implemented by an Activity hosting a {@link GridFragment}
     */
    public interface GridFragmentHost {
        GridOptionsManager getGridOptionsManager();
    }

    public static GridFragment newInstance(CharSequence title) {
        GridFragment fragment = new GridFragment();
        fragment.setArguments(AppbarFragment.createArguments(title));
        return fragment;
    }

    private WallpaperInfo mHomeWallpaper;
    private RecyclerView mOptionsContainer;
    private OptionSelectorController<GridOption> mOptionsController;
    private GridOptionsManager mGridManager;
    private GridOption mSelectedOption;
    private ContentLoadingProgressBar mLoading;
    private ViewGroup mGridPreviewContainer;
    private View mContent;
    private View mError;
    private BottomActionBar mBottomActionBar;
    private ThemesUserEventLogger mEventLogger;
    private boolean mReloadOptionsAfterApplying;

    private GridOptionPreviewer mGridOptionPreviewer;

    private final Callback mApplyGridCallback = new Callback() {
        @Override
        public void onSuccess() {
            mGridManager.fetchOptions(new OptionsFetchedListener<GridOption>() {
                @Override
                public void onOptionsLoaded(List<GridOption> options) {
                    mOptionsController.resetOptions(options);
                    mSelectedOption = getSelectedOption(options);
                    mReloadOptionsAfterApplying = true;
                    // It will trigger OptionSelectedListener#onOptionSelected.
                    mOptionsController.setSelectedOption(mSelectedOption);
                    Toast.makeText(getContext(), R.string.applied_grid_msg, Toast.LENGTH_SHORT)
                            .show();
                    // Since we disabled it when clicked apply button.
                    mBottomActionBar.enableActions();
                    mBottomActionBar.hide();
                }

                @Override
                public void onError(@Nullable Throwable throwable) {
                    if (throwable != null) {
                        Log.e(TAG, "Error loading grid options", throwable);
                    }
                    showError();
                }
            }, true);
        }

        @Override
        public void onError(@Nullable Throwable throwable) {
            //TODO(chihhangchuang): handle
        }
    };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mGridManager = ((GridFragmentHost) context).getGridOptionsManager();
        mEventLogger = (ThemesUserEventLogger)
                InjectorProvider.getInjector().getUserEventLogger(context);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_grid_picker, container, /* attachToRoot */ false);
        setUpToolbar(view);
        mGridPreviewContainer = view.findViewById(R.id.grid_preview_container);
        mContent = view.findViewById(R.id.content_section);
        mOptionsContainer = view.findViewById(R.id.options_container);
        mLoading = view.findViewById(R.id.loading_indicator);
        mError = view.findViewById(R.id.error_section);

        // Clear memory cache whenever grid fragment view is being loaded.
        Glide.get(getContext()).clearMemory();
        setUpOptions();

        ImageView wallpaperPreviewImage = view.findViewById(R.id.wallpaper_preview_image);
        wallpaperPreviewImage.setOnClickListener(v -> showFullPreview());
        SurfaceView wallpaperSurface = view.findViewById(R.id.wallpaper_preview_surface);
        WallpaperPreviewer wallpaperPreviewer = new WallpaperPreviewer(
                getLifecycle(), getActivity(), wallpaperPreviewImage, wallpaperSurface);

        // Loads current Wallpaper.
        CurrentWallpaperInfoFactory factory = InjectorProvider.getInjector()
                .getCurrentWallpaperFactory(getContext().getApplicationContext());
        factory.createCurrentWallpaperInfos((homeWallpaper, lockWallpaper, presentationMode) -> {
            mHomeWallpaper = homeWallpaper;
            wallpaperPreviewer.setWallpaper(mHomeWallpaper);
        }, false);

        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                wallpaperPreviewer.updatePreviewCardRadius();
                view.removeOnLayoutChangeListener(this);
            }
        });
        return view;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mGridOptionPreviewer != null) {
            mGridOptionPreviewer.release();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FULL_PREVIEW_REQUEST_CODE && resultCode == RESULT_OK) {
            applyGridOption(data.getParcelableExtra(EXTRA_GRID_OPTION));
        }
    }


    @Override
    protected void onBottomActionBarReady(BottomActionBar bottomActionBar) {
        mBottomActionBar = bottomActionBar;
        mBottomActionBar.showActionsOnly(APPLY);
        mBottomActionBar.setActionClickListener(APPLY, unused -> applyGridOption(mSelectedOption));
    }

    private void applyGridOption(GridOption gridOption) {
        mBottomActionBar.disableActions();
        mGridManager.apply(gridOption, mApplyGridCallback);
    }

    private void updatePreview() {
        if (mGridOptionPreviewer != null) {
            mGridOptionPreviewer.release();
        }
        mGridOptionPreviewer = new GridOptionPreviewer(
                getContext(), mGridManager, mGridPreviewContainer);
        mGridOptionPreviewer.setGridOption(mSelectedOption, mGridManager.usesSurfaceView());
    }

    private void setUpOptions() {
        hideError();
        mLoading.show();
        mGridManager.fetchOptions(new OptionsFetchedListener<GridOption>() {
            @Override
            public void onOptionsLoaded(List<GridOption> options) {
                mLoading.hide();
                mOptionsController = new OptionSelectorController<>(mOptionsContainer, options);

                mOptionsController.addListener(selected -> {
                    mSelectedOption = (GridOption) selected;
                    if (mReloadOptionsAfterApplying) {
                        mReloadOptionsAfterApplying = false;
                        return;
                    }
                    mBottomActionBar.show();
                    mEventLogger.logGridSelected(mSelectedOption);
                    updatePreview();
                });
                mOptionsController.initOptions(mGridManager);
                mSelectedOption = getSelectedOption(options);
                updatePreview();
            }

            @Override
            public void onError(@Nullable Throwable throwable) {
                if (throwable != null) {
                    Log.e(TAG, "Error loading grid options", throwable);
                }
                showError();
            }
        }, false);
    }

    private GridOption getSelectedOption(List<GridOption> options) {
        return options.stream()
                .filter(option -> option.isActive(mGridManager))
                .findAny()
                // For development only, as there should always be a grid set.
                .orElse(options.get(0));
    }

    private void hideError() {
        mContent.setVisibility(View.VISIBLE);
        mError.setVisibility(View.GONE);
    }

    private void showError() {
        mLoading.hide();
        mContent.setVisibility(View.GONE);
        mError.setVisibility(View.VISIBLE);
    }

    private void showFullPreview() {
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_WALLPAPER_INFO, mHomeWallpaper);
        bundle.putParcelable(EXTRA_GRID_OPTION, mSelectedOption);
        bundle.putBoolean(EXTRA_GRID_USES_SURFACE_VIEW, mGridManager.usesSurfaceView());
        Intent intent = ViewOnlyFullPreviewActivity.newIntent(getContext(), SECTION_GRID, bundle);
        startActivityForResult(intent, FULL_PREVIEW_REQUEST_CODE);
    }
}

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
package com.android.customization.picker.grid;

import static android.app.Activity.RESULT_OK;

import static com.android.customization.picker.grid.GridFragment.PREVIEW_FADE_DURATION_MS;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;

import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.model.grid.LauncherGridOptionsProvider;
import com.android.customization.module.CustomizationInjector;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.util.SurfaceViewUtils;
import com.android.wallpaper.util.TileSizeCalculator;
import com.android.wallpaper.widget.BottomActionBar;

import com.bumptech.glide.request.RequestOptions;

/** A Fragment for grid full preview page. */
public class GridFullPreviewFragment extends AppbarFragment {

    static final String EXTRA_WALLPAPER_INFO = "wallpaper_info";
    static final String EXTRA_GRID_OPTION = "grid_option";

    private GridOptionsManager mGridManager;
    private WallpaperInfo mWallpaper;
    private GridOption mGridOption;

    private CardView mCardView;
    private ImageView mPreview;
    private SurfaceView mPreviewSurface;
    private BitmapDrawable mCardBackground;

    /**
     * Returns a new {@link GridFullPreviewFragment} with the provided title and bundle arguments
     * set.
     */
    public static GridFullPreviewFragment newInstance(CharSequence title, Bundle intentBundle) {
        GridFullPreviewFragment fragment = new GridFullPreviewFragment();
        Bundle bundle = new Bundle();
        bundle.putAll(AppbarFragment.createArguments(title));
        bundle.putAll(intentBundle);
        fragment.setArguments(bundle);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mWallpaper = getArguments().getParcelable(EXTRA_WALLPAPER_INFO);
        mGridOption = getArguments().getParcelable(EXTRA_GRID_OPTION);

        CustomizationInjector injector = (CustomizationInjector) InjectorProvider.getInjector();
        ThemesUserEventLogger eventLogger = (ThemesUserEventLogger) injector.getUserEventLogger(
                getContext());

        mGridManager = new GridOptionsManager(new LauncherGridOptionsProvider(getContext(),
                getString(R.string.grid_control_metadata_name)),
                eventLogger);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_grid_full_preview, container, /* attachToRoot */ false);
        setUpToolbar(view);

        mCardView = view.findViewById(R.id.grid_full_preview_card);
        mPreview = view.findViewById(R.id.grid_full_preview_image);
        mPreviewSurface = view.findViewById(R.id.grid_full_preview_surface);

        final DisplayMetrics dm = getResources().getDisplayMetrics();
        float screenAspectRatio = (float) dm.heightPixels / dm.widthPixels;

        view.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                int cardWidth = (int) (mCardView.getMeasuredHeight() / screenAspectRatio);
                ViewGroup.LayoutParams layoutParams = mCardView.getLayoutParams();
                layoutParams.width = cardWidth;
                mCardView.setLayoutParams(layoutParams);
                mCardView.setRadius(TileSizeCalculator.getPreviewCornerRadius(
                        getActivity(), mCardView.getMeasuredWidth()));
                view.removeOnLayoutChangeListener(this);
                loadWallpaperBackground();
            }
        });

        // Needs to fetch for the result of #usesSurfaceView.
        mGridManager.fetchOptions(grids -> bindPreviewContent(), false);
        return view;
    }

    @Override
    protected void onBottomActionBarReady(BottomActionBar bottomActionBar) {
        bottomActionBar.bindBackButtonToSystemBackKey(getActivity());
        bottomActionBar.showActionsOnly(APPLY);
        bottomActionBar.setActionClickListener(APPLY, v -> finishActivityWithResultOk());
        bottomActionBar.show();
    }

    private void finishActivityWithResultOk() {
        Activity activity = requireActivity();
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_GRID_OPTION, mGridOption);
        activity.setResult(RESULT_OK, intent);
        activity.finish();
    }

    private void loadWallpaperBackground() {
        if (mWallpaper != null && mCardView.getMeasuredWidth() > 0
                && mCardView.getMeasuredHeight() > 0) {
            mWallpaper.getThumbAsset(getContext()).decodeBitmap(mCardView.getMeasuredWidth(),
                    mCardView.getMeasuredHeight(),
                    bitmap -> {
                        mCardBackground = new BitmapDrawable(getResources(), bitmap);
                        bindWallpaperIfAvailable();
                    });
        }
    }

    private void bindPreviewContent() {
        bindWallpaperIfAvailable();
        final boolean usesSurfaceViewForPreview = mGridManager.usesSurfaceView();
        mPreview.setVisibility(usesSurfaceViewForPreview ? View.GONE : View.VISIBLE);
        mPreviewSurface.setVisibility(usesSurfaceViewForPreview ? View.VISIBLE : View.GONE);
        if (usesSurfaceViewForPreview) {
            mPreviewSurface.setZOrderOnTop(true);
            mPreviewSurface.getHolder().addCallback(mSurfaceCallback);
        } else {
            final Asset previewAsset = new ContentUriAsset(
                    getContext(),
                    mGridOption.previewImageUri,
                    RequestOptions.fitCenterTransform());
            previewAsset.loadDrawableWithTransition(getContext(),
                    mPreview /* imageView */,
                    PREVIEW_FADE_DURATION_MS /* duration */,
                    null /* drawableLoadedListener */,
                    getResources().getColor(android.R.color.transparent,
                            null) /* placeHolderColorJ */);
        }
    }

    private void bindWallpaperIfAvailable() {
        if (mCardBackground != null) {
            mPreview.setBackground(mCardBackground);
            mPreviewSurface.setBackground(mCardBackground);
        }
    }

    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {

        private Surface mLastSurface;
        private Message mCallback;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mLastSurface != holder.getSurface()) {
                mLastSurface = holder.getSurface();
                Bundle result = mGridManager.renderPreview(
                        SurfaceViewUtils.createSurfaceViewRequest(mPreviewSurface),
                        mGridOption.name);
                if (result != null) {
                    mPreviewSurface.setChildSurfacePackage(
                            SurfaceViewUtils.getSurfacePackage(result));
                    mCallback = SurfaceViewUtils.getCallback(result);
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width,
                                   int height) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (mCallback != null) {
                try {
                    mCallback.replyTo.send(mCallback);
                } catch (RemoteException e) {
                    e.printStackTrace();
                } finally {
                    mCallback = null;
                }
            }
        }
    };
}

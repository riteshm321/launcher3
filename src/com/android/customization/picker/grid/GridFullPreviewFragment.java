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
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.customization.picker.grid.GridFragment.PREVIEW_FADE_DURATION_MS;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.service.wallpaper.WallpaperService;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;

import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.model.grid.LauncherGridOptionsProvider;
import com.android.customization.module.CustomizationInjector;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.util.SizeCalculator;
import com.android.wallpaper.util.SurfaceViewUtils;
import com.android.wallpaper.util.WallpaperConnection;
import com.android.wallpaper.widget.BottomActionBar;
import com.android.wallpaper.widget.LiveTileOverlay;

import com.bumptech.glide.request.RequestOptions;

/** A Fragment for grid full preview page. */
public class GridFullPreviewFragment extends AppbarFragment {

    static final String EXTRA_WALLPAPER_INFO = "wallpaper_info";
    static final String EXTRA_GRID_OPTION = "grid_option";
    static final String EXTRA_GRID_USES_SURFACE_VIEW = "uses_surface_view";

    private final Rect mPreviewLocalRect = new Rect();
    private final Rect mPreviewGlobalRect = new Rect();
    private final int[] mLivePreviewLocation = new int[2];

    private GridOptionsManager mGridManager;
    private WallpaperInfo mWallpaper;
    private GridOption mGridOption;
    private boolean mUsesSurfaceView;

    private CardView mCardView;
    private ImageView mHomePreview;
    private SurfaceView mGridOptionSurface;
    private SurfaceView mWallpaperSurface;
    private WallpaperConnection mWallpaperConnection;

    // Home workspace surface is behind the app window, and so must the home image wallpaper like
    // the live wallpaper. This view is rendered on mWallpaperSurface for home image wallpaper.
    private ImageView mHomeImageWallpaper;

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
        mUsesSurfaceView = getArguments().getBoolean(EXTRA_GRID_USES_SURFACE_VIEW);

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
        mHomePreview = view.findViewById(R.id.grid_full_preview_image);
        mGridOptionSurface = view.findViewById(R.id.grid_full_preview_option_surface);
        mWallpaperSurface = view.findViewById(R.id.grid_full_preview_wallpaper_surface);
        mGridOptionSurface.setVisibility(View.GONE);

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
                mCardView.setRadius(SizeCalculator.getPreviewCornerRadius(
                        getActivity(), mCardView.getMeasuredWidth()));
                view.removeOnLayoutChangeListener(this);
            }
        });
        return view;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        updateWallpaperSurface();
        updateWorkspaceSurface();
    }

    @Override
    protected void onBottomActionBarReady(BottomActionBar bottomActionBar) {
        bottomActionBar.bindBackButtonToSystemBackKey(getActivity());
        bottomActionBar.showActionsOnly(APPLY);
        bottomActionBar.setActionClickListener(APPLY, v -> finishActivityWithResultOk());
        bottomActionBar.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
            mWallpaperConnection = null;
        }
    }

    private void finishActivityWithResultOk() {
        Activity activity = requireActivity();
        activity.overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        Intent intent = new Intent();
        intent.putExtra(EXTRA_GRID_OPTION, mGridOption);
        activity.setResult(RESULT_OK, intent);
        activity.finish();
    }

    private void updateWallpaperSurface() {
        mWallpaperSurface.setZOrderMediaOverlay(false);
        mWallpaperSurface.getHolder().addCallback(mWallpaperSurfaceCallback);
    }

    private void updateWorkspaceSurface() {
        if (mUsesSurfaceView) {
            mGridOptionSurface.setZOrderOnTop(true);
            mGridOptionSurface.getHolder().addCallback(mGridOptionSurfaceCallback);
            mGridOptionSurface.setVisibility(View.VISIBLE);
        } else {
            final Asset previewAsset = new ContentUriAsset(
                    getContext(),
                    mGridOption.previewImageUri,
                    RequestOptions.fitCenterTransform());
            previewAsset.loadDrawableWithTransition(getContext(),
                    mHomePreview /* imageView */,
                    PREVIEW_FADE_DURATION_MS /* duration */,
                    null /* drawableLoadedListener */,
                    getResources().getColor(android.R.color.transparent,
                            null) /* placeHolderColorJ */);
        }
    }

    private void setUpWallpaperPreview() {
        if (mWallpaper != null && mHomeImageWallpaper != null) {
            boolean renderInImageWallpaperSurface = !(mWallpaper instanceof LiveWallpaperInfo);
            mWallpaper.getThumbAsset(getContext())
                    .loadPreviewImage(getActivity(),
                            renderInImageWallpaperSurface ? mHomeImageWallpaper : mHomePreview,
                            getResources().getColor(R.color.secondary_color));
            LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
            if (mWallpaper instanceof LiveWallpaperInfo) {
                mWallpaper.getThumbAsset(getContext().getApplicationContext())
                        .loadPreviewImage(
                                getActivity(),
                                mHomeImageWallpaper,
                                getContext().getColor(R.color.secondary_color));
                setUpLiveWallpaperPreview(mWallpaper);
            } else {
                if (mWallpaperConnection != null) {
                    mWallpaperConnection.disconnect();
                    mWallpaperConnection = null;
                }
            }
        }
    }

    private void setUpLiveWallpaperPreview(WallpaperInfo homeWallpaper) {
        Activity activity = getActivity();
        if (activity == null) {
            return;
        }

        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }

        mHomePreview.getLocationOnScreen(mLivePreviewLocation);
        mPreviewGlobalRect.set(0, 0, mHomePreview.getMeasuredWidth(),
                mHomePreview.getMeasuredHeight());
        mPreviewLocalRect.set(mPreviewGlobalRect);
        mPreviewGlobalRect.offset(mLivePreviewLocation[0], mLivePreviewLocation[1]);

        mWallpaperConnection = new WallpaperConnection(
                getWallpaperIntent(homeWallpaper.getWallpaperComponent()), activity,
                new WallpaperConnection.WallpaperConnectionListener() {
                    @Override
                    public void onEngineShown() {}
                }, mPreviewGlobalRect);

        LiveTileOverlay.INSTANCE.update(new RectF(mPreviewLocalRect), mCardView.getRadius());

        mWallpaperConnection.setVisibility(true);
        mHomePreview.post(() -> {
            if (!mWallpaperConnection.connect()) {
                mWallpaperConnection = null;
                LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
            }
        });
    }

    private Intent getWallpaperIntent(android.app.WallpaperInfo info) {
        return new Intent(WallpaperService.SERVICE_INTERFACE)
                .setClassName(info.getPackageName(), info.getServiceName());
    }

    private final SurfaceHolder.Callback mGridOptionSurfaceCallback = new SurfaceHolder.Callback() {

        private Surface mLastSurface;
        private Message mCallback;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mLastSurface != holder.getSurface()) {
                mLastSurface = holder.getSurface();
                Bundle result = mGridManager.renderPreview(
                        SurfaceViewUtils.createSurfaceViewRequest(mGridOptionSurface),
                        mGridOption.name);
                if (result != null) {
                    mGridOptionSurface.setChildSurfacePackage(
                            SurfaceViewUtils.getSurfacePackage(result));
                    mCallback = SurfaceViewUtils.getCallback(result);
                }
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

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

    private final SurfaceHolder.Callback mWallpaperSurfaceCallback = new SurfaceHolder.Callback() {

        private Surface mLastSurface;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mLastSurface != holder.getSurface()) {
                mLastSurface = holder.getSurface();
                mHomeImageWallpaper = new ImageView(getContext());
                mHomeImageWallpaper.setBackgroundColor(
                        ContextCompat.getColor(getContext(), R.color.primary_color));
                mHomeImageWallpaper.measure(makeMeasureSpec(mHomePreview.getWidth(), EXACTLY),
                        makeMeasureSpec(mHomePreview.getHeight(), EXACTLY));
                mHomeImageWallpaper.layout(0, 0, mHomePreview.getWidth(), mHomePreview.getHeight());

                SurfaceControlViewHost host = new SurfaceControlViewHost(getContext(),
                        getContext().getDisplay(), mWallpaperSurface.getHostToken());
                host.setView(mHomeImageWallpaper, mHomeImageWallpaper.getWidth(),
                        mHomeImageWallpaper.getHeight());
                mWallpaperSurface.setChildSurfacePackage(host.getSurfacePackage());
            }
            setUpWallpaperPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {}
    };
}

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
package com.android.customization.picker;

import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import android.app.Activity;
import android.app.WallpaperColors;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.service.wallpaper.WallpaperService;
import android.view.Surface;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.wallpaper.R;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.SizeCalculator;
import com.android.wallpaper.util.WallpaperConnection;
import com.android.wallpaper.util.WallpaperConnection.WallpaperConnectionListener;
import com.android.wallpaper.widget.LiveTileOverlay;
import com.android.wallpaper.widget.WallpaperColorsLoader;

/** A class to load the wallpaper to the view. */
public class WallpaperPreviewer implements LifecycleObserver {

    private final Rect mPreviewLocalRect = new Rect();
    private final Rect mPreviewGlobalRect = new Rect();
    private final int[] mLivePreviewLocation = new int[2];

    private final Activity mActivity;
    private final ImageView mHomePreview;
    private final SurfaceView mWallpaperSurface;
    private final WallpaperSurfaceCallback mWallpaperSurfaceCallback =
            new WallpaperSurfaceCallback();

    private WallpaperInfo mWallpaper;
    private WallpaperConnection mWallpaperConnection;
    // Home workspace surface is behind the app window, and so must the home image wallpaper like
    // the live wallpaper. This view is rendered on mWallpaperSurface for home image wallpaper.
    private ImageView mHomeImageWallpaper;
    @Nullable private WallpaperColorsListener mWallpaperColorsListener;

    /** Interface for getting {@link WallpaperColors} from wallpaper. */
    public interface WallpaperColorsListener {
        /** Gets called when wallpaper color is available or updated. */
        void onWallpaperColorsChanged(WallpaperColors colors);
    }

    public WallpaperPreviewer(Lifecycle lifecycle, Activity activity, ImageView homePreview,
                              SurfaceView wallpaperSurface) {
        lifecycle.addObserver(this);

        mActivity = activity;
        mHomePreview = homePreview;
        mWallpaperSurface = wallpaperSurface;
        mWallpaperSurface.setZOrderMediaOverlay(false);
        mWallpaperSurface.getHolder().addCallback(mWallpaperSurfaceCallback);

        View rootView = homePreview.getRootView();
        rootView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
            @Override
            public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                       int oldLeft, int oldTop, int oldRight, int oldBottom) {
                updatePreviewCardRadius();
                rootView.removeOnLayoutChangeListener(this);
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    @MainThread
    public void onResume() {
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(true);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    @MainThread
    public void onPause() {
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(false);
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    @MainThread
    public void onStop() {
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
            mWallpaperConnection = null;
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    @MainThread
    public void onDestroy() {
        LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
            mWallpaperConnection = null;
        }

        mWallpaperSurfaceCallback.cleanUp();
        mWallpaperSurface.getHolder().removeCallback(mWallpaperSurfaceCallback);
        Surface surface = mWallpaperSurface.getHolder().getSurface();
        if (surface != null) {
            surface.release();
        }
    }

    /**
     * Sets a wallpaper to be shown on preview screen.
     *
     * @param wallpaperInfo the wallpaper to preview
     * @param listener the listener for getting the wallpaper color of {@param wallpaperInfo}
     */
    public void setWallpaper(WallpaperInfo wallpaperInfo,
                             @Nullable WallpaperColorsListener listener) {
        mWallpaper = wallpaperInfo;
        mWallpaperColorsListener = listener;
        setUpWallpaperPreview();
    }

    private void setUpWallpaperPreview() {
        if (mWallpaper != null && mHomeImageWallpaper != null) {
            boolean renderInImageWallpaperSurface = !(mWallpaper instanceof LiveWallpaperInfo);
            mWallpaper.getThumbAsset(mActivity.getApplicationContext())
                    .loadPreviewImage(mActivity,
                            renderInImageWallpaperSurface ? mHomeImageWallpaper : mHomePreview,
                            mActivity.getResources().getColor(R.color.secondary_color));
            LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
            if (mWallpaper instanceof LiveWallpaperInfo) {
                mWallpaper.getThumbAsset(mActivity.getApplicationContext())
                        .loadPreviewImage(
                                mActivity,
                                mHomeImageWallpaper,
                                mActivity.getColor(R.color.secondary_color));
                setUpLiveWallpaperPreview(mWallpaper);
            } else {
                // Ensure live wallpaper connection is disconnected.
                if (mWallpaperConnection != null) {
                    mWallpaperConnection.disconnect();
                    mWallpaperConnection = null;
                }

                // Load wallpaper color for static wallpaper.
                if (mWallpaperColorsListener != null) {
                    WallpaperColorsLoader.getWallpaperColors(
                            mActivity,
                            mWallpaper.getThumbAsset(mActivity),
                            mWallpaperColorsListener::onWallpaperColorsChanged);
                }
            }
        }
    }

    private void setUpLiveWallpaperPreview(WallpaperInfo homeWallpaper) {
        if (mActivity == null || mActivity.isFinishing()) {
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
                getWallpaperIntent(homeWallpaper.getWallpaperComponent()), mActivity,
                new WallpaperConnectionListener() {
                    @Override
                    public void onWallpaperColorsChanged(WallpaperColors colors, int displayId) {
                        if (mWallpaperColorsListener != null) {
                            mWallpaperColorsListener.onWallpaperColorsChanged(colors);
                        }
                    }
                }, mPreviewGlobalRect);

        LiveTileOverlay.INSTANCE.update(new RectF(mPreviewLocalRect),
                ((CardView) mHomePreview.getParent()).getRadius());

        mWallpaperConnection.setVisibility(true);
        mHomePreview.post(() -> {
            if (mWallpaperConnection != null && !mWallpaperConnection.connect()) {
                mWallpaperConnection = null;
                LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
            }
        });
    }

    /** Updates the preview card view corner radius to match the device corner radius. */
    private void updatePreviewCardRadius() {
        final float screenAspectRatio =
                ScreenSizeCalculator.getInstance().getScreenAspectRatio(mActivity);
        CardView cardView = (CardView) mHomePreview.getParent();
        final int cardWidth = (int) (cardView.getMeasuredHeight() / screenAspectRatio);
        ViewGroup.LayoutParams layoutParams = cardView.getLayoutParams();
        layoutParams.width = cardWidth;
        cardView.setLayoutParams(layoutParams);
        cardView.setRadius(SizeCalculator.getPreviewCornerRadius(mActivity, cardWidth));
    }

    private static Intent getWallpaperIntent(android.app.WallpaperInfo info) {
        return new Intent(WallpaperService.SERVICE_INTERFACE)
                .setClassName(info.getPackageName(), info.getServiceName());
    }

    private class WallpaperSurfaceCallback implements SurfaceHolder.Callback {

        private Surface mLastSurface;
        private SurfaceControlViewHost mHost;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mLastSurface != holder.getSurface()) {
                mLastSurface = holder.getSurface();
                mHomeImageWallpaper = new ImageView(mActivity);
                mHomeImageWallpaper.setBackgroundColor(
                        ContextCompat.getColor(mActivity, R.color.primary_color));
                mHomeImageWallpaper.measure(makeMeasureSpec(mHomePreview.getWidth(), EXACTLY),
                        makeMeasureSpec(mHomePreview.getHeight(), EXACTLY));
                mHomeImageWallpaper.layout(0, 0, mHomePreview.getWidth(), mHomePreview.getHeight());

                cleanUp();
                mHost = new SurfaceControlViewHost(mActivity,
                        mActivity.getDisplay(), mWallpaperSurface.getHostToken());
                mHost.setView(mHomeImageWallpaper, mHomeImageWallpaper.getWidth(),
                        mHomeImageWallpaper.getHeight());
                mWallpaperSurface.setChildSurfacePackage(mHost.getSurfacePackage());
            }
            setUpWallpaperPreview();
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {}

        public void cleanUp() {
            if (mHost != null) {
                mHost.release();
                mHost = null;
            }
        }
    }
}

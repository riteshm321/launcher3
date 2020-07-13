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

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;
import com.android.wallpaper.util.SurfaceViewUtils;

import com.bumptech.glide.request.RequestOptions;

/** A class to load the {@link GridOption} preview to the view. */
class GridOptionPreviewer {

    private static final int PREVIEW_FADE_DURATION_MS = 100;

    private final Context mContext;
    private final GridOptionsManager mGridManager;
    private final ViewGroup mPreviewContainer;

    private SurfaceView mGridOptionSurface;
    private GridOption mGridOption;

    GridOptionPreviewer(Context context, GridOptionsManager gridManager,
                        ViewGroup previewContainer) {
        mContext = context;
        mGridManager = gridManager;
        mPreviewContainer = previewContainer;
    }

    /** Loads the Grid option into the container view. */
    public void setGridOption(GridOption gridOption, boolean usesSurfaceView) {
        mGridOption = gridOption;
        updateWorkspacePreview(usesSurfaceView);
    }

    /** Releases the view resource. */
    public void release() {
        if (mGridOptionSurface != null) {
            mGridOptionSurface.getHolder().removeCallback(mSurfaceCallback);
            Surface surface = mGridOptionSurface.getHolder().getSurface();
            if (surface != null) {
                surface.release();
            }
        }
        mPreviewContainer.removeAllViews();
    }

    private void updateWorkspacePreview(boolean usesSurfaceView) {
        if (mGridOption == null) {
            return;
        }
        mPreviewContainer.removeAllViews();

        if (usesSurfaceView) {
            mGridOptionSurface = new SurfaceView(mContext);
            setUpView(mGridOptionSurface);
            mGridOptionSurface.setZOrderMediaOverlay(true);
            mGridOptionSurface.getHolder().addCallback(mSurfaceCallback);
        } else {
            final ImageView previewImage = new ImageView(mContext);
            setUpView(previewImage);
            final Asset previewAsset = new ContentUriAsset(
                    mContext,
                    mGridOption.previewImageUri,
                    RequestOptions.fitCenterTransform());
            previewAsset.loadDrawableWithTransition(mContext,
                    previewImage /* imageView */,
                    PREVIEW_FADE_DURATION_MS /* duration */,
                    null /* drawableLoadedListener */,
                    mContext.getResources().getColor(android.R.color.transparent,
                            null) /* placeHolderColorJ */);
        }
    }

    private void setUpView(View view) {
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        mPreviewContainer.addView(view);
    }

    private final SurfaceHolder.Callback mSurfaceCallback = new SurfaceHolder.Callback() {
        private Surface mLastSurface;
        private Message mCallback;

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            if (mLastSurface != holder.getSurface() && mGridOption != null) {
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
                    mCallback.recycle();
                    mCallback = null;
                }
            }
        }
    };
}

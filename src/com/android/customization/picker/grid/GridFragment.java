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
import static android.view.View.MeasureSpec.EXACTLY;
import static android.view.View.MeasureSpec.makeMeasureSpec;

import static com.android.customization.picker.ViewOnlyFullPreviewActivity.SECTION_GRID;
import static com.android.customization.picker.grid.GridFullPreviewFragment.EXTRA_GRID_OPTION;
import static com.android.customization.picker.grid.GridFullPreviewFragment.EXTRA_GRID_USES_SURFACE_VIEW;
import static com.android.customization.picker.grid.GridFullPreviewFragment.EXTRA_WALLPAPER_INFO;
import static com.android.wallpaper.widget.BottomActionBar.BottomAction.APPLY;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.graphics.RectF;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.RemoteException;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceControlViewHost;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.widget.ContentLoadingProgressBar;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customization.model.CustomizationManager.Callback;
import com.android.customization.model.CustomizationManager.OptionsFetchedListener;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.customization.picker.BasePreviewAdapter;
import com.android.customization.picker.BasePreviewAdapter.PreviewPage;
import com.android.customization.picker.ViewOnlyFullPreviewActivity;
import com.android.customization.widget.OptionSelectorController;
import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.ContentUriAsset;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.picker.AppbarFragment;
import com.android.wallpaper.util.SurfaceViewUtils;
import com.android.wallpaper.util.WallpaperConnection;
import com.android.wallpaper.widget.BottomActionBar;
import com.android.wallpaper.widget.LiveTileOverlay;
import com.android.wallpaper.widget.PreviewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;

import java.util.List;

/**
 * Fragment that contains the UI for selecting and applying a GridOption.
 */
public class GridFragment extends AppbarFragment {

    static final int PREVIEW_FADE_DURATION_MS = 100;

    private static final int FULL_PREVIEW_REQUEST_CODE = 1000;

    private static final String TAG = "GridFragment";

    private final Rect mPreviewLocalRect = new Rect();
    private final Rect mPreviewGlobalRect = new Rect();
    private final int[] mLivePreviewLocation = new int[2];

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
    private GridPreviewAdapter mAdapter;
    private RecyclerView mOptionsContainer;
    private OptionSelectorController<GridOption> mOptionsController;
    private GridOptionsManager mGridManager;
    private GridOption mSelectedOption;
    private PreviewPager mPreviewPager;
    private ContentLoadingProgressBar mLoading;
    private View mContent;
    private View mError;
    private BottomActionBar mBottomActionBar;
    private ThemesUserEventLogger mEventLogger;
    private boolean mReloadOptionsAfterApplying;

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
        mContent = view.findViewById(R.id.content_section);
        mPreviewPager = view.findViewById(R.id.grid_preview_pager);
        mOptionsContainer = view.findViewById(R.id.options_container);
        mLoading = view.findViewById(R.id.loading_indicator);
        mError = view.findViewById(R.id.error_section);

        // Clear memory cache whenever grid fragment view is being loaded.
        Glide.get(getContext()).clearMemory();
        setUpOptions();

        CurrentWallpaperInfoFactory factory = InjectorProvider.getInjector()
                .getCurrentWallpaperFactory(getContext().getApplicationContext());

        factory.createCurrentWallpaperInfos((homeWallpaper, lockWallpaper, presentationMode) ->
                mHomeWallpaper = homeWallpaper, false);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mAdapter != null) {
            mAdapter.setWallpaperConnectionVisibility(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mAdapter != null) {
            mAdapter.setWallpaperConnectionVisibility(false);
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (mAdapter != null) {
            mAdapter.disconnectWallpaperConnection();
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

    private void createAdapter() {
        mAdapter = new GridPreviewAdapter(mSelectedOption);
        mPreviewPager.setAdapter(mAdapter);
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
                    createAdapter();
                });
                mOptionsController.initOptions(mGridManager);
                mSelectedOption = getSelectedOption(options);
                createAdapter();
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

    private Intent getWallpaperIntent(android.app.WallpaperInfo info) {
        return new Intent(WallpaperService.SERVICE_INTERFACE)
                .setClassName(info.getPackageName(), info.getServiceName());
    }

    // TODO(b/156059583): Remove the usage of PreviewPage, and add util class to load live wallpaper
    // for GridFragment and GridFullPreviewFragment.
    private class GridPreviewPage extends PreviewPage {
        private final int mPageId;
        private final Asset mPreviewAsset;
        private final int mCols;
        private final int mRows;
        private final Activity mActivity;

        private final String mName;

        private ImageView mHomePreview;
        private SurfaceView mGridPreviewSurface;
        private SurfaceView mWallpaperSurface;

        private WallpaperConnection mWallpaperConnection;

        // Home workspace surface is behind the app window, and so must the home image wallpaper
        // like the live wallpaper. This view is rendered on mWallpaperSurface for home image
        // wallpaper.
        private ImageView mHomeImageWallpaper;

        private final SurfaceHolder.Callback mGridSurfaceCallback = new SurfaceHolder.Callback() {

            private Surface mLastSurface;
            private Message mCallback;

            @Override
            public void surfaceCreated(SurfaceHolder holder) {
                if (mLastSurface != holder.getSurface()) {
                    mLastSurface = holder.getSurface();
                    Bundle result = mGridManager.renderPreview(
                            SurfaceViewUtils.createSurfaceViewRequest(mGridPreviewSurface), mName);
                    if (result != null) {
                        mGridPreviewSurface.setChildSurfacePackage(
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

        private final SurfaceHolder.Callback mWallpaperSurfaceCallback =
                new SurfaceHolder.Callback() {
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
                    mHomeImageWallpaper.layout(
                            0, 0, mHomePreview.getWidth(), mHomePreview.getHeight());

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

        private GridPreviewPage(Activity activity, int id, Uri previewUri, String name, int rows,
                int cols) {
            super(null, activity);
            mPageId = id;
            mPreviewAsset = new ContentUriAsset(activity, previewUri,
                    RequestOptions.fitCenterTransform());
            mName = name;
            mRows = rows;
            mCols = cols;
            mActivity = activity;
        }

        @Override
        public void setCard(CardView card) {
            super.setCard(card);
            mHomePreview = card.findViewById(R.id.grid_preview_image);
            mGridPreviewSurface = card.findViewById(R.id.grid_preview_surface);
            mWallpaperSurface = card.findViewById(R.id.wallpaper_surface);
            mGridPreviewSurface.setVisibility(View.GONE);
            // PreviewSurface is the top of its window(card view), due to #setZOrderOnTop(true).
            mGridPreviewSurface.setOnClickListener(view -> showFullPreview());
        }

        @Override
        public void bindPreviewContent() {
            updateWallpaperSurface();
            updateWorkspaceSurface();
        }

        private void updateWallpaperSurface() {
            mWallpaperSurface.setZOrderMediaOverlay(false);
            mWallpaperSurface.getHolder().addCallback(mWallpaperSurfaceCallback);
        }

        private void updateWorkspaceSurface() {
            final boolean usesSurfaceViewForPreview = mGridManager.usesSurfaceView();
            if (usesSurfaceViewForPreview) {
                mGridPreviewSurface.setZOrderOnTop(true);
                mGridPreviewSurface.getHolder().addCallback(mGridSurfaceCallback);
                mGridPreviewSurface.setVisibility(View.VISIBLE);
            } else {
                mPreviewAsset.loadDrawableWithTransition(mActivity,
                        mHomePreview /* imageView */,
                        PREVIEW_FADE_DURATION_MS /* duration */,
                        null /* drawableLoadedListener */,
                        card.getResources().getColor(android.R.color.transparent,
                                null) /* placeHolderColorJ */);
            }
        }

        private void setUpWallpaperPreview() {
            if (mHomeWallpaper != null && mHomeImageWallpaper != null) {
                boolean renderInImageWallpaperSurface =
                        !(mHomeWallpaper instanceof LiveWallpaperInfo);
                mHomeWallpaper.getThumbAsset(getContext())
                        .loadPreviewImage(getActivity(),
                                renderInImageWallpaperSurface ? mHomeImageWallpaper : mHomePreview,
                                getResources().getColor(R.color.secondary_color));
                LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
                if (mHomeWallpaper instanceof LiveWallpaperInfo) {
                    mHomeWallpaper.getThumbAsset(getContext().getApplicationContext())
                            .loadPreviewImage(
                                    getActivity(),
                                    mHomeImageWallpaper,
                                    getContext().getColor(R.color.secondary_color));
                    setUpLiveWallpaperPreview(mHomeWallpaper);
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

            LiveTileOverlay.INSTANCE.update(new RectF(mPreviewLocalRect), card.getRadius());

            mWallpaperConnection.setVisibility(true);
            mHomePreview.post(() -> {
                if (!mWallpaperConnection.connect()) {
                    mWallpaperConnection = null;
                    LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
                }
            });
        }

        void setWallpaperConnectionVisibility(boolean visibility) {
            if (mWallpaperConnection != null) {
                mWallpaperConnection.setVisibility(visibility);
            }
        }

        void disconnectWallpaperConnection() {
            LiveTileOverlay.INSTANCE.detach(mHomePreview.getOverlay());
            if (mWallpaperConnection != null) {
                mWallpaperConnection.disconnect();
                mWallpaperConnection = null;
            }
        }
    }

    /**
     * Adapter class for mPreviewPager.
     * This is a ViewPager as it allows for a nice pagination effect (ie, pages snap on swipe,
     * we don't want to just scroll)
     */
    class GridPreviewAdapter extends BasePreviewAdapter<GridPreviewPage> {

        GridPreviewAdapter(GridOption gridOption) {
            super(getContext(), R.layout.grid_preview_card);
            for (int i = 0; i < gridOption.previewPagesCount; i++) {
                addPage(new GridPreviewPage(getActivity(), i,
                        gridOption.previewImageUri.buildUpon().appendPath("" + i).build(),
                        gridOption.name, gridOption.rows, gridOption.cols));
            }
        }

        void setWallpaperConnectionVisibility(boolean visibility) {
            for (GridPreviewPage page : mPages) {
                page.setWallpaperConnectionVisibility(visibility);
            }
        }

        void disconnectWallpaperConnection() {
            for (GridPreviewPage page : mPages) {
                page.disconnectWallpaperConnection();
            }
        }
    }
}

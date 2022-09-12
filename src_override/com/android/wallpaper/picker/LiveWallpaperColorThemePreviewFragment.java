package com.android.wallpaper.picker;

import android.app.WallpaperColors;
import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.RemoteViews;

import com.android.customization.model.color.WallpaperColorResources;

import com.android.wallpaper.R;
import com.android.wallpaper.widget.LockScreenPreviewer;

import com.google.android.material.resources.MaterialAttributes;
import com.google.android.material.tabs.TabLayout;

public class LiveWallpaperColorThemePreviewFragment extends LivePreviewFragment implements WallpaperColorThemePreview {
    private boolean mIgnoreInitialColorChange;
    private boolean mThemedIconSupported;
    private WallpaperColors mWallpaperColors;

    @Override
    public WorkspaceSurfaceHolderCallback createWorkspaceSurfaceCallback(SurfaceView surfaceView) {
        return new WorkspaceSurfaceHolderCallback(surfaceView, getContext(), mThemedIconSupported);
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mThemedIconSupported = determineThemedIconsSupport(context);
    }

    @Override
    public void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        Bundle bundle2 = getArguments();
        if (bundle2 != null && bundle2.getInt("preview_mode") == 0) {
            mIgnoreInitialColorChange = true;
        }
    }

    @Override
    public void onWallpaperColorsChanged(WallpaperColors wallpaperColors, int i) {
        if (mIgnoreInitialColorChange || wallpaperColors == null) {
            updateWorkspacePreview(mWorkspaceSurface, mWorkspaceSurfaceCallback, null);
        } else if (!wallpaperColors.equals(mWallpaperColors) && shouldApplyWallpaperColors()) {
            mWallpaperColors = wallpaperColors;
            Context context = getContext();
            RemoteViews.ColorResources.create(context, new WallpaperColorResources(wallpaperColors, context).getColorOverlay()).apply(context);
            updateSystemBarColor(context);
            getView().setBackgroundColor(MaterialAttributes.resolveOrThrow(context, android.R.attr.colorPrimary, "android.R.attr.colorPrimary is not set in the current theme"));
            LayoutInflater from = LayoutInflater.from(context);
            ViewGroup viewGroup = (ViewGroup) getView().findViewById(R.id.section_header_container);
            viewGroup.removeAllViews();
            setUpToolbar(from.inflate(R.layout.section_header, viewGroup), true);
            mFullScreenAnimation.ensureToolbarIsCorrectlyLocated();
            mFullScreenAnimation.ensureToolbarIsCorrectlyColored();
            ViewGroup viewGroup2 = (ViewGroup) getView().findViewById(R.id.fullscreen_buttons_container);
            viewGroup2.removeAllViews();
            setFullScreenActions(from.inflate(R.layout.fullscreen_buttons, viewGroup2));
            ((PreviewFragment) this).mBottomActionBar.setColor(from.getContext());
            updateWorkspacePreview(mWorkspaceSurface, mWorkspaceSurfaceCallback, wallpaperColors);
            ViewGroup viewGroup3 = (ViewGroup) getView().findViewById(R.id.separated_tabs_container);
            viewGroup3.removeAllViews();
            setUpTabs((TabLayout) from.inflate(R.layout.separated_tabs, viewGroup3).findViewById(R.id.separated_tabs));
            mLockScreenPreviewer.release();
            mLockPreviewContainer.removeAllViews();
            LockScreenPreviewer lockScreenPreviewer = new LockScreenPreviewer(getLifecycle(), context, mLockPreviewContainer);
            mLockScreenPreviewer = lockScreenPreviewer;
            lockScreenPreviewer.setDateViewVisibility(!mFullScreenAnimation.isFullScreen());
        }
        mIgnoreInitialColorChange = false;
        super.onWallpaperColorsChanged(wallpaperColors, i);
    }

    @Override
    public boolean shouldUpdateWorkspaceColors() {
        return mThemedIconSupported;
    }
}

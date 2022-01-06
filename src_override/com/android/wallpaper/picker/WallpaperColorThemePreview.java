package com.android.wallpaper.picker;

import android.app.WallpaperColors;
import android.content.Context;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.customization.model.color.ColorUtils;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.themedicon.ThemedIconSwitchProvider;

import com.google.android.material.resources.MaterialAttributes;

public interface WallpaperColorThemePreview {

    default boolean determineThemedIconsSupport(Context context) {
        ThemedIconSwitchProvider instance = ThemedIconSwitchProvider.getInstance(context);
        return instance.isThemedIconAvailable() && instance.isThemedIconEnabled();
    }

    default boolean shouldApplyWallpaperColors() {
        FragmentActivity activity = ((Fragment) this).getActivity();
        if (activity == null || activity.isFinishing()) {
            Log.w("WallpaperColorThemePreview", "shouldApplyWallpaperColors: activity is null or finishing");
            return false;
        } else if (!ColorUtils.isMonetEnabled(activity)) {
            Log.w("WallpaperColorThemePreview", "Monet is not enabled");
            return false;
        } else {
            return true;
        }
    }

    boolean shouldUpdateWorkspaceColors();

    default void updateSystemBarColor(Context context) {
        int resolveOrThrow = MaterialAttributes.resolveOrThrow(context, android.R.attr.colorPrimary, "android.R.attr.colorPrimary is not set in the current theme");
        Window window = ((Fragment) this).getActivity().getWindow();
        window.setStatusBarColor(resolveOrThrow);
        window.setNavigationBarColor(resolveOrThrow);
    }

    default void updateWorkspacePreview(SurfaceView surfaceView, WorkspaceSurfaceHolderCallback workspaceSurfaceHolderCallback, WallpaperColors wallpaperColors) {
        if (shouldUpdateWorkspaceColors()) {
            int visibility = surfaceView.getVisibility();
            surfaceView.setVisibility(View.GONE);
            if (workspaceSurfaceHolderCallback != null) {
                workspaceSurfaceHolderCallback.cleanUp();
                workspaceSurfaceHolderCallback.setWallpaperColors(wallpaperColors);
                surfaceView.setUseAlpha();
                surfaceView.setAlpha(0.0f);
                workspaceSurfaceHolderCallback.setListener(new WorkspaceSurfaceHolderCallback.WorkspaceRenderListener() {
                    @Override
                    public void onWorkspaceRendered() {
                        surfaceView.setTop(-1);
                        surfaceView.animate().alpha(1.0f).setDuration(300).start();
                    }
                });
            }
            surfaceView.setVisibility(visibility);
        }
    }
}

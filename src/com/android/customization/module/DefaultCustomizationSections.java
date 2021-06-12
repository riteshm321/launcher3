package com.android.customization.module;

import android.app.Activity;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;

import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.model.grid.GridSectionController;
import com.android.customization.model.mode.ModeSection;
import com.android.customization.model.themedicon.ThemedIconSectionController;
import com.android.customization.model.themedicon.ThemedIconSwitchProvider;
import com.android.customization.model.themedicon.ThemedIconUtils;
import com.android.wallpaper.R;
import com.android.wallpaper.model.HubSectionController;
import com.android.wallpaper.model.PermissionRequester;
import com.android.wallpaper.model.WallpaperColorsViewModel;
import com.android.wallpaper.model.WallpaperPreviewNavigator;
import com.android.wallpaper.model.WallpaperSectionController;
import com.android.wallpaper.model.WorkspaceViewModel;
import com.android.wallpaper.module.HubSections;

import java.util.ArrayList;
import java.util.List;

/** {@link HubSections} for the customization picker. */
public final class DefaultCustomizationSections implements HubSections {

    @Override
    public List<HubSectionController<?>> getAllSectionControllers(Activity activity,
            LifecycleOwner lifecycleOwner, WallpaperColorsViewModel wallpaperColorsViewModel,
            WorkspaceViewModel workspaceViewModel, PermissionRequester permissionRequester,
            WallpaperPreviewNavigator wallpaperPreviewNavigator,
            HubSectionController.HubSectionNavigationController hubSectionNavigationController,
            @Nullable Bundle savedInstanceState) {
        List<HubSectionController<?>> sectionControllers = new ArrayList<>();

        // Wallpaper section.
        sectionControllers.add(new WallpaperSectionController(
                activity, lifecycleOwner, permissionRequester, wallpaperColorsViewModel,
                workspaceViewModel, hubSectionNavigationController, wallpaperPreviewNavigator,
                savedInstanceState));

        // Dark/Light theme section.
        sectionControllers.add(new ModeSection(activity, lifecycleOwner.getLifecycle()));

        // Themed app icon section.
        sectionControllers.add(new ThemedIconSectionController(
                new ThemedIconSwitchProvider(activity, new ThemedIconUtils(activity,
                        activity.getString(R.string.themed_icon_metadata_key))),
                workspaceViewModel));

        // App grid section.
        sectionControllers.add(new GridSectionController(
                GridOptionsManager.get(activity), hubSectionNavigationController));

        return sectionControllers;
    }
}

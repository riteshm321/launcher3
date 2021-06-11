/*
 * Copyright (C) 2021 The Android Open Source Project
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
package com.android.customization.model.themedicon;

import android.content.Context;
import android.view.LayoutInflater;

import androidx.annotation.Nullable;

import com.android.customization.picker.themedicon.ThemedIconSectionView;
import com.android.wallpaper.R;
import com.android.wallpaper.model.HubSectionController;
import com.android.wallpaper.model.WorkspaceViewModel;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The {@link HubSectionController} for themed icon section. */
public class ThemedIconSectionController implements HubSectionController<ThemedIconSectionView> {

    private final ThemedIconSwitchProvider mThemedIconOptionsProvider;
    private final WorkspaceViewModel mWorkspaceViewModel;

    private static ExecutorService sExecutorService = Executors.newSingleThreadExecutor();

    public ThemedIconSectionController(ThemedIconSwitchProvider themedIconOptionsProvider,
            WorkspaceViewModel workspaceViewModel) {
        mThemedIconOptionsProvider = themedIconOptionsProvider;
        mWorkspaceViewModel = workspaceViewModel;
    }

    @Override
    public boolean isAvailable(@Nullable Context context) {
        return context != null && mThemedIconOptionsProvider.isThemedIconAvailable();
    }

    @Override
    public ThemedIconSectionView createView(Context context) {
        ThemedIconSectionView themedIconColorSectionView =
                (ThemedIconSectionView) LayoutInflater.from(context).inflate(
                        R.layout.themed_icon_section_view, /* root= */ null);
        themedIconColorSectionView.setViewListener(this::onViewActivated);
        sExecutorService.submit(() -> {
            boolean themedIconEnabled = mThemedIconOptionsProvider.fetchThemedIconEnabled();
            themedIconColorSectionView.post(() ->
                    themedIconColorSectionView.getSwitch().setChecked(themedIconEnabled));
        });
        return themedIconColorSectionView;
    }

    private void onViewActivated(Context context, boolean viewActivated) {
        if (context == null) {
            return;
        }
        mThemedIconOptionsProvider.setThemedIconEnabled(viewActivated);
        mWorkspaceViewModel.getUpdateWorkspace().setValue(viewActivated);
    }
}

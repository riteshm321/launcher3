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
package com.android.customization.model.mode;


import static android.Manifest.permission.MODIFY_DAY_NIGHT_MODE;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;

import android.app.UiModeManager;
import android.content.Context;
import android.content.IntentFilter;
import android.os.PowerManager;
import android.view.LayoutInflater;
import android.widget.Switch;
import android.widget.Toast;

import androidx.annotation.MainThread;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;

import com.android.customization.picker.mode.ModeSectionView;
import com.android.wallpaper.R;
import com.android.wallpaper.model.HubSectionController;
import com.android.wallpaper.model.HubSectionController.HubSectionBatterySaverListener;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Section for dark theme toggle that controls if this section will be shown visually
 */
public class ModeSection implements HubSectionController<ModeSectionView>, LifecycleObserver,
        HubSectionBatterySaverListener {

    private final Lifecycle mLifecycle;
    private final BatterySaverStateReceiver mBatterySaverStateReceiver;

    private static ExecutorService sExecutorService = Executors.newSingleThreadExecutor();

    private Context mContext;
    private ModeSectionView mModeSectionView;

    public ModeSection(Context context, Lifecycle lifecycle) {
        mContext = context;
        mLifecycle = lifecycle;
        mBatterySaverStateReceiver = new BatterySaverStateReceiver(this);
        mLifecycle.addObserver(this);
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    @MainThread
    public void onStart() {
        sExecutorService.submit(() -> {
            if (mContext != null && mLifecycle.getCurrentState().isAtLeast(
                    Lifecycle.State.STARTED)) {
                mContext.registerReceiver(mBatterySaverStateReceiver,
                        new IntentFilter(ACTION_POWER_SAVE_MODE_CHANGED));
            }
        });
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    @MainThread
    public void onStop() {
        sExecutorService.submit(() -> {
            if (mContext != null && mBatterySaverStateReceiver != null) {
                mContext.unregisterReceiver(mBatterySaverStateReceiver);
            }
        });
    }

    @Override
    public void release() {
        mLifecycle.removeObserver(this);
        mContext = null;
    }

    @Override
    public boolean isAvailable(Context context) {
        if (context == null) {
            return false;
        }
        return ContextCompat.checkSelfPermission(context, MODIFY_DAY_NIGHT_MODE)
                == PERMISSION_GRANTED;
    }

    @Override
    public ModeSectionView createView(Context context) {
        mModeSectionView = (ModeSectionView) LayoutInflater.from(
                context).inflate(R.layout.mode_section_view, /* root= */ null);
        mModeSectionView.setViewListener(this::onViewActivated);
        PowerManager pm = context.getSystemService(PowerManager.class);
        mModeSectionView.setEnabled(!pm.isPowerSaveMode());
        return mModeSectionView;
    }

    private void onViewActivated(Context context, boolean viewActivated) {
        if (context == null) {
            return;
        }
        Switch switchView = mModeSectionView.findViewById(R.id.dark_mode_toggle);
        if (!switchView.isEnabled()) {
            Toast disableToast = Toast.makeText(mContext,
                    mContext.getString(R.string.mode_disabled_msg), Toast.LENGTH_SHORT);
            disableToast.show();
            return;
        }
        UiModeManager uiModeManager = context.getSystemService(UiModeManager.class);
        uiModeManager.setNightModeActivated(viewActivated);
    }

    @Override
    public void onBatterySaverStateChanged(boolean isEnabled) {
        mModeSectionView.setEnabled(!isEnabled);
    }
}

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

import static android.os.PowerManager.ACTION_POWER_SAVE_MODE_CHANGED;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.text.TextUtils;

import com.android.customization.model.HubSectionController.HubSectionBatterySaverListener;

/**
 * Broadcast receiver for getting battery saver state and callback to
 * {@link HubSectionBatterySaverListener}
 */
public class BatterySaverStateReceiver extends BroadcastReceiver {

    private final HubSectionBatterySaverListener mHubSectionBatterySaverListener;

    public BatterySaverStateReceiver(HubSectionBatterySaverListener batterySaverController) {
        mHubSectionBatterySaverListener = batterySaverController;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (TextUtils.equals(intent.getAction(), ACTION_POWER_SAVE_MODE_CHANGED)) {
            PowerManager pm = context.getSystemService(PowerManager.class);
            mHubSectionBatterySaverListener.onBatterySaverStateChanged(pm.isPowerSaveMode());
        }
    }
}

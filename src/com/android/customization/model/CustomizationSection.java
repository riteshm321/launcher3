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
package com.android.customization.model;

import android.content.Context;

import androidx.annotation.IdRes;
import androidx.fragment.app.Fragment;

/**
 * Represents a section of the Picker (eg "ThemeBundle", "Clock", etc).
 * There should be a concrete subclass per available section, providing the corresponding
 * Fragment to be displayed when switching to each section.
 * @param <T> CustomizationOption that this section represents.
 */
public abstract class CustomizationSection<T extends CustomizationOption> {

    /**
     * IdRes used to identify this section in the BottomNavigationView menu.
     */
    @IdRes
    public final int id;
    protected final CustomizationManager<T> mCustomizationManager;

    public CustomizationSection(@IdRes int id, CustomizationManager<T> manager) {
        this.id = id;
        mCustomizationManager = manager;
    }

    /**
     * @return the Fragment corresponding to this section.
     */
    public abstract Fragment getFragment(Context c);

    public CustomizationManager<T> getCustomizationManager() {
        return mCustomizationManager;
    }

}

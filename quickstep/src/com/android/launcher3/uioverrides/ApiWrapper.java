/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.android.launcher3.uioverrides;

import android.app.Person;
import android.content.Context;
import android.content.pm.ShortcutInfo;
import android.content.res.Resources;
import android.view.Display;

import com.android.launcher3.R;
import com.android.launcher3.Utilities;
import com.android.quickstep.SysUINavigationMode;
import com.android.quickstep.SysUINavigationMode.Mode;

public class ApiWrapper {

    public static final boolean TASKBAR_DRAWN_IN_PROCESS = true;

    public static Person[] getPersons(ShortcutInfo si) {
        Person[] persons = si.getPersons();
        return persons == null ? Utilities.EMPTY_PERSON_ARRAY : persons;
    }

    /**
     * Returns true if the display is an internal displays
     */
    public static boolean isInternalDisplay(Display display) {
        return display.getType() == Display.TYPE_INTERNAL;
    }

    /**
     * Returns the minimum space that should be left empty at the end of hotseat
     */
    public static int getHotseatEndOffset(Context context) {
        if (SysUINavigationMode.INSTANCE.get(context).getMode() == Mode.THREE_BUTTONS) {
            Resources res = context.getResources();
            return 2 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_spacing)
                    + 3 * res.getDimensionPixelSize(R.dimen.taskbar_nav_buttons_size);
        } else {
            return 0;
        }

    }
}
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

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;

import androidx.annotation.WorkerThread;

/**
 * Retrieves the themed icon switch by {@link ContentResolver} from the current launcher
 */
public class ThemedIconSwitchProvider {

    private static final String ICON_THEMED = "icon_themed";
    private static final int ENABLED = 1;
    private static final String COL_ICON_THEMED_VALUE = "boolean_value";

    private final Context mContext;
    private final ThemedIconUtils mThemedIconUtils;

    public ThemedIconSwitchProvider(Context context, ThemedIconUtils themedIconUtils) {
        mContext = context;
        mThemedIconUtils = themedIconUtils;
    }

    public boolean isThemedIconAvailable() {
        return mThemedIconUtils.isThemedIconAvailable();
    }

    @WorkerThread
    public boolean fetchThemedIconEnabled() {
        ContentResolver contentResolver = mContext.getContentResolver();
        try (Cursor cursor = contentResolver.query(
                mThemedIconUtils.getUriForPath(ICON_THEMED), /* projection= */
                null, /* selection= */ null, /* selectionArgs= */ null, /* sortOrder= */ null)) {
            if (cursor != null && cursor.moveToNext()) {
                int themedIconEnabled = cursor.getInt(cursor.getColumnIndex(COL_ICON_THEMED_VALUE));
                return themedIconEnabled == ENABLED;
            }
        }
        return false;
    }

    protected int setThemedIconEnabled(boolean enabled) {
        ContentValues values = new ContentValues();
        values.put(COL_ICON_THEMED_VALUE, enabled);
        return mContext.getContentResolver().update(
                mThemedIconUtils.getUriForPath(ICON_THEMED), values,
                /* where= */ null, /* selectionArgs= */ null);
    }
}

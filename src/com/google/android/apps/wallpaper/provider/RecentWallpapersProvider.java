package com.google.android.apps.wallpaper.provider;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import com.android.systemui.flags.FlagManager;

public class RecentWallpapersProvider extends ContentProvider {

    @Override // android.content.ContentProvider
    public boolean onCreate() {
        return true;
    }

    @Override // android.content.ContentProvider
    public String getType(Uri uri) {
        return "vnd.android.cursor.dir/recent_wallpapers";
    }

    @Override // android.content.ContentProvider
    public Cursor query(Uri uri, String[] strArr, String str, String[] strArr2, String str2) {
        if (!"/list_recent".equals(uri.getPath())) {
            return null;
        }
        MatrixCursor matrixCursor = new MatrixCursor(new String[]{FlagManager.FIELD_ID, "placeholder_color", "component", "title"});
        return matrixCursor;
    }

    @Override // android.content.ContentProvider
    public int update(Uri uri, ContentValues contentValues, String str, String[] strArr) {
        if (!"/set_recent_wallpaper".equals(uri.getPath())) {
            return 0;
        }
        return 1;
    }

    @Override // android.content.ContentProvider
    public int delete(Uri uri, String str, String[] strArr) {
        return 0;
    }

    @Override // android.content.ContentProvider
    public Uri insert(Uri uri, ContentValues contentValues) {
        return null;
    }
}

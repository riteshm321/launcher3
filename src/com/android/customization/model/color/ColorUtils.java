package com.android.customization.model.color;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.SystemProperties;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public final class ColorUtils {
    public static int sFlagId;
    @Nullable
    public static Resources sSysuiRes;

    public static boolean isMonetEnabled(@NonNull Context context) {
        boolean monet = SystemProperties.getBoolean("persist.systemui.flag_monet", false);
        if (monet) {
            return true;
        }
        if (sSysuiRes == null) {
            try {
                PackageManager packageManager = context.getPackageManager();
                ApplicationInfo applicationInfo = packageManager.getApplicationInfo(
                        "com.android.systemui", 0);
                if (applicationInfo != null) {
                    sSysuiRes = packageManager.getResourcesForApplication(applicationInfo);
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.w("ColorUtils", "Couldn't read color flag, skipping section", e);
            }
        }
        if (sFlagId == 0 && sSysuiRes != null) {
            sFlagId = sSysuiRes.getIdentifier("flag_monet", "bool", "com.android.systemui");
        }
        if (sFlagId <= 0) {
            return false;
        }
        return sSysuiRes.getBoolean(sFlagId);
    }
}

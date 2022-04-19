package com.android.launcher3.icons;

import android.content.Context;
import android.content.res.Resources;
import android.content.pm.LauncherActivityInfo;
import android.graphics.drawable.Drawable;

import android.content.res.XmlResourceParser;

import android.annotation.SuppressLint;
import com.android.launcher3.util.ComponentKey;

import com.android.launcher3.icons.pack.IconResolver;

import static com.android.launcher3.icons.BaseIconFactory.CONFIG_HINT_NO_WRAP;
import static com.android.launcher3.util.Themes.isThemedIconEnabled;

import android.text.TextUtils;
import android.util.ArrayMap;

import com.android.launcher3.icons.ThemedIconDrawable.ThemeData;

import org.xmlpull.v1.XmlPullParser;

@SuppressWarnings("unused")
public class ThirdPartyIconProvider extends RoundIconProvider {
    private final Context mContext;
    private ArrayMap<String, ThemeData> mThemedIconMap;

    private static final String TAG_ICON = "icon";
    private static final String ATTR_PACKAGE = "package";
    private static final String ATTR_DRAWABLE = "drawable";
    private static final String THEMED_ICON_MAP_FILE = "grayscale_icon_map";
    static final int ICON_TYPE_DEFAULT = 0;

    public ThirdPartyIconProvider(Context context) {
        super(context);
        mContext = context;
    }

    @SuppressLint("WrongConstant")
    @Override
    public Drawable getIcon(LauncherActivityInfo launcherActivityInfo, int iconDpi) {
        ComponentKey key = new ComponentKey(
                launcherActivityInfo.getComponentName(), launcherActivityInfo.getUser());
        String packageName = key.componentName.getPackageName();
        IconResolver.DefaultDrawableProvider fallback =
                () -> super.getIcon(launcherActivityInfo, iconDpi);
        Drawable icon = ThirdPartyIconUtils.getByKey(mContext, key, iconDpi, fallback);

        icon = icon == null ? fallback.get() : icon;
        icon.setChangingConfigurations(icon.getChangingConfigurations() | CONFIG_HINT_NO_WRAP);
        if (isThemedIconEnabled(mContext)) {
            ThemeData td = getThemedIconMap().get(packageName);
            icon = td != null ? td.wrapDrawable(icon, ICON_TYPE_DEFAULT) : icon;
        }
        return icon;
    }

    private ArrayMap<String, ThemeData> getThemedIconMap() {
        if (mThemedIconMap != null) {
            return mThemedIconMap;
        }
        ArrayMap<String, ThemeData> map = new ArrayMap<>();
        try {
            Resources res = mContext.getResources();
            int resID = res.getIdentifier(THEMED_ICON_MAP_FILE, "xml", mContext.getPackageName());
            if (resID != 0) {
                XmlResourceParser parser = res.getXml(resID);
                final int depth = parser.getDepth();

                int type;

                while ((type = parser.next()) != XmlPullParser.START_TAG
                        && type != XmlPullParser.END_DOCUMENT);

                while (((type = parser.next()) != XmlPullParser.END_TAG ||
                        parser.getDepth() > depth) && type != XmlPullParser.END_DOCUMENT) {
                    if (type != XmlPullParser.START_TAG) {
                        continue;
                    }
                    if (TAG_ICON.equals(parser.getName())) {
                        String pkg = parser.getAttributeValue(null, ATTR_PACKAGE);
                        int iconId = parser.getAttributeResourceValue(null, ATTR_DRAWABLE, 0);
                        if (iconId != 0 && !TextUtils.isEmpty(pkg)) {
                            map.put(pkg, new ThemeData(res, iconId));
                        }
                    }
                }
            }
        } catch (Exception e) {
        }
        mThemedIconMap = map;
        return mThemedIconMap;
    }

}

package com.prestoncinema.app;

import android.content.Context;
import android.graphics.Typeface;

/**
 * Created by MATT on 9/15/2017.
 */

public class FontManager {
    public static final String ROOT = "fonts/",
    FONTAWESOME = ROOT + "fontawesome-webfont.ttf";

    public static Typeface getTypeFace(Context context, String font) {
        return Typeface.createFromAsset(context.getAssets(), font);
    }
}

package com.prestoncinema.app;

import android.util.Log;
import timber.log.Timber;

/**
 * Created by MATT on 9/26/2017.
 */

public class HuaweiTree extends Timber.DebugTree {
    @Override
    protected void log(int priority, String tag, String message, Throwable t) {
        if (priority == Log.VERBOSE || priority == Log.DEBUG) {
            priority = Log.INFO;
        }
        super.log(priority, tag, message, t);
    }
}

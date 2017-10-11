package com.prestoncinema.app;

import android.app.Application;
import android.content.res.Configuration;

import timber.log.Timber;

/**
 * Created by MATT on 9/29/2017.
 */

public class PCSApplication extends Application {
    // this onCreate method is called when the application is starting, before any other application
    // objects have been created. Overriding this method is totally optional!
    @Override
    public void onCreate() {
       super.onCreate();

        // special logging shit to work with Huawei Phones
        if (BuildConfig.DEBUG) {
            String deviceManufacturer = android.os.Build.MANUFACTURER;
            if (deviceManufacturer.toLowerCase().contains("samsung")) {
                Timber.plant(new HuaweiTree());
            } else {
                Timber.plant(new Timber.DebugTree());
            }
        }
    }

    // called by the system when the device configuration changes while your component is running.
    // overriding this method is totall optional!
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    // this is called when the overall system is running low on memory, and would like actively
    // running processes to tighten their belts. Overriding is total optional!
    @Override
    public void onLowMemory() {
        super.onLowMemory();
    }
}

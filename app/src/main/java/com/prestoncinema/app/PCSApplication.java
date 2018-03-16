package com.prestoncinema.app;

import android.app.Activity;
import android.app.Application;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.DataRepository;

import timber.log.Timber;

/**
 * Created by MATT on 9/29/2017.
 */

public class PCSApplication extends Application {
    private AppExecutors appExecutors;

    // this onCreate method is called when the application is starting, before any other application
    // objects have been created. Overriding this method is totally optional!
    @Override
    public void onCreate() {
       super.onCreate();

       appExecutors = new AppExecutors();

        // special logging shit to work with Huawei Phones
        if (BuildConfig.DEBUG) {
            String deviceManufacturer = android.os.Build.MANUFACTURER;
            if (deviceManufacturer.toLowerCase().contains("samsung")) {
                Timber.plant(new HuaweiTree());
            } else {
                Timber.plant(new Timber.DebugTree());
            }
        }

        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override
            public void onActivityCreated(Activity activity, Bundle bundle) {
                // new activity created, force its orientation to portrait
                activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
            }

            @Override
            public void onActivityStarted(Activity activity) {

            }

            @Override
            public void onActivityResumed(Activity activity) {

            }

            @Override
            public void onActivityPaused(Activity activity) {

            }

            @Override
            public void onActivityStopped(Activity activity) {

            }

            @Override
            public void onActivitySaveInstanceState(Activity activity, Bundle bundle) {

            }

            @Override
            public void onActivityDestroyed(Activity activity) {

            }
        });

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

    public AppDatabase getDatabase() {
        return AppDatabase.getInstance(this, appExecutors);
    }

    public DataRepository getRepository() {
        return DataRepository.getInstance(getDatabase());
    }
}

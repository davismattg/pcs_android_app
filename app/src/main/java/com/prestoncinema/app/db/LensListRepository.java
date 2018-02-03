package com.prestoncinema.app.db;

import android.arch.lifecycle.MediatorLiveData;

import com.prestoncinema.app.model.LensList;

import java.lang.ref.WeakReference;

/**
 * Created by MATT on 1/22/2018.
 * The repository is responsible for handling lens list data operations
 */

public class LensListRepository {
    private AppExecutors appExecutors;
    private LensListDataSource lensListDataSource;
    private LensList lensList;

    private static LensListRepository INSTANCE;
//    private final AppDatabase database;
    private MediatorLiveData<LensList> observableLensLists;

    public LensListRepository(AppExecutors ex, LensListDataSource ds) {
        appExecutors = ex;
        lensListDataSource = ds;
    }

    /**
     * Get the lens list from the data source, cache it, and notify via the callback that the lens list
     * has been retrieved.
     */
    public void getLensList(final LoadLensListCallback callback, final Long id) {
        final WeakReference<LoadLensListCallback> loadLensListCallback = new WeakReference<LoadLensListCallback>(callback);

        // request the lens list on the I/O thread
        appExecutors.diskIO.execute(new Runnable() {
            @Override
            public void run() {
                final LensList list = lensListDataSource.getLensList(id);

                // notify on the main thread
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
//                        final LoadLensListCallback listCallback = LoadLensListCallback.get();

                        if (callback == null) {
                            return;
                        }

                        if (lensList == null) {
                            callback.onDataNotAvailable();
                        }
                        else {
                            lensList = list;
                            callback.onLensListLoaded(lensList);
                        }
                    }
                });
            }
        });
    }

    /**
     * Get the lens list from the data source, cache it, and notify via the callback that the lens list
     * has been retrieved.
     */
    public void getLensListByName(final String name, final LoadLensListCallback callback) {
        final WeakReference<LoadLensListCallback> loadLensListCallback = new WeakReference<LoadLensListCallback>(callback);

        // request the lens list on the I/O thread
        appExecutors.diskIO.execute(new Runnable() {
            @Override
            public void run() {
                final LensList list = lensListDataSource.getLensListByName(name);

                // notify on the main thread
                appExecutors.mainThread().execute(new Runnable() {
                    @Override
                    public void run() {
//                        final LoadLensListCallback lensListCallback = LoadLensListCallback.get();

//                        if (lensListCallback == null) {
//                            return;
//                        }
//
//                        if (lensList == null) {
//                            lensListCallback.onDataNotAvailable();
//                        }
//                        else {
//                            lensList = list;
//                            lensListCallback.onLensListLoaded(lensList);
//                        }
                    }
                });
            }
        });
    }

    /**
     * Insert a new lens list into the database with the given file name
     *
     */
    public void insertLensList(final String name, final LoadLensListCallback callback) {
//        final WeakReference<UpdateLensListCallback> updateLensListCallback = new WeakReference<UpdateLensListCallback>(callback);
    }
}
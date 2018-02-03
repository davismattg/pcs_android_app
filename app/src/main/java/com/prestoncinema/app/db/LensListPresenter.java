package com.prestoncinema.app.db;

import android.support.annotation.Nullable;

import com.prestoncinema.app.model.LensList;

import timber.log.Timber;

/**
 * Created by MATT on 1/22/2018.
 * Listens for user's actions from the UI, retrieves the data and updates the UI as required.
 */

public class LensListPresenter {
    private LensListRepository dataSource;

    @Nullable
//    private LensListView lensView;

    private LoadLensListCallback loadLensListCallback;
//    private UpdateLensListCallback updateLensListCallback;

    public LensListPresenter(LensListRepository ds) { //}, LensListView view) {
        dataSource = ds;
//        lensView = view;

        loadLensListCallback = createLensListCallback();
    }

    /**
     * Start working with the view.
     */
    public void start() {
//        dataSource.getLensList(loadLensListCallback)
    }

    public void stop() {
//        lensView = null;
    }

    public void getLensListByName(final String name) {
        dataSource.getLensListByName(name, loadLensListCallback);
    }

    public void insertLensList(String listName) {
//        dataSource.insertLensList(listName, insertLensListCallback);
    }

    private LoadLensListCallback createLensListCallback() {
        return new LoadLensListCallback() {
            @Override
            public void onLensListLoaded(LensList list) {
                Timber.d("onLensListLoaded callback: " + list.getId() + "$$");
            }

            @Override
            public void onDataNotAvailable() {
                Timber.d("onDataNotAvailable callback");
            }
        };
    }
}

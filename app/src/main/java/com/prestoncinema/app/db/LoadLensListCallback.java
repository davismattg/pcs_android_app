package com.prestoncinema.app.db;

import android.support.annotation.MainThread;

import com.prestoncinema.app.model.LensList;

/**
 * Created by MATT on 1/22/2018.
 * Callback called when a lens list is loaded from the repository
 */

public interface LoadLensListCallback {
    /**
     * Method called when the lens list was loaded from the repository
     * @param list
     */
    @MainThread
    void onLensListLoaded(LensList list);

    /**
     * Method called when there was no lens list in the repository.
     */
    @MainThread
    void onDataNotAvailable();
}

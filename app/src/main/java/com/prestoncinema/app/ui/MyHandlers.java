package com.prestoncinema.app.ui;

import android.view.View;

import com.prestoncinema.app.db.entity.LensListEntity;

import timber.log.Timber;

/**
 * Created by MATT on 2/12/2018.
 */

public class MyHandlers {
    public void onClick(View view) {
        Timber.d("Lens list onClick");
    }

    public void onLongClick(View view) {
        Timber.d("Lens list onLongClick");
    }
}

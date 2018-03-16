package com.prestoncinema.app.db;

import android.view.View;

import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.LensList;

/**
 * Created by MATT on 1/25/2018.
 */

public interface LensListClickCallback {
    void onClick(LensListEntity list, View v);
    void onClickDetails(LensListEntity list, View v);
}
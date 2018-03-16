package com.prestoncinema.app.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.support.annotation.NonNull;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.entity.LensEntity;

/**
 * Created by MATT on 3/6/2018.
 */

public class LensViewModel extends AndroidViewModel {
    private final LiveData<LensEntity> lens;

    private AppExecutors appExecutors;

    public LensViewModel(@NonNull Application app, int lensId) {
        super(app);

        lens = AppDatabase.getInstance(this.getApplication(), appExecutors).lensDao().findLensById(lensId);
    }

    public LiveData<LensEntity> getLens() {
        return lens;
    }
}

package com.prestoncinema.app.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;
import android.arch.lifecycle.Observer;
import android.support.annotation.Nullable;

import com.prestoncinema.app.PCSApplication;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.LensList;

import java.util.List;
import java.util.concurrent.Callable;

import rx.Observable;

/**
 * Created by MATT on 1/25/2018.
 */

public class LensFileListViewModel extends AndroidViewModel {
    // MediatorLiveData can observe other LiveData objects and react on their emissions
    private final MediatorLiveData<List<LensListEntity>> observableLensLists;

//    private Observable<List<LensListEntity>> lensListsObservable;

    public LensFileListViewModel(final Application application) {
        super(application);

        observableLensLists = new MediatorLiveData<>();
        // set default value to null, unless we get data from the database
        observableLensLists.setValue(null);

        LiveData<List<LensListEntity>> lensLists = ((PCSApplication) application).getRepository().getLensLists();

//        lensListsObservable = Observable.fromCallable(new Callable<List<LensListEntity>>() {
//            @Override
//            public List<LensListEntity> call() throws Exception {
//                return ((PCSApplication) application).getRepository().getLensLists();
//            }
//        });

        // observe the changes of the lens lists from the database and forward them
        observableLensLists.addSource(lensLists, new Observer<List<LensListEntity>>() {
            @Override
            public void onChanged(@Nullable List<LensListEntity> lensLists) {
                observableLensLists.setValue(lensLists);
            }
        });
    }

    /**
     * Expose the LiveData Lens List query so the UI can observe it.
     */
    public LiveData<List<LensListEntity>> getLensLists() {
        return observableLensLists;
    }
}
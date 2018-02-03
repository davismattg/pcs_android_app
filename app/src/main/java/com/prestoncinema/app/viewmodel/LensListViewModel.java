package com.prestoncinema.app.viewmodel;

import android.app.Application;
import android.arch.lifecycle.AndroidViewModel;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.ViewModel;
import android.arch.lifecycle.ViewModelProvider;
import android.databinding.ObservableField;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;

import com.prestoncinema.app.PCSApplication;
import com.prestoncinema.app.db.DataRepository;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.List;

/**
 * Created by MATT on 1/31/2018.
 */

public class LensListViewModel extends AndroidViewModel {
    private final LiveData<LensListEntity> observableLensList;

    public ObservableField<LensListEntity> lensList = new ObservableField<>();

    private final int mLensListId;

    private final LiveData<List<LensEntity>> observableLenses;

    public LensListViewModel(@NonNull Application application, DataRepository repository, final int lensListId) {
        super(application);
        mLensListId = lensListId;

        observableLenses = repository.loadLenses(lensListId);
        observableLensList = repository.loadLensList(lensListId);
    }

    /**
     * Expose the LiveData Lenses query so the UI can observe it.
     */
    public LiveData<List<LensEntity>> getLenses() { return observableLenses; }

    public LiveData<LensListEntity> getObservableLensList() { return observableLensList; }

    public void setLensList(LensListEntity list) { this.lensList.set(list); }

    /**
     * A creator is used to inject the product ID into the ViewModel.
     */
    public static class Factory extends ViewModelProvider.NewInstanceFactory {

        @NonNull
        private final Application mApp;
        private final int mLensListId;
        private final DataRepository repo;

        public Factory(@NonNull Application application, int lensListid) {
            mApp = application;
            mLensListId = lensListid;
            repo = ((PCSApplication) application).getRepository();
        }

        @Override
        public <T extends ViewModel> T create(Class<T> modelClass) {
            return (T) new LensListViewModel(mApp, repo, mLensListId);
        }
    }
}

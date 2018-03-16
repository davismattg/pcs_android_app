package com.prestoncinema.app.db;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MediatorLiveData;

import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 1/25/2018.
 * Repository handling the work with lenses and lens lists
 */

public class DataRepository {
    private static DataRepository INSTANCE;
    private final AppDatabase database;
    private MediatorLiveData<List<LensListEntity>> observableLensLists;
//    private List<LensListEntity> observableLensLists;
//    private MediatorLiveData<Lens> observableLenses;

    private DataRepository(final AppDatabase db) {
        database = db;
        observableLensLists = new MediatorLiveData<>();

//        observableLensLists.addSource(database.lensListDao().loadAllLensLists(),
//                new Observer<List<LensListEntity>>() {
//                    @Override
//                    public void onChanged(@Nullable List<LensListEntity> lensListEntities) {
//                        if (database.getDatabaseCreated().getValue() != null) {
//                            observableLensLists.postValue(lensListEntities);
//                        }
//                    }
//                });
    }

    public static DataRepository getInstance(final AppDatabase db) {
        if (INSTANCE == null) {
            synchronized (DataRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new DataRepository(db);
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Get the lens lists from the database and get notified when the data changes.
     */
    public LiveData<List<LensListEntity>> getLensLists() {
        return observableLensLists;
    }

    public void insertLensList(LensListEntity list) {
        Timber.d("inserting new list " + list.getName() + " into db");
        database.lensListDao().insert(list);
    }

    /**
     *
     * @param lensListId
     * @return
     */
//    public LiveData<LensListEntity> loadLensList(final int lensListId) {
//        return database.lensListDao().loadLensList(lensListId);
//    }
//
//    public LiveData<List<LensEntity>> loadLenses(final int listId) {
//        return database.lensDao().loadLenses(listId);
//    }
}

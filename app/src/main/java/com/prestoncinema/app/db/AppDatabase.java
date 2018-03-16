package com.prestoncinema.app.db;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.MutableLiveData;
import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.Database;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;

import com.prestoncinema.app.db.dao.LensDao;
import com.prestoncinema.app.db.dao.LensListDao;
import com.prestoncinema.app.db.dao.LensListLensJoinDao;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;

import java.util.List;

/**
 * Created by MATT on 1/18/2018.
 */

@Database(entities = {LensListEntity.class, LensEntity.class, LensListLensJoinEntity.class}, version = 1)
public abstract class AppDatabase extends RoomDatabase {
    private static AppDatabase INSTANCE;

    @VisibleForTesting
    public static final String DATABASE_NAME = "preston-database.db";

    public abstract LensDao lensDao();
    public abstract LensListDao lensListDao();
    public abstract LensListLensJoinDao lensListLensJoinDao();


    private final MutableLiveData<Boolean> isDatabaseCreated = new MutableLiveData<>();

    public static AppDatabase getInstance(final Context context, final AppExecutors executors) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = buildDatabase(context.getApplicationContext(), executors);
                    INSTANCE.updateDatabaseCreated(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    /**
     * Build the database.
     */
    private static AppDatabase buildDatabase(final Context context, final AppExecutors executors) {
        return Room.databaseBuilder(context, AppDatabase.class, DATABASE_NAME)
                .addCallback(new Callback() {
                    @Override
                    public void onCreate(@NonNull SupportSQLiteDatabase db) {
                        super.onCreate(db);
                        executors.diskIO.execute(new Runnable() {
                            @Override
                            public void run() {
                                // Generate the data for pre-population
                                AppDatabase database = AppDatabase.getInstance(context, executors);
                                LensListEntity list = DataGenerator.generateDefaultLensList();
                                List<LensEntity> lenses = DataGenerator.generateDefaultLenses(list);

                                insertData(database, list, lenses);

                                // notify that the database was created and it's ready to be used
                                database.setDatabaseCreated();
                            }
                        });
                    }
                }).build();
    }

    /**
     * Check whether the database already exists and expose it via #getDatabaseCreated()
     */
    private void updateDatabaseCreated(final Context context) {
        if (context.getDatabasePath(DATABASE_NAME).exists()) {
            setDatabaseCreated();
        }
    }

    private void setDatabaseCreated() {
        isDatabaseCreated.postValue(true);
    }

    private static void insertData(final AppDatabase database, final LensListEntity lensList,
                                   final List<LensEntity> lenses) {
        database.runInTransaction(new Runnable() {
            @Override
            public void run() {
                long listId = database.lensListDao().insert(lensList);
                long lensId;
                for (LensEntity lens : lenses) {
                    lensId = database.lensDao().insert(lens);
                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(listId, lensId));
                }
            }
        });
    }

    private static void addDelay() {
        try {
            Thread.sleep(4000);
        } catch (InterruptedException ignored) {

        }
    }

    public LiveData<Boolean> getDatabaseCreated() {
        return isDatabaseCreated;
    }
}

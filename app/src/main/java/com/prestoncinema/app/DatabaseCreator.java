package com.prestoncinema.app;

import android.arch.persistence.db.SupportSQLiteDatabase;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Room;
import android.arch.persistence.room.RoomDatabase;
import android.content.ContentValues;
import android.content.Context;
import android.support.annotation.NonNull;

import com.prestoncinema.app.db.AppDatabase;

import timber.log.Timber;

/**
 * Created by MATT on 1/22/2018.
 */

public class DatabaseCreator {
    private static AppDatabase database;
    private static final Object LOCK = new Object();

    private static RoomDatabase.Callback defaultLensListCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            ContentValues cv = new ContentValues();
            cv.put("name", "Default Lenses");
            db.insert("lens_list", OnConflictStrategy.REPLACE, cv);
            Timber.d("Inserted default lens list into lens_list database");
            super.onCreate(db);
        }

        @Override
        public void onOpen(@NonNull SupportSQLiteDatabase db) {
            super.onOpen(db);
        }
    };

    public synchronized static AppDatabase getDatabase(Context context) {
        if (database == null) {
            synchronized (LOCK) {
                if (database == null) {
                    database = Room.databaseBuilder(context, AppDatabase.class, "preston db")
                            .addCallback(new RoomDatabase.Callback() {
                                @Override
                                public void onCreate(@NonNull SupportSQLiteDatabase db) {
                                    ContentValues cv = new ContentValues();
                                    cv.put("name", "Default Lenses");
                                    db.insert("lens_list", OnConflictStrategy.REPLACE, cv);
                                    Timber.d("Inserted default lens list into lens_list database");
                                    super.onCreate(db);
                                }

                                @Override
                                public void onOpen(@NonNull SupportSQLiteDatabase db) {
                                    super.onOpen(db);
                                }
                            }).build();
                }
            }
        }

        return database;
    }


}

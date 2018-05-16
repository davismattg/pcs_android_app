package com.prestoncinema.app.db;

import android.app.ProgressDialog;
import android.arch.core.util.Function;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Transformations;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Database;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.prestoncinema.app.AllLensListsActivity;
import com.prestoncinema.app.AllLensesActivity;
import com.prestoncinema.app.LensListDetailsActivity;
import com.prestoncinema.app.SharedHelper;
import com.prestoncinema.app.db.dao.LensDao;
import com.prestoncinema.app.db.dao.LensListDao;
import com.prestoncinema.app.db.dao.LensListLensJoinDao;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.SingleSubscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

//import javax.inject.Inject;

/**
 * Created by MATT on 3/29/2018.
 */

public class DatabaseHelper {
    private LensDao lensDao;
    private LensListDao lensListDao;
    private LensListLensJoinDao joinDao;
    private static AppDatabase database;

//    private static long listId;
//    private static long lensId;

    private static List<LensEntity> lensesGlobal = new ArrayList<>();
    private static List<LensListEntity> lensListsGlobal = new ArrayList<>();

//    @Inject
    public DatabaseHelper(AppDatabase db) {
        database = db;

        lensDao = database.lensDao();
        lensListDao = database.lensListDao();
        joinDao = database.lensListLensJoinDao();
    }

    /**
     * This method retrieves all the lens lists stored in the DB. The found lists are stored in the
     * global variable lensListsGlobal and returned to the calling activity.
     * @param context
     * @return
     */
    public static List<LensEntity> getAllLenses(final Context context) {
        Observable<List<LensEntity>> lensListsObservable = rx.Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                return database.lensDao().loadAll();
            }
        });

        lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("Observable onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("Observable onError: " + e);
                    }

                    @Override
                    public void onNext(List<LensEntity> lenses) {
                        Timber.d("Observable onNext");
                        lensesGlobal = lenses;
                    }
                });

        return lensesGlobal;
    }

    /**
     * This method retrieves all the lens lists stored in the DB. The found lists are stored in the
     * global variable lensListsGlobal and returned to the calling activity.
     * @param context
     * @return
     */
    public static List<LensListEntity> getAllLensLists(final Context context) {
//        List<LensListEntity> lensListsLocal = new ArrayList<>();

        Observable<List<LensListEntity>> lensListsObservable = rx.Observable.fromCallable(new Callable<List<LensListEntity>>() {
            @Override
            public List<LensListEntity> call() {
                return database.lensListDao().loadAllLensLists();
            }
        });

        lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensListEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("Observable onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("Observable onError: " + e);
                    }

                    @Override
                    public void onNext(List<LensListEntity> lensLists) {
                        Timber.d("Observable onNext");
                        lensListsGlobal = lensLists;
                    }
                });

        return lensListsGlobal;
    }

    /**
     * This method saves new lenses to the database and assigns them to the proper Lens List(s).
     * @param context
     * @param lenses
     * @param lists
     */
    public static void insertLensesToExistingLists(final Context context, final ArrayList<LensEntity> lenses, final ArrayList<LensListEntity> lists) {
        // show a progress dialog to the user while the database operation is running
        CharSequence progressText = "Saving lenses to database...";
        final ProgressDialog pd = SharedHelper.createProgressDialog(context, progressText);
        pd.show();

        // begin database operations. start by creating a new list, then the then a lens, then the join
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                for (LensListEntity list : lists) {
                    // get the ID of the list to insert the lenses into
                    long listId = list.getId();

                    // update the count of the lens list to reflect the newly added lenses
                    int count = list.getCount();
                    count += lenses.size();
                    list.setCount(count);

                    // update the list in the database (since we changed its count)
                    database.lensListDao().update(list);

                    // for each lens, insert it into the database, then build/insert a join entry to assign it to the list
                    for (LensEntity lens : lenses) {                                                                    // loop through lenses
                        long lensId = database.lensDao().insert(lens);                                                  // insert the lens and return its id
                        Timber.d("inserted lens, returned id = " + lensId);

                        database.lensListLensJoinDao().insert(new LensListLensJoinEntity(listId, lensId));              // insert the list/lens join
                    }
                }
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        pd.dismiss();
                    }

                    @Override
                    public void onError(Throwable error) {
                        pd.dismiss();
                        CharSequence toastText = "Error saving lens list, please try again.";
                        SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                        Timber.d(error.getMessage());
                    }
                });
    }

    /**
     * This method saves a Lens List and its associated lenses to the database.
     * @param context
     * @param lenses
     * @param fileName
     * @param note
     * @param count
     * @param fromImport
     */
    public static void insertLensesAndList(final Context context, final ArrayList<LensEntity> lenses, final String fileName, String note, int count, final boolean fromImport) {
        // initialise the new list to insert
        final LensListEntity lensListToInsert = new LensListEntity();

        // set the name, note, and number of lenses
        lensListToInsert.setName(fileName);
        lensListToInsert.setNote(note);
        lensListToInsert.setCount(count);

        // show a progress dialog to the user while the database operation is running
        CharSequence progressText = "Saving lenses to database...";
        final ProgressDialog pd = SharedHelper.createProgressDialog(context, progressText);
        pd.show();

        // begin database operations. start by creating a new list, then the then a lens, then the join
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                long listId = database.lensListDao().insert(lensListToInsert);                                   // insert the list and return its id
                for (LensEntity lens : lenses) {                                                            // loop through lenses
                    long lensId = database.lensDao().insert(lens);                                               // insert the lens and return its id
                    Timber.d("inserted lens, returned id = " + lensId);

                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(listId, lensId));      // insert the list/lens join
                }
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        pd.dismiss();
                        CharSequence toastText = "'" + fileName + "' created successfully";
                        SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                    }

                    @Override
                    public void onError(Throwable error) {
                        pd.dismiss();
                        CharSequence toastText = "Error saving lens list, please try again.";
                        SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                        Timber.d(error.getMessage());
                    }
                });
    }
}

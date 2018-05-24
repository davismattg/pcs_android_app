package com.prestoncinema.app.db;

import android.app.ProgressDialog;
import android.arch.core.util.Function;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Transformations;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Database;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.widget.Toast;

import com.prestoncinema.app.AllLensListsActivity;
import com.prestoncinema.app.AllLensesActivity;
import com.prestoncinema.app.LensHelper;
import com.prestoncinema.app.LensListDetailsActivity;
import com.prestoncinema.app.SharedHelper;
import com.prestoncinema.app.db.dao.LensDao;
import com.prestoncinema.app.db.dao.LensListDao;
import com.prestoncinema.app.db.dao.LensListLensJoinDao;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;

import java.util.ArrayList;
import java.util.HashMap;
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

    private static int LENS_LIST_COUNT;

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
    public static List<LensEntity> getAllLenses(final Context context, final boolean fromInsert) {
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
                        if (fromInsert) {

                        }
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


    public static int getLensListCount(final Context context, final long listId) {
        Single.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() {
                return database.lensListLensJoinDao().getLensCountForList(listId);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Integer>() {
                    @Override
                    public void onSuccess(Integer value) {
                        LENS_LIST_COUNT = value;
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error getting lens count";
                        SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                    }
                });

        return LENS_LIST_COUNT;
    }

    /**
     * This method saves new lenses to the database and assigns them to the proper Lens List(s).
     * It also checks for duplicate lens entries and assigns existing lenses to a list instead of
     * creating a duplicate lens with the same attributes
     * @param context
     * @param lenses
     * @param lists
     */
    public static void insertLensesToExistingLists(final Context context, final ArrayList<LensEntity> lenses, final ArrayList<LensListEntity> lists) {
        // after inserting a lens into the DB, its returned ID will get added to this list
        // so we can create the join entry
        final HashMap<Long, HashMap<String, ArrayList<Long>>> lensIds = new HashMap<>();

        for (LensListEntity list : lists) {
            lensIds.put(list.getId(), new HashMap<String, ArrayList<Long>>());

            HashMap<String, ArrayList<Long>> thisListMap = lensIds.get(list.getId());

            thisListMap.put("All", new ArrayList<Long>());
//            thisListMap.put("My List A", new ArrayList<Long>());
//            thisListMap.put("My List B", new ArrayList<Long>());
//            thisListMap.put("My List C", new ArrayList<Long>());
            lensIds.put(list.getId(), thisListMap);
        }

        // show a progress dialog to the user while the database operation is running
        CharSequence progressText = "Saving lenses to database...";
        final ProgressDialog pd = SharedHelper.createProgressDialog(context, progressText);
        pd.show();

        // begin database operations. start by creating a new list, then the then a lens, then the join
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                for (LensListEntity lensList : lists) {
                    // add each lens' ID into the HashMap so we can create the join entries later
                    HashMap<String, ArrayList<Long>> thisListMap = lensIds.get(lensList.getId());
                    ArrayList<Long> allIds = thisListMap.get("All");

                    for (LensEntity lens : lenses) {                                                            // loop through lenses
                        int countInDb = database.lensDao().lensExists(LensHelper.removeMyListFromDataString(lens.getDataString()));

                        long lensId;

                        // if countInDb == 0, the lens is not present in the database
                        if (countInDb == 0) {
                            lensId = database.lensDao().insert(lens);                                               // insert the lens and return its id
                        }

                        // record found in database, so retrieve it
                        else {
                            LensEntity foundLens = database.lensDao().getLensByAttributes(lens.getManufacturer(), lens.getSeries(), lens.getFocalLength1(), lens.getFocalLength2(), lens.getSerial(), lens.getNote());
                            lensId = foundLens.getId();
                            Timber.d("duplicate lens detected, retrieving from DB (ID = " + lensId + ")");
                        }

                        allIds.add(lensId);
                    }

                    thisListMap.put("All", allIds);

                    // finally, iterate over the lenses to create the list/lens join entries
                    for (Long lensId : thisListMap.get("All")) {
                        database.lensListLensJoinDao().insert(new LensListLensJoinEntity(lensList.getId(), lensId));
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
     * This method saves a Lens List and its associated lenses to the database. It first inserts the
     * lenses, returning the ID and storing that in a HashMap in case the lens was a member of My List.
     * Then it sets the MyListXIds attribute on the list and inserts it into the DB, returning the ID.
     * Finally, it creates lens_list_lens_join entries using the list ID and the ArrayList of lens IDs.
     * @param context the activity that called this method
     * @param lenses the ArrayList of lenses to insert into the DB
     * @param lensList the pre-built lens list that has the new My List A/B/C setup
     */
     public static void insertLensesAndList(final Context context, final ArrayList<LensEntity> lenses, final LensListEntity lensList) {
         // after inserting a lens into the DB, its returned ID will get added to this list
         // so we can create the join entry
        final HashMap<String, ArrayList<Long>> lensIds = new HashMap<>();
        lensIds.put("All", new ArrayList<Long>());
        lensIds.put("My List A", new ArrayList<Long>());
        lensIds.put("My List B", new ArrayList<Long>());
        lensIds.put("My List C", new ArrayList<Long>());

        // show a progress dialog to the user while the database operation is running
        CharSequence progressText = "Saving lenses to database...";
        final ProgressDialog pd = SharedHelper.createProgressDialog(context, progressText);
        pd.show();

        // begin database operations. start by saving each lens and then checking if it's a member of My List
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                for (LensEntity lens : lenses) {                                                            // loop through lenses
                    int countInDb = database.lensDao().lensExists(LensHelper.removeMyListFromDataString(lens.getDataString()));

                    long lensId;

                    Timber.d("checking for the following lens in database: ");
                    Timber.d("Manuf: " + lens.getManufacturer() + ", series: " + lens.getSeries() + ", " + lens.getFocalLength1() + "-" + lens.getFocalLength2() + "mm, serial: " + lens.getSerial() + ", note: " + lens.getNote() + " :)");

                    // if countInDb == 0, the lens is not present in the database
                    if (countInDb == 0) {
                        lensId = database.lensDao().insert(lens);                                               // insert the lens and return its id
                    }

                    // record found in database, so retrieve it
                    else {
                        LensEntity foundLens = database.lensDao().getLensByAttributes(lens.getManufacturer(), lens.getSeries(), lens.getFocalLength1(), lens.getFocalLength2(), lens.getSerial(), lens.getNote());
                        lensId = foundLens.getId();
                        Timber.d("duplicate lens detected, retrieving from DB (ID = " + lensId + ")");
                    }

                    // add each lens' ID into the HashMap so we can create the join entries later
                    ArrayList<Long> allIds = lensIds.get("All");
                    allIds.add(lensId);
                    lensIds.put("All", allIds);

                    // if lens was a member of My List, add its ID to the HashMap to set in the LensListEntity
                    if (lens.getMyListA()) {
                        ArrayList<Long> aIds = lensIds.get("My List A");
                        aIds.add(lensId);
                        lensIds.put("My List A", aIds);
                    }

                    if (lens.getMyListB()) {
                        ArrayList<Long> bIds = lensIds.get("My List B");
                        bIds.add(lensId);
                        lensIds.put("My List B", bIds);
                    }

                    if (lens.getMyListC()) {
                        ArrayList<Long> cIds = lensIds.get("My List C");
                        cIds.add(lensId);
                        lensIds.put("My List C", cIds);
                    }
                }

                // set the MyList attributes on the list itself
                lensList.setMyListAIds(lensIds.get("My List A"));
                lensList.setMyListBIds(lensIds.get("My List B"));
                lensList.setMyListCIds(lensIds.get("My List C"));

                // insert the list into the DB, returning its ID
                long listId = database.lensListDao().insert(lensList);

                // finally, iterate over the lenses to create the list/lens join entries
                for (Long id : lensIds.get("All")) {
                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(listId, id));
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
                        CharSequence toastText = "'" + lensList.getName() + "' created successfully";
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

package com.prestoncinema.app;

import android.app.Activity;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.SingleSubscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

public class AllLensesActivity extends AppCompatActivity implements AllLensesFragment.OnLensAddedListener, AllLensesFragment.OnChildLensChangedListener,
        AllLensesFragment.OnLensSelectedListener, AllLensesFragment.OnLensSeriesSelectedListener, AllLensesFragment.OnLensManufacturerSelectedListener {

    CharSequence title = "Lens Database";

    private AllLensesFragment fragment;
    private FloatingActionButton fab;

    /* Initialize the variables used in the 'All Lenses' fragment */
    private List<String> allLensesManufHeader;
    private Map<Integer, Integer> allLensesTypeHeaderCount;
    private Map<Integer, Integer> allLensesTypeHeaderCountInitial;
    private HashMap<String, List<String>> allLensesTypeHeader;
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> allLensesPositionMap;
    private ArrayList<LensEntity> allLenses;
    private List<LensListEntity> listsToUpdate = new ArrayList<LensListEntity>();

    private int numLenses;

    private int ang_byte = 0x0;
    private int can_byte = 0x1;
    private int cooke_byte = 0x2;
    private int fuj_byte = 0x3;
    private int lei_byte = 0x4;
    private int pan_byte = 0x5;
    private int zei_byte = 0x6;
    private int oth_byte = 0xF;
    private byte[] ETX = {0x0A, 0x0D};
    private String ETXStr = new String(ETX);

    private Context context;
    private AppExecutors appExecutors;
    private AppDatabase database;

    private int numLensesChecked = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = AllLensesActivity.this;
        setContentView(R.layout.activity_all_lenses);

        fab = findViewById(R.id.allLensesFab);

        allLenses = getIntent().getParcelableArrayListExtra("lenses");
        Collections.sort(allLenses);

        numLenses = allLenses.size();
        numLensesChecked = getNumberCheckedLenses();

        showOrHideFab();

        allLensesManufHeader = SharedHelper.populateLensManufHeader(context);
        allLensesTypeHeader = SharedHelper.populateLensTypeHeader(context, allLensesManufHeader);
        allLensesTypeHeaderCountInitial = SharedHelper.initializeLensTypeHeaderCount(allLensesManufHeader);
        allLensesTypeHeaderCount = SharedHelper.populateLensTypeHeaderCount(allLensesTypeHeaderCountInitial, allLensesManufHeader, allLenses);
        allLensesPositionMap = SharedHelper.initializePositionMap(allLensesManufHeader, allLensesTypeHeaderCount);

        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragment = AllLensesFragment.newInstance(0, allLensesManufHeader, allLensesTypeHeader, allLensesTypeHeaderCount, allLensesPositionMap, allLenses, context);
        fragmentTransaction.add(R.id.allLensesFragmentContainer, fragment);
        fragmentTransaction.commit();

        setActivityTitle();

        setTitle(title + " (" + numLenses + ")");

        appExecutors = new AppExecutors();
        database = AppDatabase.getInstance(AllLensesActivity.this, appExecutors);
    }

    private void setActivityTitle() {
        setTitle(title + " (" + numLenses + ")");
    }

    @Override
    public void onLensAdded(String manuf, String series, int focal1, int focal2, String serial, String note) {
        Timber.d("onLensAdded");
        buildLensData(manuf, series, focal1, focal2, serial, note, false, false, false);
    }

    @Override
    public void onChildLensChanged(LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        Timber.d("onChildLensChanged");
        editLens(lens, serial, note, myListA, myListB, myListC);
    }

    @Override
    public void onChildLensDeleted(LensEntity lens) {
        Timber.d("onChildLensDeleted");
        getListsForLens(lens);
    }

    @Override
    public void onLensSelected(LensEntity lens) {
        Timber.d("onLensSelected");
        incrementNumLensesChecked(lens.getChecked());
        updateLensInDatabase(lens, false);
        showOrHideFab();
    }

    /**
     * This method handles clicks on the checkbox next to a manufacturer in the ExpandableListView
     * @param manufacturer
     */
    public void onManufacturerSelected(String manufacturer, boolean checked) {
        String verb = checked ? "Check" : "Uncheck";
        Timber.d(verb + " all lenses for manufacturer: " + manufacturer);
        selectLensesInDatabase(manufacturer, null, checked);
    }

    public void onSeriesSelected(String manufacturer, String series, boolean checked) {
        String verb = checked ? "Check" : "Uncheck";
        Timber.d(verb + " all lenses for " + manufacturer + " " + series);
        selectLensesInDatabase(manufacturer, series, checked);
    }

    private int getNumberCheckedLenses() {
        int count = 0;
        for (LensEntity lens : allLenses) {
            if (lens.getChecked()) count += 1;
        }

        return count;
    }

    private void incrementNumLensesChecked(boolean checked) {
        if (checked) {
            numLensesChecked += 1;
        } else {
            numLensesChecked -= 1;
        }

        if (numLensesChecked < 0) {
            numLensesChecked = 0;
        }
    }

    private void showOrHideFab() {
        if (numLensesChecked > 0) {
            fab.setVisibility(View.VISIBLE);
        }
        else {
            fab.setVisibility(View.INVISIBLE);
        }
    }

    // function to do the heavy lifting of creating the hex characters from the user's selections
    private void buildLensData(String manuf, String lensType, int focal1, int focal2, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        int width = 110;
        char fill = '0';
        int manufByte = 0x0;
        int typeByte = 0x0;
        int statByte0 = 0x8;
        int statByte1 = 0x0;

        int lensId = 0;

        String lensName;
        String lensStatus1;
        String lensStatus2;
        String lensFocal1Str;
        String lensFocal2Str;
        String lensSerialStr;

        // look @ the focal lengths to determine if prime or zoom lens, and format the string appropriately (should always be 14 characters long)
        if (focal1 == focal2) {
            Timber.d("prime lens detected by focal lengths");
            lensName = String.format("%-14s", String.valueOf(focal1) + "mm " + serial + note);
        }
        else if (focal2 == 0) {
            Timber.d("prime lens detected by zero FL2");
            lensName = String.format("%-14s", String.valueOf(focal1) + "mm " + serial + note);
        }
        else {              // zoom lens
            Timber.d("zoom lens detected by focal lengths");
            statByte1 += 1;
            lensName = String.format("%-14s", String.valueOf(focal1) + "-" + String.valueOf(focal2) + "mm " + serial + note);
        }

        switch (manuf) {
            case "Angenieux": //48
                manufByte = ang_byte;
                switch (lensType) {
                    case "Optimo":
                        typeByte = 0x0;
                        break;
                    case "Rouge":
                        typeByte = 0x1;
                        break;
                    case "HR":
                        typeByte = 0x2;
                        break;
                    case "Other":
                        typeByte = 0x3;
                        break;
                    default:
                        break;
                }
                break;
            case "Canon":
                manufByte = can_byte;
                switch (lensType) {
                    case "Cinema Prime":
                        typeByte = 0x0;
                        break;
                    case "Cinema Zoom":
                        typeByte = 0x1;
                        break;
                    case "Other":
                        typeByte = 0x2;
                        break;
                    default:
                        break;
                }
                break;
            case "Cooke":
                manufByte = cooke_byte;
                switch (lensType) {
                    case "S4":
                        typeByte = 0x0;
                        break;
                    case "S5":
                        typeByte = 0x1;
                        break;
                    case "Panchro":
                        typeByte = 0x2;
                        break;
                    case "Zoom":
                        typeByte = 0x3;
                        break;
                    case "Other":
                        typeByte = 0x4;
                        break;
                    default:
                        break;
                }
                break;
            case "Fujinon": //48
                manufByte = fuj_byte;
                switch (lensType) {
                    case "Premier Zoom":
                        typeByte = 0x0;
                        break;
                    case "Alura Zoom":
                        typeByte = 0x1;
                        break;
                    case "Prime":
                        typeByte = 0x2;
                        break;
                    case "Other":
                        typeByte = 0x3;
                        break;
                    default:
                        break;
                }
                break;
            case "Leica":
                manufByte = lei_byte;
                switch (lensType) {
                    case "Summilux Prime":
                        typeByte = 0x0;
                        break;
                    case "Other":
                        typeByte = 0x1;
                        break;
                    default:
                        break;
                }
                break;
            case "Panavision":
                manufByte = pan_byte;
                switch (lensType) {
                    case "Primo Prime":
                        typeByte = 0x0;
                        break;
                    case "Primo Zoom":
                        typeByte = 0x1;
                        break;
                    case "Anam. Prime":
                        typeByte = 0x2;
                        break;
                    case "Anam. Zoom":
                        typeByte = 0x3;
                        break;
                    case "P70 Prime":
                        typeByte = 0x4;
                        break;
                    case "Other":
                        typeByte = 0x5;
                        break;
                    default:
                        break;
                }
                break;
            case "Zeiss":
                manufByte = zei_byte;
                switch (lensType) {
                    case "Master Prime":
                        typeByte = 0x0;
                        break;
                    case "Ultra Prime":
                        typeByte = 0x1;
                        break;
                    case "Compact Prime":
                        typeByte = 0x2;
                        break;
                    case "Zoom":
                        typeByte = 0x3;
                        break;
                    case "Other":
                        typeByte = 0x4;
                        break;
                    default:
                        break;
                }
                break;
            case "Other":
                manufByte = oth_byte;
                switch (lensType) {
                    case "Prime":
                        typeByte = 0x0;
                        break;
                    case "Zoom":
                        typeByte = 0x1;
                        break;
                    default:
                        break;
                }
                break;

            default:
                break;
        }

        if (myListA) {
            statByte1 += 0x8;
        }

        if (myListB) {
            statByte0 += 0x1;
        }

        if (myListC) {
            statByte0 += 0x2;
        }

        if (statByte0 == 10) {
            statByte0 = 0xA;
        }

        if (statByte0 == 11) {
            statByte0 = 0xB;
        }

        // convert to the hex characters that will be written in the file. these strings all need to
        // be constant length no matter how many characters are inside, so you have to pad with 0's if necessary
        lensStatus1 = Integer.toHexString(statByte0).toUpperCase() + Integer.toHexString(statByte1).toUpperCase();
        lensStatus2 = Integer.toHexString(manufByte).toUpperCase() + Integer.toHexString(typeByte).toUpperCase();
        lensFocal1Str = String.format("%4s", Integer.toHexString(focal1).toUpperCase()).replaceAll(" ", "0");
        lensFocal2Str = String.format("%4s", Integer.toHexString(focal2).toUpperCase()).replaceAll(" ", "0");

        if (serial.length() > 0) {
            lensSerialStr = String.format("%4s", Integer.toHexString(Integer.parseInt(serial)).toUpperCase()).replaceAll(" ", "0");
        }
        else {
            lensSerialStr = "0000"; //String.format("%4s", Integer.toHexString(0).toUpperCase()).replaceAll(" ", "0");
        }
        String toPad = lensName + lensStatus1 + lensStatus2 + lensFocal1Str + lensFocal2Str + lensSerialStr;
        String padded = toPad + new String(new char[width - toPad.length()]).replace('\0', fill) + ETXStr;

        Timber.d("lensString length: " + padded.length());
        Timber.d("lensString:" + padded + "$$");

//        lensArray.add(padded);
//        int index = lensArray.size() - 1;

//        LensEntity newLensObject = parseLensLine(padded, index, lensId, true);
        LensEntity newLensObject = SharedHelper.buildLensFromDataString(padded);
        insertLensInDatabase(newLensObject);
    }

    private void insertLensInDatabase(final LensEntity lens) {
        Timber.d("inserting lens in database");

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                long lensId = database.lensDao().insert(lens);
                Timber.d("inserted lens, returned id = " + lensId);

//                database.lensListLensJoinDao().insert(new LensListLensJoinEntity(currentListId, lensId));
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Timber.d("lens inserted successfully");
                        addOrRemoveLensFromList(lens, false);
//                        allLenses.add(lens);
//                        Collections.sort(allLenses);
//                        fragment.updateAdapter();
                        updateLensListCount(lens, false);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d("insertLensInDatabase onError: " + error.getMessage());
                        CharSequence text = "Error inserting lens - please try again";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    private void getListsForLens(final LensEntity lens) {
        listsToUpdate.clear();
        Observable.fromCallable(new Callable<List<LensListEntity>>() {
            @Override
            public List<LensListEntity> call() {
                return database.lensListLensJoinDao().getListsForLens(lens.getId());
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensListEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("getListsForLens onCompleted");
                        deleteLens(lens);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e("ERROR: " + e.getMessage());
                    }

                    @Override
                    public void onNext(List<LensListEntity> lists) {
                        Timber.d("getListsForLens onNext: " + lists.size());
                        for (LensListEntity list : lists) {
                            Timber.d("List: " + list.getName());
                            listsToUpdate.add(list);
                        }
                    }
                });
    }

    private void selectLensesInDatabase(String manufacturer, String series, final boolean checked) {
        Timber.d("getting all " + manufacturer + " lenses from list");

        final LensEntity[] lensesToUpdate = getLenses(manufacturer, series, checked);

        Single.fromCallable(new Callable<Integer>() {
            public Integer call() {
                return database.lensDao().updateAll(lensesToUpdate);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Integer>() {
                    @Override
                    public void onSuccess(Integer value) {
                        Timber.d(value + " lenses updated");
                        updateLensCheckedCount(value, checked);
//                        updateLensList();
                        CharSequence toastText = (checked ? "Added " + value + " lenses to selection" : "Removed " + value + " lenses from selection");
                        SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_SHORT);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d("ERROR: " + error);
                    }
                });
    }

//    private void updateLensList() {
//
//    }

    private void updateLensCheckedCount(int numUpdated, boolean checked) {
        if (checked) {
//            numLensesChecked = getNumberCheckedLenses();
            numLensesChecked += numUpdated;
        }

        else {
            numLensesChecked -= numUpdated;
        }

        showOrHideFab();
    }

    private LensEntity[] getLenses(String manufacturer, String series, boolean checked) {
        ArrayList<LensEntity> lenses = new ArrayList<>();

        for (LensEntity lens : allLenses) {
            if (lens.getManufacturer().equals(manufacturer)) {
                if (series != null) {
                    if (lens.getSeries().equals(series)) {
                        lens.setChecked(checked);
                        lenses.add(lens);
                    }
                }
                else {
                    lens.setChecked(checked);
                    lenses.add(lens);
                }
            }
        }

        return lenses.toArray(new LensEntity[lenses.size()]);
    }

    private void deleteLens(final LensEntity lens) {
        Timber.d("delete lens ID: " + lens.getId());
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                database.lensDao().delete(lens);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Timber.d("Deleted lens successfully. Tag: " + lens.getId());
                        updateLensListCount(lens, true);
//                lensArray.remove(lens.getTag());

//                lensObjectArray.remove(lens.getTag());
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d(error.getMessage());
                        CharSequence text = "Error deleting lens - please try again";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // function to edit an existing lens after user changes the serial or mylist assignment in the edit dialog
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
//    private boolean editLens(int lensInd, int childPosition, String manufTitle, String typeTitle, String focal1, String focal2, String serial, boolean myListA, boolean myListB, boolean myListC) {
    private void editLens(LensEntity lensObject, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        Timber.d("///////////////////////////////////////////////////////////////");
        Timber.d("editLens - params: ");
        Timber.d("serial: " + serial);
        Timber.d("note: " + note);
        Timber.d("myListA: " + myListA);
        Timber.d("myListB: " + myListB);
        Timber.d("myListC: " + myListC);
        Timber.d("///////////////////////////////////////////////////////////////");

        lensObject.setSerial(serial);
        lensObject.setNote(note);
        lensObject.setMyListA(myListA);
        lensObject.setMyListB(myListB);
        lensObject.setMyListC(myListC);
        lensObject.setDataString(SharedHelper.buildLensDataString(lensObject));

        updateLensInDatabase(lensObject, true);
    }

    private void updateLensInDatabase(final LensEntity lens, final boolean showToast) {
        Timber.d("updating lens in database");
        Timber.d("lens id: " + lens.getId());

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                database.lensDao().update(lens);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        if (showToast) {
                            CharSequence text = "Lens updated successfully";
                            SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                        }

//                        allLenses.remove(lens);
//                        allLenses.add(lens);
//                        Collections.sort(allLenses);
                        fragment.updateAdapter();
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d("updateLensInDatabase onError: " + error.getMessage());
                        CharSequence text = "Error updating lens - please try again";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    private void updateLensListCount(final LensEntity lens, final boolean remove) {
        Timber.d("update lens list count for lens id = " + lens.getId());

        int numToUpdate = (remove ? listsToUpdate.size() : 1);
        int count;
        final LensListEntity[] lists = new LensListEntity[numToUpdate];
        if (remove) {
            for (int i = 0; i < listsToUpdate.size(); i++) {
                LensListEntity list = listsToUpdate.get(i);
                count = (list.getCount()) - 1;

                list.setCount(count);
                lists[i] = list;
            }
        }

        else {
//            count = currentLensList.getCount() + 1;
            numLenses = allLenses.size();
            setActivityTitle();
        }

        if (remove) {
            Single.fromCallable(new Callable<Void>() {
                @Override
                public Void call() throws Exception {
                    database.lensListDao().update(lists);
                    return null;
                }
            })
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(new SingleSubscriber<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            Timber.d("updated lens list count successfully");
                            addOrRemoveLensFromList(lens, remove);
                        }

                        @Override
                        public void onError(Throwable error) {
                            Timber.d(error.getMessage());
                            CharSequence text = "Error updating lens list count - please try again";
                            SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                        }
                    });
        }
    }

    private void addOrRemoveLensFromList(LensEntity lens, boolean remove) {
        if (remove) {
            for (int i = 0; i < allLenses.size(); i++) {
                LensEntity obj = allLenses.get(i);

                if (obj.getId() == lens.getId()) {
                    allLenses.remove(obj);
                    break;
                }
            }
        }

        else {
            allLenses.add(lens);
        }

        numLenses = allLenses.size();
        setActivityTitle();
        Collections.sort(allLenses);
        fragment.updateAdapter();
    }
}

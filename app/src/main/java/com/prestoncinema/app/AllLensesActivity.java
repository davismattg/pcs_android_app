package com.prestoncinema.app;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.FragmentTransaction;
import android.content.Context;
import android.support.v4.app.FragmentManager;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Toast;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.DatabaseHelper;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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

public class AllLensesActivity extends AppCompatActivity implements LensListFragment.OnLensAddedListener, LensListFragment.OnChildLensChangedListener,
        LensListFragment.OnLensSelectedListener, LensListFragment.OnLensSeriesSelectedListener, LensListFragment.OnLensManufacturerSelectedListener {
//        AllLensListsArrayAdapter.ListSelectedListener {

    CharSequence title = "Lens Database";

    private LensListFragment fragment;
    private FloatingActionButton fab;

    /* Initialize the variables used in the 'All Lenses' fragment */
    private List<String> allLensesManufHeader;
    private Map<Integer, Integer> allLensesTypeHeaderCount;
    private Map<Integer, Integer> allLensesTypeHeaderCountInitial;
    private HashMap<String, List<String>> allLensesTypeHeader;
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> allLensesPositionMap;
    private ArrayList<LensEntity> allLenses = new ArrayList<>();
    private ArrayList<LensEntity> lensesFromIntent = new ArrayList<>();
    private ArrayList<LensEntity> lensesToSend = new ArrayList<>();
    private ArrayList<String> lensDataStrings = new ArrayList<>();
    private List<LensListEntity> listsToUpdate = new ArrayList<LensListEntity>();

    private int MODE_HU3 = 0;
    private int MODE_SHARE = 1;
    private int MODE_SELECTED = 2;

    private String STRING_DELETE = "DELETE";

    private int numLenses;
    private boolean isConnected = false;
    private boolean fromImport = false;
    private String listNote;

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
    private DatabaseHelper databaseHelper;

    private int numLensesChecked = 0;
    private int MAX_LENS_COUNT = 255;

    private Button saveLensesToDatabaseButton;
    private Button sendLensesButton;

    private ArrayList<LensListEntity> listsToAddImportedLensesTo = new ArrayList<>();
    private ArrayList<LensListEntity> allLensLists = new ArrayList<>();

    ArrayList<String> invalidFileNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        context = AllLensesActivity.this;
        setContentView(R.layout.activity_all_lenses);

        /* UI initialization */
        fab = findViewById(R.id.allLensesFab);
        saveLensesToDatabaseButton = findViewById(R.id.saveLensesToDatabaseButton);
        sendLensesButton = findViewById(R.id.sendSelectedLensesButton);

        Intent intent = getIntent();

        /* Get necessary variables from the intent (lensLists, fromImport, etc) */
        lensesFromIntent = intent.getParcelableArrayListExtra("lenses");
        allLensLists = intent.getParcelableArrayListExtra("lists");
        isConnected = intent.getBooleanExtra("isConnected", false);
        fromImport = intent.getBooleanExtra("fromImport", false);
        title = intent.getStringExtra("title");
        listNote = intent.getStringExtra("listNote");

        appExecutors = new AppExecutors();
        database = AppDatabase.getInstance(AllLensesActivity.this, appExecutors);
        databaseHelper = new DatabaseHelper(database);

        getAllLenses();

        /* Initialize the FloatingActionButton used to send the lenses to HU3 */
//        fab.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                Timber.d("FAB clicked, export lenses to HU3");
//
//                if (isConnected) {
//                    sendLensesFromFab();
//                }
//
//                else {
//                    CharSequence toastText = "Error: Not connected to Preston Updater";
//                    SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_SHORT);
//                }
//            }
//        });

        invalidFileNames.add("All Lenses");
        invalidFileNames.add("Received Lenses");
        invalidFileNames.add("Lens Database");
        invalidFileNames.add("Selected Lenses");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_all_lenses, menu);

//        if (isConnected) {
//            menu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_bluetooth_connected_blue));
//        }

//        optionsMenu = menu;

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        switch (id) {
            case R.id.shareSelectedLenses:
                Timber.d("share selected lenses");
                if (getNumberCheckedLenses() > 0) {
                    String timeStamp = new SimpleDateFormat("MM-dd-yyyy HH.mm.ss").format(new Date());
                    String name = "Lens Database - " + timeStamp;
                    getSelectedLenses();
                    prepareLensesForExport(name, MODE_SHARE);
                }
                else {
                    CharSequence toastText = "Error: no lenses selected";
                    SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_SHORT);
                }
                return true;
            case R.id.deleteSelectedLenses:
                Timber.d("delete selected lenses");
                if (getNumberCheckedLenses() > 0) {
                    getSelectedLenses();
                    askToConfirmLensDelete();
                }
                else {
                    CharSequence toastText = "Error: no lenses selected";
                    SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_SHORT);
                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setActivityTitle() {
        setTitle(title + " (" + numLenses + ")");
    }

    @Override
    public void onLensAdded(String manuf, String series, int focal1, int focal2, String serial, String note) {
        Timber.d("onLensAdded");
        buildLensData(manuf, series, focal1, focal2, serial, note, false, false, false);
    }

    /**
     * Interface method called when the user edits a lens from anywhere other than inside a specific
     * lens list. For this reason, we only call updateLensInDatabase on the edited lens.
     * @param lensList the "list" associated with the operation. Not really relevant since this method
     *                 is called outside a specific list. As a result, lensList is just a new LensListEntity
     *                 with the name "All Lenses", which is used for filtering operations later.
     * @param lens the lens to edit
     * @param serial the new serial
     * @param note the new note
     * @param listA always should be false since not associated with a specific list
     * @param listB always should be false since not associated with a specific list
     * @param listC always should be false since not associated with a specific list
     */
    @Override
    public void onChildLensChanged(LensListEntity lensList, LensEntity lens, String serial, String note, boolean listA, boolean listB, boolean listC) {
        HashMap<String, Object> lensAndListMap = LensHelper.editListAndLens(lensList, lens, serial, note, listA, listB, listC);

        LensEntity editedLens = (LensEntity) lensAndListMap.get("lens");

        // don't update the database if the user is editing a lens during import from the HU3
        if (!fromImport) {
            updateLensInDatabase(editedLens, true, true);
        }
    }

    @Override
    public void onChildLensDeleted(LensListEntity lensList, LensEntity lens) {
        Timber.d("onChildLensDeleted");
        getListsForLenses(lens);
    }

    @Override
    public void onLensSelected(LensEntity lens) {
        Timber.d("onLensSelected");

        updateLensChecked(lens);
    }

    /**
     * This method handles clicks on the checkbox next to a manufacturer in the ExpandableListView
     * @param manufacturer
     */
    public void onManufacturerSelected(String manufacturer, boolean checked) {
        if (fromImport) {
            allLenses = SharedHelper.setChecked(allLenses, manufacturer, null, checked);

            // update the adapter if a checkbox was tapped from the Manufacturer or Series levels
            if (manufacturer != null) {
                fragment.updateAdapter();
            }

            // use a different updating method because the user tapped the checkbox for "Select All"
            else {
                fragment.updateAdapterFromSelectAll(checked);
            }
        }
        else {
            selectLensesInDatabase(manufacturer, null, checked);
        }
    }

    public void onSeriesSelected(String manufacturer, String series, boolean checked, boolean checkParent) {
        if (fromImport) {
            allLenses = SharedHelper.setChecked(allLenses, manufacturer, series, checked);

            // update the adapter if a checkbox was tapped from the Manufacturer or Series levels
            fragment.updateAdapter();
        }

        else {
            selectLensesInDatabase(manufacturer, series, checked);
        }
    }

    /**
     * This method is called from the onListSelected interface from the AllLensListArrayAdapter.
     * It's called when the user checks/unchecks the checkbox to add imported lenses from the HU3
     * to an existing LensListEntity. These lists are then used to build new LensListLensJoinEntity
     * objects and insert them into the database.
     * @param list LensListEntity, the lens list checked/unchecked
     * @param selected boolean, whether the check box was checked or unchecked
     */
//    public void onListSelected(LensListEntity list, boolean selected) {
//        if (selected) {
//            listsToAddImportedLensesTo.add(list);
//        }
//
//        else {
//            listsToAddImportedLensesTo.remove(list);
//        }
//    }

    /**
     * This method returns the number of checked lenses in all the lenses stored in the DB.
     * @return count, the number of checked lenses
     */
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

    /**
     * This method retrieves all lenses from the database. It then stores those lenses in the global
     * variable allLenses, and calls setupDataWithAllLenses() which populates the various components
     * of the ExpandableListView adapter, sets the activity title, etc
     */
    private void getAllLenses() {
        if (lensesFromIntent != null) {
            allLenses = lensesFromIntent;
            setupDataWithAllLenses();
        }
        else {
            Observable<List<LensEntity>> lensListsObservable = Observable.fromCallable(new Callable<List<LensEntity>>() {
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

                            // now that allLenses is populated, set up the other parts of the activity that depend on them
                            setupDataWithAllLenses();
                        }

                        @Override
                        public void onError(Throwable e) {
                            Timber.d("Observable onError: " + e);
                        }

                        @Override
                        public void onNext(List<LensEntity> lenses) {
                            Timber.d("Observable onNext");
                            allLenses = new ArrayList<>(lenses);
                        }
                    });
        }
    }

    /**
     * This method sets up the components of the activity that depend on allLenses. For large numbers
     * of lenses, it's not possible to send them via Intents so we have to pull them from the DB,
     * which can take time. This method is only called once the lenses are fetched from the database.
     */
    private void setupDataWithAllLenses() {
        // sort the lenses by focal length
        Collections.sort(allLenses);

        // set the number of lenses
        numLenses = allLenses.size();

        // get the number of selected lenses
        numLensesChecked = getNumberCheckedLenses();

        // show or hide he FAB to send lenses to the HU3
        showOrHideFab();

        // set up components of the adapter for the ExpandableListView
        allLensesManufHeader = SharedHelper.populateLensManufHeader(context);
        allLensesTypeHeader = SharedHelper.populateLensTypeHeader(context, allLensesManufHeader);
        allLensesTypeHeaderCountInitial = SharedHelper.initializeLensTypeHeaderCount(allLensesManufHeader);
        allLensesTypeHeaderCount = SharedHelper.populateLensTypeHeaderCount(allLensesTypeHeaderCountInitial, allLensesManufHeader, allLenses);
        allLensesPositionMap = SharedHelper.initializePositionMap(allLensesManufHeader, allLensesTypeHeaderCount);

        // initialize the Fragment that actually holds everything
        LensListEntity lensList = new LensListEntity();
        lensList.setName("All Lenses");
        FragmentManager fragmentManager = getSupportFragmentManager();
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragment = LensListFragment.newInstance(0, lensList, allLensesManufHeader, allLensesTypeHeader, allLensesTypeHeaderCount, allLensesPositionMap, allLenses, fromImport, listNote, context);
        fragmentTransaction.add(R.id.allLensesFragmentContainer, fragment);
        fragmentTransaction.commit();

        // update the activity title to reflect the number of lenses
        setActivityTitle();
    }

    /**
     * Shows or hides the FloatingActionButton depending on if multiple lenses are selected.
     * If this Activity was launched after importing lenses from an HU3, we don't show the FAB
     * and instead show a button to save the (selected) lenses to the database.
     */
    private void showOrHideFab() {
        // if at least one lens is selected and we're not trying to select lenses for import to DB
        if (numLensesChecked > 0 && !fromImport) {
//            fab.setVisibility(View.VISIBLE);
            sendLensesButton.setVisibility(View.VISIBLE);
        }

        // don't need the FAB
        else {
//            fab.setVisibility(View.INVISIBLE);

            // if we're trying to import lenses to the DB, show the appropriate options
            if (fromImport) {
                saveLensesToDatabaseButton.setVisibility(View.VISIBLE);
            }
        }
    }

    /**
     * This method is the OnClick handler for the "Send..." button that appears when the user is
     * viewing this activity by pressing on the "Selected Lenses" area. The button lets the user choose
     * what action to take for the selected lenses.
     * @param view the button that triggered this method
     */
    public void getSelectedLensesShareAction(View view) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                final AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);
                final LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_send_lenses.xml, which we'll inflate to the dialog
                View getActionView = inflater.inflate(R.layout.dialog_send_to_action_selection, null);
                LinearLayout toHU3 = getActionView.findViewById(R.id.sendSelectedLensesToHU3);
                LinearLayout toExisting = getActionView.findViewById(R.id.sendSelectedLensesToExistingList);
                LinearLayout toNew = getActionView.findViewById(R.id.sendSelectedLensesToNewList);
                LinearLayout toFile = getActionView.findViewById(R.id.sendSelectedLensesToFile);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(getActionView).setCancelable(true);

                // create the alert dialog
                final AlertDialog alert = builder.create();

                getSelectedLenses();

                toHU3.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        if (isConnected) {
                            Timber.d("send selected lenses to HU3");
                            prepareLensesForExport(null, MODE_HU3);
                            alert.dismiss();
                        }

                        else {
                            CharSequence text = "No module detected";
                            SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                        }
                    }
                });

                toNew.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("send selected lenses to new list");
                        performImportedLensesAction(true, lensesToSend);
                        alert.dismiss();
                    }
                });

                toExisting.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("send selected lenses to existing list");
                        performImportedLensesAction(false, lensesToSend);
                        alert.dismiss();
                    }
                });

                toFile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("export selected lenses to lens file");
                        askToNameExportedLensFile();
                        alert.dismiss();
                    }
                });

                alert.show();
            }
        });
    }

    /**
     * This method handles picking the selected lenses from allLenses and getting them ready to send
     * to the HU3. Once the lenses are ready, it calls confirmLensAddOrReplace() so the user can confirm
     * whether they want to add to or replace the lenses on the HU3
     */
//    private void sendLensesFromFab() {
////        getSelectedLenses();
////        confirmLensAddOrReplace();
//    }

    /**
     * This method looks at all the lenses contained in allLenses and adds the selected ones. This
     * prepares the ArrayList for export to the HU3 or file
     */
    private void getSelectedLenses() {
        lensesToSend.clear();

        for (int i = 0; i < allLenses.size(); i++) {
            LensEntity lens = allLenses.get(i);
            if (lens.getChecked() && !(lensesToSend.contains(lens))) {
                lensesToSend.add(lens);
            }
        }
    }

    /**
     * This method gets the data strings from each lens in lensesToSend and adds them to the
     * lensDataStrings ArrayList. It then either sends the lenses to the HU3 or creates the text
     * file and calls shareLensFile to send it out via an Intent. The action taken is determined
     * by the mode param.
     * @param fileName the name of the file (only used if exporting to an external file)
     * @param mode flag indicating the action to take (send to Hu3 or file)
     */
    private void prepareLensesForExport(String fileName, int mode) {
        lensDataStrings.clear();

        // gets only the selected lenses
        getSelectedLenses();

        // get the data string for each lens and add it to the array that will be sent
        for (LensEntity lens : lensesToSend) {
            String dataStr = SharedHelper.buildLensDataString(null, lens);

            if (dataStr.length() > 100) {
                lensDataStrings.add(dataStr);
            }
        }

        numLenses = lensDataStrings.size();

        // send lenses to the HU3
        if (mode == MODE_HU3) {
            if (isConnected) {
                if (numLenses > MAX_LENS_COUNT) {
                    CharSequence toastText = "Error: HU3 can accept 255 lenses max";
                    SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_LONG);
                }
                else {
                    confirmLensAddOrReplace();
                }
            }

            else {
                CharSequence text = "No Bluetooth module detected. Please connect and try again";
                SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_LONG);
            }
        }

        if (mode == MODE_SHARE) {
            Timber.d("share the lenses - need to build file");
            File listToExport = createTextLensFile(fileName);
            shareLensFile(listToExport);
        }
    }

    private void askToNameExportedLensFile() {
        CharSequence title = "Please enter a file name";

        AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);
        LayoutInflater inflater = getLayoutInflater();

        // the custom view is defined in dialog_rename_lens_list.xml, which we inflate
        View newLensListView = inflater.inflate(R.layout.dialog_rename_lens_list, null);
        final EditText listNameEditText = newLensListView.findViewById(R.id.renameLensListNameEditText);
        final EditText listNoteEditText = newLensListView.findViewById(R.id.renameLensListNoteEditText);

        // hide the note EditText cuz the user is just entering a name for the exported lens file
        listNoteEditText.setVisibility(View.GONE);

        builder.setView(newLensListView)
                .setTitle(title)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // get the list name and note entered by the user
                        String name = listNameEditText.getText().toString().trim();

//                        LensListEntity lensList = new LensListEntity(); //SharedHelper.buildLensList(null, name, "", lensesToManage.size(), lensesToManage);
//                        lensList.setName(name);

                        prepareLensesForExport(name, MODE_SHARE);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setCancelable(true);

        // create the alert dialog
        final AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * This method prompts the user for whether they would like to add the selected lenses to the HU3,
     * or replace all lenses currently stored on the HU3. It's called once the user selects "Send to
     * HU3" option in the dialog that pops up after tapping on the "Send..." button. The actual
     * transfer of lenses is taken care of by the method sendSelectedLenses, with a boolean flag for
     * add/replace as a param.
     */
    private void confirmLensAddOrReplace() {
        Timber.d("confirmLensAddOrReplace");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens_list.xml, which we'll inflate to the dialog
                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
                final RadioButton addLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.addLensesRadioButton);

                final int numLensesToSend = lensesToSend.size();
                String lensPluralString = "Lenses";
                if (numLensesToSend == 1) {
                    lensPluralString = "Lens";
                }
                final String title = "Ready To Send " + numLensesToSend + " " + lensPluralString;

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(sendLensView)
                        .setTitle(title)
                        .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Timber.d("Start the intent to send these lenses");
                                boolean addLenses = addLensesRadioButton.isChecked();

                                // send the lenses to the HU3
                                sendSelectedLenses(addLenses);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                lensesToSend.clear();
                            }
                        })
                        .setCancelable(false);

                // create the alert dialog
                final AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    /**
     * This method creates a text file of lens data strings which is used to export to a file or
     * send via an Intent.
     * @param fileName
     * @return lensFile, the file created
     */
    private File createTextLensFile(String fileName) {
        File lensFile = new File(getExternalFilesDir(null), fileName + ".lens");

        if (isExternalStorageWritable()) {
            Timber.d("Number of lenses in file: " + lensDataStrings.size());
            try {
                FileOutputStream fos = new FileOutputStream(lensFile);
                for (String lens : lensDataStrings) {
                    try {
                        fos.write(lens.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fos.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return lensFile;
    }

    /**
     * This method is called when the user wants to share/export a lens file. It uses the
     * Intent.ACTION_SEND argument to let the system know which apps/services should be able to handle
     * the file.
     * @param file
     */
    private void shareLensFile(File file) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        try {
            Uri fileUri = FileProvider.getUriForFile(AllLensesActivity.this, "com.prestoncinema.app.fileprovider", file);
            Timber.d("fileUri: " + fileUri);
            if (fileUri != null) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                startActivity(Intent.createChooser(shareIntent, "Export lenses via: "));
            } else {
                // TODO: add a toast to alert user to error sharing file
            }
        }
        catch (IllegalArgumentException e) {
            Timber.e("File Selector", "The selected file can't be shared: " + file.toString() + ": " + e);
        }
    }

    /**
     * This method is the
     * @param addToExisting
     */
    private void sendSelectedLenses(boolean addToExisting) {
//        ArrayList<String> dataStringsToSend = new ArrayList<String>(lensesToSend.size());
//
//        for (LensEntity lens : lensesToSend) {
//            dataStringsToSend.add(lens.getDataString());
//        }

        Bundle bundle = new Bundle();
        bundle.putStringArrayList("lensArray", lensDataStrings);
        bundle.putBoolean("addToExisting", addToExisting);

        Intent intent = new Intent(AllLensesActivity.this, AllLensListsActivity.class);
        intent.putExtra("lensTransferInfo", bundle);
        startActivity(intent);
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

    private void updateLensChecked(LensEntity lens) {
        Timber.d("Update lens checked status for id: " + lens.getTag());
        if (lens.getChecked()) {
            numLensesChecked++;
        }
        else {
            numLensesChecked--;
        }

        if (!fromImport) {
            updateLensInDatabase(lens, false, false);
        }
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
                        addOrRemoveLensFromList(false, lens);
//                        allLenses.add(lens);
//                        Collections.sort(allLenses);
//                        fragment.updateAdapter();
                        updateLensListCount(false, lens);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d("insertLensInDatabase onError: " + error.getMessage());
                        CharSequence text = "Error inserting lens - please try again";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }



    /**
     * This method retrieves the lists for a given lens or array of lenses. It looks up the lens IDs
     * in the join table and gets the corresponding list IDs. It then adds those lists to the
     * listsToUpdate ArrayList, which is what's updated after deleting the lenses. After completing
     * the database query, it calls deleteLenses and passes it the LensEntity[] to be deleted.
     * @param lenses
     */
    private void getListsForLenses(final LensEntity... lenses) {
        listsToUpdate.clear();

        final long[] lensIds = SharedHelper.getLensIds(lenses);

        Observable.fromCallable(new Callable<List<LensListEntity>>() {
            @Override
            public List<LensListEntity> call() {
                return database.lensListLensJoinDao().getListsForLenses(lensIds);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensListEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("getListsForLenses onCompleted");
                        deleteLenses(lenses);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.e("ERROR: " + e.getMessage());
                    }

                    @Override
                    public void onNext(List<LensListEntity> lists) {
                        Timber.d("getListsForLenses onNext: " + lists.size());
                        for (LensListEntity list : lists) {
                            Timber.d("List: " + list.getName());
                            listsToUpdate.add(list);
                        }
                    }
                });
    }

    private void updateLensLists(final List<LensListEntity> lists) {
        final LensListEntity[] toUpdateArray = lists.toArray(new LensListEntity[lists.size()]);

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                database.lensListDao().update(toUpdateArray);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Timber.d("lens lists updated successfully");
                        for (LensListEntity list : lists) {
                            updateLensListInAllLensLists(list);
                        }
//                        setAllLensesCount();
//                        lensFileAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error updating lens list, please try again.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }
                });

    }

    private void updateLensListInAllLensLists(final LensListEntity list) {
        for (int i=0; i < allLensLists.size(); i++) {
            if (allLensLists.get(i).getId() == list.getId()) {
                allLensLists.set(i, list);
                return;
            }
        }
    }

    /**
     * This method selects lenses from the DB. It utilizes the LensHelper method getLenses to
     * determine which lenses to update. If manufacturer == null, it selects all lenses of
     * lensesToUpdate
     * @param manufacturer
     * @param series
     * @param checked
     */
    private void selectLensesInDatabase(final String manufacturer, String series, final boolean checked) {
        Timber.d("getting all " + manufacturer + " lenses from list");

        final LensEntity[] lensesToUpdate = LensHelper.getLenses(allLenses, manufacturer, series, checked);

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

                        // update the adapter if a checkbox was tapped from the Manufacturer or Series levels
                        if (manufacturer != null) {
                            fragment.updateAdapter();
                        }

                        // use a different updating method because the user tapped the checkbox for "Select All"
                        else {
                            fragment.updateAdapterFromSelectAll(checked);
                        }

                        if (value > 0) {
                            CharSequence toastText = (checked ? "Added " + value + " lenses to selection" : "Removed " + value + " lenses from selection");
                            SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_SHORT);
                        }
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

    /**
     * Make sure the user actually wants to clear the database of all lenses and lists
     */
    private void askToConfirmLensDelete() {
        final boolean deleteAll = numLensesChecked == numLenses;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_delete_all_lenses.xml, which we'll inflate to the dialog
                View deleteAllLensesView = inflater.inflate(deleteAll ? R.layout.dialog_delete_all_lenses : R.layout.dialog_delete_selected_lenses, null);

                final EditText deleteEditText = deleteAllLensesView.findViewById(R.id.confirmLensDeleteEditText);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(deleteAllLensesView)
                        .setPositiveButton("I'm Sure", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                if (deleteAll) {
                                    String confirmationText = deleteEditText.getText().toString().trim();
                                    if (confirmationText.equals(STRING_DELETE)) {
                                        deleteEverything();
                                    } else {
                                        CharSequence toastText = "Error: make sure you typed \"DELETE\"";
                                        SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_LONG);
                                    }
                                }

                                else {
                                    getSelectedLenses();
                                    deleteSelectedLenses();
                                }
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setCancelable(false);

                // create the alert dialog
                final AlertDialog alert = builder.create();

                // show the alert
                alert.show();
            }

        });
    }

    /**
     * This method deletes everything in the database. The user must confirm by typing "DELETE"
     * in the AlertDialog before this method is called.
     */
    private void deleteEverything() {
        Timber.d("clear the database");

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                database.lensListLensJoinDao().deleteAll();
                database.lensDao().deleteAll();
                database.lensListDao().deleteAll();

                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        CharSequence text = "Database cleared successfully";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_LONG);

                        returnToAllLensListsActivity();
                    }

                    @Override
                    public void onError(Throwable error) {

                    }
                });
    }

    /**
     * This method gets the selected lenses (populated via getSelectedLenses()) and creates a new array
     * of LensEntitys that are passed to the deleteLenses method.
     */
    private void deleteSelectedLenses() {
        LensEntity[] lensesToDelete = new LensEntity[lensesToSend.size()];

        int index = 0;
        for (LensEntity lens : lensesToSend) {
            lensesToDelete[index] = lens;
            index++;
        }

        getListsForLenses(lensesToDelete);
//        deleteLenses(lensesToDelete);
    }

    private void deleteLenses(final LensEntity... lens) {
        final int numLensesDeleted = lens.length;

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
                        updateLensListCount(true, lens);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d(error.getMessage());
                        CharSequence text = "Error deleting lens - please try again";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    private void updateLensInDatabase(final LensEntity lens, final boolean showToast, final boolean updateAdapter) {
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
                        showOrHideFab();

                        if (showToast) {
                            CharSequence text = "Lens updated successfully";
                            SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d("updateLensInDatabase onError: " + error.getMessage());
                        CharSequence text = "Error updating lens - please try again";
                        SharedHelper.makeToast(AllLensesActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    private void updateLensListCount(final boolean remove, final LensEntity... lens) {
        int numToUpdate = (remove ? listsToUpdate.size() : 1);
        int count;
        final LensListEntity[] lists = new LensListEntity[numToUpdate];
        if (remove) {
            for (int i = 0; i < listsToUpdate.size(); i++) {
                LensListEntity list = listsToUpdate.get(i);
                count = (list.getCount()) - lens.length;

                list.setCount(count);
                lists[i] = list;
            }

            numLensesChecked -= lens.length;
        }

        else {
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
                            addOrRemoveLensFromList(remove, lens);
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

    private void addOrRemoveLensFromList(boolean remove, LensEntity... lens) {
        if (remove) {
            for (LensEntity l : lens) {
                for (int i = 0; i < allLenses.size(); i++) {
                    LensEntity obj = allLenses.get(i);

                    if (obj.getId() == l.getId()) {
                        allLenses.remove(obj);
                        break;
                    }
                }
            }
        }

        else {
            for (LensEntity l : lens) {
                allLenses.add(l);
            }
        }

        numLenses = allLenses.size();
        setActivityTitle();
        Collections.sort(allLenses);
        fragment.updateAdapter();
    }

    /**
     * OnClick handler to the "Save selected" button. This method saves the selected lenses to the database.
     * Only relevant if the activity was launched via intent after receiving lenses from the HU3.
     * It first checks whether >= 1 lens is checked, and if so, gets the checked lenses and saves them to the database.
     * @param view the "Save selected" button
     */
    public void saveImportedLensesOnClick(View view) {
        // remove all the lenses that aren't selected
        ArrayList<LensEntity> lensesToSave = SharedHelper.getCheckedLenses(allLenses);

        if (lensesToSave.size() > 0) {
            selectImportedLensesAction(lensesToSave);
        }

        else {
            CharSequence toastText = "You must select at least one lens";
            SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_SHORT);
        }
    }

    /**
     * This method presents an AlertDialog to the user so they can select what they want to do with the imported lenses.
     * Based on their selection, the function performImportedLensesAction either lets them create a new list
     * or select an existing list(s) to add the lenses to.
     * @param lensesToSave
     */
    private void selectImportedLensesAction(final ArrayList<LensEntity> lensesToSave) {
        CharSequence lenses = (lensesToSave.size() > 1 ? " lenses " : " lens ");
        CharSequence title = String.valueOf(lensesToSave.size()) + lenses + "selected";

        AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);
        LayoutInflater inflater = getLayoutInflater();

        // the custom view is defined in dialog_import_lenses_select_action.xml, which we'll inflate to the dialog
        View selectActionView = inflater.inflate(R.layout.dialog_import_lenses_select_action, null);

        final RadioButton createNewListRadioButton = selectActionView.findViewById(R.id.createNewListRadioButton);

        builder.setView(selectActionView)
                .setTitle(title)
                .setPositiveButton("Next", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        boolean createNewList = createNewListRadioButton.isChecked();

                        performImportedLensesAction(createNewList, lensesToSave);
                    }
                })
                .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {

                    }
                })
                .setCancelable(true);


        // create the alert dialog
        final AlertDialog alert = builder.create();
        alert.show();
    }

    /**
     * This method displays an AlertDialog to the user to allow them to either:
     * 1) Create a new LensListEntity to add the selected lenses to; or
     * 2) Add the selected lenses to an existing LensListEntity.
     * @param createNewList
     */
    private void performImportedLensesAction(boolean createNewList, final ArrayList<LensEntity> lensesToSave) {
        if (createNewList) {
            CharSequence dialogTitle = "Create new lens list";

            AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);
            LayoutInflater inflater = getLayoutInflater();

            // the custom view is defined in dialog_import_lenses_select_action.xml, which we'll inflate to the dialog
            View newLensListView = inflater.inflate(R.layout.dialog_rename_lens_list, null);
            final EditText listNameEditText = newLensListView.findViewById(R.id.renameLensListNameEditText);
            final EditText listNoteEditText = newLensListView.findViewById(R.id.renameLensListNoteEditText);


            if (!invalidFileNames.contains(title)) {
                listNameEditText.setText(title);
            }

            builder.setView(newLensListView)
                    .setTitle(dialogTitle)
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            // get the list name and note entered by the user
                            String name = listNameEditText.getText().toString().trim();
                            String note = listNoteEditText.getText().toString().trim();

                            LensListEntity lensList = SharedHelper.buildLensList(name, note, lensesToSave.size());

                            // insert the lenses and list into the database
                            DatabaseHelper.insertLensesAndList(AllLensesActivity.this, lensesToSave, lensList);

                            resetUI();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })
                    .setCancelable(true);


            // create the alert dialog
            final AlertDialog alert = builder.create();
            alert.show();
        }

        else {
            CharSequence title = "Select list(s)";

            // Build the AlertDialog
            AlertDialog.Builder builder = new AlertDialog.Builder(AllLensesActivity.this);

            LayoutInflater inflater = getLayoutInflater();

            // the custom view is defined in dialog_import_lenses_select_action.xml, which we'll inflate to the dialog
            View addToExistingLensListsView = inflater.inflate(R.layout.dialog_add_to_existing_lens_list, null);
            ListView currentFilesListView = addToExistingLensListsView.findViewById(R.id.existingLensListsListView);

            // the custom adapter used to populate the ListView
            AllLensListsArrayAdapter adapter = new AllLensListsArrayAdapter(AllLensesActivity.this, allLensLists);

            adapter.setListener(new AllLensListsArrayAdapter.ListSelectedListener() {
                @Override
                public void onListSelected(LensListEntity list, boolean selected) {
                    Timber.d(list.getName() + " selected: " + selected);
                    if (selected) {
                        listsToAddImportedLensesTo.add(list);
                    }
                    else {
                        listsToAddImportedLensesTo.remove(list);
                    }
                }
            });

            currentFilesListView.setAdapter(adapter);

            builder.setView(addToExistingLensListsView)
                    .setTitle(title)
                    .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            DatabaseHelper.insertLensesToExistingLists(AllLensesActivity.this, lensesToSave, listsToAddImportedLensesTo);

                            CharSequence toastText = "Lenses added successfully";
                            SharedHelper.makeToast(AllLensesActivity.this, toastText, Toast.LENGTH_LONG);

                            allLenses = SharedHelper.setChecked(allLenses, null, null, false);

                            resetUI();
//                            DatabaseHelper.insertLensesAndList(AllLensesActivity.this, lensesToSave, name, note, lensesToSave.size(), true);
//                            returnToAllLensListsActivity();
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {

                        }
                    })
                    .setCancelable(true);

            // create the alert dialog
            final AlertDialog alert = builder.create();
            alert.show();
        }
    }

    /**
     * This method returns the user to the AllLensListsActivity. It's called after the user either creates
     * a new list or adds the selected lenses to an existing list.
     */
    private void returnToAllLensListsActivity() {
        Intent intent = new Intent(AllLensesActivity.this, AllLensListsActivity.class);

        startActivity(intent);
    }

    /**
     * This method is used to reset the UI after saving lenses to a list (or any other time we need
     * a fresh UI)
     */
    private void resetUI() {
        // TODO: make sure this is behaving as expected. Seems like some lenses are still checked
        SharedHelper.setChecked(allLenses, null, null, false);
//        saveLensesToDatabaseButton.setVisibility(View.GONE);
        fragment.updateSelectAllImageView(false);
        fragment.updateAdapter();
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }
}

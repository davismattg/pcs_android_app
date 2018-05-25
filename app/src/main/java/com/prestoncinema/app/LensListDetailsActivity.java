package com.prestoncinema.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.content.FileProvider;
import android.support.v4.view.ViewPager;
import android.text.InputType;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RadioButton;
import android.widget.Toast;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.DatabaseHelper;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;
import com.prestoncinema.app.ui.MyListFragment;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;

import static android.media.CamcorderProfile.get;
import static android.util.Log.d;


import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.SingleSubscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by MATT on 3/9/2017.
 * This activity is used to edit existing lens files. You can add/remove lenses, add/remove lenses to My List A/B/C
 * TODO: Give user ability to rename a lens
 * TODO: Restrict input length on focal length (9999mm max) and serial/note length (14 bytes including focal length)
 */

public class LensListDetailsActivity extends UartInterfaceActivity implements AdapterView.OnItemSelectedListener,
        MyListFragment.OnLensChangedListener, LensListFragment.OnLensAddedListener,
        LensListFragment.OnChildLensChangedListener, LensListFragment.OnLensSelectedListener,
        LensListFragment.OnLensManufacturerSelectedListener, LensListFragment.OnLensSeriesSelectedListener {

    // Log
    private final static String TAG = AllLensListsActivity.class.getSimpleName();

    // UI
    private ProgressDialog mProgressDialog;
    private LensListParentExpListViewAdapter expAdapter;
    private MyListExpListViewAdapter myListExpAdapter;
    private ExpandableListView myListExpListView;
    private ExpandableListView expListView;
    private TabLayout listTabs;
    private ViewPager viewPager;
    private FloatingActionButton fab;
    private Button sendButton;

    private int numLensesChecked = 0;

    private List<String> lensListDataHeader = new ArrayList<>(Arrays.asList("Angenieux", "Canon", "Cooke", "Fujinon", "Leica", "Panavision", "Zeiss", "Other"));
    private Map<Integer, Integer> lensListDataHeaderCount = new HashMap<Integer, Integer>(lensListDataHeader.size());

    private List<String> myListDataHeader;
    private HashMap<String, List<LensEntity>> myListDataChild;
    private List<LensEntity> temporaryLensList = new ArrayList<>();

    private ArrayList<LensEntity> lensObjectArray = new ArrayList<>();
    private ArrayList<LensEntity> lensObjectArrayToSend = new ArrayList<>();

    private List<String> lensListManufHeader = new ArrayList<String>();
    private HashMap<String, List<String>> lensListTypeHeader = new HashMap<>();

    private ImageView mAddLensImageView;
//    private TextView noteTextView;

    private ArrayList<String> lensArray = new ArrayList<String>();
    private ArrayList<LensEntity> lensesFromIntent = new ArrayList<LensEntity>();
    private HashMap<Integer, HashMap<String, Object>> lensMap = new HashMap<Integer, HashMap<String, Object>>();
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap = new HashMap<Integer, HashMap<Integer, ArrayList<Integer>>>();
    private List<LensListEntity> listsToUpdate = new ArrayList<LensListEntity>();

    private int numLenses = 0;
    private String lensFileString = "";
    private String lensFileStringStripped = "";
    private String lensFileNote;

    private LensListEntity currentLensList;
    private File lensFile;
    private boolean isPrime = false;

    private int currentTab;
    private boolean myListEditEnabled = false;

    private boolean isConnected = false;

    private int ang_byte = 0x0;
    private int can_byte = 0x1;
    private int cooke_byte = 0x2;
    private int fuj_byte = 0x3;
    private int lei_byte = 0x4;
    private int pan_byte = 0x5;
    private int zei_byte = 0x6;
    private int oth_byte = 0xF;
    private int maxSerialLength = 14;

    private boolean allLensesSelected = false;

    private int lensId;                                                 // used to identify
    private long currentListId = 0;

    private int MODE_HU3 = 0;
    private int MODE_SHARE = 1;
    private int MODE_SELECTED = 2;
    private int MODE_SAVE = 3;

    private byte[] STX = {02};
    private byte[] ETX = {0x0A, 0x0D};
    private String STXStr = new String(STX);
    private String ETXStr = new String(ETX);

    private AppExecutors appExecutors;
    private AppDatabase database;

    private LensListFragmentAdapter lensListFragmentAdapter;

    private AllLensListsArrayAdapter allLensListsArrayAdapter;
    private AllLensListsArrayAdapter.ListSelectedListener allLensListsListener;
    private ArrayList<LensListEntity> allLensLists;
    private ArrayList<LensListEntity> selectedLensLists;

    public LensListDetailsActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lens_list);

        Timber.d("onCreate called =====");

        /* Database instantiation */
        appExecutors = new AppExecutors();
        database = AppDatabase.getInstance(LensListDetailsActivity.this, appExecutors);

        // UI initialization
        mAddLensImageView = findViewById(R.id.lensTypeAddImageView);
        fab = findViewById(R.id.LensListFab);
        sendButton = findViewById(R.id.sendSelectedLensesButton);

        for (int i = 0; i < lensListDataHeader.size(); i++) {
            lensListDataHeaderCount.put(i, 0);
            lensPositionMap.put(i, new HashMap<Integer, ArrayList<Integer>>());
        }

        lensListManufHeader = Arrays.asList(getResources().getStringArray(R.array.lens_manuf_array));                                                   // use the lens manuf string array resource to populate the headers of the lens list view
        lensListTypeHeader = SharedHelper.populateLensTypeHeader(getApplicationContext(), lensListManufHeader);                                                                               // using the header, populate the children (lens series) header

        /* Initialize the My List HashMap used to populate the My List ExpandableListView by adding empty List<LensEntity> for each list */
        myListDataChild = new HashMap<String, List<LensEntity>>();
        myListDataChild.put("My List A", new ArrayList<LensEntity>());
        myListDataChild.put("My List B", new ArrayList<LensEntity>());
        myListDataChild.put("My List C", new ArrayList<LensEntity>());

//        noteTextView = findViewById(R.id.lensListNoteTextView);
        allLensLists = new ArrayList<>();

        /* Get the filename string from the previous activity (AllLensListsActivity) and import the file */
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lensFileString = extras.getString("lensFile");
            lensFileStringStripped = lensFileString;

            lensFileNote = extras.getString("listNote");

            lensesFromIntent = getIntent().getParcelableArrayListExtra("lenses");

            allLensLists = getIntent().getParcelableArrayListExtra("allLensLists");

            currentListId = extras.getLong("listId");

            isConnected = extras.getBoolean("connected");
            importLenses();

            /* Set the activity title in the top bar */
            updateActivityTitle();
        }

        if (savedInstanceState != null) {
            Timber.d("restored with savedInstanceState");

            lensFileString = savedInstanceState.getString("lensFileString");
            lensFileStringStripped = savedInstanceState.getString("lensFileStringStripped");

            lensFile = new File(lensFileString);

            importLenses();
        }

        /* Initialize the data header for the My List ListView */
        myListDataHeader = Arrays.asList(getResources().getStringArray(R.array.my_list_array));                                                         // use the my list string array resource to populate the header of the my list list view

        selectedLensLists = new ArrayList<>();

//        /* Initialize the FloatingActionButton used to send the lenses to HU3 My Lists */
//        sendButton.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View v) {
//                Timber.d("FAB clicked, export lenses to HU3");
//
//                if (isConnected) {
//                    sendLensesFromFab();
//                }
//
//                else {
//                    CharSequence toastText = "Error: Not connected to Preston Updater";
//                    SharedHelper.makeToast(LensListDetailsActivity.this, toastText, Toast.LENGTH_SHORT);
//                }
//            }
//        });

        /* Initialize the tabs that are used to toggle between My List and All Lenses ExpandableListViews */
        viewPager = findViewById(R.id.LensFileTabViewPager);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                currentTab = position;
                Timber.d("current tab index: " + currentTab);
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        listTabs = findViewById(R.id.LensFileTabLayout);
        listTabs.setupWithViewPager(viewPager);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        Timber.d("onPause called -----------------");
        super.onPause();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Timber.d("onSaveInstanceState called =========");
        // Save the contents of the lens lists when the device is rotated or activity is destroyed for some reason

        savedInstanceState.putString("lensFileString", lensFileString);
        savedInstanceState.putString("lensFileStringStripped", lensFileStringStripped);

        super.onSaveInstanceState(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
    }


    /* ---------------------------------------------------------------------------------------------------------------------------- */
    /* ---------------------------------------------------------------------------------------------------------------------------- */
    /* The following methods facilitate communication with the tabbed ViewPager that holds the My Lists and All Lenses lists. */
    /** onLensChanged handles changes made to a lens from within one of the "My List" tabs
     * @param lens
     * @param serial
     * @param note
     * @param listA
     * @param listB
     * @param listC
     */
    public void onLensChanged(LensListEntity lensList, LensEntity lens, String serial, String note, boolean listA, boolean listB, boolean listC) {
        onChildLensChanged(lensList, lens, serial, note, listA, listB, listC);
    }

    /** onLensDeleted handles when the user deletes a lens from the popup within one of the "My List" tabs
     * @param lens
     */
    public void onLensDeleted(LensListEntity lensList, LensEntity lens) {
        getListsForLens(lensList, lens);
//        deleteAssociations(lens);
    }

    /** onLensAdded handles new lenses that are added from the "All Lenses" tab by clicking the + button next to a lens series header
     *
     * @param manuf
     * @param series
     * @param focal1
     * @param focal2
     * @param serial
     * @param note
     */
    public void onLensAdded(String manuf, String series, int focal1, int focal2, String serial, String note) {
        buildLensData(manuf, series, focal1, focal2, serial, note, false, false, false);
    }

    /** onChildLensChanged handles changes to existing lenses from within the "All Lenses" tab
     *
     * @param lens
     * @param serial
     * @param note
     * @param listA
     * @param listB
     * @param listC
     */
    public void onChildLensChanged(LensListEntity lensList, LensEntity lens, String serial, String note, boolean listA, boolean listB, boolean listC) {
        HashMap<String, Object> lensAndListMap = LensHelper.editListAndLens(lensList, lens, serial, note, listA, listB, listC);

        LensEntity editedLens = (LensEntity) lensAndListMap.get("lens");
        LensListEntity editedList = (LensListEntity) lensAndListMap.get("list");

        updateLensInDatabase(editedLens, true, true);
        updateLensListInDatabase(editedList);
    }

    /** onChildLensDeleted handles deleting a lens when the user selects "Delete" from the Edit Lens dialog
     *
     * @param lens
     */
    public void onChildLensDeleted(LensListEntity lensList, LensEntity lens) {
        getListsForLens(lensList, lens);
//        deleteLens(lens);
    }

    /** onLensesSelected handles selecting/deselecting lenses from the list
     *
     * @param lens
     */
    public void onLensSelected(LensEntity lens) {
        Timber.d("selected lens: " + lens.getId() + ", checked: " + lens.getChecked());
        updateLensChecked(lens);
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

    public void onSeriesSelected(String manufacturer, String series, boolean seriesChecked, boolean checkParent) {
        String verb = seriesChecked ? "Check" : "Uncheck";
        Timber.d(verb + " all lenses for " + manufacturer + " " + series);
        selectLensesInDatabase(manufacturer, series, seriesChecked);
    }

    private void showOrHideFab() {
        if (numLensesChecked < 0) {
            numLensesChecked = 0;
        }

        if (numLensesChecked > 0) {
            sendButton.setVisibility(View.VISIBLE);
        }
        else {
            sendButton.setVisibility(View.INVISIBLE);
        }
    }
    /* ------------------------------------------------------------------------------------------------------------------------------------------- */
    /* ------------------------------------------------------------------------------------------------------------------------------------------- */


    // the menu that's created when the user long-presses on a lens within the lens list
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        super.onCreateContextMenu(menu, v, menuInfo);

//        ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) menuInfo;
//        int type = ExpandableListView.getPackedPositionType(info.packedPosition);
//        int group = ExpandableListView.getPackedPositionGroup(info.packedPosition);
//        int child = ExpandableListView.getPackedPositionChild(info.packedPosition);

        int viewId = v.getId();

//        Timber.d(v.toString());
//        Timber.d("view tag: ");
//        Timber.d(String.valueOf(v.getTag()));

//        View targView = info.targetView;

//        long tag = info.tag;
//        Timber.d("Type: " + type + ", Group: " + group + ", Child: " + child + ", tag: " + tag);

        lensId = viewId;

//        if (type == 1) {
            MenuInflater inflater = getMenuInflater();
            inflater.inflate(R.menu.lens_context_menu, menu);
//        }

//        lensId = (int) v.getTag();
//        Timber.d("View: ");
//        Timber.d(v.toString());
//        menu.setHeaderTitle("Index: " + v.getTag().toString());
//
//        for (int tag : hideItems) {
//            menu.findItem(tag).setVisible(false);
//        }

    }

    // handle the user's item selection
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ExpandableListView.ExpandableListContextMenuInfo info = (ExpandableListView.ExpandableListContextMenuInfo) item.getMenuInfo();
        switch (item.getItemId()) {
            case R.id.editLensContextMenuItem:
                Timber.d("edit lens: " + lensId);
                return true;
            case R.id.deleteLensContextMenuItem:
                Timber.d("delete lens: " + lensId);
//                confirmLensDelete(info);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_manage_lenses, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        Context context = getApplicationContext();
        CharSequence toastText = "This feature coming soon.";
        int duration = Toast.LENGTH_SHORT;

        // make a toast letting the user know that this feature is coming soon.
        Toast toast = Toast.makeText(context, toastText, duration);

        switch (id) {
//            case R.id.selectAllLensesMenuItem:
//                Timber.d("select all lenses in this list");
//                selectLensesInDatabase(null, null, !allLensesSelected);
//                break;
            case R.id.renameLensFileMenuItem:
                if (currentLensList != null) {
                    Timber.d("rename the lens file");
                    renameLensList(currentLensList);
                }
                break;
        }

        return super.onOptionsItemSelected(item);
    }

//    private void respondToFab(boolean editEnabled, int tab) {
//        if (!editEnabled) {
//            Timber.d("save the changes to tab: " + tab);
//            fab.setImageResource(R.drawable.ic_edit_24dp);
//        }
//        else {
//            Timber.d("enable editing for tab: " + tab);
//            fab.setImageResource(R.drawable.ic_done_white_24dp);
//        }
//    }

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // this function brings up a dialog box where the user can enter a new name for the lens file.  //
    // before renaming, it checks if the filename is already in use, and prevents duplicate names   //
    // in that case.                                                                                //
    //////////////////////////////////////////////////////////////////////////////////////////////////
    private void renameLensList(final LensListEntity list) {
        final String oldName = list.getName();
        final String oldNote = list.getNote();

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensListDetailsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens_list_list.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens_list, null);
                final EditText fileNameEditText = renameLensView.findViewById(R.id.renameLensListNameEditText);
                final EditText fileNoteEditText = renameLensView.findViewById(R.id.renameLensListNoteEditText);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(renameLensView)
                        .setTitle("Enter new filename")
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setCancelable(true);

                // set the text to the existing filename and select it
                fileNameEditText.setText(oldName);
                fileNameEditText.setSelection(oldName.length());
                fileNoteEditText.setText(oldNote);

                // create the alert dialog
                final AlertDialog alert = builder.create();

                // force the keyboard to be shown when the alert dialog appears
                alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                alert.show();

                //Overriding the onClick handler so we can check if the file name is already in use
                alert.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener()
                {
                    @Override
                    public void onClick(View v)
                    {
                        // get the text entered by the user
                        String newName = fileNameEditText.getText().toString().trim().replace(".lens", "");           // do some housekeeping on the user-entered string
                        String newNote = fileNoteEditText.getText().toString().trim();

                        // TODO: make sure the filename check is working robustly
                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {
                            Timber.d("\n\nOriginal list name: " + oldName);
                            Timber.d("Save the file as: " + newName);

                            // rename the file
                            list.setName(newName);
                            list.setNote(newNote);

                            updateLensListInDatabase(list);
                            alert.dismiss();
                        }

                        else {
                            // make a toast to inform the user the filename is already in use
                            Context context = getApplicationContext();
                            CharSequence toastText = "That filename already exists";
                            int duration = Toast.LENGTH_LONG;

                            Toast toast = Toast.makeText(context, toastText, duration);
                            toast.show();
                        }
                    }
                });
            }
        });
    }

    // check if the filename newFile is already in use on the phone
    private boolean checkLensFileNames(String newFile) {
        ArrayList<String> currentFileNames = getLensFiles();
        if (currentFileNames.contains(newFile.trim().toLowerCase())) {
            return false;
        }
        else {
            return true;
        }
    }

    // get the existing lens files
    private ArrayList<String> getLensFiles() {
        File path = new File(getExternalFilesDir(null), "");                       // the external files directory is where the lens files are stored
        File[] savedLensFiles = path.listFiles();
        if (savedLensFiles.length > 0) {
            ArrayList<String> fileStrings = new ArrayList<String>();
            for (int i = 0; i < savedLensFiles.length; i++) {
                String[] splitArray = savedLensFiles[i].toString().split("/");
                fileStrings.add(i, splitArray[splitArray.length - 1].toLowerCase());
            }
            return fileStrings;
        }
        else {
            return new ArrayList<String>();
        }
    }

    // function for when the user is adding a new lens - when manufacturer name is selected,
    // populate the lens type dropdown (spinner in Android) with the correct lens names
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // get the item that was selected, and call toString() to get the lens manuf. name
        String type_id = parent.getItemAtPosition(pos).toString();
        Timber.d("item selected: " + type_id);

        // create the new adapter based on the lens manuf. selection. this populates the lens type spinner
//        if (parent.getTag() == R.tag.LensManufSpinner) {
//            switch (type_id) {
//                case "Angenieux":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Angenieux, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Canon":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Canon, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Cooke":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Cooke, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Fujinon":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Fujinon, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Leica":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Leica, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Panavision":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Panavision, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Zeiss":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Zeiss, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                case "Other":
//                    typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Other, android.R.layout.simple_spinner_item);
//                    typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
//                    mLensTypeSpinner.setAdapter(typeAdapter);
//                    mLensTypeSpinner.setSelection(0, false);
//                    mLensTypeSpinner.setOnItemSelectedListener(this);
//                    break;
//                default_lenses:
//                    break;
//            }
//        }
//        else {                                  // item clicked was part of lens type spinner
//            checkFocalLengthType(type_id);
//        }
    }

    private void updateLensListInDatabase(final LensListEntity list) {
        final LensListEntity[] listToUpdate = new LensListEntity[1];
        listToUpdate[0] = list;

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                database.lensListDao().update(listToUpdate);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Timber.d("lens list updated successfully");
                        updateActivityTitle();
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error updating lens list, please try again.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }
                });
    }

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    private HashMap<String, List<String>> populateLensNameHeader(HashMap<String, List<String>> header) {
        HashMap<String, List<String>> lensNamesEmpty = new HashMap<>();
        List<String> emptyList = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : header.entrySet()) {
            String manufName = entry.getKey();

            for (String val : entry.getValue()) {
                String fullName = manufName + " - " + val;
                lensNamesEmpty.put(fullName, emptyList);
            }
        }

        return lensNamesEmpty;
    }

    private HashMap<String, List<Integer>> populateLensIndex(HashMap<String, List<String>> header) {
        HashMap<String, List<Integer>> lensIndexEmpty = new HashMap<>();
        List<Integer> emptyList = new ArrayList<>();

        for (Map.Entry<String, List<String>> entry : header.entrySet()) {
            String manufName = entry.getKey();

            for (String val : entry.getValue()) {
                String fullName = manufName + " - " + val;
                lensIndexEmpty.put(fullName, emptyList);
            }
        }

        return lensIndexEmpty;
    }

//    private populateLensMap

//    // function to populate the lens type HashMap with each lens type name, based on manufacturer name
//    private List<String> populateLensManufHeader() {
//        List<String> manufNames = new ArrayList<String>();              // the return value
//
//        // access the string-array resource where the manufacturer names are stored
//        manufNames = Arrays.asList(getResources().getStringArray(R.array.lens_manuf_array));
//
//        for (String manufName : lensListDataHeader) {                                                   // loop through array of manuf names
//            final int arrayId;                                            // the ID of the string-array resource containing the lens names
//
//            switch (manufName) {
//                case "Angenieux":
//                    arrayId = R.array.lens_type_Angenieux;
//                    break;
//                case "Canon":
//                    arrayId = R.array.lens_type_Canon;
//                    break;
//                case "Cooke":
//                    arrayId = R.array.lens_type_Cooke;
//                    break;
//                case "Fujinon":
//                    arrayId = R.array.lens_type_Fujinon;
//                    break;
//                case "Leica":
//                    arrayId = R.array.lens_type_Leica;
//                    break;
//                case "Panavision":
//                    arrayId = R.array.lens_type_Panavision;
//                    break;
//                case "Zeiss":
//                    arrayId = R.array.lens_type_Zeiss;
//                    break;
//                case "Other":
//                    arrayId = R.array.lens_type_Other;
//                    break;
//                default_lenses:
//                    arrayId = R.array.lens_type_Empty;
//                    break;
//            }
//
//            lensTypes.put(manufName, Arrays.asList(getResources().getStringArray(arrayId)));
//        }
//
//        return lensTypes;
//    }

    // ask the user if they want to delete a lens; called when they select the "Delete" option from the lens context menu
//    private void confirmLensDelete(AdapterView.AdapterContextMenuInfo lens) {
//        final int id = (int) lens.id;
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                new AlertDialog.Builder(LensListDetailsActivity.this)
//                    .setMessage("Are you sure you want to delete this lens?\n\nThis will not remove it from the HU3 until you export this lens file to HU3.")
//                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                            deleteLens(id);         // delete the lens from the lens array
//                        }
//                    })
//                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                        }
//                    })
//                    .setCancelable(false)
//                    .show();
//        }
//        });
//    }


    private void importLenses() {
        Timber.d("importing lens list");

        Single.fromCallable(new Callable<LensListEntity>() {
            @Override
            public LensListEntity call() {
                return database.lensListDao().loadLensList(currentListId);
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<LensListEntity>() {
                    @Override
                    public void onSuccess(LensListEntity list) {
                        currentLensList = list;
                        Timber.d("assigned current lens list");
                        initializeLenses();
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error assigning lens list. Please try again.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
                    }
                });


    }

    private void initializeLenses() {
        lensArray.clear();                                                                          // clear the lens array since we'll be populating it with the file contens

        for (LensEntity lens : lensesFromIntent) {
            lensArray.add(lens.getDataString());
            if (lens.getChecked()) numLensesChecked++;
        }

//        if (lensArray.size() > 0) {                                                                 // make sure something was actually imported
            numLenses = lensArray.size();                                                           // the number of lenses, used for loops and display on the UI

            lensObjectArray = new ArrayList<>(numLenses);
            updateLensList(true);
//        }

        if (numLensesChecked == numLenses) {
            allLensesSelected = true;
        }

        lensListFragmentAdapter = new LensListFragmentAdapter(getSupportFragmentManager(), currentLensList, myListDataHeader, myListDataChild, lensListManufHeader, lensListTypeHeader, lensListDataHeaderCount, lensPositionMap, lensObjectArray, lensFileNote, LensListDetailsActivity.this);
        viewPager.setAdapter(lensListFragmentAdapter);

        showOrHideFab();

    }

    private void populateAllLenses() {
        Timber.d("populating lenses (" + numLenses + ")");
        lensObjectArray.clear();
        initializeLensListHeaderCount();
        for (int i=0; i < numLenses; i++) {
            LensEntity lens = lensesFromIntent.get(i);
            String dataStr = lens.getDataString();
            countLensLine(dataStr);
//            LensEntity thisLens = parseLensLine(dataStr, i, lensesFromIntent.get(i).getId(), true);
            lensObjectArray.add(i, lens);
        }

        sortLensObjectArray();

        Timber.d("lensObjectArray populated and sorted");
    }

    private void populateMyLists() {
        myListDataChild.get("My List A").clear();
        myListDataChild.get("My List B").clear();
        myListDataChild.get("My List C").clear();


        for (LensEntity thisLens : lensObjectArray) {
//            if (thisLens.getMyListA()) {
            if (currentLensList.getMyListALongIds().contains(thisLens.getId())) {
                temporaryLensList = myListDataChild.get("My List A");
                temporaryLensList.add(thisLens);
                myListDataChild.put("My List A", temporaryLensList);
            }

//            if (thisLens.getMyListB()) {
            if (currentLensList.getMyListBLongIds().contains(thisLens.getId())) {
                temporaryLensList = myListDataChild.get("My List B");
                temporaryLensList.add(thisLens);
                myListDataChild.put("My List B", temporaryLensList);
            }

//            if (thisLens.getMyListC()) {
            if (currentLensList.getMyListCLongIds().contains(thisLens.getId())) {
                List<LensEntity> myListCLenses = myListDataChild.get("My List C");
                myListCLenses.add(thisLens);
                myListDataChild.put("My List C", myListCLenses);
            }
        }
    }

    private void initializeLensListHeaderCount() {
        lensListDataHeaderCount.clear();
        for (int i = 0; i < lensListDataHeader.size(); i++) {
            lensListDataHeaderCount.put(i, 0);
//            lensPositionMap.put(i, new HashMap<Integer, ArrayList<Integer>>());
        }
    }


    private void sortLensObjectArray() {
        Collections.sort(lensObjectArray);
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // This function takes in the raw string from the lens file and formats it in the way we want  //
    // to display it in the UI. Check the HU3 document/ask Mirko for the data structure            //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    private LensEntity parseLensLine(String line, int index, long lensId, boolean isNewLens) {
        /* Initialize the LensEntity object that will store all the info about this lens */
        LensEntity lensObject = new LensEntity(); //(index, "","", "", 0, 0,
//                0, 0, false, "", "", false,
//                false, false, false, false, false, false, null);
        lensObject.setId(lensId);

        byte[] bytes = line.getBytes();                                                             // get the hex bytes from the ASCII string

        /* Lens status (calibrated, myList, etc) */
        byte[] status1 = Arrays.copyOfRange(bytes, 14, 16);                               // bytes 15 and 16 (ASCII bytes) are the first (hex) status byte
        HashMap<String, boolean[]> statusMap = convertLensStatus(status1);
        lensObject.setCalibratedF(statusMap.get("calibrated")[0]);
        lensObject.setCalibratedI(statusMap.get("calibrated")[1]);
        lensObject.setCalibratedZ(statusMap.get("calibrated")[2]);
        lensObject.setMyListA(statusMap.get("myList")[0]);
        lensObject.setMyListB(statusMap.get("myList")[1]);
        lensObject.setMyListC(statusMap.get("myList")[2]);

        /* Lens Manufacturer and Type */
        byte[] status2 = Arrays.copyOfRange(bytes, 16, 18);                                         // bytes 17 and 18 (ASCII bytes) are the second (hex) status byte
        HashMap<String, Object> nameAndTypeMap = convertManufName(status2);
        lensObject.setManufacturer((String) nameAndTypeMap.get("manufacturer"));
        lensObject.setSeries((String) nameAndTypeMap.get("series"));

        // adding the lens' tag to the correct position to be retrieved later in the ListView
        int manufPos = (int) nameAndTypeMap.get("manufPosition");                                   // position of the manufacturer header in the ListView
        int seriesPos = (int) nameAndTypeMap.get("seriesPosition");                                 // position of the series header within the manufacturer header of the ListView
        lensObject.setManufacturerPosition(manufPos);
        lensObject.setSeriesPosition(seriesPos);

        HashMap<Integer, ArrayList<Integer>> currentLensPositionMap = lensPositionMap.get(manufPos);        // get the current Map for this position combo
        ArrayList<Integer> idArrayList = currentLensPositionMap.get(seriesPos);                             // array of ids currently assigned to this position combo

        boolean isNull = idArrayList == null;                                                       // null check. if idArrayList is null, we need to initialize it
        if (isNull) {
            idArrayList = new ArrayList<>();
        }

        if (isNewLens) {
            idArrayList.add(index);                                                                 // add the current lens tag to the ArrayList for this position combo
        }

        currentLensPositionMap.put(seriesPos, idArrayList);                                         // add the tag ArrayList to the placeholder HashMap
        lensPositionMap.put(manufPos, currentLensPositionMap);                                      // add the ids back into the correct position of the overall lens position map

        /* Focal length(s) */
        String focal1 = line.substring(18, 22);                                                     // bytes 19-22 (ASCII bytes) are the first (hex) focal length byte
        String focal2 = line.substring(22, 26);                                                     // bytes 23-26 (ASCII bytes) are the second (hex) focal length byte
        lensObject.setFocalLength1(convertFocalLength(focal1));
        lensObject.setFocalLength2(convertFocalLength(focal2));

        /* Serial number */
        String serial = line.substring(26, 30);
        String convertedSerial = convertSerial(serial);
        lensObject.setSerial(convertedSerial);

        /* Note */
        String lensName = line.substring(0, 14);                                                    // get the substring that contains the note (& serial & focal lengths)
        int noteBegin;
        String lensNote;
        if (convertedSerial.length() > 0) {                                                         // serial string present, look for it in the lens name
            noteBegin = lensName.indexOf(convertedSerial) + convertedSerial.length();               // set the tag to separate the lens serial and note
        }
        else {
            noteBegin = lensName.indexOf("mm") + 2;                                                 // no serial present, so anything after "mm" is considered the note
        }

        lensNote = lensName.substring(noteBegin).trim();                                            // grab the note using the tag determined above
        lensObject.setNote(lensNote);                                                               // set the note property of the lens object

        /* Data String (raw String that gets sent to HU3 */
        lensObject.setDataString(line);

        /* isPrime */
        lensObject.setIsPrime(SharedHelper.isPrime(lensObject.getSeries()));

        /* Checked attribute TODO: make sure this pulls the value from DB correctly */
        lensObject.setChecked(false);

        return lensObject;
    }

    // function to get the tag after the last character of the lens name (focal length and serial) //
    // the lens name is in the format: 24-290mm 123 if they entered a serial number. if they didn't, //
    // the lens name is in the format: 24-290mm. so we look for the spaces //
    private String convertLensName(byte[] bytes) {
        int ind = -1;
        boolean firstSpaceFound = false;
        for (int i=0; i<bytes.length; i++) {
            if (bytes[i] == 32) {
                if (!firstSpaceFound) {
                    if (bytes[i + 1] == 32) {
                        ind = i;
                        break;
                    } else {
                        firstSpaceFound = true;
                    }
                }
                else {
                    ind = i;
                    break;
                }
            }
        }

        if (ind != -1) {
            return new String(Arrays.copyOfRange(bytes, 0, ind));
        }
        else {
            return new String(Arrays.copyOfRange(bytes, 0, bytes.length));
        }
    }

    /* This method accepts a status byte as input and returns a map of the lens' manufacturer name and series as strings.
    It calls the methods bytesToLensManuf and bytesToLensType to determine each of those values   */
    private HashMap<String, Object> convertManufName(byte[] status) {
        HashMap<String, Object> lensManufAndTypeMap = new HashMap<>();
        String manufName = (String) bytesToLensManuf(status).get("manufacturer");
        String manufSeries = (String) bytesToLensSeries(status).get("series");
        int manufPos = (int) bytesToLensManuf(status).get("groupPos");
        int seriesPos = (int) bytesToLensSeries(status).get("seriesPos");

        lensManufAndTypeMap.put("manufacturer", manufName);
        lensManufAndTypeMap.put("series", manufSeries);
        lensManufAndTypeMap.put("manufPosition", manufPos);
        lensManufAndTypeMap.put("seriesPosition", seriesPos);

        return lensManufAndTypeMap;
    }

    /* This method accepts a status byte as input and returns the lens manufacturer and group position within the ListView according to that status byte */
    private HashMap<String, Object> bytesToLensManuf(byte[] status) {
        HashMap<String, Object> manufNameAndPosition = new HashMap<>();
        String name;
        int groupPos;
        switch (status[0]) {
            case 48:
                name = "Angenieux";
                groupPos = 0;
                break;
            case 49:
                name = "Canon";
                groupPos = 1;
                break;
            case 50:
                name = "Cooke";
                groupPos = 2;
                break;
            case 51:
                name = "Fujinon";
                groupPos = 3;
                break;
            case 52:
                name = "Leica";
                groupPos = 4;
                break;
            case 53:
                name = "Panavision";
                groupPos = 5;
                break;
            case 54:
                name = "Zeiss";
                groupPos = 6;
                break;
            default:
                name = "Other";
                groupPos = 7;
                break;
        }

        manufNameAndPosition.put("manufacturer", name);
        manufNameAndPosition.put("groupPos", groupPos);
        return manufNameAndPosition;
    }

    /* This method accepts a status byte as input and returns the lens series according to that status byte
        The type is dependent on the manufacturer name as well which is why there are two switch statements. */
    private HashMap<String, Object> bytesToLensSeries(byte[] status) {
        HashMap<String, Object> seriesAndPosition = new HashMap<>();
        String manufType;
        int seriesPos;
        switch (status[0]) {
            case 48:
                switch (status[1]) {
                    case 48:
                        manufType = "Optimo";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Rouge";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "HR";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Other";
                        seriesPos = 3;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 3;
                        break;
                }
                break;
            case 49:
                switch (status[1]) {
                    case 48:
                        manufType = "Cinema Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Cinema Zoom";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Other";
                        seriesPos = 2;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 2;
                        break;
                }
                break;
            case 50:
                switch (status[1]) {
                    case 48:
                        manufType = "S4";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "S5";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Panchro";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Zoom";
                        seriesPos = 3;
                        break;
                    case 52:
                        manufType = "Other";
                        seriesPos = 4;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 4;
                        break;
                }
                break;
            case 51:
                switch (status[1]) {
                    case 48:
                        manufType = "Premier Zoom";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Alura Zoom";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Prime";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Other";
                        seriesPos = 3;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 3;
                        break;
                }
                break;
            case 52:
                switch (status[1]) {
                    case 48:
                        manufType = "Summilux Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Other";
                        seriesPos = 1;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 1;
                        break;
                }
                break;
            case 53:
                switch (status[1]) {
                    case 48:
                        manufType = "Primo Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Primo Zoom";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Anam. Prime";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Anam. Zoom";
                        seriesPos = 3;
                        break;
                    case 52:
                        manufType = "P70 Prime";
                        seriesPos = 4;
                        break;
                    case 53:
                        manufType = "Other";
                        seriesPos = 5;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 5;
                        break;
                }
                break;
            case 54:
                switch (status[1]) {
                    case 48:
                        manufType = "Master Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Ultra Prime";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Compact Prime";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Zoom";
                        seriesPos = 3;
                        break;
                    case 52:
                        manufType = "Other";
                        seriesPos = 4;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 4;
                        break;
                }
                break;
            default:
                switch (status[1]) {
                    case 48:
                        manufType = "Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Zoom";
                        seriesPos = 1;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 0;
                        break;
                }
                break;
        }

        seriesAndPosition.put("series", manufType);
        seriesAndPosition.put("seriesPos", seriesPos);
        return seriesAndPosition;
    }

    /* Method that accepts String of lens focal length (in hex representation, 4 characters) and returns that value as a (decimal) integer */
    private int convertFocalLength(String focal) {
        return Integer.parseInt(focal, 16);
    }

    /* Method to build the correctly formatted focal length(s) String depending on if the lens is a zoom or prime (focalLength2 == 0) */
    public static String constructFocalLengthString(int fL1, int fL2) {
        if (fL2 > 0) {                                                                     // fL2 > 0 implies zoom lens
            return String.valueOf(fL1) + "-" + String.valueOf(fL2) + "mm";
        }
        return String.valueOf(fL1) + "mm";                                                                          // prime lens, so just return the first FL
    }

    /* Method that accepts a String of the lens serial number (in hex representation, 4 characters) and returns that value as a (decimal) integer */
    private String convertSerial(String serial) {
        int serialInDecimal = Integer.parseInt(serial, 16);                                         // convert from hex to decimal
        if (serialInDecimal > 0) {                                                                  // if serial > 0, user entered a serial for this lens
            return Integer.toString(serialInDecimal);
        }
        return "";                                                                                  // no serial entered, return empty string
    }


    // use the hex characters to parse the lens calibration status and if it's a member of any lists
    // just follow mirko's lens data structure //
    private HashMap<String, boolean[]> convertLensStatus(byte[] bytes) {
        /* Initialize variables. lensStatusMap is return value, containing a value for keys "calibrated" and "myList" */
        HashMap<String, boolean[]> lensStatusMap = new HashMap<String, boolean[]>();
        boolean FCal = false;
        boolean ICal = false;
        boolean ZCal = false;
        boolean myListA = false;
        boolean myListB = false;
        boolean myListC = false;
        boolean[] calArray = new boolean[3];
        boolean[] listArray = new boolean[3];

        // check the first byte to determine the status
        switch (bytes[0]) {
            case 70:    // F
                FCal = true;
                myListC = true;
                myListB = true;
                break;
            case 69:    // E
                FCal = true;
                myListC = true;
                break;
            case 68:    // D
                FCal = true;
                myListB = true;
                break;
            case 67:    // C
                FCal = true;
                break;
            case 66:    // B
                myListC = true;
                myListB = true;
                break;
            case 65:    // A
                myListC = true;
                break;
            case 57:    // 9
                myListB = true;
                break;
            default:        // 8 => no list, F not calibrated. Default case
                break;
        }

        // check the second byte to dermine the status
        switch (bytes[1]) {
            case 70: case 69:  // F & E (since we don't care about the Z bit)
                myListA = true;
                ICal = true;
                ZCal = true;
                break;
            case 68: case 67: // D & C
                myListA = true;
                ICal = true;
                break;
            case 66: case 65:   // B & A
                myListA = true;
                ZCal = true;
                break;
            case 57: case 56:   // 9 & 8
                myListA = true;
                break;
            case 55:case 54:    // 7 & 6
                ICal = true;
                ZCal = true;
                break;
            case 53:case 52:    // 5 & 4
                ICal = true;
                break;
            case 51:case 50:    // 3 & 2
                ZCal = true;
                break;
            default:
                break;
        }

        // build the boolean arrays
        calArray[0] = FCal;
        calArray[1] = ICal;
        calArray[2] = ZCal;

        listArray[0] = myListA;
        listArray[1] = myListB;
        listArray[2] = myListC;

        // add to the HashMap and return
        lensStatusMap.put("calibrated", calArray);
        lensStatusMap.put("myList", listArray);

        return lensStatusMap;
    }

    // save the data stored in lensArray to a text file (.lens)
    // TODO: add check to make sure user doesn't enter more than 255 lenses
    private void saveLensFile(String fileString, boolean saveAs) {
        Timber.d("Save lensArray to file, saveAs: " + saveAs);
        if (isExternalStorageWritable()) {
            Timber.d("Number of lenses in array: " + lensArray.size());
            File lensFile;

            if (saveAs) {           // if the customer wants to save as a new file, create new filename
                lensFile = new File(getExternalFilesDir(null), fileString);
            }
            else {                  // save w/ same name as before
                lensFile = new File(fileString);
            }

//            Timber.d("lensFile: " + lensFile.toString());
            try {
                FileOutputStream fos = new FileOutputStream(lensFile);
                for (String lens : lensArray) {
//                    Timber.d("current lens: " + lens);
                    String lensOut = SharedHelper.checkLensChars(lens);
                    try {
                        fos.write(lensOut.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fos.close();
                    Timber.d("Changes saved successfully.");
//                    Intent intent = new Intent(LensListDetailsActivity.this, AllLensListsActivity.class);
//                    startActivity(intent);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    // save the lens as a new file
    private void saveLensFileAs() {
        Timber.d("rename and save lens file");
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                final EditText input = new EditText(LensListDetailsActivity.this);
                input.setSelectAllOnFocus(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT);

                new AlertDialog.Builder(LensListDetailsActivity.this)
                        .setMessage("Enter a new file name for the lenses")
                        .setView(input)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                saveLensFile(input.getText().toString() + ".lens", true);
                            }
                        })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
//                        .setCancelable(false)
                    .show();
            }
        });
    }

    private void getListsForLens(final LensListEntity lensList, final LensEntity lens) {
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
                // TODO: remove lens ID from MyList if it's a part of that list
                deleteAssociations(lens);
//                deleteLens(lens);
//                deleteAssociations(listsToUpdate);
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

    private void deleteAssociations(final LensEntity lens) {
        Timber.d("deleting associations for lens ID " + lens.getId());

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                database.lensListLensJoinDao().deleteByLensId(lens.getId());
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<Void>() {
            @Override
            public void onSuccess(Void value) {
                Timber.d("Deleted associations successfully");
                updateLensListCount(lens, true);
//                lensArray.remove(lens.getTag());

//                lensObjectArray.remove(lens.getTag());
            }

            @Override
            public void onError(Throwable error) {
                Timber.d(error.getMessage());
                CharSequence text = "Error deleting lens - please try again";
                SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
            }
        });
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
                Timber.d("Deleted lens successfully. Tag: " + lens.getTag());
                updateLensListCount(lens, true);
//                lensArray.remove(lens.getTag());

//                lensObjectArray.remove(lens.getTag());
            }

            @Override
            public void onError(Throwable error) {
                Timber.d(error.getMessage());
                CharSequence text = "Error deleting lens - please try again";
                SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
            }
        });
    }

    private void updateLensListCount(final LensEntity lens, final boolean remove) {
        Timber.d("update lens list count for lens id = " + lensId);

        int numToUpdate = (remove ? listsToUpdate.size() : 1);
        int count = 0;
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
            count = currentLensList.getCount() + 1;
            currentLensList.setCount(count);
            lists[0] = currentLensList;
        }

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
                        updateLensList(true);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d(error.getMessage());
                        CharSequence text = "Error updating lens list count - please try again";
                        SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    private void addOrRemoveLensFromList(LensEntity lens, boolean remove) {
        String msg = remove ? "Removing" : "Adding";
        Timber.d(msg + " lens " + lens.getId());

        if (remove) {
            for (int i = 0; i < lensObjectArray.size(); i++) {
                LensEntity obj = lensObjectArray.get(i);

                if (obj.getId() == lens.getId()) {
                    lensesFromIntent.remove(obj);
//                    lensArray.remove(i);
//                    return;
                }
            }

            for (int j = 0; j < lensArray.size(); j++) {
                String dataStr = lensArray.get(j);

                if (lens.getDataString().equals(dataStr)) {
                    lensArray.remove(j);
                    return;
                }
            }
        }

        else {
            lensesFromIntent.add(lens);
//            lensObjectArray.add(lens);
//            lensArray.add(lens.getDataString());
        }
    }

    private void updateLensChecked(LensEntity lens) {
        Timber.d("Update lens checked status for id: " + lens.getTag());
        if (lens.getChecked()) {
            numLensesChecked++;
        }
        else {
            numLensesChecked--;
        }
        updateLensInDatabase(lens, false, false);
    }

    private void selectLensesInDatabase(final String manufacturer, String series, final boolean checked) {
        Timber.d("selecting lenses in database");

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

                // update the adapter if a checkbox was tapped from the Manufacturer or Series levels
                if (manufacturer != null) {
                    updateLensList(false);
                }

                // use a different updating method because the user tapped the checkbox for "Select All"
                else {
                    if (checked) {
                        numLensesChecked = value;
                    }
                    else {
                        numLensesChecked = 0;
                    }

                    showOrHideFab();
                    lensListFragmentAdapter.updateAdapterFromSelectAll(checked);
                }

                CharSequence toastText = (checked ? "Added " + value + " lenses to selection" : "Removed " + value + " lenses from selection");
                SharedHelper.makeToast(LensListDetailsActivity.this, toastText, Toast.LENGTH_SHORT);
            }

            @Override
            public void onError(Throwable error) {
                Timber.d("ERROR: " + error);
            }
        });
    }

    /** This method selects the lenses from the ArrayList and returns the appropriate ones given the manufacturer/series
     * parameters. This makes it easy to use the updateAll method in the LensDao
     * @param manufacturer
     * @param series
     * @param checked
     * @return
     */
    private LensEntity[] getLenses(String manufacturer, String series, boolean checked) {
        ArrayList<LensEntity> lenses = new ArrayList<>();

        for (LensEntity lens : lensObjectArray) {
            if (manufacturer != null) {
                if (lens.getManufacturer().equals(manufacturer)) {
                    if (series != null) {
                        if (lens.getSeries().equals(series)) {
                            if (lens.getChecked() != checked) {
                                lens.setChecked(checked);
                                lenses.add(lens);
                                if (checked) {
                                    numLensesChecked++;
                                } else {
                                    numLensesChecked--;
                                }
                            }
                        }
                    } else {
                        if (lens.getChecked() != checked) {
                            lens.setChecked(checked);
                            lenses.add(lens);
                            if (checked) {
                                numLensesChecked++;
                            } else {
                                numLensesChecked--;
                            }
                        }
                    }
                }
            }
            else {
                if (lens.getChecked() != checked) {
                    lens.setChecked(checked);
                    lenses.add(lens);
                    if (checked) {
                        numLensesChecked++;
                    } else {
                        numLensesChecked--;
                    }
                }
            }
        }

        return lenses.toArray(new LensEntity[lenses.size()]);
    }

    /**
     * OnClick handler for the "Send..." button that is shown after the user selects at least one lens.
     * It lets the user select the action to carry out with these selected lenses (HU3, existing list,
     * new list, or file) and gets the lenses ready for export to any of those.
     * @param view
     */
    public void getLensesShareAction(View view) {
        final ArrayList<LensEntity> selectedLenses = getSelectedLenses(lensObjectArray);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                final AlertDialog.Builder builder = new AlertDialog.Builder(LensListDetailsActivity.this);
                final LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_send_lenses.xml, which we'll inflate to the dialog
                View getActionView = inflater.inflate(R.layout.dialog_send_to_action_selection, null);

                // These are layouts that the user selects
                LinearLayout toHU3 = getActionView.findViewById(R.id.sendSelectedLensesToHU3);
                LinearLayout toExisting = getActionView.findViewById(R.id.sendSelectedLensesToExistingList);
                LinearLayout toNew = getActionView.findViewById(R.id.sendSelectedLensesToNewList);
                LinearLayout toFile = getActionView.findViewById(R.id.sendSelectedLensesToFile);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(getActionView)
                        .setCancelable(true);

                // create the alert dialog
                final AlertDialog alert = builder.create();

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
                            SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
                        }
                    }
                });

                toNew.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("send selected lenses to new list");
                        createNewListAndLenses(selectedLenses);
                        alert.dismiss();
                    }
                });

                toExisting.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("send selected lenses to existing list");
                        selectExistingLensLists(selectedLenses);
                        alert.dismiss();

                    }
                });

                toFile.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("export these lenses to file");
                        prepareLensesForExport(currentLensList.getName(), MODE_SHARE);
                        alert.dismiss();
                    }
                });

                alert.show();
            }
        });
    }

    private void createNewListAndLenses(final ArrayList<LensEntity> lenses) {
        CharSequence title = "Create new lens list";

        AlertDialog.Builder builder = new AlertDialog.Builder(LensListDetailsActivity.this);
        LayoutInflater inflater = getLayoutInflater();

        // the custom view is defined in dialog_rename_lens_list.xml, which we'll inflate to the dialog
        View newLensListView = inflater.inflate(R.layout.dialog_rename_lens_list, null);
        final EditText listNameEditText = newLensListView.findViewById(R.id.renameLensListNameEditText);
        final EditText listNoteEditText = newLensListView.findViewById(R.id.renameLensListNoteEditText);

        builder.setView(newLensListView)
                .setTitle(title)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        // get the list name and note entered by the user
                        String name = listNameEditText.getText().toString().trim();
                        String note = listNoteEditText.getText().toString().trim();

                        LensListEntity lensList = SharedHelper.buildLensList(null, name, note, lenses.size(), lenses);

                        // insert the lenses and list into the database
                        DatabaseHelper.insertLensesAndList(LensListDetailsActivity.this, lenses, lensList); //name, note, selectedLenses.size(), false);
//                        createLensListsObservable();
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

    private void selectExistingLensLists(final ArrayList<LensEntity> lenses) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(LensListDetailsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                View existingLensListsView = inflater.inflate(R.layout.dialog_add_to_existing_lens_list, null);
                ListView existingLensListsListView = existingLensListsView.findViewById(R.id.existingLensListsListView);
                allLensListsArrayAdapter = new AllLensListsArrayAdapter(LensListDetailsActivity.this, allLensLists);
                allLensListsListener = new AllLensListsArrayAdapter.ListSelectedListener() {
                    @Override
                    public void onListSelected(LensListEntity list, boolean selected) {
                        Timber.d(list.getName() + " selected: " + selected);
                        if (selected) {
                            selectedLensLists.add(list);
                        }
                        else {
                            selectedLensLists.remove(list);
                        }
                    }
                };

                allLensListsArrayAdapter.setListener(allLensListsListener);

                existingLensListsListView.setAdapter(allLensListsArrayAdapter);

                builder.setView(existingLensListsView)
                        .setPositiveButton("Add", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                insertLensListJoins(lenses, selectedLensLists);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {

                            }
                        });

                final AlertDialog alert = builder.create();

                alert.show();
            }
        });
    }

    /**
     * This method constructs and then inserts entries into the lens/lens list join table, basically
     * adding lenses to a list.
     * @param lenses the lenses to add
     * @param lists the lists to add the lenses to
     */
    private void insertLensListJoins(final ArrayList<LensEntity> lenses, final ArrayList<LensListEntity> lists) {
        final ArrayList<LensListLensJoinEntity> joins = new ArrayList<>();                                                                      // the joins list and will be used to update the database
        for (LensEntity lens : lenses) {                                                                                                        // iterate over the list of lenses
            for (LensListEntity list : lists) {                                                                                                 // iterate over the list of lists
                LensListLensJoinEntity join = new LensListLensJoinEntity(list.getId(), lens.getId());                                           // create a new join entity for the lens within the list
                list.increaseCount();                                                                                                           // increase the lens count for the list
                joins.add(join);                                                                                                                // add the join to the list that will update the db
            }
        }

        /* Update the database with the newly created lens/list join entities */
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                database.lensListLensJoinDao().insertAll(joins);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
//                        updateLensLists(lists);
//                        createLensListsObservable();
                        CharSequence toastText = "Lenses added successfully.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error adding lenses, please try again.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }
                });
    }
//    /**
//     * This method is called when the user presses the FloatingActionButton to send lenses to the HU3.
//     * It checks whether a Bluetooth Module is present, and if so, prepares the lenses to send by
//     * retrieving only those whose checked value == true. The user then must select whether to add
//     * the selected lenses to existing ones on the HU3, or replace all HU3 lenses with the
//     * selected ones.
//     */
//    private void sendLensesFromFab() {
//        // loop through all lenses and get just the selected ones
//        for (int i = 0; i < lensObjectArray.size(); i++) {
//            LensEntity lens = lensObjectArray.get(i);
//            if (lens.getChecked() && !(lensObjectArrayToSend.contains(lens))) {
//                lensObjectArrayToSend.add(lens);
//            }
//        }
//
//        // ask the user whether they want to add to or replace lenses on the HU3
//        confirmLensSend();
//    }


    /**
     * This method returns all the lenses that are selected
     * @param lenses
     * @return
     */
    private ArrayList<LensEntity> getSelectedLenses(ArrayList<LensEntity> lenses) {
        ArrayList<LensEntity> selectedLenses = new ArrayList<>();

        // loop through all lenses and get just the selected ones
        for (int i = 0; i < lenses.size(); i++) {
            LensEntity lens = lenses.get(i);
            if (lens.getChecked()) {
                selectedLenses.add(lens);
            }
        }

        return selectedLenses;
    }

    /**
     * This method gets the data strings from each lens in lensesToSend and adds them to the
     * lensDataStrings ArrayList. It then creates the text file and calls shareLensFile to
     * send it out via an Intent.
     * @param fileName
     * @param mode
     */
    private void prepareLensesForExport(String fileName, int mode) {
        ArrayList<LensEntity> lensesToExport = getSelectedLenses(lensObjectArray);

        ArrayList<String> dataStrings = new ArrayList<>();

        for (LensEntity lens : lensesToExport) {
            String dataStr = SharedHelper.buildLensDataString(currentLensList, lens);

            if (dataStr.length() > 100) {
                dataStrings.add(dataStr);
            }
        }

        numLenses = dataStrings.size();

        if (mode == MODE_HU3) {
            Timber.d("Send lenses to HU3");
            if (isConnected) {
                confirmLensSend(dataStrings);
            }

            else {
                CharSequence text = "No Bluetooth module detected. Please connect and try again";
                SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_LONG);
            }
        }

        if (mode == MODE_SHARE) {
            Timber.d("share the lenses - need to build file");
            File listToExport = createTextLensFile(fileName, dataStrings);
            shareLensFile(listToExport);
        }
    }

    /**
     * This method creates a text file of lens data strings which is used to export to a file or
     * send via an Intent.
     * @param fileName
     * @return lensFile, the file created
     */
    private File createTextLensFile(String fileName, ArrayList<String> dataStrings) {
        File lensFile = new File(getExternalFilesDir(null), fileName + ".lens");

        if (isExternalStorageWritable()) {
            Timber.d("Number of lenses in file: " + dataStrings.size());
            try {
                FileOutputStream fos = new FileOutputStream(lensFile);
                for (String lens : dataStrings) {
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
            Uri fileUri = FileProvider.getUriForFile(LensListDetailsActivity.this, "com.prestoncinema.app.fileprovider", file);
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
     * This method allows the user to decide whether they want to add the selected lenses to the HU3,
     * or replace all lenses on the HU3 with the selected ones. Once the user has made their selection,
     * it called sendSelectedLenses with a flag indicating whether to add to or replace lenses on
     * the HU3.
     */
    private void confirmLensSend(final ArrayList<String> lensDataStrings) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensListDetailsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens_list_list.xml, which we'll inflate to the dialog
                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
                final RadioButton addLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.addLensesRadioButton);
                final RadioButton replaceLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.replaceLensesRadioButton);

                // some pretty formatting for the title of the Dialog
                final int numLensesToSend = lensDataStrings.size();
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
                                // get which RadioButton was checked
                                boolean addLenses = addLensesRadioButton.isChecked();

                                // send the selected lenses, using the flag for adding/replacing
                                sendSelectedLenses(lensDataStrings, addLenses);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                lensObjectArrayToSend.clear();
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
     * This method prepares the lens DataStrings for transmission to the HU3 using the LensEntity
     * class method getDataString(LensListEntity list) which uses the list arg to determine whether
     * the lens should be a member of MyList A/B/C for this situation. Then it builds the bundle
     * and intent to send the lenses from AllLensListsActivity
     * @param addToExisting
     */
    private void sendSelectedLenses(ArrayList<String> lensDataStrings, boolean addToExisting) {
        // build the Bundle to send to AllLensListsActivity
        Bundle bundle = new Bundle();

        // the lenses to send
        bundle.putStringArrayList("lensArray", lensDataStrings);

        // boolean of whether to add or replace
        bundle.putBoolean("addToExisting", addToExisting);

        // start that shit
        Intent intent = new Intent(LensListDetailsActivity.this, AllLensListsActivity.class);
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

        lensArray.add(padded);
        int index = lensArray.size() - 1;

        LensEntity newLensObject = parseLensLine(padded, index, lensId, true);
        insertLensInDatabase(newLensObject);
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
                updateLensList(updateAdapter);

                if (showToast) {
                    CharSequence text = "Lens updated successfully";
                    SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
                }

            }

            @Override
            public void onError(Throwable error) {
                Timber.d("updateLensInDatabase onError: " + error.getMessage());
                CharSequence text = "Error updating lens - please try again";
                SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
            }
        });
    }

    private void insertLensInDatabase(final LensEntity lens) {
        Timber.d("inserting lens in database");

        Single.fromCallable(new Callable<Long>() {
            @Override
            public Long call() throws Exception {
                String dataString = LensHelper.removeMyListFromDataString(lens.getDataString());
                int countInDb = database.lensDao().lensExists(dataString);

                long lensId;

                // if countInDb == 0, the lens is not present in the database
                if (countInDb == 0) {
                    lensId = database.lensDao().insert(lens);                                               // insert the lens and return its id
                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(currentListId, lensId));
                }

                // record found in database, so don't do anything
                else {
                    LensEntity foundLens = database.lensDao().getLensByAttributes(lens.getManufacturer(), lens.getSeries(), lens.getFocalLength1(), lens.getFocalLength2(), lens.getSerial(), lens.getNote());
                    lensId = foundLens.getId();
                    Timber.d("duplicate lens detected, retrieving from DB (ID = " + lensId + ")");

                    // look to see if there's a join entry for this lens/list combo so we don't have duplicates
                    LensListLensJoinEntity existingJoin = database.lensListLensJoinDao().getByListAndLensId(currentListId, lensId);

                    // if the join was found, the lens the user is trying to create already is a member of this list, so don't do anything.
                    // if the query above resulted in null, we can create a new entry
                    if (existingJoin == null) {
                        LensListLensJoinEntity newJoin = new LensListLensJoinEntity(currentListId, lensId);
                        database.lensListLensJoinDao().insert(newJoin);
                    }
                }

                return lensId;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<Long>() {
                    @Override
                    public void onSuccess(Long id) {
                        if (id != null) {
                            updateLensListCount(lens, false);
                        }
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.d("insertLensInDatabase onError: " + error.getMessage());
                        CharSequence text = "Error inserting lens - please try again";
                        SharedHelper.makeToast(LensListDetailsActivity.this, text, Toast.LENGTH_SHORT);
                    }
                });
    }

    private void updateLensList(final boolean updateAdapter) {
        Timber.d("Updating lens list.");

        // get the new numLenses in case the user added/deleted a lens
        numLenses = lensArray.size();

        // clear lensObjectArray so there will be correct data when refreshing the LensListFragmentAdapter
        populateAllLenses();

        // clear the My List lists so that there will be new data when we notify the LensListFragmentAdapter
        populateMyLists();

        showOrHideFab();

//        lensListFragmentAdapter = new LensListFragmentAdapter(getSupportFragmentManager(), myListDataHeader, myListDataChild, lensListManufHeader, lensListTypeHeader, lensListDataHeaderCount, lensPositionMap, lensObjectArray, LensListDetailsActivity.this);
//        viewPager.setAdapter(lensListFragmentAdapter);

        // run the UI updates on the UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (lensListFragmentAdapter != null && updateAdapter) {
                    Timber.d("update lens list fragment adapter data");
                    lensListFragmentAdapter.notifyDataSetChanged();
                    viewPager.setCurrentItem(currentTab);
                }

                /* Update the title of the activity in case the number of lenses has changed */
                updateActivityTitle();

//                viewPager.setCurrentItem(currentTab);
            }
        });
    }

    private void updateActivityTitle() {
        String titleString, noteString;
        if (currentLensList != null) {
            titleString = currentLensList.getName() + " (" + numLenses + ")";
            noteString = currentLensList.getNote();
        }
        else {
            titleString = "";
            noteString = "";
        }
        setTitle(titleString);
//        if (noteTextView != null) {
//            noteTextView.setText(noteString);
//        }
    }

    private void countLensLine(String lens) {
        int sub_ind = 16;                                                                           // the tag to chop the lens strings (16 for manufacturer)
        int key = 0;

        String subLensString = lens.substring(sub_ind, sub_ind + 1).trim();
        switch(subLensString) {
            case "0":
                key = 0;
                break;
            case "1":
                key = 1;
                break;
            case "2":
                key = 2;
                break;
            case "3":
                key = 3;
                break;
            case "4":
                key = 4;
                break;
            case "5":
                key = 5;
                break;
            case "6":
                key = 6;
                break;
            case "F":
                key = 7;
                break;
            default:
                key = 0;
                break;
        }

        int currCount = lensListDataHeaderCount.get(key);
        lensListDataHeaderCount.put(key, currCount + 1);
    }

//    private String stripLensFileString() {
//        String[] fileStringArray = lensFileString.split("/");
//        String tempLensFileString = fileStringArray[fileStringArray.length - 1];
//        return tempLensFileString.substring(0, tempLensFileString.length() - 5);
//    }
//
//    public File getLensStorageDir(String lens) {
//        // Create the directory for the saved lens files
//        File file = new File(getExternalFilesDir(null), lens);
//        Timber.d("File: " + file);
//        return file;
//    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

//    /* Checks if external storage is available to at least read */
//    public boolean isExternalStorageReadable() {
//        String state = Environment.getExternalStorageState();
//        if (Environment.MEDIA_MOUNTED.equals(state) ||
//                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
//            return true;
//        }
//        return false;
//    }

//    /* Checks if any of the My List assignment states are toggled from the + button on the My List A/B/C header */
//    private boolean myListEnabled() {
//        return (myListExpAdapter.addToMyListA || myListExpAdapter.addToMyListB || myListExpAdapter.addToMyListC);
//    }

//    /* Adds or removes the selected lens from the selected list */
//    private boolean toggleMyList(String list, LensEntity lens, boolean currentStatus) {
//        editMyList(list, lens, !currentStatus);
//        return !currentStatus;
//    }
//
//    /* Adds a lens to specified "My List" */
//    private void editMyList(String list, LensEntity lens, boolean add) {
//        Timber.d("-- Edit -- List: " + list + ", Lens: " + lens.getTag() + ", add: " + add);
//
//        int lensInd = lens.getTag();
//        int lensId = 0;
//
//        String originalData = lensArray.get(lensInd);
//        Timber.d("original String: " + originalData);
//
//        char[] data = originalData.toCharArray();
//
//        char status0H = data[15];                                                                   // Status byte 0 for the lens (Cal and MyList status)
//        char status0L = data[16];
//
//        int statByte0H = Integer.parseInt(String.valueOf(status0H), 16);                                      // convert to int
//        int statByte0L = Integer.parseInt(String.valueOf(status0L), 16);                                      // convert to int
//
//        // update the status bytes according to the my list assignments
//        switch(list) {
//            case "A":
//                if (add) statByte0L += 0x8;                                                         // add 0x8 to add to My List A
//                else statByte0L -= 0x8;                                                             // subtract 0x8 to remove from My List A
//                break;
//            case "B":
//                if (add) statByte0H += 0x1;                                                         // add 0x1 to add to My List B
//                else statByte0H -= 0x1;                                                             // subtract 0x1 to remove from My List A
//                break;
//            case "C":
//                if (add) statByte0H += 0x2;                                                         // add 0x2 to add to My List C
//                else statByte0H -= 0x2;                                                             // subtract 0x2 to remove from My List A
//                break;
//        }
//
//        if (statByte0H == 10) {                      // keep everything in Hex
//            statByte0H = 0xA;
//        }
//
//        if (statByte0H == 11) {                      // keep everything in Hex
//            statByte0H = 0xB;
//        }
//
//        // convert to the hex characters given the new My List assignment
//        String newStatus0H = Integer.toHexString(statByte0H).toUpperCase();
//        String newStatus0L = Integer.toHexString(statByte0L).toUpperCase();
//
//        // set the individual status byte characters (2 ASCII bytes)
//        data[15] = newStatus0H.charAt(0);
//        data[16] = newStatus0L.charAt(0);
//
//        // the updated string
//        String updatedData = String.valueOf(data);
//        Timber.d("updated String:  " + updatedData);
//
//        // update the variables used in the ExpandableListView adapter
//        // TODO: get rid of lensArray and make everything go off lensObjectArray using getDataString() field for each lens string
//        lensArray.set(lensInd, updatedData);
//
//        LensEntity updatedLens = parseLensLine(updatedData, lensInd, lensId, false);
//
//        lensObjectArray.set(lensInd, updatedLens);
//
//        String listName = "My List " + list;
//        temporaryLensList = myListDataChild.get(listName);
//        Timber.d("myListLenses before adding: " + temporaryLensList.toString());
//
//        if (add) {
//            temporaryLensList.add(updatedLens);
//        }
//        else {
//            temporaryLensList.remove(lens);
//        }
//
////        Timber.d("myListALenses after adding: " + temporaryLensList.toString());
//        myListDataChild.put(listName, temporaryLensList);
//
//        // TODO: Toggle icons for My List edit when you click directly on the edit image for another list
//        // update the adapter to refresh the UI
//        updateLensList(true);
//    }
}

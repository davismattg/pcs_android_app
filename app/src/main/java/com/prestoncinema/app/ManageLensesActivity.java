package com.prestoncinema.app;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TabLayout;
import android.support.v4.app.NavUtils;
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
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.Toast;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.model.Lens;
import com.prestoncinema.app.ui.MyListFragment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.media.CamcorderProfile.get;
import static android.util.Log.d;


import timber.log.Timber;

/**
 * Created by MATT on 3/9/2017.
 * This activity is used to edit existing lens files. You can add/remove lenses, add/remove lenses to My List A/B/C
 * TODO: Give user ability to rename a lens
 * TODO: Restrict input length on focal length (9999mm max) and serial/note length (14 bytes including focal length)
 */

public class ManageLensesActivity extends UartInterfaceActivity implements AdapterView.OnItemSelectedListener,
        MyListFragment.OnLensChangedListener, AllLensesFragment.OnLensAddedListener,
        AllLensesFragment.OnChildLensChangedListener, AllLensesFragment.OnLensSelectedListener {

    // Log
    private final static String TAG = LensActivity.class.getSimpleName();

    // UI
    private ProgressDialog mProgressDialog;
    private LensListParentExpListViewAdapter expAdapter;
    private MyListExpListViewAdapter myListExpAdapter;
    private ExpandableListView myListExpListView;
    private ExpandableListView expListView;
    private TabLayout listTabs;
    private ViewPager viewPager;
    private FloatingActionButton fab;

    private int numLensesChecked = 0;

    private List<String> lensListDataHeader = new ArrayList<>(Arrays.asList("Angenieux", "Canon", "Cooke", "Fujinon", "Leica", "Panavision", "Zeiss", "Other"));
    private Map<Integer, Integer> lensListDataHeaderCount = new HashMap<Integer, Integer>(lensListDataHeader.size());

    private List<String> myListDataHeader;
    private HashMap<String, List<Lens>> myListDataChild;
    private List<Lens> temporaryLensList = new ArrayList<>();

    private ArrayList<Lens> lensObjectArray = new ArrayList<>();
    private ArrayList<Lens> lensObjectArrayToSend = new ArrayList<>();

    private List<String> lensListManufHeader = new ArrayList<String>();
    private HashMap<String, List<String>> lensListTypeHeader = new HashMap<>();

    private ImageView mAddLensImageView;

    private ArrayList<String> lensArray = new ArrayList<String>();
    private HashMap<Integer, HashMap<String, Object>> lensMap = new HashMap<Integer, HashMap<String, Object>>();
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap = new HashMap<Integer, HashMap<Integer, ArrayList<Integer>>>();
    private int numLenses = 0;
    private String lensFileString = "";
    private String lensFileStringStripped = "";

    private File lensFile;
    private boolean isPrime = false;

    private int currentTab;
    private boolean myListEditEnabled = false;

    private int ang_byte = 0x0;
    private int can_byte = 0x1;
    private int cooke_byte = 0x2;
    private int fuj_byte = 0x3;
    private int lei_byte = 0x4;
    private int pan_byte = 0x5;
    private int zei_byte = 0x6;
    private int oth_byte = 0xF;
    private int maxSerialLength = 14;

    private int lensId;                                                 // used to identify

    private byte[] STX = {02};
    private byte[] ETX = {0x0A, 0x0D};
    private String STXStr = new String(STX);
    private String ETXStr = new String(ETX);

    private AppDatabase database;

    private LensListFragmentAdapter lensListFragmentAdapter;

    public ManageLensesActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(@Nullable final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_lenses);

        Timber.d("onCreate called =====");

        /* Initialize the database used (w/ Room) to store the lenses and include migrations */
//        database = Room.databaseBuilder(getApplicationContext(), AppDatabase.class, "lens-database")
//                                        .addMigrations(MIGRATION_1_2)
//                                        .addMigrations(MIGRATION_2_3)
//                                        .build();

        // UI initialization
        mAddLensImageView = findViewById(R.id.lensTypeAddImageView);
        fab = findViewById(R.id.LensListFab);

        for (int i = 0; i < lensListDataHeader.size(); i++) {
            lensListDataHeaderCount.put(i, 0);
            lensPositionMap.put(i, new HashMap<Integer, ArrayList<Integer>>());
        }

        lensListManufHeader = Arrays.asList(getResources().getStringArray(R.array.lens_manuf_array));                                                   // use the lens manuf string array resource to populate the headers of the lens list view
        lensListTypeHeader = populateLensTypeHeader(lensListManufHeader);                                                                               // using the header, populate the children (lens series) header

        /* Initialize the My List HashMap used to populate the My List ExpandableListView by adding empty List<Lens> for each list */
        myListDataChild = new HashMap<String, List<Lens>>();
        myListDataChild.put("My List A", new ArrayList<Lens>());
        myListDataChild.put("My List B", new ArrayList<Lens>());
        myListDataChild.put("My List C", new ArrayList<Lens>());

        /* Get the filename string from the previous activity (LensActivity) and import the file */
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lensFileString = extras.getString("lensFile");

            lensFileStringStripped = stripLensFileString();

            /* IMPORT THE LENS FILE INTO lensArray and lensObjectArray */
//            lensFile = new File(lensFileString);
//            importLensFile(lensFile);
            importLensFile(lensFileString);

            /* Set the activity title in the top bar */
            updateActivityTitle();
        }

        if (savedInstanceState != null) {
            Timber.d("restored with savedInstanceState");

            lensFileString = savedInstanceState.getString("lensFileString");
            lensFileStringStripped = savedInstanceState.getString("lensFileStringStripped");

            lensFile = new File(lensFileString);
            importLensFile(lensFile);

            updateActivityTitle();
        }

        /* Initialize the data header for the My List ListView */
        myListDataHeader = Arrays.asList(getResources().getStringArray(R.array.my_list_array));                                                         // use the my list string array resource to populate the header of the my list list view

        /* Initialize the FloatingActionButton used to send the lenses to HU3 My Lists */
        fab.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Timber.d("FAB clicked, export lenses to HU3");

                sendLensesFromFab();
//                int tabToEdit = 0;
//                if (myListEditEnabled) {                    // done editing
//                    myListEditEnabled = false;
//                }
//                else {                                      // enable editing
//                    myListEditEnabled = true;
//                    tabToEdit = currentTab;
//                }
//                respondToFab(myListEditEnabled, tabToEdit);
            }
        });

        /* Initialize the tabs that are used to toggle between My List and All Lenses ExpandableListViews */
        viewPager = findViewById(R.id.LensFileTabViewPager);
        lensListFragmentAdapter = new LensListFragmentAdapter(getSupportFragmentManager(), myListDataHeader, myListDataChild, lensListManufHeader, lensListTypeHeader, lensListDataHeaderCount, lensPositionMap, lensObjectArray, ManageLensesActivity.this);
        viewPager.setAdapter(lensListFragmentAdapter);
        viewPager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {
                currentTab = position;

//                switch (position) {
//                    case 3:
//                        if (!myListEditEnabled) {
//                            fab.hide();
//                        }
//                        break;
//                    default:
//                        fab.show();
//                        break;
//                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        listTabs = (TabLayout) findViewById(R.id.LensFileTabLayout);
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
     * @param focal
     * @param serial
     * @param note
     * @param listA
     * @param listB
     * @param listC
     */
    public void onLensChanged(Lens lens, String focal, String serial, String note, boolean listA, boolean listB, boolean listC) {
        editLens(lens, focal, serial, note, listA, listB, listC);
    }

    /** onLensDeleted handles when the user deletes a lens from the popup within one of the "My List" tabs
     * @param lens
     */
    public void onLensDeleted(Lens lens) {
        deleteLens(lens.getTag());
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
     * @param focal
     * @param serial
     * @param note
     * @param listA
     * @param listB
     * @param listC
     */
    public void onChildLensChanged(Lens lens, String focal, String serial, String note, boolean listA, boolean listB, boolean listC) {
        editLens(lens, focal, serial, note, listA, listB, listC);
    }

    /** onChildLensDeleted handles deleting a lens when the user selects "Delete" from the Edit Lens dialog
     *
     * @param lens
     */
    public void onChildLensDeleted(Lens lens) {
        deleteLens(lens.getTag());
    }

    /** onLensesSelected handles sending/receiving only selected lenses from the list
     *
     * @param lens
     */
    public void onLensSelected(Lens lens) {
        Timber.d("selected lens: " + lens.getTag() + ", checked: " + lens.getChecked());
        showOrHideFab(lens.getChecked());
        updateLensChecked(lens);
    }

    private void showOrHideFab(boolean checked) {
        if (checked) {
            numLensesChecked += 1;
        }
        else {
            numLensesChecked -= 1;
        }

        if (numLensesChecked < 0) {
            numLensesChecked = 0;
        }

        if (numLensesChecked > 0) {
            fab.setVisibility(View.VISIBLE);
        }
        else {
            fab.setVisibility(View.INVISIBLE);
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
            case R.id.home:
                NavUtils.navigateUpFromSameTask(this);
                return true;
            case R.id.importLensMenuItem:
                Timber.d("import lenses");
                toast.show();
                break;
            case R.id.exportLensMenuItem:
                Timber.d("export lenses");

                /* Get the Lens List Fragment and call the method enableSelection() */
//                AllLensesFragment allLensesFragment = (AllLensesFragment) getSupportFragmentManager().findFragmentById(R.tag.AllLensesFragment);
//                AllLensesFragment allLensesFragment = (AllLensesFragment) lensListFragmentAdapter.getItem(3);
                AllLensesFragment allLensesFragment = (AllLensesFragment) lensListFragmentAdapter.instantiateItem(null, 3);
                Timber.d("allLensesFragment: " + allLensesFragment);

                if (allLensesFragment != null) {
                    Timber.d("found lens list fragment. Enable checkboxes");
                    allLensesFragment.enableLensSelection();
                }


//                toast.show();
                break;
            case R.id.deleteLensMenuItem:
                Timber.d("delete lenses");
                toast.show();
                break;
            case R.id.renameLensFileMenuItem:
                renameLensFile(lensFile, lensFileStringStripped);
                Timber.d("rename the lens file");
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
    private void renameLensFile(final File file, String currentName) {
        final String oldName = currentName.split(".lens")[0];

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(ManageLensesActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens, null);
                final EditText fileNameEditText = (EditText) renameLensView.findViewById(R.id.renameLensEditText);

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
                        String newName = fileNameEditText.getText().toString().trim().replace(".lens", "") + ".lens";           // do some housekeeping on the user-entered string

                        // TODO: make sure the filename check is working robustly
                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {
                            Timber.d("\n\nOriginal file: " + file.toString());
                            Timber.d("Save the file as: " + newName);
                            Timber.d("getLensStorageDir: " + getLensStorageDir(newName).toString() + "\n\n");

                            // rename the file
                            boolean wasFileRenamed = file.renameTo(getLensStorageDir(newName));                                     // rename the old file
                            if (wasFileRenamed) {                                                                                       // file.renameTo() returned true
                                setTitle(newName);                                                                                  // update the title of the activity w/ new file name
                                lensFileStringStripped = newName.split(".lens")[0];
                                lensFile = getLensStorageDir(newName);
                                Timber.d("lensFile after rename: " + lensFile.toString());
                                alert.dismiss();
                            }
                            else {
                                Context context = getApplicationContext();
                                CharSequence toastText = "Error renaming lens file. Please try again.";
                                int duration = Toast.LENGTH_LONG;

                                // make a toast letting the user know that there was an error renaming the file
                                Toast toast = Toast.makeText(context, toastText, duration);
                                toast.show();
                            }
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

//                LayoutInflater dialogInflater = (LayoutInflater) getApplicationContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
//                final View renameLensView = dialogInflater.inflate(R.layout.dialog_rename_lens, null);
//                final EditText fileNameEditText = (EditText) renameLensView.findViewById(R.tag.renameLensEditText);
//
//                fileNameEditText.setText(oldName);
//                fileNameEditText.setSelection(oldName.length());
//
//                new AlertDialog.Builder(ManageLensesActivity.this)
//                        .setTitle("Enter new filename")
//                        .setView(renameLensView)
//                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                String newFileName = fileNameEditText.getText().toString().trim().replace(".lens", "") + ".lens";           // do some housekeeping on the user-entered string
//                                boolean wasFileRenamed = file.renameTo(getLensStorageDir(newFileName));                                     // rename the old file
//                                if (wasFileRenamed) {                                                                                       // file.renameTo() returned true
//                                    setTitle(newFileName);                                                                                  // update the title of the activity w/ new file name
//                                }
//                                else {      // file rename returned false, make a toast letting the user know
//                                    Context context = getApplicationContext();
//                                    CharSequence toastText = "Error renaming lens file. Please try again.";
//                                    int duration = Toast.LENGTH_LONG;
//
//                                    // make a toast letting the user know that this feature is coming soon.
//                                    Toast toast = Toast.makeText(context, toastText, duration);
//                                    toast.show();
//                                }
//
//                            }
//                        })
//                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                            }
//                        })
//                        .setCancelable(false)
//                        .show();
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

    public void onNothingSelected(AdapterView<?> parent) {
        // Another interface callback
    }

    // function to populate the lens type HashMap with each lens type name, based on manufacturer name
    private HashMap<String, List<String>> populateLensTypeHeader(List<String> listDataHeader) {
        HashMap<String, List<String>> lensTypes = new HashMap<>();              // the return value
        for (String manufName : listDataHeader) {                                                   // loop through array of manuf names
            final int arrayId;                                            // the ID of the string-array resource containing the lens names

            switch (manufName) {
                case "Angenieux":
                    arrayId = R.array.lens_type_Angenieux;
                    break;
                case "Canon":
                    arrayId = R.array.lens_type_Canon;
                    break;
                case "Cooke":
                    arrayId = R.array.lens_type_Cooke;
                    break;
                case "Fujinon":
                    arrayId = R.array.lens_type_Fujinon;
                    break;
                case "Leica":
                    arrayId = R.array.lens_type_Leica;
                    break;
                case "Panavision":
                    arrayId = R.array.lens_type_Panavision;
                    break;
                case "Zeiss":
                    arrayId = R.array.lens_type_Zeiss;
                    break;
                case "Other":
                    arrayId = R.array.lens_type_Other;
                    break;
                default:
                    arrayId = R.array.lens_type_Empty;
                    break;
            }

            lensTypes.put(manufName, Arrays.asList(getResources().getStringArray(arrayId)));
        }

        return lensTypes;
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
    private void confirmLensDelete(AdapterView.AdapterContextMenuInfo lens) {
        final int id = (int) lens.id;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                new AlertDialog.Builder(ManageLensesActivity.this)
                    .setMessage("Are you sure you want to delete this lens?\n\nThis will not remove it from the HU3 until you export this lens file to HU3.")
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            deleteLens(id);         // delete the lens from the lens array
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    })
                    .setCancelable(false)
                    .show();
        }
        });
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // BIG function. This function reads the text file specified in lensFile and parses it into an //
    // array (lensArray). It also calls the function parseLensLine, which takes the lens text file //
    // raw data and formats it to display as an item in the lens list. Anytime the user changes    //
    // something w/ a lens (add to my list, remove a lens, etc, you have to make sure to update    //
    // BOTH lensArray and lensMap. lensArray is the basis for saving the modified file as new one  //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    private void importLensFile(File lensFile) {
        Timber.d("Customer selected lens file: " + lensFile.toString());
        BufferedReader reader = null;
        lensArray.clear();                                                                          // clear the lens array since we'll be populating it with the file contens

        try {
            FileInputStream lensIn = new FileInputStream(lensFile);                                 // open a FileInputStream for the selected file
            reader = new BufferedReader(
                    new InputStreamReader(lensIn));                                                 // read the file
            String line;                                                                            // read the file one line at a time
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0) {
                    lensArray.add(SharedHelper.checkLensChars(line));                                                            // add the read lens into the array
                }
            }
            if (lensArray.size() > 0) {                                                             // make sure something was actually imported
                numLenses = lensArray.size();                                                       // the number of lenses, used for loops and display on the UI

                lensObjectArray = new ArrayList<>(numLenses);
                populateAllLenses();
                populateMyLists();
            }
        } catch (Exception ex) {
            Timber.d("importLensFile()", ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();                                             // close the file reader
                }   catch (Exception e) {
                    Timber.d("reader exception", e);
                }
            }
        }
    }

    private void importLensFile(String name) {
        Timber.d("importing lens file with name: " + name);


    }

    private void populateAllLenses() {
        Timber.d("populating lenses (" + numLenses + ")");
        lensObjectArray.clear();
        initializeLensListHeaderCount();
        for (int i=0; i < numLenses; i++) {
            String len = lensArray.get(i);
            countLensLine(len);
            Lens thisLens = parseLensLine(len, i, true);
            lensObjectArray.add(i, thisLens);
//            new DatabaseTask().execute(thisLens);
        }

        sortLensObjectArray();

        Timber.d("lensObjectArray populated and sorted");
    }

    private void populateMyLists() {
        myListDataChild.get("My List A").clear();
        myListDataChild.get("My List B").clear();
        myListDataChild.get("My List C").clear();

        for (Lens thisLens : lensObjectArray) {
            if (thisLens.getMyListA()) {
                temporaryLensList = myListDataChild.get("My List A");
                temporaryLensList.add(thisLens);
                myListDataChild.put("My List A", temporaryLensList);
            }

            if (thisLens.getMyListB()) {
                temporaryLensList = myListDataChild.get("My List B");
                temporaryLensList.add(thisLens);
                myListDataChild.put("My List B", temporaryLensList);
            }

            if (thisLens.getMyListC()) {
                List<Lens> myListCLenses = myListDataChild.get("My List C");
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

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    //                             Custom function to sort the lens Array                              //
    // this function looks at each lens data string after truncating the part before it. For example,  //
    // when sorting by manufacturer, the manufacturer ID starts at byte 17 of the overall lens data.   //
    // so this function gets each string, chops it at the specified tag, and then sorts the chopped  //
    // strings. Then it searches the original array for the chopped string to determine the mapping    //
    // from old order to new, sorted order. then it rearranges the original array with the new         //
    // indices.                                                                                        //
    //  TODO: Finish sorting logic                                                                     //
    /////////////////////////////////////////////////////////////////////////////////////////////////////
    private void sortLensObjectArray() {
//        Timber.d("unsorted: " + lensObjectArray.toString());
//        ArrayList<Lens> unsorted = lensObjectArray;

//        Collections.sort(lensObjectArray);

//        for (int i = 0; i < lensObjectArray.size(); i++) {
//            Timber.d("Index: " + i + ". Old ID: " + unsorted.get(i).getTag() + ". New ID: " + lensObjectArray.get(i).getTag());
//        }

//        Timber.d("sorted: ");
//        Timber.d(lensObjectArray.toString());
    }
//    private void sortLensArray(ArrayList<String> arr, String param, String dir) {
//        Timber.d("sorting lens array --------------------------------------------");
//
//        ArrayList<String> sub_arr = new ArrayList<String>(arr.size());                       // initialize the ArrayList that will store the truncated strings
//        ArrayList<String> new_arr = new ArrayList<String>(arr.size());                  // initialize the ArrayList that will store the rearranged array
//        int sub_ind = 0;                                                                // the tag to chop the lens strings. depends on param
//        switch(param) {
//            case "manufacturer":
//                sub_ind = 17;               // lens manuf bytes start at tag 17
//                break;
//            case "fLength":
//                sub_ind = 19;               // focal length starts at tag 19
//                break;
//        }
//
//        for (int i=0; i < arr.size(); i++) {
//            sub_arr.add(arr.get(i).substring(sub_ind));
//        }
//
//        Collections.sort(sub_arr);
//
//        for (int j=0; j < sub_arr.size(); j++) {
//            for (String str : arr) {
//                if (str.contains(sub_arr.get(j))) {
//                    new_arr.add(j, str);
//                }
//            }
//        }
//
//        lensArray.clear();
//        lensArray = new_arr;
//    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // This function takes in the raw string from the lens file and formats it in the way we want  //
    // to display it in the UI. Check the HU3 document/ask Mirko for the data structure            //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    private Lens parseLensLine(String line, int index, boolean isNewLens) {
        /* Initialize the Lens object that will store all the info about this lens */
        LensEntity lensObject = new LensEntity(); //(index, "","", "", 0, 0,
//                0, 0, false, "", "", false,
//                false, false, false, false, false, false, null);

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
//                    Intent intent = new Intent(ManageLensesActivity.this, LensActivity.class);
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
                final EditText input = new EditText(ManageLensesActivity.this);
                input.setSelectAllOnFocus(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT);

                new AlertDialog.Builder(ManageLensesActivity.this)
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

    private void deleteLens(int id) {
        Timber.d("delete lens ID: " + id);
        lensArray.remove(id);
        lensObjectArray.remove(id);
        updateLensList();
    }

    private void updateLensChecked(Lens lens) {
        Timber.d("Update lens checked status for tag: " + lens.getTag());
        lensObjectArray.set(lens.getTag(), lens);
    }

    private void sendLensesFromFab() {
        for (int i=0; i < lensObjectArray.size(); i++) {
            Lens lens = lensObjectArray.get(i);
            Timber.d("Index: " + i + ", ID: " + lens.getTag() + ", Checked: " + lens.getChecked());
            if (lens.getChecked() && !(lensObjectArrayToSend.contains(lens))) {
                lensObjectArrayToSend.add(lens);
                Timber.d("adding lens " + lens.getTag());
            }
        }

        Timber.d("ready to send " + lensObjectArrayToSend.size() + " lenses");

        confirmLensSend();
    }

    private void confirmLensSend() {
        Timber.d("confirmLensSend");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(ManageLensesActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
                final RadioButton addLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.addLensesRadioButton);
                final RadioButton replaceLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.replaceLensesRadioButton);

                final int numLensesToSend = lensObjectArrayToSend.size();
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
//                                boolean replaceLenses = replaceLensesRadioButton.isChecked();

                                sendSelectedLenses(addLenses);
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

    private void sendSelectedLenses(boolean addToExisting) {
        ArrayList<String> dataStringsToSend = new ArrayList<String>(lensObjectArrayToSend.size());

        for (Lens lens : lensObjectArrayToSend) {
            dataStringsToSend.add(lens.getDataString());
        }

        Bundle bundle = new Bundle();
        bundle.putStringArrayList("lensArray", dataStringsToSend);
        bundle.putBoolean("addToExisting", addToExisting);

        Intent intent = new Intent(ManageLensesActivity.this, LensActivity.class);
        intent.putExtra("lensTransferInfo", bundle);
        startActivity(intent);
    }

    // function called when the user enters a new lens through the alert dialog and presses "save"
    private boolean saveNewLens(String manufName, String lensType, int focal1, int focal2, String serial, String note) {
        Timber.d("save the lens");

        Timber.d("Save new lens. Info:\n" + "Manuf: " + manufName + ", type: " + lensType + "\nFocal: 1) " + String.valueOf(focal1) + ", 2) " + String.valueOf(focal2) + "\nSerial: " + serial + "\nNote: " + note);

        buildLensData(manufName, lensType, focal1, focal2, serial, note, false, false, false);
        return true;
    }

    // function to do the heavy lifting of creating the hex characters from the user's selections
    private void buildLensData(String manuf, String lensType, int focal1, int focal2, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        int width = 110;
        char fill = '0';
        int manufByte = 0x0;
        int typeByte = 0x0;
        int statByte0 = 0x8;
        int statByte1 = 0x0;
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

        Lens newLensObject = parseLensLine(padded, index, true);
        lensObjectArray.add(newLensObject);

        updateLensList();
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // function to edit an existing lens after user changes the serial or mylist assignment in the edit dialog
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
//    private boolean editLens(int lensInd, int childPosition, String manufTitle, String typeTitle, String focal1, String focal2, String serial, boolean myListA, boolean myListB, boolean myListC) {
    private void editLens(Lens lensObject, String focalLen, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        Timber.d("///////////////////////////////////////////////////////////////");
        Timber.d("editLens - params: ");
        Timber.d("focal: " + focalLen);
        Timber.d("serial: " + serial);
        Timber.d("note: " + note);
        Timber.d("myListA: " + myListA);
        Timber.d("myListB: " + myListB);
        Timber.d("myListC: " + myListC);
        Timber.d("///////////////////////////////////////////////////////////////");

        int lensInd = lensObject.getTag();                                                           // the tag of the lens in the overall array
        String prevLensString = lensArray.get(lensInd);                                             // the original string that needs to be updated

        Timber.d("previous lens string: " + prevLensString);

        // TODO: make sure this tag is correct
        String toKeep = prevLensString.substring(30);                                               // substring of the stuff we don't care about updating, starting after the serial number

        String nameSubString = prevLensString.substring(0, 18);                                     // get the first 19 characters of the lens data. This includes STX, 14 chars for name, and 2 chars for status
        String status0H = nameSubString.substring(14, 15);                                           // Status byte 0 for the lens (Cal and MyList status)
        String status0L = nameSubString.substring(15, 16);
        String status1H = nameSubString.substring(16, 17);                                           // Status byte 1 for the lens (Manuf and Series)
        String status1L = nameSubString.substring(17, 18);
        String focalString = prevLensString.substring(18, 26);                                      // 8 characters, 4 for each focal length
//        String serialString = buildSerial(prevLensString.substring(27, 31));                        // 4 characters for the serial
        String serialString = buildSerial(serial);

        int statByte0H = Integer.parseInt(status0H, 16);                                      // convert to int
        int statByte0L = Integer.parseInt(status0L, 16);                                      // convert to int
        int statByte1H = Integer.parseInt(status1H, 16);                                      // convert to int
        int statByte1L = Integer.parseInt(status1L, 16);                                      // convert to int

        String lensName;
        String lensStatus1;

        boolean isMyListC = isBitSet(statByte0H, 0x2);                                     // bitwise check of (previous) status byte for My List C
        boolean isMyListB = isBitSet(statByte0H, 0x1);                                     // bitwise check of (previous) status byte for My List B
        boolean isMyListA = isBitSet(statByte0L, 0x8);                                     // bitwise check of (previous) status byte for My List A

        lensName = buildLensName(focalLen, serial, note);                                           // concat the lens focal lengths, serial and note together. Always 14 chars long

        Timber.d("new lens name: " + lensName + "$$");

        // update the status bytes according to the my list assignments
        // myListA
        if (myListA != isMyListA) {                 // setting changed by user, so update
            if (isMyListA) {                        // was in my list A, but user removed it
               statByte0L -= 0x8;                    // subtract 0x8 to remove from myList A
            }
            else {
                statByte0L += 0x8;                   // add 0x8 to add to myList A
            }
        }

        // myListB
        if (myListB != isMyListB) {                 // old/new settings don't match, so update
            if (isMyListB) {                        // if lens was in myList B
                statByte0H -= 0x1;                   // remove it
            }
            else {
                statByte0H += 0x1;                   // add 0x1 to add to myList B
            }
        }

        // myListC
        if (myListC != isMyListC) {                 // old/new settings don't match, so update
            if (isMyListC) {                        // lens used to be in myList C
                statByte0H -= 0x2;                   // remove it
            }
            else {
                statByte0H += 0x2;                   // add 0x2 to add to myList C
            }
        }

        if (statByte0H == 10) {                      // keep everything in Hex
            statByte0H = 0xA;
        }

        if (statByte0H == 11) {                      // keep everything in Hex
            statByte0H = 0xB;
        }

        String newLensName = lensName;

        // convert to the hex characters that will be written in the file. these strings all need to
        // be constant length no matter how many characters are inside, so you have to pad with 0's if necessary
        String newStatus0H = Integer.toHexString(statByte0H).toUpperCase();
        String newStatus0L = Integer.toHexString(statByte0L).toUpperCase();
        String newStatus1H = Integer.toHexString(statByte1H).toUpperCase();
        String newStatus1L = Integer.toHexString(statByte1L).toUpperCase();

//        String newStatus2 = String.format("%2s", Integer.toHexString(statByte1H + statByte1L).toUpperCase().replaceAll(" ", "0"));
//        Timber.d("newStatus1:" + newStatus1 + "$$");
//        Timber.d("newStatus2:" + newStatus2 + "$$");

//        lensStatus1 = String.format("%4s", (newStatus1 + newStatus2).replaceAll(" ", "0"));
        lensStatus1 = newStatus0H + newStatus0L + newStatus1H + newStatus1L;

        Timber.d("lensStatus1: " + lensStatus1 + "$$");
        String newString = newLensName + lensStatus1 + focalString + serialString + toKeep;

        Timber.d("lensArray prev: " + lensArray.get(lensInd));
        lensArray.set(lensInd, newString);
        Timber.d("lensArray post: " + lensArray.get(lensInd));

        Lens newLensObject = parseLensLine(newString, lensInd, false);

        Timber.d(String.valueOf(newLensObject.getManufacturerPosition()));
        Timber.d(String.valueOf(newLensObject.getSeriesPosition()));

//        lensObjectArray.remove(lensInd);
        lensObjectArray.set(lensInd, newLensObject);

        updateLensList();

//        return true;
    }

    public boolean isBitSet(int val, int bitNumber) {
        return (val & bitNumber) == bitNumber;
    }

    // This method accepts Strings of the Focal length (including "mm"), serial and note, and returns the formatted string padded with spaces to fill 14 chars
    private String buildLensName(String focal, String serial, String note) {
        return String.format("%-14s", focal + " " + serial + note);
    }

    // This function accepts the (decimal) string serial number and returns a 4-character long hex string
    private String buildSerial(String serial) {
        if (serial.length() == 0) {
            return "0000";
        }
        else {
            return String.format("%4s", Integer.toHexString(Integer.parseInt(serial)).toUpperCase()).replaceAll(" ", "0");
        }
    }

    private void updateLensList() {
        Timber.d("Updating lens list.");

        // get the new numLenses in case the user added a lens
        numLenses = lensArray.size();

        // save the lens file right away
        saveLensFile(lensFileString, false);

        // clear lensObjectArray so there will be correct data when refreshing the LensListFragmentAdapter
        populateAllLenses();

        // clear the My List lists so that there will be new data when we notify the LensListFragmentAdapter
        populateMyLists();

        // run the UI updates on the UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Context context = getApplicationContext();

                if (lensListFragmentAdapter != null) {
                    Timber.d("update lens list fragment adapter data");
                    lensListFragmentAdapter.notifyDataSetChanged();
                }

                /* Update the title of the activity in case the number of lenses has changed */
                updateActivityTitle();

                // make a toast letting the user know that their changes were successful
                CharSequence toastText = "Changes saved.";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(context, toastText, duration);
                toast.show();
            }
        });
    }

    private void updateActivityTitle() {
        String titleString = lensFileStringStripped + " (" + numLenses + ")";
        setTitle(titleString);
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

    private String stripLensFileString() {
        String[] fileStringArray = lensFileString.split("/");
        String tempLensFileString = fileStringArray[fileStringArray.length - 1];
        return tempLensFileString.substring(0, tempLensFileString.length() - 5);
    }

    public File getLensStorageDir(String lens) {
        // Create the directory for the saved lens files
        File file = new File(getExternalFilesDir(null), lens);
        Timber.d("File: " + file);
        return file;
    }

    /* Checks if external storage is available for read and write */
    public boolean isExternalStorageWritable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if external storage is available to at least read */
    public boolean isExternalStorageReadable() {
        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state) ||
                Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
            return true;
        }
        return false;
    }

    /* Checks if any of the My List assignment states are toggled from the + button on the My List A/B/C header */
    private boolean myListEnabled() {
        return (myListExpAdapter.addToMyListA || myListExpAdapter.addToMyListB || myListExpAdapter.addToMyListC);
    }

    /* Adds or removes the selected lens from the selected list */
    private boolean toggleMyList(String list, Lens lens, boolean currentStatus) {
        editMyList(list, lens, !currentStatus);
        return !currentStatus;
    }

    /* Adds a lens to specified "My List" */
    private void editMyList(String list, Lens lens, boolean add) {
        Timber.d("-- Edit -- List: " + list + ", Lens: " + lens.getTag() + ", add: " + add);

        int lensInd = lens.getTag();
        String originalData = lensArray.get(lensInd);
        Timber.d("original String: " + originalData);

        char[] data = originalData.toCharArray();

        char status0H = data[15];                                                                   // Status byte 0 for the lens (Cal and MyList status)
        char status0L = data[16];

        int statByte0H = Integer.parseInt(String.valueOf(status0H), 16);                                      // convert to int
        int statByte0L = Integer.parseInt(String.valueOf(status0L), 16);                                      // convert to int

        // update the status bytes according to the my list assignments
        switch(list) {
            case "A":
                if (add) statByte0L += 0x8;                                                         // add 0x8 to add to My List A
                else statByte0L -= 0x8;                                                             // subtract 0x8 to remove from My List A
                break;
            case "B":
                if (add) statByte0H += 0x1;                                                         // add 0x1 to add to My List B
                else statByte0H -= 0x1;                                                             // subtract 0x1 to remove from My List A
                break;
            case "C":
                if (add) statByte0H += 0x2;                                                         // add 0x2 to add to My List C
                else statByte0H -= 0x2;                                                             // subtract 0x2 to remove from My List A
                break;
        }

        if (statByte0H == 10) {                      // keep everything in Hex
            statByte0H = 0xA;
        }

        if (statByte0H == 11) {                      // keep everything in Hex
            statByte0H = 0xB;
        }

        // convert to the hex characters given the new My List assignment
        String newStatus0H = Integer.toHexString(statByte0H).toUpperCase();
        String newStatus0L = Integer.toHexString(statByte0L).toUpperCase();

        // set the individual status byte characters (2 ASCII bytes)
        data[15] = newStatus0H.charAt(0);
        data[16] = newStatus0L.charAt(0);

        // the updated string
        String updatedData = String.valueOf(data);
        Timber.d("updated String:  " + updatedData);

        // update the variables used in the ExpandableListView adapter
        // TODO: get rid of lensArray and make everything go off lensObjectArray using getDataString() field for each lens string
        lensArray.set(lensInd, updatedData);

        Lens updatedLens = parseLensLine(updatedData, lensInd, false);

        lensObjectArray.set(lensInd, updatedLens);

        String listName = "My List " + list;
        temporaryLensList = myListDataChild.get(listName);
        Timber.d("myListLenses before adding: " + temporaryLensList.toString());

        if (add) {
            temporaryLensList.add(updatedLens);
        }
        else {
            temporaryLensList.remove(lens);
        }

//        Timber.d("myListALenses after adding: " + temporaryLensList.toString());
        myListDataChild.put(listName, temporaryLensList);

        // TODO: Toggle icons for My List edit when you click directly on the edit image for another list
        // update the adapter to refresh the UI
        updateLensList();
    }

    private class DatabaseTask extends AsyncTask<Lens, Integer, Boolean> {
//    @Override
//    protected void onPreExecute() {
//        super.onPreExecute();
//
//        // Perform pre-adding operations here...
//    }

        @Override
        protected Boolean doInBackground(Lens... lenses) {
            int numLenses = lenses.length;
            for (Lens lens : lenses) {
//                database.lensDao().insertLenses(lens);
                Timber.d("Lens ID: " + lens.getId());
            }

            return true;
        }

//    @Override
//    protected void onPostExecute(Object obj) {
//        super.onPostExecute(obj);
//    }
    }
//
//    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
//        @Override
//        public void migrate(SupportSQLiteDatabase db) {
//            // create the new lens table with the changed column types (PITA, thanks SQLite)
//            db.execSQL("CREATE TABLE lens_new (tag INTEGER, data_string STRING, manufacturer STRING, series STRING, manufacturer_position INTEGER, " +
//                        "series_position INTEGER, focal_length_1 INTEGER, focal_length_2 INTEGER, is_prime BOOLEAN, serial STRING, note STRING, " +
//                        "calibrated_f BOOLEAN, calibrated_i BOOLEAN, calibrated_z BOOLEAN, my_list_a BOOLEAN, my_list_b BOOLEAN, my_list_c BOOLEAN, " +
//                        "checked BOOLEAN, list_id LONG, lens_id LONG, PRIMARY KEY(lens_id))");
//            db.execSQL("INSERT INTO lens_new (tag, data_string, manufacturer, series, manufacturer_position, series_position, focal_length_1, " +
//                    "focal_length_2, is_prime, serial, note, calibrated_f, calibrated_i, calibrated_z, my_list_a, my_list_b, my_list_c, checked, list_id, lens_id) " +
//                    "SELECT tag, data_string, manufacturer, series, manufacturer_position, series_position, focal_length_1, focal_length_2, is_prime, " +
//                    "serial, note, calibrated_f, calibrated_i, calibrated_z, my_list_a, my_list_b, my_list_c, checked, list_id, lens_id from lens");
//            db.execSQL("DROP table lens");
//            db.execSQL("ALTER TABLE lens_new RENAME TO lens");
//
//            // create the new list table with the changed column types
//            db.execSQL("CREATE TABLE lens_list_new (name STRING, lens_id LONG, list_id LONG, PRIMARY KEY(list_id))");
//            db.execSQL("INSERT INTO lens_list_new (name, lens_id, list_id) SELECT name, lens_id, list_id from lens_list");
//            db.execSQL("DROP table lens_list");
//            db.execSQL("ALTER TABLE lens_list_new RENAME TO lens_list");
//        }
//    };
//
//    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
//        @Override
//        public void migrate(SupportSQLiteDatabase db) {
//            // create the new lens table with the changed column types (PITA, thanks SQLite)
//            db.execSQL("DROP table lens");
//            db.execSQL("CREATE TABLE lens (tag INTEGER, data_string TEXT, manufacturer TEXT, series TEXT, manufacturer_position INTEGER, " +
//                    "series_position INTEGER, focal_length_1 INTEGER, focal_length_2 INTEGER, is_prime BOOLEAN, serial TEXT, note TEXT, " +
//                    "calibrated_f BOOLEAN, calibrated_i BOOLEAN, calibrated_z BOOLEAN, my_list_a BOOLEAN, my_list_b BOOLEAN, my_list_c BOOLEAN, " +
//                    "checked BOOLEAN, list_id LONG NULL, lens_id LONG, PRIMARY KEY(lens_id), FOREIGN KEY(lens_id) REFERENCES lens_list(list_id) ON UPDATE NO ACTION ON DELETE NO ACTION)");
//
////            db.execSQL("ALTER TABLE lens_new RENAME TO lens");
//
//            // create the new list table with the changed column types
//            db.execSQL("CREATE TABLE list_new (name TEXT, lens_id LONG NULL, list_id LONG, PRIMARY KEY(list_id), FOREIGN KEY(list_id) REFERENCES lens(lens_id) ON UPDATE NO ACTION ON DELETE NO ACTION)");
////            db.execSQL("INSERT INTO list_new (name, lens_id, list_id) SELECT name, lens_id, list_id from list");
//            db.execSQL("DROP table lens_list");
//            db.execSQL("ALTER TABLE list_new RENAME TO lens_list");
//        }
//    };
}

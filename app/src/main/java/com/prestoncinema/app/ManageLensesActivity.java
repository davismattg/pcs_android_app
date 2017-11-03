package com.prestoncinema.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Environment;
import android.support.v4.app.NavUtils;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.BaseInputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.NumberPicker;
import android.widget.SimpleAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Array;
import java.net.MalformedURLException;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static android.R.attr.id;
import static android.R.attr.key;
import static android.R.attr.layout_marginLeft;
import static android.R.attr.name;
import static android.R.attr.tag;
import static android.R.attr.type;
import static android.R.attr.width;
import static android.R.id.edit;
import static android.R.id.input;
import static android.icu.lang.UCharacter.GraphemeClusterBreak.L;
import static android.icu.lang.UCharacter.GraphemeClusterBreak.T;
import static android.media.CamcorderProfile.get;
import static android.util.Log.d;
import static android.view.ViewGroup.LayoutParams.FILL_PARENT;
import static android.view.ViewGroup.LayoutParams.MATCH_PARENT;
import static com.prestoncinema.app.R.array.lens_type_Angenieux;
import static com.prestoncinema.app.R.id.lensIndexTextView;
import static com.prestoncinema.app.R.id.lensManufTextView;
import static com.prestoncinema.app.R.id.lensSeriesTextView;


import timber.log.Timber;

/**
 * Created by MATT on 3/9/2017.
 * This activity is used to edit existing lens files. You can add/remove lenses, add/remove lenses to My List A/B/C
 * TODO: Give user ability to rename a lens
 * TODO: Restrict input length on focal length (9999mm max) and serial/note length (14 bytes including focal length)
 */

public class ManageLensesActivity extends UartInterfaceActivity implements AdapterView.OnItemSelectedListener { //implements MqttManager.MqttManagerListener
    // Log
    private final static String TAG = LensActivity.class.getSimpleName();

    // UI
    private TextView mNumLensesTextView;
//    private TextView mLensFileTextView;
//    private TextView mHeaderCountTextView;
    private ProgressDialog mProgressDialog;
    private ListView mLensesListView;
//    private ArrayAdapter<String> adapter;
    private SimpleAdapter adapter;
    private LensParentLevel expAdapter;
    private ExpandableListView expListView;

    private List<String> listDataHeader = new ArrayList<>(Arrays.asList("Angenieux", "Canon", "Cooke", "Fujinon", "Leica", "Panavision", "Zeiss", "Other"));
    private Map<Integer, Integer> listDataHeaderCount = new HashMap<Integer, Integer>(listDataHeader.size());
    private HashMap<String, List<String>> listDataChild;
    private HashMap<String, List<String>> lensTypeMap;

    private ArrayList<Lens> lensObjectArray = new ArrayList<>();
    private List<String> lensManufHeader = new ArrayList<String>();
    private HashMap<String, List<String>> lensTypeHeader = new HashMap<>();
    private HashMap<String, List<String>> lensNameHeader = new HashMap<>();
    private HashMap<String, List<String>> lensSerialAndNoteHeader = new HashMap<>();
    private HashMap<String, List<String>> lensStatusHeader = new HashMap<>();
    private HashMap<String, List<Integer>> lensNameIndex = new HashMap<>();
//    private HashMap<String, Map<Integer, Map<String, String>>> lensInfoMap = new HashMap<>();

    private ArrayAdapter<CharSequence> manufAdapter;
    private ArrayAdapter<CharSequence> typeAdapter;
    private ArrayAdapter<CharSequence> sortAdapter;
//    private Spinner mLensManufSpinner;
//    private Spinner mLensTypeSpinner;
//    private Spinner mSortBySpinner;
//    private EditText mLensFocal1EditText;
//    private EditText mLensFocal2EditText;
//    private TextView mLensFocalDashTextView;
//    private EditText mLensSerialEditText;
//    private CheckBox myListACheckBox;
//    private CheckBox myListBCheckBox;
//    private CheckBox myListCCheckBox;
//    private Button mSaveLensButton;
//    private Button mAddLensButton;
    private ImageView mAddLensImageView;

    // Lens
    private ArrayList<String> lensArray = new ArrayList<String>();
//    private ArrayList<String> lensDisplayArray = new ArrayList<String>();
//    private List<Map<String, String>> lensMap = new ArrayList<Map<String, String>>();
    private HashMap<Integer, HashMap<String, Object>> lensMap = new HashMap<Integer, HashMap<String, Object>>();
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap = new HashMap<Integer, HashMap<Integer, ArrayList<Integer>>>();
    private int numLenses = 0;
    private int currentLens = 0;
    private String lensFileString = "";
    private String lensFileStringStripped = "";

    private File lensFile;
    private boolean lensFileLoaded = false;
    private boolean changeDetected = false;
    private boolean isPrime = false;

    private int ang_byte = 0x0;
    private int can_byte = 0x1;
    private int cooke_byte = 0x2;
    private int fuj_byte = 0x3;
    private int lei_byte = 0x4;
    private int pan_byte = 0x5;
    private int zei_byte = 0x6;
    private int oth_byte = 0xF;
    private int mLA = 0x8;
    private int mLB = 0x1;
    private int mLC = 0x2;
    private int maxSerialLength = 14;

    private int lensId;                                                 // used to identify

    private byte[] STX = {02};
    private String STXStr = new String(STX);

//    private enum sortManufAsc = {}

    public ManageLensesActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_manage_lenses);

//        // special logging shit to work with Huawei Phones
//        if (BuildConfig.DEBUG) {
//            String deviceManufacturer = android.os.Build.MANUFACTURER;
//            if (deviceManufacturer.toLowerCase().contains("huawei")) {
//                Timber.plant(new HuaweiTree());
//            } else {
//                Timber.plant(new Timber.DebugTree());
//            }
//        }

        // UI initialization
        mNumLensesTextView = (TextView) findViewById(R.id.NumLensesTextView);
//        mLensFileTextView = (TextView) findViewById(R.id.lensFileTextView);
//        mHeaderCountTextView = (TextView) findViewById(R.id.lensHeaderCountTextView);
//        mLensesListView = (ListView) findViewById(R.id.LensesListView);
//        mLensManufSpinner = (Spinner) findViewById(R.id.LensManufSpinner);
//        mLensTypeSpinner = (Spinner) findViewById(R.id.LensTypeSpinner);
//        mSortBySpinner = (Spinner) findViewById(R.id.sortBySpinner);
//        mLensFocal1EditText = (EditText) findViewById(R.id.LensFocal1EditText);
//        mLensFocal2EditText = (EditText) findViewById(R.id.LensFocal2EditText);
//        mLensFocalDashTextView = (TextView) findViewById(R.id.LensFocalDashTextView);
//        mLensSerialEditText = (EditText) findViewById(R.id.LensSerialEditText);
//        myListACheckBox = (CheckBox) findViewById(R.id.MyListACheckBox);
//        myListBCheckBox = (CheckBox) findViewById(R.id.MyListBCheckBox);
//        myListCCheckBox = (CheckBox) findViewById(R.id.MyListCCheckBox);
//        mSaveLensButton = (Button) findViewById(R.id.SaveLensButton);
//        mAddLensButton = (Button) findViewById(R.id.AddLensButton);
        mAddLensImageView = (ImageView) findViewById(R.id.lensTypeAddImageView);

        // create the array adapters that are used to populate the new lens and sorting spinners, using string-array resource for the values
        manufAdapter = ArrayAdapter.createFromResource(this, R.array.lens_manuf_array, android.R.layout.simple_spinner_item);
        typeAdapter = ArrayAdapter.createFromResource(this, R.array.lens_type_Angenieux, android.R.layout.simple_spinner_item);
        sortAdapter = ArrayAdapter.createFromResource(this, R.array.lens_sort_array, android.R.layout.simple_spinner_item);

        // set the view resource for the spinner items
        manufAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        typeAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // assign the adapter and itemSelectedListener to each spinner
//        mLensManufSpinner.setAdapter(manufAdapter);
//        mLensManufSpinner.setSelection(0, false);
//        mLensManufSpinner.setOnItemSelectedListener(this);

//        mLensTypeSpinner.setAdapter(typeAdapter);
//        mLensTypeSpinner.setSelection(0, false);
//        mLensTypeSpinner.setOnItemSelectedListener(this);

//        mSortBySpinner.setAdapter(sortAdapter);
//        mSortBySpinner.setSelection(0, false);
//        mSortBySpinner.setOnItemSelectedListener(this);

//        adapter = new SimpleAdapter(ManageLensesActivity.this, lensMap, R.layout.lens_list_item, new String[] {"manufString", "serialString", "flString", "statusString"}, new int[] {R.id.lensTypeTextView, R.id.lensSerialTextView, R.id.lensFocalTextView, R.id.lensStatusTextView});
//        mLensesListView.setAdapter(adapter);
//        mLensesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View v, int position, long id) {
//                // make a toast here that informs the user to long-press for editing abilities
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
//                        Context context = getApplicationContext();
//                        CharSequence toastText = "Press and hold to edit this lens.";
//                        int duration = Toast.LENGTH_SHORT;
//
//                        Toast toast = Toast.makeText(context, toastText, duration);
//                        toast.show();
//                    }
//                });
////                Timber.d("list item clicked. id: " + id);
//            }
//        });

        listDataChild = populateLensTypeHeader(listDataHeader);

        for (int i=0; i < listDataHeader.size(); i++) {
            listDataHeaderCount.put(i, 0);
            lensPositionMap.put(i, new HashMap<Integer, ArrayList<Integer>>());
//            listDataHeaderCount.put(i == 6 ? "F" : String.valueOf(i), 0);
//            listDataHeaderCount.put(i == 6 ? 15 : i, 0);
        }

        lensManufHeader = Arrays.asList(getResources().getStringArray(R.array.lens_manuf_array));
        lensTypeHeader = populateLensTypeHeader(lensManufHeader);
        lensNameHeader = populateLensNameHeader(lensTypeHeader);
        lensSerialAndNoteHeader = populateLensNameHeader(lensTypeHeader);
        lensStatusHeader = populateLensNameHeader(lensTypeHeader);
        lensNameIndex = populateLensIndex(lensTypeHeader);
//        lensInfoMap = populateLensMap(lensTypeHeader);

        expAdapter = new LensParentLevel(this, lensManufHeader, lensTypeHeader);
        expListView = (ExpandableListView) findViewById(R.id.ParentLensLevel);
        expListView.setAdapter(expAdapter);

//        registerForContextMenu(expListView);

//        expListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
//            @Override
//            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//                Timber.d("view: " + view.toString());
//            }
//        });
//        mLensesListView.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
//            @Override
//            public boolean onItemLongClick(AdapterView<?> parent, View v, int position, long id) {
//                Timber.d("item long-clicked: " + position);
//                return true;
//            }
//        });

        // register the longclick menu for when the user long-presses on a lens within the list
//        registerForContextMenu(mLensesListView);

        /* Get the filename string from the previous activity (LensActivity) */
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lensFileString = extras.getString("lensFile");
            lensFileStringStripped = lensFileString.split("/")[lensFileString.split("/").length - 1].split(".lens")[0];
            setTitle(lensFileStringStripped);

            lensFile = new File(lensFileString);
            importLensFile(lensFile);
        }
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
    public void onDestroy() {
        super.onDestroy();
    }

    // when the user presses the back button, if changes are detected, prompt them to save the file
//    @Override
//    public void onBackPressed() {
//        Timber.d("Back button pressed");
//        super.onBackPressed();
//
//    }
////        if (changeDetected == true) {
////            runOnUiThread(new Runnable() {
////                @Override
////                public void run() {
////                    new AlertDialog.Builder(ManageLensesActivity.this)
////                            .setMessage("Would you like to save your changes?")
////                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
////                                @Override
////                                public void onClick(DialogInterface dialog, int which) {
////                                    saveLensFile(lensFileString, false);
////                                }
////                            })
////                            .setNeutralButton("Save As...", new DialogInterface.OnClickListener() {
////                                @Override
////                                public void onClick(DialogInterface dialog, int which) {
////                                    saveLensFileAs();
////                                }
////                            })
////                            .setNegativeButton("Discard", new DialogInterface.OnClickListener() {
////                                @Override
////                                public void onClick(DialogInterface dialog, int which) {
////                                    Intent intent = new Intent(ManageLensesActivity.this, LensActivity.class);
////                                    startActivity(intent);
////                                }
////                            })
////                            .setCancelable(true)
////                            .show();
////                }
////            });
////        }
////        else {
////            super.onBackPressed();
////        }
//    }

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
//        Timber.d("view id: ");
//        Timber.d(String.valueOf(v.getId()));

//        View targView = info.targetView;

//        long tag = info.id;
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
//        for (int id : hideItems) {
//            menu.findItem(id).setVisible(false);
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
                toast.show();
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
//                final EditText fileNameEditText = (EditText) renameLensView.findViewById(R.id.renameLensEditText);
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

    // function to hide menu items based on the lens status (i.e. if the lens is already in My List A,
    // hide "Add to My List A" option and show "Remove From My List A" instead
    private List<Integer> checkMenuItems(int ind) {
//        Timber.d("lensString: " + lensArray.get(ind) + "$$");
//        Timber.d("lensString bytes: " + Arrays.toString(lensArray.get(ind).getBytes()));
//        Timber.d("lensString length: " + lensArray.get(ind).length());
        List<Integer> menuItemsToHide = new ArrayList<Integer>();
//        byte[] lens = lensArray.get(ind).getBytes();
//        int byte1 = (int) lens[15];
//        int byte2 = (int) lens[16];
//
//        switch (byte1) {
//            case 48:case 52:case 56:case 67:        // 0, 4, 8, C
//                menuItemsToHide.add(R.id.removeFromMyListB);
//                menuItemsToHide.add(R.id.removeFromMyListC);
//                break;
//            case 49:case 53:case 57:case 68:        // 1, 5, 9, D
//                menuItemsToHide.add(R.id.removeFromMyListC);
//                menuItemsToHide.add(R.id.addToMyListB);
//                break;
//            case 50:case 54:case 65:case 69:        // 2, 6, A, E
//                menuItemsToHide.add(R.id.addToMyListC);
//                menuItemsToHide.add(R.id.removeFromMyListB);
//                break;
//            case 51:case 55:case 66:case 70:        // 3, 7, B, F
//                menuItemsToHide.add(R.id.addToMyListB);
//                menuItemsToHide.add(R.id.addToMyListC);
//                break;
//            default:
//                break;
//        }
//
//        if (byte2 >= 56) {
//            menuItemsToHide.add(R.id.addToMyListA);
//        }
//        else {
//            menuItemsToHide.add(R.id.removeFromMyListA);
//        }

        return menuItemsToHide;
    }

    // function for when the user is adding a new lens - when manufacturer name is selected,
    // populate the lens type dropdown (spinner in Android) with the correct lens names
    public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
        // get the item that was selected, and call toString() to get the lens manuf. name
        String type_id = parent.getItemAtPosition(pos).toString();
        Timber.d("item selected: " + type_id);

        // create the new adapter based on the lens manuf. selection. this populates the lens type spinner
//        if (parent.getId() == R.id.LensManufSpinner) {
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
//        for (String manufName : listDataHeader) {                                                   // loop through array of manuf names
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

    // if Prime lens, just show ___ mm. If zoom lens, show ____-____ mm
    private boolean isPrime(String type) {
        switch (type) {
            // all the lens types that are zoom
            case "Optimo":case "Rouge":case "HR":case "Cinema Zoom":case "Zoom":case "Premier Zoom":case "Alura Zoom":case "Primo Zoom":case "Anam. Zoom":
                Timber.d("Zoom lens detected, switch to zoom lens FL mode");
                return false;
            default:            // a prime lens detected
                Timber.d("Prime lens detected, switch to prime lens FL mode");
                return true;
        }
    }

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
        lensArray.clear();                                                  // clear the lens array since we'll be populating it with the file contens

        try {
            FileInputStream lensIn = new FileInputStream(lensFile);         // open a FileInputStream for the selected file
            reader = new BufferedReader(
                    new InputStreamReader(lensIn));                         // read the file
            String line;                                                    // read the file one line at a time
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0) {
                    lensArray.add(line);                                    // add the read lens into the array
                }
            }
            if (lensArray.size() > 0) {                                     // make sure something was actually imported
                lensFileLoaded = true;                                      // set the flag
                numLenses = lensArray.size();                               // the number of lenses, used for loops and display on the UI
                currentLens = 0;                                            // index mostly used for looping

                lensObjectArray = new ArrayList<>(numLenses);

                for (int i=0; i < lensArray.size(); i++) {
                    String len = lensArray.get(i);
                    countLensLine(len);
                    lensObjectArray.add(i, parseLensLine(len, i, true));
                }
            }

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mNumLensesTextView.setText(numLenses + " lenses in this file");                     // setting the UI to display how many lenses were found
                }
            });
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

    /////////////////////////////////////////////////////////////////////////////////////////////////////
    //                             Custom function to sort the lens Array                              //
    // this function looks at each lens data string after truncating the part before it. For example,  //
    // when sorting by manufacturer, the manufacturer ID starts at byte 17 of the overall lens data.   //
    // so this function gets each string, chops it at the specified index, and then sorts the chopped  //
    // strings. Then it searches the original array for the chopped string to determine the mapping    //
    // from old order to new, sorted order. then it rearranges the original array with the new         //
    // indices.                                                                                        //
    //  TODO: Finish sorting logic and make it responsive to the sorting spinner                       //
    /////////////////////////////////////////////////////////////////////////////////////////////////////
//    private ArrayList<String> sortLensArray(ArrayList<String> arr, String param, String dir) {
    private void sortLensArray(ArrayList<String> arr, String param, String dir) {
        Timber.d("sorting lens array --------------------------------------------");

        ArrayList<String> sub_arr = new ArrayList<String>(arr.size());                       // initialize the ArrayList that will store the truncated strings
        ArrayList<String> new_arr = new ArrayList<String>(arr.size());                  // initialize the ArrayList that will store the rearranged array
        int sub_ind = 0;                                                                // the index to chop the lens strings. depends on param
        switch(param) {
            case "manufacturer":
                sub_ind = 17;               // lens manuf bytes start at index 17
                break;
            case "fLength":
                sub_ind = 19;               // focal length starts at index 19
                break;
        }

        for (int i=0; i < arr.size(); i++) {
            sub_arr.add(arr.get(i).substring(sub_ind));
        }

        Collections.sort(sub_arr);

        for (int j=0; j < sub_arr.size(); j++) {
            for (String str : arr) {
                if (str.contains(sub_arr.get(j))) {
                    new_arr.add(j, str);
                }
            }
        }

        lensArray.clear();
        lensArray = new_arr;
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // This function takes in the raw string from the lens file and formats it in the way we want  //
    // to display it in the UI. Check the HU3 document/ask Mirko for the data structure            //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    private Lens parseLensLine(String line, int index, boolean isNewLens) {
        Timber.d("parse line: " + line);

        /* Initialize the Lens object that will store all the info about this lens */
        Lens lensObject = new Lens(index, "", "", 0, 0,
                0, 0, false, "", "", false,
                false, false, false, false, false);

        byte[] bytes = line.getBytes();                                                             // get the hex bytes from the ASCII string

        /* Lens status (calibrated, myList, etc) */
        byte[] status1 = Arrays.copyOfRange(bytes, 15, 17);                               // bytes 15 and 16 (ASCII bytes) are the first (hex) status byte
        HashMap<String, boolean[]> statusMap = convertLensStatus(status1);
        lensObject.setCalibratedF(statusMap.get("calibrated")[0]);
        lensObject.setCalibratedI(statusMap.get("calibrated")[1]);
        lensObject.setCalibratedZ(statusMap.get("calibrated")[2]);
        lensObject.setMyListA(statusMap.get("myList")[0]);
        lensObject.setMyListB(statusMap.get("myList")[1]);
        lensObject.setMyListC(statusMap.get("myList")[2]);

        /* Lens Manufacturer and Type */
        byte[] status2 = Arrays.copyOfRange(bytes, 17, 19);                                         // bytes 17 and 18 (ASCII bytes) are the second (hex) status byte
        HashMap<String, Object> nameAndTypeMap = convertManufName(status2);
        lensObject.setManufacturer((String) nameAndTypeMap.get("manufacturer"));
        lensObject.setSeries((String) nameAndTypeMap.get("series"));

        // adding the lens' index to the correct position to be retrieved later in the ListView
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
            idArrayList.add(index);                                                                 // add the current lens index to the ArrayList for this position combo
        }

        currentLensPositionMap.put(seriesPos, idArrayList);                                         // add the id ArrayList to the placeholder HashMap
        lensPositionMap.put(manufPos, currentLensPositionMap);                                      // add the ids back into the correct position of the overall lens position map

        /* Focal length(s) */
        String focal1 = line.substring(19, 23);                                                     // bytes 19-22 (ASCII bytes) are the first (hex) focal length byte
        String focal2 = line.substring(23, 27);                                                     // bytes 23-26 (ASCII bytes) are the second (hex) focal length byte
        lensObject.setFocalLength1(convertFocalLength(focal1));
        lensObject.setFocalLength2(convertFocalLength(focal2));

        /* Serial number */
        String serial = line.substring(27, 31);
        String convertedSerial = convertSerial(serial);
        lensObject.setSerial(convertedSerial);

        /* Note */
        String lensName = line.substring(0, 15);                                                    // get the substring that contains the note (& serial & focal lengths)
        int noteBegin;
        String lensNote;
        if (convertedSerial.length() > 0) {                                                         // serial string present, look for it in the lens name
            noteBegin = lensName.indexOf(convertedSerial) + convertedSerial.length();               // set the index to separate the lens serial and note
        }
        else {
            noteBegin = lensName.indexOf("mm") + 2;                                                 // no serial present, so anything after "mm" is considered the note
        }

        lensNote = lensName.substring(noteBegin).trim();                                            // grab the note using the index determined above
        lensObject.setNote(lensNote);                                                               // set the note property of the lens object

        return lensObject;
    }

    // function to get the index after the last character of the lens name (focal length and serial) //
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
    private String constructFocalLengthString(int fL1, int fL2) {
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

    // function to assign or remove a lens from a given list. You just do this by adding or subtracting //
    // the correct character from the status byte
    private void lensListAssign(String list, int id, boolean toAdd) {
        // defining the bytes to add/subtract
        int myListAByte = 8;
        int myListBByte = 1;
        int myListCByte = 2;
        byte[] line = lensArray.get(id).getBytes();
        Timber.d("line: " + new String(line) + "\nAdding: " + toAdd);

        int byte1 = (int) line[15];
        int byte2 = (int) line[16];
        int newByte;

        Timber.d("Lens bytes: " + byte1 + ", " + byte2);

        switch (list) {
            case "A":           // do something w/ My List A
                if (toAdd) {        // if Add to My List A, +
                    newByte = byte2 + myListAByte;
                }
                else {              // if remove from My List A, -
                    newByte = byte2 - myListAByte;
                }
                if (newByte >= 58 && newByte <= 64) {       // ASCII conversion to go from 9 to A (see ASCII-HEX conversion table)
                    if (toAdd) {
                        newByte += 7;
                    }
                    else {
                        newByte -= 7;
                    }
                }

                Timber.d("A; newByte: " + newByte);
                byte2 = newByte;
                break;
            case "B":
                if (toAdd) {
                    newByte = byte1 + myListBByte;
                }
                else {
                    newByte = byte1 - myListBByte;
                }
                if (newByte >= 58 && newByte <= 64) {
                    if (toAdd) {
                        newByte += 7;
                    }
                    else {
                        newByte -= 7;
                    }
                }
                Timber.d("B; newByte: " + newByte);
                byte1 = newByte;
                break;
            case "C":
                if (toAdd) {
                    newByte = byte1 + myListCByte;
                }
                else {
                    newByte = byte1 - myListCByte;
                }
                if (newByte >= 58 && newByte <= 64) {
                    if (toAdd) {
                        newByte += 7;
                    }
                    else {
                        newByte -= 7;
                    }
                }
                Timber.d("C; newByte: " + newByte);
                byte1 = newByte;
                break;
            default:
                break;
        }

        Timber.d("After conversion, byte1: " + byte1 + ", byte2: " + byte2);
        line[15] = (byte) byte1;
        line[16] = (byte) byte2;

        String lineString = new String(line);
        Timber.d("line string: " + lineString);
        lensArray.set(id, lineString);
        // TODO: get the following line working to remove a lens from a list

        updateLensList();           // update the UI to reflect the changes to the lens
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
                    String lensOut = lens + "\n";
                    try {
                        fos.write(lensOut.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fos.close();
                    currentLens = 0;
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
        lensMap.remove(id);
        updateLensList();
    }

    public void addNewLens(View view) {
        Timber.d("add new lens");
    }

    // hide or show the add new lens section of the UI
    public void toggleAddNewLens(View view) {
        final View layout = findViewById(R.id.NewLensLayout);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (layout.getVisibility() == View.VISIBLE) {
                    layout.setVisibility(View.GONE);
//                    mAddLensButton.setText("Add Lens");
                }
                else {
                    layout.setVisibility(View.VISIBLE);
//                    mAddLensButton.setText("Close");
                }
            }
        });
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
        String padded = STXStr + toPad + new String(new char[width - toPad.length()]).replace('\0', fill);

        Timber.d("lensString length: " + padded.length());
//        Timber.d("lensString bytes: " + Arrays.toString(padded.getBytes()));
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
    private boolean editLens(Lens lensObject, String focalLen, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        Timber.d("///////////////////////////////////////////////////////////////");
        Timber.d("editLens - params: ");
        Timber.d("focal: " + focalLen);
        Timber.d("serial: " + serial);
        Timber.d("note: " + note);
        Timber.d("myListA: " + myListA);
        Timber.d("myListB: " + myListB);
        Timber.d("myListC: " + myListC);
        Timber.d("///////////////////////////////////////////////////////////////");

        int lensInd = lensObject.getId();                                                           // the index of the lens in the overall array
        String prevLensString = lensArray.get(lensInd);                                             // the original string that needs to be updated

        Timber.d("previous lens string: " + prevLensString);

        // TODO: make sure this index is correct
        String toKeep = prevLensString.substring(31);                                               // substring of the stuff we don't care about updating, starting after the serial number

        String nameSubString = prevLensString.substring(0, 19);                                     // get the first 19 characters of the lens data. This includes STX, 14 chars for name, and 2 chars for status
        String status0H = nameSubString.substring(15, 16);                                           // Status byte 0 for the lens (Cal and MyList status)
        String status0L = nameSubString.substring(16, 17);
        String status1H = nameSubString.substring(17, 18);                                           // Status byte 1 for the lens (Manuf and Series)
        String status1L = nameSubString.substring(18, 19);
        String focalString = prevLensString.substring(19, 27);                                      // 8 characters, 4 for each focal length
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

        String newLensName = STXStr + lensName;

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

        lensObjectArray.set(lensInd, newLensObject);
        updateLensList();

        return true;
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

    // get the lens index within the array. useful for entering a new lens in the correct spot on the UI, but not much else since HU3 does its own sorting in the UI
    private int getLensIndex(String lens) {
        Map.Entry<Integer, Integer> maxEntry = null;
        byte[] bytes = Arrays.copyOfRange(lens.getBytes(), 17, 19);
        byte[] serialBytes = Arrays.copyOfRange(lens.getBytes(), 1, 15);
        String serialString = new String(serialBytes).trim();
        byte manuf = bytes[0];
        byte type = bytes[1];

        Map<Integer, Integer> indexMap = new HashMap<Integer, Integer>();

        Timber.d("manufByte: " + Arrays.toString(bytes));
        Timber.d("serialString: " + serialString);

        for (int i=0; i < lensArray.size(); i++) {
            String l = lensArray.get(i);                                // the lens string within the array
            byte man = Arrays.copyOfRange(l.getBytes(), 17, 18)[0];
            byte typ = Arrays.copyOfRange(l.getBytes(), 18, 19)[0];
            byte[] ser = Arrays.copyOfRange(l.getBytes(), 1, 15);
            String serStr = new String(ser).trim();
            boolean manufCompare = manuf == man;
            boolean typeCompare = type == typ;

            if (manufCompare && typeCompare) {
                Timber.d("Same lens manuf and type detected");
                Timber.d("comparing focals: " + serialString + " & " + serStr);
                int strCompare = serialString.compareTo(serStr);
                Timber.d("serial compare: " + strCompare);
                if (strCompare >= 0) {
                    indexMap.put(i, strCompare);
                }
            }

            if (indexMap.size() == 0) {
                indexMap.put(0, 0);
            }

            for (Map.Entry<Integer, Integer> entry : indexMap.entrySet()) {
                if (maxEntry == null || entry.getValue().compareTo(maxEntry.getValue()) >= 0) {
                    maxEntry = entry;
                }
            }
        }

        return (maxEntry.getKey() + 1);
    }

    // TODO: get working to update adapter properly when lens is edited. Editing is working, but adapter isn't refreshing view elements
    private void updateLensList() {
        Timber.d("Updating lens list.");

        // get the new numLenses in case the user added a lens
        numLenses = lensArray.size();

        Timber.d("numLenses during update: " + numLenses);

        // save the lens file right away
        saveLensFile(lensFileString, false);

        // run the UI updates on the UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Context context = getApplicationContext();
                expAdapter.notifyDataSetChanged();                                                      // let the expandableListView custom adapter know we have changed data

                String numLensesText = numLenses + " lenses in this file:";                             // building the text to populate the number of lenses textView
                mNumLensesTextView.setText(numLensesText);                                              // set the text

                // make a toast letting the user know that their changes were successful
                CharSequence toastText = "File modified successfully.";
                int duration = Toast.LENGTH_LONG;

                Toast toast = Toast.makeText(context, toastText, duration);
                toast.show();
            }
        });
    }

    private boolean checkSerialLength(String focal, String serial, String note) {
        String completeString;
        int completeStringLength;

        completeString = focal + " " + serial + note;
        Timber.d("completeString: " + completeString);

        completeStringLength = completeString.length();
        Timber.d("length: " + completeStringLength);

        return completeStringLength <= maxSerialLength;
    }

    private void countLensLine(String lens) {
        int sub_ind = 17;                                                                        // the index to chop the lens strings (17 for manufacturer)
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

        int currCount = listDataHeaderCount.get(key);
        listDataHeaderCount.put(key, currCount + 1);
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

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // parent-level adapter class to generate custom multi-level ExpandableListView for displaying //
    // lenses currently in a file. This level is the Header, which should just be the lens manuf.   //
    // names (lensDataHeader var)                                                                  //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    public class LensParentLevel extends BaseExpandableListAdapter {
        private Context _context;
        private List<String> _listDataHeader;       // header titles. Lens Manuf Names in this case
        private HashMap<String, List<String>> _listDataChild;

        public LensParentLevel(Context context, List<String> listDataHeader, HashMap<String, List<String>> listChildData) {
            this._context = context;
            this._listDataHeader = listDataHeader;
            this._listDataChild = listChildData;
        }

        @Override
        public Object getChild(int groupPosition, int childPosition)
        {
            return this._listDataChild.get(this._listDataHeader.get(groupPosition)).get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition)
        {
            return childPosition;
        }

        @Override
        public View getChildView(int groupPosition, int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent)
        {
            List<String> lensTypeList = this._listDataChild.get(_listDataHeader.get(groupPosition));
            String parentNode = (String) getGroup(groupPosition); //_listDataHeader.get(groupPosition);
            HashMap<Integer, ArrayList<Integer>> lensPositionIndicesMap = lensPositionMap.get(groupPosition);

            HashMap<String, ArrayList<Lens>> lensChildHash = new HashMap<>();

            for (String series : lensTypeList) {
                ArrayList<Lens> tempLensList = new ArrayList<>();
                lensChildHash.put(series, tempLensList);
                for (Lens lens : lensObjectArray) {
                    if (lens.getManufacturer().equals(parentNode) && lens.getSeries().equals(series)) {
                        tempLensList.add(lens);
                    }
                }
                lensChildHash.put(series, tempLensList);
            }

            /* Initialize the 2nd level of the ExpandableListView */
            LensSecondLevel lensSecondLevel = new LensSecondLevel(ManageLensesActivity.this);
            /* Initialize the adapter for the 2nd level of the ExpandableListView */
            SecondLevelListViewAdapter lensSecondLevelAdapter = new SecondLevelListViewAdapter(
                                                                    ManageLensesActivity.this,
                                                                    this._listDataChild.get(parentNode),
                                                                    lensChildHash,
                                                                    lensPositionIndicesMap,
                                                                    parentNode
                                                                );
            lensSecondLevel.setAdapter(lensSecondLevelAdapter);
            lensSecondLevel.setGroupIndicator(null);

            // TODO: Get onGroupCollapseListener working to prevent group collapse when focus on EditText in Dialog
//            lensSecondLevel.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {
//                @Override
//                public void onGroupCollapse(int groupIndex) {
//                    Timber.d("collapse called for position %d", groupIndex);
//                    lensSecondLevel.expandGroup(groupIndex);
//                }
//            });

//            registerForContextMenu(lensSecondLevel);
            return lensSecondLevel;
        }

        @Override
        public int getChildrenCount(int groupPosition)
        {
            // TODO: try to figure out why this has to be hard-coded 1 as opposed to actually using the children's array size
//            int count = this._listDataChild.get(this._listDataHeader.get(groupPosition)).size();
//            return count;
            return 1;
        }

        @Override
        public Object getGroup(int groupPosition)
        {
            return this._listDataHeader.get(groupPosition);
        }

        @Override
        public int getGroupCount()
        {
            return this._listDataHeader.size();
        }

        @Override
        public long getGroupId(int groupPosition)
        {
            return groupPosition;
        }

        @Override
        public View getGroupView(int groupPosition, boolean isExpanded,
                                 View convertView, ViewGroup parent)
        {
            String headerTitle = (String) getGroup(groupPosition);
            String headerCount = String.valueOf(listDataHeaderCount.get(groupPosition));

            if (convertView == null) {
                LayoutInflater headerInflater = (LayoutInflater) this._context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = headerInflater.inflate(R.layout.lens_list_group, null);
            }

            ImageView headerImageView = (ImageView) convertView.findViewById(R.id.lensHeaderImageView);
            TextView headerTextView = (TextView) convertView.findViewById(R.id.lensListHeader);
            TextView headerCountTextView = (TextView) convertView.findViewById(R.id.lensHeaderCountTextView);

            headerImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);
            headerTextView.setText(headerTitle);
            headerCountTextView.setText(headerCount);

            // TODO: make this use newRed color resource
            headerImageView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF333333);
            headerTextView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF333333);
            headerCountTextView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF333333);

            return convertView;
        }

        @Override
        public boolean hasStableIds()
        {
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition)
        {
            return true;
        }
    }



    /////////////////////////////////////////////////////////////////////////////////////////////////
    // second level of multi-level ExpandableListView for displaying lenses within a given file    //
    // this is typically the lens type "Optimo, Prime, Cinema Zoom," etc, and is dependent on the  //
    // lens manufacturer that is the parent to this second level                                   //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    public class LensSecondLevel extends ExpandableListView
    {
        int groupPosition, childPosition, groupid;

        public LensSecondLevel(Context context)
        {
            super(context);
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(100000000, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public class SecondLevelListViewAdapter extends BaseExpandableListAdapter
    {
        private Context _context;
        private List<String> _listDataHeader;                                                             // header titles (lens types, per manufacturer)
//        private HashMap<Integer, HashMap<String, Object>> _listDataChild;
//        private ArrayList<Lens> _listDataChild;
        private HashMap<String, ArrayList<Lens>> _listDataChild;
        private HashMap<Integer, ArrayList<Integer>> _listDataChildIndices;
        //        private HashMap<String, List<String>> _listDataChild;                                   // child data in format of header title, child title (list of lenses within each type)
//        private HashMap<String, List<String>> _listDataChildSerial;                                     // child data in format of heaer title, child title (list of serial numbers by lens)
//        private HashMap<String, List<String>> _listDataChildNote;                                       // note of each lens
//        private HashMap<String, List<String>> _listDataChildStatus;                                     // status of each lens (myList, calibrated, etc)
//        private HashMap<String, List<Integer>> _listDataChildIndex;                                     // index of each lens within the overall lensArray
        private String _manufName;                                                                      // manufacturer name

        public SecondLevelListViewAdapter(Context context,
                                          List<String> listDataHeader,
                                          HashMap<String, ArrayList<Lens>> listDataChild,
//                                          HashMap<Integer, HashMap<String, Object>> listChildMap,
                                          HashMap<Integer, ArrayList<Integer>> listDataChildIndices,
//                                          HashMap<String, List<String>> listChildData,
//                                          HashMap<String, List<String>> listDataChildSerial,
//                                          HashMap<String, List<String>> listDataChildStatus,
//                                          HashMap<String, List<Integer>> listDataChildIndex,
                                          String manufName) {
            this._context = context;
            this._listDataHeader = listDataHeader;
            this._listDataChild = listDataChild;
            this._listDataChildIndices = listDataChildIndices;
//            this._listDataChild = listChildData;
//            this._listDataChildSerial = listDataChildSerial;
////            this._listDataChildNote = listDataChildNote;
//            this._listDataChildStatus = listDataChildStatus;
//            this._listDataChildIndex = listDataChildIndex;
            this._manufName = manufName;

//            Timber.d("LensSecondLevel: ");
//            Timber.d("header: " + this._listDataHeader.toString());
//            Timber.d("children: " + this._listDataChild.toString());
//            Timber.d("indices: " + this._listDataChildIndices.toString());
        }

        @Override
        public Object getChild(int groupPosition, int childPosition)
        {
            Timber.d("--------------- getChild (" + groupPosition + ", " + childPosition + ") -------------");
            Timber.d("listDataHeader: " + this._listDataHeader.toString());
            Timber.d("listDataChild: " + this._listDataChild.toString());
            return this._listDataChild.get(this._listDataHeader.get(groupPosition)).get(childPosition);
        }

        @Override
        public long getChildId(int groupPosition, int childPosition)
        {
            return childPosition;
        }

        public Object getChildTag(int groupPosition, int childPosition) {
            return this._listDataChildIndices.get(groupPosition).get(childPosition);
        }

        @Override
        public View getChildView(int groupPosition, int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent)
        {
            final Lens childObject = (Lens) getChild(groupPosition, childPosition);

            // get the strings that will be used to populate the lens row
            final String childText = constructFocalLengthString(childObject.getFocalLength1(), childObject.getFocalLength2());
            final String childSerialText = childObject.getSerial();
            final String childNoteText = childObject.getNote();
//            final String childStatusText = "Cal: None";                                                                                       // TODO: implement boolean values
            final String manufTitle = childObject.getManufacturer();
            final String typeTitle = childObject.getSeries();
//            final String childText = (String) getChild(groupPosition, childPosition);                                                       // "24-290mm"
//            final String childSerialText = (String) getChildSerial(groupPosition, childPosition);                                           // "111 ANA"
//            final String childNoteText = (String) getChildNote(groupPosition, childPosition);
//            final String childStatusText = (String) getChildStatus(groupPosition, childPosition);                                           // "Cal: F \n MyList: A"

//            Timber.d("childText: " + childText);
//            Timber.d("childSerialText: " + childSerialText);

            if (convertView == null) {
                LayoutInflater headerInflater = (LayoutInflater) this._context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = headerInflater.inflate(R.layout.lens_list_lens, null);                                                        // inflate the view used to display the lens
            }

            TextView lensView = (TextView) convertView.findViewById(R.id.lensListLensTextView);                                             // the textView that displays the lens focal length
            TextView serialView = (TextView) convertView.findViewById(R.id.lensListSerialTextView);                                         // the textView that displays the lens serial number
            ImageView fCalImageView = (ImageView) convertView.findViewById(R.id.lensCalFImageView);                                         // ImageView that holds the "F" icon
            ImageView iCalImageView = (ImageView) convertView.findViewById(R.id.lensCalIImageView);                                         // ImageView that holds the "I" icon
            ImageView zCalImageView = (ImageView) convertView.findViewById(R.id.lensCalZImageView);                                         // ImageView that holds the "Z" icon

            ImageView myListAImageView = (ImageView) convertView.findViewById(R.id.myListAImageView);                                       // ImageView that holds the "A" icon
            ImageView myListBImageView = (ImageView) convertView.findViewById(R.id.myListBImageView);                                       // ImageView that holds the "B" icon
            ImageView myListCImageView = (ImageView) convertView.findViewById(R.id.myListCImageView);                                       // ImageView that holds the "C" icon

            final ImageView editLensImageView = (ImageView) convertView.findViewById(R.id.editLensImageView);                               // the imageView used to contain the edit icon (pencil)

            // TODO: get lens note showing up
            lensView.setText(childText);                                                                                                    // set the focal length string text
            serialView.setText(childSerialText + " " + childNoteText);                                                                      // set the serial string text

            // Display the appropriate ImageViews depending on the lens status
            if (childObject.getCalibratedF()) {
                fCalImageView.setVisibility(View.VISIBLE);
            }
            if (childObject.getCalibratedI()) {
                iCalImageView.setVisibility(View.VISIBLE);
            }
            if (childObject.getCalibratedZ()) {
                zCalImageView.setVisibility(View.VISIBLE);
            }
            if (childObject.getMyListA()) {
                myListAImageView.setVisibility(View.VISIBLE);
            }
            if (childObject.getMyListB()) {
                myListBImageView.setVisibility(View.VISIBLE);
            }
            if (childObject.getMyListC()) {
                myListCImageView.setVisibility(View.VISIBLE);
            }

            editLensImageView.setTag(getChildTag(groupPosition, childPosition));                                                            // set the tag, which is the lens' index in the overall array (lensArray var)
            convertView.setTag(getChildTag(groupPosition, childPosition));

            convertView.setId((int) getChildTag(groupPosition, childPosition));
            convertView.setLongClickable(true);                                                                                             // enable longClick on the lens view

            registerForContextMenu(convertView);                                                                                            // register the lens for a context menu

            // listen for the onGroupCollapsed event so we can override it to prevent list from collapsing when dialog is present


//            convertView.setOnLongClickListener(new View.OnLongClickListener() {
//                @Override
//                public boolean onLongClick(View v) {
//                    lensId = (int) getChildTag(groupPosition, childPosition);
//                    registerForContextMenu(convertView);
//                    openContextMenu(convertView);
//                    return true;
//                }
//            });
            // onClickListener for when the user taps on the edit lens icon. This is used to inflate the dialog where the use can actually edit the lens.
            editLensImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
//                    openContextMenu(editLensImageView);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LayoutInflater dialogInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            final View editLensView = dialogInflater.inflate(R.layout.dialog_edit_lens, null);                               // inflate the view to use as the edit dialog

                            // initialize the UI components so we can access their contents when the user presses "Save"
                            TextView lensManufAndSeriesTextView = (TextView) editLensView.findViewById(R.id.lensManufAndSeriesTextView);                       // textView to display the lens manufacturer name
                            final TextView lensFocalLengthTextView = (TextView) editLensView.findViewById(R.id.lensFocalTextView);
                            final EditText lensSerialEditText = (EditText) editLensView.findViewById(R.id.LensSerialEditText);
                            final EditText lensNoteEditText = (EditText) editLensView.findViewById(R.id.LensNoteEditText);

                            final CheckBox myListACheckBox = (CheckBox) editLensView.findViewById(R.id.MyListACheckBox);
                            final CheckBox myListBCheckBox = (CheckBox) editLensView.findViewById(R.id.MyListBCheckBox);
                            final CheckBox myListCCheckBox = (CheckBox) editLensView.findViewById(R.id.MyListCCheckBox);

                            ImageView CalFImageView = (ImageView) editLensView.findViewById(R.id.lensCalFImageView);
                            ImageView CalIImageView = (ImageView) editLensView.findViewById(R.id.lensCalIImageView);
                            ImageView CalZImageView = (ImageView) editLensView.findViewById(R.id.lensCalZImageView);

                            // the hidden textView where we store the lens index (in the form of the view's tag)
                            final TextView lensIndexTextView = (TextView) editLensView.findViewById(R.id.lensIndexTextView);

                            // check the status string to see if the lens is part of a list
                            final boolean myListA = childObject.getMyListA();
                            final boolean myListB = childObject.getMyListB();
                            final boolean myListC = childObject.getMyListC();

                            boolean calF = childObject.getCalibratedF();
                            boolean calI = childObject.getCalibratedI();
                            boolean calZ = childObject.getCalibratedZ();

                            if (calF) {
                                CalFImageView.setVisibility(View.VISIBLE);
                            }

                            if (calI) {
                                CalIImageView.setVisibility(View.VISIBLE);
                            }

                            if (calZ) {
                                CalZImageView.setVisibility(View.VISIBLE);
                            }

                            // set up listeners for when the user checks the MyList boxes. If F of Lens isn't calibrated, don't let them add to list
                            MyListCheckBoxListener listener = new MyListCheckBoxListener();
                            listener.mContext = _context;
                            listener.isFCal = childObject.getCalibratedF();

                            myListACheckBox.setOnCheckedChangeListener(listener);
                            myListBCheckBox.setOnCheckedChangeListener(listener);
                            myListCCheckBox.setOnCheckedChangeListener(listener);


                            // populate the text fields with existing values from the lens
                            String lensManufAndSerial = childObject.getManufacturer() + " - " + childObject.getSeries();
                            lensManufAndSeriesTextView.setText(lensManufAndSerial);
                            lensFocalLengthTextView.setText(constructFocalLengthString(childObject.getFocalLength1(), childObject.getFocalLength2()));

                            lensSerialEditText.setText(childObject.getSerial());
                            lensSerialEditText.setSelection(childObject.getSerial().length());             // position the cursor at the end of the serial string

                            // check the myList checkboxes according to whether it's a member of the appropriate list
                            myListACheckBox.setChecked(myListA);
                            myListBCheckBox.setChecked(myListB);
                            myListCCheckBox.setChecked(myListC);

                            // add the tag from the lens item in the listView to the hidden textView so we can retrieve it later
                            int lensTag = (int) editLensImageView.getTag();
                            lensIndexTextView.setText(String.valueOf(lensTag));

                            final Lens thisLens = lensObjectArray.get(lensTag);
                            Timber.d(thisLens.getManufacturer());
                            Timber.d(thisLens.getSeries());
                            Timber.d(String.valueOf(thisLens.getFocalLength1()));
                            Timber.d(String.valueOf(thisLens.getFocalLength2()));

                            final AlertDialog dialog = new AlertDialog.Builder(_context)
//                                    .setTitle("Edit Lens")
                                    .setView(editLensView)
                                    .setPositiveButton("Save", null)
                                    .setNegativeButton("Cancel", null)
                                    .setNeutralButton("Delete", null)
                                    .setCancelable(true)
                                    .create();

//                            // set up the TextWatcher on the lens serial editText so we can check the entered text length as the user types
//                            final TextWatcher serialTextWatcher = new TextWatcher() {
//                                boolean acceptEntry = true;
//                                boolean wasTrimmed = false;                                                                                // boolean to indicate if the text was changed by the TextWatcher afterTextChanged method
//                                int maxEnteredLength;
//                                @Override
//                                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
//                                    Timber.d("before changing text, s = " + s);
//                                    if (!wasTrimmed) {
//                                        String serial = lensSerialEditText.getText().toString().trim();
////                                        String serial = s.toString();
//                                        // returns true if the lens serial + note is 14 chars or less
//                                        int serialStringLength = checkSerialLength(childText, serial, note), serial);
//                                        maxEnteredLength = 14 - serialStringLength;
//                                        if (serialStringLength > 14) {
//                                            acceptEntry = false;
//                                        } else {
//                                            acceptEntry = true;
//                                        }
//                                    }
//                                }
//
//                                @Override
//                                public void onTextChanged(CharSequence s, int start, int before, int count) {
//                                    Timber.d("onTextChanged: " + s);
//                                }
//
//                                @Override
//                                public void afterTextChanged(Editable s) {
//                                    Timber.d("max length allowed: " + maxEnteredLength);
//                                    if (acceptEntry) {
////                                    if (maxEnteredLength > 0) {
//                                        wasTrimmed = false;
//                                        Timber.d("accepted. s = " + s);
//                                        return;
//                                    }
//
//                                    else {
////                                        acceptEntry = true;
//                                        wasTrimmed = true;
//
//                                        CharSequence toastText = "Error: Lens name too long.";
//                                        int duration = Toast.LENGTH_SHORT;
//                                        Toast toast = Toast.makeText(_context, toastText, duration);
//                                        toast.show();
//
//                                        Timber.d("acceptEntry false, s length = " + s.length());
//                                        Timber.d("s.length() - 1: " + s.charAt(s.length() - 2));
//                                        Timber.d("s.length(): " + s.charAt(s.length() - 1));
//
//
//                                        s.delete(s.length() - 1, s.length());
////                                        s.replace(s.length() - 1, s.length(), "");
////                                        Timber.d("s = " + s);
////                                        CharSequence trimmedSerial = s.delete(s.length() - 1, s.length());
////                                        CharSequence trimmedSerial = s.subSequence(0, s.length() - 1);
////                                        Timber.d("s = " + s + ", trimmed = " + trimmedSerial);
////                                        lensSerialEditText.setText(trimmedSerial);
////                                        lensSerialEditText.setText(s.delete(s.length() - 2, s.length() - 1));
//                                    }
//
//                                    Timber.d("afterTextChanged: " + s);
//                                }
//                            };
////
//                            lensSerialEditText.addTextChangedListener(serialTextWatcher);

                            // custom onShowListener so we can do some checks before saving the lens. prevents "Save" button from automatically closing the dialog
                            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(final DialogInterface dialog) {
                                    Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                    Button negButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                                    Button neutralButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);

                                    // perform some housekeeping before closing the dialog. Specifically, make sure the lens serial/note is not more than 14 chars long
                                    posButton.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
//                                            String fLength1 = lensFLength1.getText().toString().trim();                                             // get the first focal length string
//                                            String fLength2 = (isPrime(typeTitle) ? fLength1 : lensFLength2.getText().toString().trim());           // second focal length (if it's a zoom)
                                            String newSerial = lensSerialEditText.getText().toString().trim();                                         // serial of the lens
                                            String newNote = lensNoteEditText.getText().toString().trim();

                                            // get the (potentially new) my list assignments for the lens
                                            boolean myListA = myListACheckBox.isChecked();
                                            boolean myListB = myListBCheckBox.isChecked();
                                            boolean myListC = myListCCheckBox.isChecked();

                                            // returns true if the lens serial + note is 14 chars or less
//                                            boolean readyToSave = checkSerialLength(typeTitle, fLength1, fLength2, serial);
                                            boolean readyToSave = checkSerialLength(childText, newSerial, newNote);

                                            if (readyToSave) {
                                                boolean editSuccessful = editLens(childObject, childText, newSerial, newNote, myListA, myListB, myListC);
                                                if (editSuccessful) {
                                                    dialog.dismiss();
                                                }
                                                else {
                                                    CharSequence toastText = "Error updating lens. Please try again.";
                                                    int duration = Toast.LENGTH_LONG;

                                                    Toast toast = Toast.makeText(_context, toastText, duration);
                                                    toast.show();
                                                }
                                            }
                                            else {
                                                CharSequence toastText = "Error: Lens name too long.";
                                                int duration = Toast.LENGTH_LONG;

                                                Toast toast = Toast.makeText(_context, toastText, duration);
                                                toast.show();
                                            }
                                            // get the tag from the hidden textView. This is the index of the lens in the overall lensArray variable that we actually need to update
//                                            int lensInd = Integer.parseInt(lensIndexTextView.getText().toString());

                                            // if everything is OK, dismiss dialog
//                                            if (readyToSave == true) {
//                                                dialog.dismiss();
//                                                Timber.d("Edit this lens. New attributes for lens index " + String.valueOf(lensInd) + ": ");
//                                                boolean editSuccessful = editLens(lensInd, childPosition, manufTitle, typeTitle, fLength1, fLength2, serial, myListA, myListB, myListC);
//                                            }

                                        }
                                    });
                                }
                            });

                            dialog.show();
                        }
                    });
                }
            });

            return convertView;
        }

        @Override
        public int getChildrenCount(int groupPosition)
        {
            Timber.d("getChildrenCount (" + groupPosition + ")");
            Timber.d(this._listDataChildIndices.toString());
            try {
                return this._listDataChildIndices.get(groupPosition).size();
            }
            catch (Exception e) {
                return 0;
            }
        }

        @Override
        public Object getGroup(int groupPosition)
        {
            return this._listDataHeader.get(groupPosition);
        }

        @Override
        public int getGroupCount()
        {
            return this._listDataHeader.size();
        }

        @Override
        public long getGroupId(int groupPosition)
        {
            return groupPosition;
        }

        // this method is called when creating the views for the lens types (Optimo, Cinema Prime, etc).
        // We attach a "+" button (ImageView really) to each header so the user can add a lens in that series.
        // The alert dialog builder in the setOnClickListener function takes care of this.
        @Override
        public View getGroupView(final int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            final String manufTitle = this._manufName;                                                                                      // the name of the lens manufacturer
            final String typeTitle = (String) getGroup(groupPosition);                                                                      // the series of the lens (Optimo, Cinema Prime, etc)

            // inflate the view to be shown
            if (convertView == null) {
                LayoutInflater headerInflater = (LayoutInflater) this._context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = headerInflater.inflate(R.layout.lens_list_type, null);
            }

            // initialize the view components
            ImageView typeImageView = (ImageView) convertView.findViewById(R.id.lensTypeImageView);
            TextView typeTextView = (TextView) convertView.findViewById(R.id.lensListType);
            ImageView addImageView = (ImageView) convertView.findViewById(R.id.lensTypeAddImageView);

            // set the lens type in the textView
            typeTextView.setText(typeTitle);

            int childCount = getChildrenCount(groupPosition);
            Timber.d("childCount: " + childCount);

            if (childCount == 0) {
                typeTextView.setTextColor(0xFFBBBBBB);
                typeImageView.setImageResource(R.drawable.ic_expand_more_empty_24dp);
            }
            else {
                typeTextView.setTextColor(0xFFFFFFFF);
                // depending on the isExpanded state of the group (and if there are > 0 children in the group), display the appropriate up/down chevron icon
                typeImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);
            }

            addImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // inflate the layout from dialog_add_lens.xml
                            LayoutInflater dialogInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            final View addLensView = dialogInflater.inflate(R.layout.dialog_add_lens, null);

                            // initialize the UI components
                            TextView lensManufTextView = (TextView) addLensView.findViewById(R.id.lensManufTextView);
                            TextView lensSeriesTextView = (TextView) addLensView.findViewById(R.id.lensSeriesTextView);
                            final TextView lensDashTextView = (TextView) addLensView.findViewById(R.id.LensFocalDashTextView);

                            // initialize the UI components so we can access their contents when the user presses "Save"
                            final EditText lensFLength1 = (EditText) addLensView.findViewById(R.id.LensFocal1EditText);
                            final EditText lensFLength2 = (EditText) addLensView.findViewById(R.id.LensFocal2EditText);
                            final EditText lensSerialEditText = (EditText) addLensView.findViewById(R.id.LensSerialEditText);
                            final EditText lensNoteEditText = (EditText) addLensView.findViewById(R.id.LensNoteEditText);

                            final CheckBox myListACheckBox = (CheckBox) addLensView.findViewById(R.id.MyListACheckBox);
                            final CheckBox myListBCheckBox = (CheckBox) addLensView.findViewById(R.id.MyListBCheckBox);
                            final CheckBox myListCCheckBox = (CheckBox) addLensView.findViewById(R.id.MyListCCheckBox);

                            lensManufTextView.setText(manufTitle);
                            lensSeriesTextView.setText(typeTitle);

                            // check if the lens type is a Prime or Zoom (or Other) and show/hide Zoom/Prime toggle button accordingly
                            isPrime = isPrime(typeTitle);
                            togglePrimeOrZoom(isPrime, lensDashTextView, lensFLength2);

                            final AlertDialog dialog = new AlertDialog.Builder(_context)
                                    .setView(addLensView)
                                    .setPositiveButton("Save", null)
                                    .setNegativeButton("Cancel", null)
                                    .setNeutralButton("Zoom", null)
                                    .setCancelable(true)
                                    .create();

                            dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                                @Override
                                public void onShow(final DialogInterface dialog) {
                                    Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                    final Button modeButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);

                                    if (typeTitle.trim().equals("Other")) {
                                        modeButton.setVisibility(View.VISIBLE);
                                    }
                                    else {
                                        modeButton.setVisibility(View.GONE);
                                    }

                                    posButton.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            int fLength1 = Integer.parseInt(lensFLength1.getText().toString().trim());
                                            int fLength2 = Integer.parseInt((isPrime ? "0" : lensFLength2.getText().toString().trim()));
                                            String serial = lensSerialEditText.getText().toString().trim();
                                            String note = lensNoteEditText.getText().toString().trim();

                                            String completeFocalString = constructFocalLengthString(fLength1, fLength2);

                                            boolean readyToSave = checkSerialLength(completeFocalString, serial, note);

                                            if (readyToSave) {
                                                saveNewLens(manufTitle, typeTitle, fLength1, fLength2, serial, note);
                                                dialog.dismiss();
                                            }
                                            else {
                                                CharSequence toastText = "Error: Lens name too long.";
                                                int duration = Toast.LENGTH_SHORT;

                                                Toast toast = Toast.makeText(_context, toastText, duration);
                                                toast.show();
                                            }
                                        }
                                    });

                                    modeButton.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
                                            if (isPrime) {
                                                isPrime = false;
                                                modeButton.setText("Prime");
                                            }
                                            else {
                                                isPrime = true;
                                                modeButton.setText("Zoom");
                                            }
                                            togglePrimeOrZoom(isPrime, lensDashTextView, lensFLength2);
                                        }
                                    });
                                }
                            });

                            dialog.show();
                        }
                    });
                }
            });

            return convertView;
        }

        @Override
        public boolean hasStableIds() {
            // TODO Auto-generated method stub
            return true;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            // TODO Auto-generated method stub
            return true;
        }

//        public void setNewItems(List<String> listDataHeader, HashMap<String, List<String>> listChildData) {
//            this._listDataHeader = listDataHeader;
//            this._listDataChild = listChildData;
//            notifyDataSetChanged();
//        }

        private void togglePrimeOrZoom(boolean prime, TextView dash, EditText serial2) {
            if (prime) {
                dash.setVisibility(View.GONE);
                serial2.setVisibility(View.GONE);
            }
            else {
                dash.setVisibility(View.VISIBLE);
                serial2.setVisibility(View.VISIBLE);
            }
        }

    }
}

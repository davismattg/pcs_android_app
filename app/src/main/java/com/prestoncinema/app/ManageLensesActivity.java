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
import android.text.InputType;
import android.util.Log;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
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

    private List<String> lensManufHeader = new ArrayList<String>();
    private HashMap<String, List<String>> lensTypeHeader = new HashMap<>();
    private HashMap<String, List<String>> lensNameHeader = new HashMap<>();
    private HashMap<String, List<String>> lensSerialHeader = new HashMap<>();
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
    private List<Map<String, String>> lensMap = new ArrayList<Map<String, String>>();
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
//            listDataHeaderCount.put(i == 6 ? "F" : String.valueOf(i), 0);
//            listDataHeaderCount.put(i == 6 ? 15 : i, 0);
        }

        lensManufHeader = Arrays.asList(getResources().getStringArray(R.array.lens_manuf_array));
        lensTypeHeader = populateLensTypeHeader(lensManufHeader);
        lensNameHeader = populateLensNameHeader(lensTypeHeader);
        lensSerialHeader = populateLensNameHeader(lensTypeHeader);
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

        // get the filename string from the previous activity (LensActivity)
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            lensFileString = extras.getString("lensFile");
            lensFileStringStripped = lensFileString.split("/")[lensFileString.split("/").length - 1];
//            mLensFileTextView.setText(mLensFileTextView.getText() + lensFileStringStripped);
            setTitle(lensFileStringStripped);

//            Timber.d("lensFileString: " + lensFileString);
            lensFile = new File(lensFileString);
//            Timber.d("lensFile: " + lensFile);
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
    @Override
    public void onBackPressed() {
//        super.onBackPressed();
        if (changeDetected == true) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    new AlertDialog.Builder(ManageLensesActivity.this)
                            .setMessage("Would you like to save your changes?")
                            .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    saveLensFile(lensFileString, false);
                                }
                            })
                            .setNeutralButton("Save As...", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    saveLensFileAs();
                                }
                            })
                            .setNegativeButton("Discard", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(ManageLensesActivity.this, LensActivity.class);
                                    startActivity(intent);
                                }
                            })
                            .setCancelable(true)
                            .show();
                }
            });
        }
        else {
            super.onBackPressed();
        }
    }

    // the menu that's created when the user long-presses on a lens within the lens list
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;

        // check if lens is already part of a list, if so, hide appropriate menu items
//        int pos = ((AdapterView.AdapterContextMenuInfo) menuInfo).position;
//        List<Integer> hideItems = checkMenuItems(pos);
//
        super.onCreateContextMenu(menu, v, menuInfo);

        int lensIndex = (int) v.getTag();

        MenuInflater inflater = getMenuInflater();
//        inflater.inflate(R.layout.dialog_edit_lens, menu);
        inflater.inflate(R.menu.lens_context_menu, menu);

        menu.setHeaderTitle("Index: " + v.getTag().toString());
//
//        for (int id : hideItems) {
//            menu.findItem(id).setVisible(false);
//        }

    }

    // handle the user's item selection
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int id = (int) info.id;
        changeDetected = true;
//        setTitle(getTitle() + "*");
//        mLensFileTextView.setText(mLensFileTextView.getText() + "*");
        switch (item.getItemId()) {
            case R.id.renameLens:
//                renameLens(id);
                return true;
            case R.id.addToMyListA:
                lensListAssign("A", id, true);
                return true;
            case R.id.removeFromMyListA:
                lensListAssign("A", id, false);
                return true;
            case R.id.addToMyListB:
                lensListAssign("B", id, true);
                return true;
            case R.id.removeFromMyListB:
                lensListAssign("B", id, false);
                return true;
            case R.id.addToMyListC:
                lensListAssign("C", id, true);
                return true;
            case R.id.removeFromMyListC:
                lensListAssign("C", id, false);
                return true;
            case R.id.deleteLens:
                Timber.d("Delete this lens");
                confirmLensDelete(info);
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

        return true;
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
//                        String newName;

//                        // check if they erroneously included ".lens" in their entry, and if so, don't append ".lens"
//                        if (enteredName.indexOf(".lens") != -1) {
//                            newName = enteredName.trim();
//                        }
//                        else {
//                            newName = enteredName.trim() + ".lens";
//                        }
                        // TODO: make sure the filename check is working robustly
                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {
                            // rename the file
                            boolean wasFileRenamed = file.renameTo(getLensStorageDir(newName));                                     // rename the old file
                            if (wasFileRenamed) {                                                                                       // file.renameTo() returned true
                                setTitle(newName);                                                                                  // update the title of the activity w/ new file name
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
        Timber.d("lensString: " + lensArray.get(ind) + "$$");
        Timber.d("lensString bytes: " + Arrays.toString(lensArray.get(ind).getBytes()));
        Timber.d("lensString length: " + lensArray.get(ind).length());
        List<Integer> menuItemsToHide = new ArrayList<Integer>();
        byte[] lens = lensArray.get(ind).getBytes();
        int byte1 = (int) lens[15];
        int byte2 = (int) lens[16];

        switch (byte1) {
            case 48:case 52:case 56:case 67:        // 0, 4, 8, C
                menuItemsToHide.add(R.id.removeFromMyListB);
                menuItemsToHide.add(R.id.removeFromMyListC);
                break;
            case 49:case 53:case 57:case 68:        // 1, 5, 9, D
                menuItemsToHide.add(R.id.removeFromMyListC);
                menuItemsToHide.add(R.id.addToMyListB);
                break;
            case 50:case 54:case 65:case 69:        // 2, 6, A, E
                menuItemsToHide.add(R.id.addToMyListC);
                menuItemsToHide.add(R.id.removeFromMyListB);
                break;
            case 51:case 55:case 66:case 70:        // 3, 7, B, F
                menuItemsToHide.add(R.id.addToMyListB);
                menuItemsToHide.add(R.id.addToMyListC);
                break;
            default:
                break;
        }

        if (byte2 >= 56) {
            menuItemsToHide.add(R.id.addToMyListA);
        }
        else {
            menuItemsToHide.add(R.id.removeFromMyListA);
        }

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
    // TODO: "Other" could be prime or zoom. Need to give user ability to select
    private boolean isPrime(String type) {
        switch (type) {
            // all the lens types that are zoom
            case "Optimo":case "Rouge":case "HR":case "Cinema Zoom":case "Zoom":case "Premier Zoom":case "Alura Zoom":case "Primo Zoom":case "Anam. Zoom":
                Timber.d("Zoom lens detected, switch to zoom lens FL mode");
                return false;
//                mLensFocal2EditText.setVisibility(View.VISIBLE);
//                mLensFocalDashTextView.setVisibility(View.VISIBLE);
//                break;
            default:            // a prime lens detected
                Timber.d("Prime lens detected, switch to prime lens FL mode");
                return true;
//                mLensFocal2EditText.setVisibility(View.GONE);
//                mLensFocalDashTextView.setVisibility(View.GONE);
//                break;
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
//                    Timber.d("Reading lens file: " + line);                   // one lens at a time
                    lensArray.add(line);                                    // add the read lens into the array
//                    lensMap.add(parseLensLine(line));                       // format the raw data for easy display in lens list
                }
            }
            if (lensArray.size() > 0) {                                     // make sure something was actually imported
                lensFileLoaded = true;                                      // set the flag
                numLenses = lensArray.size();                               // the number of lenses, used for loops and display on the UI
                currentLens = 0;                                            // index mostly used for looping

                //lensDisplayArray = sortLensArray(lensArray, "manufacturer", "asc");

//                sortLensArray(lensArray, "manufacturer", "asc");

                for (int i=0; i < lensArray.size(); i++) {
                    String len = lensArray.get(i);
                    countLensLine(len);
                    lensMap.add(parseLensLine(len, i));
                }
            }

            Timber.d("lensArray loaded successfully. NumLenses: " + numLenses);

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
//        return new_arr;

    }

    /////////////////////////////////////////////////////////////////////////////////////////////////
    // This function takes in the raw string from the lens file and formats it in the way we want  //
    // to display it in the UI. Check the HU3 document/ask Mirko for the data structure            //
    /////////////////////////////////////////////////////////////////////////////////////////////////
    private Map<String, String> parseLensLine(String line, int index) {
        Map<String, String> lMap = new HashMap<String, String>(2);          // map of Key/Value pairs, with both key and value being strings
        Map<String, String> lMapNew = new HashMap<String, String>(2);

//        Timber.d("parsingLensLine at index " + index);

        byte[] bytes = line.getBytes();                                 // it's easier to work with the actual hex values

        byte[] lensNameBytes = Arrays.copyOfRange(bytes, 1, 15);        // select the first 14 bytes of the file. this is the lens focal length and serial/note
        String lensNameAndSerial = convertLensName(lensNameBytes);      // get the index within the first 14 bytes where the lens name (focal length and serial) ends
        String lensName;
        String serial;
        String[] lensNameSplit = lensNameAndSerial.split(" ");          // separate the lens focal length from serial number/note since it's separated by a space
        lensName = lensNameSplit[0];                                    // the name is the part before the space
        if (lensNameSplit.length > 1) {                                 // length > 1 if there was a serial/note present, otherwise length = 1 (just the focal length)
            serial = lensNameAndSerial.split(" ")[lensNameAndSerial.split(" ").length - 1];     // the serial is the second part of the split array
        }
        else {
            serial = "";            // user didn't enter any serial
        }

        byte[] status1 = Arrays.copyOfRange(bytes, 15, 17);         // bytes 15 and 16 (ASCII bytes) are the first (hex) status byte
        byte[] status2 = Arrays.copyOfRange(bytes, 17, 19);         // bytes 17 and 18 (ASCII bytes) are the second (hex) status byte

        String lensStatus = convertLensStatus(status1);             // get the Calibrated, mylist, etc status for the given status byte

        String lName = convertManufName(status2);                   // get the lens manuf and lens type from the second status byte

        String manufName = lName.split(" - ")[0];
        String manufType = lName.split(" - ")[1];

        // populate the map that the UI will pull from later
        lMap.put("manufString", manufName);
        lMap.put("serialString", serial);
        lMap.put("flString", lensName);
        lMap.put("statusString", lensStatus);

        int manufInd = listDataHeader.indexOf(manufName);
        String manufIndString = String.valueOf(manufInd);

        int arrayId;

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

        List<String> typeArray = Arrays.asList(getResources().getStringArray(arrayId));

        int typeInd = typeArray.indexOf(manufType);
        String typeIndString = String.valueOf(typeInd);

        lMapNew.put("headerPosition", manufIndString);
        lMapNew.put("typePosition", typeIndString);
        lMapNew.put("flString", lensName);
        lMapNew.put("serialString", serial);
        lMapNew.put("statusString", lensStatus);

        // populating the various maps that will be used to display the children in the expListView
        // focal lengths
        List<String> theseLenses = new ArrayList<String>(lensNameHeader.get(lName));
        theseLenses.add(lensName);
        lensNameHeader.put(lName, theseLenses);

        // serial/notes
        List<String> theseLensSerials = new ArrayList<String>(lensSerialHeader.get(lName));
        theseLensSerials.add(serial);
        lensSerialHeader.put(lName, theseLensSerials);

        // lens status (calibrated, myList, etc)
        List<String> theseLensStatuses = new ArrayList<String>(lensStatusHeader.get(lName));
        theseLensStatuses.add(lensStatus);
        lensStatusHeader.put(lName, theseLensStatuses);

        // index of each lens in the overall array. needed b/c expandableListView indices don't match
        List<Integer> theseLensIndices = new ArrayList<Integer>(lensNameIndex.get(lName));
        theseLensIndices.add(index);
        lensNameIndex.put(lName, theseLensIndices);

        return lMapNew;
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

    // use the hex characters to parse the manufacturer and lens type
    private String convertManufName(byte[] status) {
//        Timber.d("manufNameBytes: " + Arrays.toString(status));
        String manufName = "";
        String manufType = "";

        switch (status[0]) {
            case 48:
                manufName = "Angenieux";
                switch (status[1]) {
                    case 48:
                        manufType = "Optimo";
                        break;
                    case 49:
                        manufType = "Rouge";
                        break;
                    case 50:
                        manufType = "HR";
                        break;
                    case 51:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            case 49:
                manufName = "Canon";
                switch (status[1]) {
                    case 48:
                        manufType = "Cinema Prime";
                        break;
                    case 49:
                        manufType = "Cinema Zoom";
                        break;
                    case 50:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            case 50:
                manufName = "Cooke";
                switch (status[1]) {
                    case 48:
                        manufType = "S4";
                        break;
                    case 49:
                        manufType = "S5";
                        break;
                    case 50:
                        manufType = "Panchro";
                        break;
                    case 51:
                        manufType = "Zoom";
                        break;
                    case 52:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            case 51:
                manufName = "Fujinon";
                switch (status[1]) {
                    case 48:
                        manufType = "Premier Zoom";
                        break;
                    case 49:
                        manufType = "Alura Zoom";
                        break;
                    case 50:
                        manufType = "Prime";
                        break;
                    case 51:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            case 52:
                manufName = "Leica";
                switch (status[1]) {
                    case 48:
                        manufType = "Summilux Prime";
                        break;
                    case 49:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            case 53:
                manufName = "Panavision";
                switch (status[1]) {
                    case 48:
                        manufType = "Primo Prime";
                        break;
                    case 49:
                        manufType = "Primo Zoom";
                        break;
                    case 50:
                        manufType = "Anam. Prime";
                        break;
                    case 51:
                        manufType = "Anam. Zoom";
                        break;
                    case 52:
                        manufType = "P70 Prime";
                        break;
                    case 53:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            case 54:
                manufName = "Zeiss";
                switch (status[1]) {
                    case 48:
                        manufType = "Master Prime";
                        break;
                    case 49:
                        manufType = "Ultra Prime";
                        break;
                    case 50:
                        manufType = "Compact Prime";
                        break;
                    case 51:
                        manufType = "Zoom";
                        break;
                    case 52:
                        manufType = "Other";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
            default:
                manufName = "Other";
                switch (status[1]) {
                    case 48:
                        manufType = "Prime";
                        break;
                    case 49:
                        manufType = "Zoom";
                        break;
                    default:
                        manufType = "";
                        break;
                }
                break;
        }

        if (manufType.length() > 0) {
            return manufName + " - " + manufType;
//            return manufType;
        }
        else {
            return manufName;
        }
    }

    // use the hex characters to parse the lens calibration status and if it's a member of any lists
    // just follow mirko's lens data structure //
    private String convertLensStatus(byte[] bytes) {
//        Timber.d("convertLensStatus: " + Arrays.toString(bytes));
        boolean FCal = false;
        boolean ICal = false;
        boolean ZCal = false;
        boolean myListA = false;
        boolean myListB = false;
        boolean myListC = false;
        String calString = "Cal:";
        String listString = "My List:";

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

        // adding to the string to display in the UI. This is kinda crude and there's def a better way
        if (FCal) {
            calString += " F";
        }

        if (ICal) {
            calString += " I";
        }

        if (ZCal) {
            calString += " Z";
        }

        if (!FCal && !ICal && !ZCal) {
//            calString += " None";
            calString = "";
        }

        if (myListA) {
            listString += " A";
        }

        if (myListB) {
            listString += " B";
        }

        if (myListC) {
            listString += " C";
        }

        if (!myListA && !myListB && !myListC) {
//            listString += " None";
            listString = "";
        }

        if (calString.length() == 0) {
            return listString;
        }
        else if (listString.length() == 0) {
            return calString;
        }
        else {
            return calString + "\n" + listString;
        }
    }

//    // function to rename the lens. NOT WORKING YET TODO: get rename lens working
//    private void renameLens(int id) {
//        Timber.d("renaming lens at index " + id);
//        final String manufString = lensMap.get(id).get("manufString");
//        final String serialString = lensMap.get(id).get("serialString");
//        final String flString = lensMap.get(id).get("flString");
//
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                final EditText input = new EditText(ManageLensesActivity.this);
//                input.setSelectAllOnFocus(true);
//                input.setInputType(InputType.TYPE_CLASS_TEXT);
//
//                new AlertDialog.Builder(ManageLensesActivity.this)
//                        .setMessage("Edit serial/note for " + manufString + " " + flString)
//                        .setView(input)
//                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                // TODO: add logic for renaming lens
//                                Timber.d("new lens note: " + input.getText());
//                            }
//                        })
//                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                            }
//                        })
//                        .setCancelable(false)
//                        .show();
//            }
//        });
//    }

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
//        lensMap.set(id, parseLensLine(lineString));

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
                    Timber.d("File saved successfully");
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
    private boolean saveNewLens(String manufName, String lensType, String focal1, String focal2, String serial, boolean myListA, boolean myListB, boolean myListC) {
        Timber.d("save the lens");

//        changeDetected = true;
        Timber.d("Lens info:\n" + "Manuf: " + manufName + ", type: " + lensType + "\nFocal1: " + focal1 + ", Focal2: " + focal2 + "\nSerial: " + serial + "\nMyList: " + myListA + ", " + myListB + ", " + myListC);
        buildLensData(manufName, lensType, focal1, focal2, serial, myListA, myListB, myListC);
        return true;
    }

    // function to do the heavy lifting of creating the hex characters from the user's selections
    private void buildLensData(String manuf, String lensType, String focal1, String focal2, String serial, boolean myListA, boolean myListB, boolean myListC) {
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

        // look @ the focal lengths to determine if prime or zoom lens, and format the string appropriately (should always be 14 bytes long)
        if (focal1.trim().equals(focal2.trim())) {
            Timber.d("prime lens detected by focal lengths");
            lensName = String.format("%-14s", focal1 + "mm " + serial);
        }
        else {
            Timber.d("zoom lens detected by focal lengths");
            statByte1 += 1;
            lensName = String.format("%-14s", focal1 + "-" + focal2 + "mm " + serial);
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
        lensFocal1Str = String.format("%4s", Integer.toHexString(Integer.parseInt(focal1)).toUpperCase()).replaceAll(" ", "0");
        lensFocal2Str = String.format("%4s", Integer.toHexString(Integer.parseInt(focal2)).toUpperCase()).replaceAll(" ", "0");

        if (serial.length() > 0) {
            lensSerialStr = String.format("%4s", Integer.toHexString(Integer.parseInt(serial)).toUpperCase()).replaceAll(" ", "0");
        }
        else {
            lensSerialStr = "0000"; //String.format("%4s", Integer.toHexString(0).toUpperCase()).replaceAll(" ", "0");
        }
        String toPad = lensName + lensStatus1 + lensStatus2 + lensFocal1Str + lensFocal2Str + lensSerialStr;
        String padded = STXStr + toPad + new String(new char[width - toPad.length()]).replace('\0', fill);

        Timber.d("lensString length: " + padded.length());
        Timber.d("lensString bytes: " + Arrays.toString(padded.getBytes()));
        Timber.d("lensString: " + padded + "$$");

        int index = getLensIndex(padded);

        lensArray.add(padded);
        lensMap.add(parseLensLine(padded, index));

        updateLensList();
    }
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    // function to edit an existing lens after user changes the serial or mylist assignment in the edit dialog
    //
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////
    private boolean editLens(int lensInd, int childPosition, String manufTitle, String typeTitle, String focal1, String focal2, String serial, boolean myListA, boolean myListB, boolean myListC) {
        Timber.d("///////////////////////////////////////////////////////////////");
        Timber.d("editLens - params: ");
        Timber.d("lensInd: " + lensInd);
        Timber.d("childPosition: " + childPosition);
        Timber.d("manufTitle: " + manufTitle);
        Timber.d("typeTitle: " + typeTitle);
        Timber.d("focal1: " + focal1);
        Timber.d("focal2: " + focal2);
        Timber.d("serial: " + serial);
        Timber.d("myListA: " + myListA);
        Timber.d("myListB: " + myListB);
        Timber.d("myListC: " + myListC);
        Timber.d("///////////////////////////////////////////////////////////////");
//        changeDetected = true;

        String prevLensString = lensArray.get(lensInd);
        String toKeep = prevLensString.substring(17);

        String editString = prevLensString.substring(0, 17);
        String status0 = String.valueOf(editString.charAt(15));
        String status1 = String.valueOf(editString.charAt(16));

        int statByte0 = Integer.parseInt(status0, 16);
        int statByte1 = Integer.parseInt(status1, 16);
        String lensName;
        String lensFocalLengthHeader;
        String lensStatus1;

        boolean isMyListC = isBitSet(statByte0, 0x2);
        boolean isMyListB = isBitSet(statByte0, 0x1);
        boolean isMyListA = isBitSet(statByte1, 0x8);

        // look @ the focal lengths to determine if prime or zoom lens, and format the string appropriately (should always be 14 bytes long)
        if (focal1.trim().equals(focal2.trim())) {
            lensName = String.format("%-14s", focal1 + "mm " + serial.trim());
            lensFocalLengthHeader = focal1 + "mm";
//            lensNameAndSerial = lensName + " " + serial.trim();
        }
        else {
            lensName = String.format("%-14s", focal1 + "-" + focal2 + "mm " + serial.trim());
            lensFocalLengthHeader = focal1 + "-" + focal2 + "mm";
//            lensNameAndSerial = lensName + " " + serial.trim();
        }

        // update the status bytes according to the my list assignments
        // myListA
        if (myListA != isMyListA) {                 // setting changed by user, so update
            if (isMyListA) {                        // was in my list A, but user removed it
               statByte1 -= 0x8;                    // subtract 0x8 to remove from myList A
            }
            else {
                statByte1 += 0x8;                   // add 0x8 to add to myList A
            }
        }

        // myListB
        if (myListB != isMyListB) {                 // old/new settings don't match, so update
            if (isMyListB) {                        // if lens was in myList B
                statByte0 -= 0x1;                   // remove it
            }
            else {
                statByte0 += 0x1;                   // add 0x1 to add to myList B
            }
        }

        // myListC
        if (myListC != isMyListC) {                 // old/new settings don't match, so update
            if (isMyListC) {                        // lens used to be in myList C
                statByte0 -= 0x2;                   // remove it
            }
            else {
                statByte0 += 0x2;                   // add 0x2 to add to myList C
            }
        }

        if (statByte0 == 10) {
            statByte0 = 0xA;
        }

        if (statByte0 == 11) {
            statByte0 = 0xB;
        }

        String newLensName = STXStr + lensName;

        // convert to the hex characters that will be written in the file. these strings all need to
        // be constant length no matter how many characters are inside, so you have to pad with 0's if necessary
        lensStatus1 = Integer.toHexString(statByte0).toUpperCase() + Integer.toHexString(statByte1).toUpperCase();

        String newString = newLensName + lensStatus1 + toKeep;
        lensArray.set(lensInd, newString);

        byte[] bytes = newString.getBytes();                                 // it's easier to work with the actual hex values
        byte[] statusByte1 = Arrays.copyOfRange(bytes, 15, 17);         // bytes 15 and 16 (ASCII bytes) are the first (hex) status byte
        byte[] statusByte2 = Arrays.copyOfRange(bytes, 17, 19);         // bytes 17 and 18 (ASCII bytes) are the second (hex) status byte
        byte[] lensNameBytes = Arrays.copyOfRange(bytes, 1, 15);        // select the first 14 bytes of the file. this is the lens focal length and serial/note

        String lensStatus = convertLensStatus(statusByte1);             // get the Calibrated, mylist, etc status for the given status byte
        String lName = convertManufName(statusByte2);                   // get the lens manuf and lens type from the second status byte
//        String lensNameAndSerial = convertLensName(lensNameBytes);      // get the index within the first 14 bytes where the lens name (focal length and serial) ends

        List<String> theseLenses = lensNameHeader.get(lName);           // get the current group of lenses (their focal lengths)
        theseLenses.set(childPosition, lensFocalLengthHeader);                       // add the new/edited one at the correct index
        lensNameHeader.put(lName, theseLenses);                         // update the header with the new changes

        Timber.d("lensNameHeader: " + lensNameHeader.get(lName).toString());
        List<String> theseLensSerials = lensSerialHeader.get(lName);    // get the lens serial numbers
        theseLensSerials.set(childPosition, serial);
        lensSerialHeader.put(lName, theseLensSerials);
        Timber.d("lensSerialHeader: " + lensSerialHeader.get(lName).toString());

        List<String> theseLensStatuses = lensStatusHeader.get(lName);
        theseLensStatuses.set(childPosition, lensStatus);
        lensStatusHeader.put(lName, theseLensStatuses);

        updateLensList();

        return true;
    }

    public boolean isBitSet(int val, int bitNumber) {
//        int val = Integer.valueOf(hexValue, 16);
        return (val & bitNumber) == bitNumber;
//        return (val & (1 << bitNumber)) != 0;
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


//            Timber.d("manuf comparison (w/ " + man + "): " + manufCompare);
//            Timber.d("type comparison (w/ " + typ + "): " + typeCompare);
//            Timber.d("overall comparison --------------------------------------------------------------: " + (manufCompare && typeCompare));
        }

        Timber.d("Insert lens at index: " + maxEntry.getKey());
        Timber.d("lens right before: " + lensArray.get(maxEntry.getKey()));

        return (maxEntry.getKey() + 1);
    }

    // TODO: get working to update adapter properly when lens is edited. Editing is working, but adapter isn't refreshing view elements
    private void updateLensList() {
        Timber.d("Updating lens list.");

        // get the new numLenses in case the user added a lens
        numLenses = lensArray.size();

        // save the lens file right away
        saveLensFile(lensFileString, false);

        // run the UI updates on the UI thread
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Context context = getApplicationContext();
                expAdapter.notifyDataSetChanged();                                                      // let the expandableListView custom adapter know we have changed data
//                if (!String.valueOf(getTitle().charAt(getTitle().length()-1)).equals("*")) {            // check if we've already added "*" to the filename
//                    setTitle(getTitle() + "*");                                                         // if not, add "*"
//                }

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

    private boolean checkSerialLength(String type, String fLength1, String fLength2, String serial) {
        String completeString;
        int completeStringLength;

        if (isPrime(type) == true) {
            completeString = fLength1 + "mm " + serial;
        }
        else {
            completeString = fLength1 + "-" + fLength2 + "mm " + serial;
        }

        Timber.d("completeString: " + completeString);

        completeStringLength = completeString.length();
        Timber.d("length: " + completeStringLength);

        if (completeStringLength > maxSerialLength) {
            return false;
        }
        else {
            return true;
        }
    }

//    private int getNumLenses(int groupPosition) {
    private void countLensLine(String lens) {

//        ArrayList<String> sub_arr = new ArrayList<String>(lensArray.size());                       // initialize the ArrayList that will store the truncated strings
//        ArrayList<String> new_arr = new ArrayList<String>(lensArray.size());                    // initialize the ArrayList that will store the rearranged array
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

//        switch(subLensString) {
//            case "0":
//                counter = listDataHeaderCount.get("Angenieux");
//                counter += 1;
//                listDataHeaderCount.put("Angenieux", counter);
//                break;
//            case "1":
//
//        }
//        String[] manufNames = getResources().getStringArray(R.array.lens_manuf_byte_array);
//        String manufByteString = manufNames[groupPosition].trim();
//
//        for (int i=0; i < lensArray.size(); i++) {
//            String lookAt = lensArray.get(i).substring(sub_ind, sub_ind + 1).trim();
//            if (lookAt.equals(manufByteString)) {
//                counter += 1;
//            }
//        }
//        return counter;
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

    class MapComparator implements Comparator<Map<String, String>> {
        private final String key;
        private final String seriesKey;// = "manufString";

        public MapComparator(String key, String seriesKey){
            this.key = key;
            this.seriesKey = seriesKey; //"manufString";
        }

        public int compare(Map<String, String> first,
                           Map<String, String> second){
            // TODO: Null checking, both for maps and values
//            String firstValue = first.get(key);
//            String secondValue = second.get(key);
//            return firstValue.compareTo(secondValue);
            String firstManuf = first.get(seriesKey);
            String secondManuf = second.get(seriesKey);
            String firstFl = first.get(key);
            String secondFl = second.get(key);
            int sameSeries = firstManuf.compareTo(secondManuf);
//            int focalCompare = firstFl.compareTo(secondFl);
//            Timber.d("Comparing: " + firstManuf + " (" + firstFl + ") & " + secondManuf + " (" + secondFl + ") = " + sameSeries + ", " + focalCompare);
            return sameSeries;
        }
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
            String manuf = _listDataHeader.get(groupPosition);

            // initialize the maps that will be passed to the child views
            HashMap<String, List<String>> lensChildren = new HashMap<>();
            HashMap<String, List<String>> lensChildrenSerial = new HashMap<>();
            HashMap<String, List<String>> lensChildrenStatus = new HashMap<>();
            HashMap<String, List<Integer>> lensChildrenIndices = new HashMap<>();

            // populate each child map
            for (String type : lensTypeList) {
                String manufNameAndType = manuf + " - " + type;
                lensChildren.put(type, lensNameHeader.get(manufNameAndType));
                lensChildrenSerial.put(type, lensSerialHeader.get(manufNameAndType));
                lensChildrenStatus.put(type, lensStatusHeader.get(manufNameAndType));
                lensChildrenIndices.put(type, lensNameIndex.get(manufNameAndType));
            }

            List<String> thisLensType = new ArrayList<>(Arrays.asList(lensTypeList.get(childPosition)));

            LensSecondLevel lensSecondLevel = new LensSecondLevel(ManageLensesActivity.this);
            lensSecondLevel.setAdapter(new SecondLevelListViewAdapter(ManageLensesActivity.this, thisLensType, lensChildren, lensChildrenSerial, lensChildrenStatus, lensChildrenIndices, (String) getGroup(groupPosition)));
            lensSecondLevel.setGroupIndicator(null);
            return lensSecondLevel;
        }

        @Override
        public int getChildrenCount(int groupPosition)
        {
            return this._listDataChild.get(this._listDataHeader.get(groupPosition)).size();
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
        int intGroupPosition, intChildPosition, intGroupid;

        public LensSecondLevel(Context context)
        {
            super(context);
        }

        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
        {
            widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
            heightMeasureSpec = MeasureSpec.makeMeasureSpec(Integer.MAX_VALUE, MeasureSpec.AT_MOST);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    public class SecondLevelListViewAdapter extends BaseExpandableListAdapter
    {
        private Context _context;
        private List<String> _listDataHeader;                                                           // header titles (lens types, per manufacturer)
        private HashMap<String, List<String>> _listDataChild;                                           // child data in format of header title, child title (list of lenses within each type)
        private HashMap<String, List<String>> _listDataChildSerial;                                     // child data in format of heaer title, child title (list of serial numbers by lens)
        private HashMap<String, List<String>> _listDataChildStatus;                                     // status of each lens (myList, calibrated, etc)
        private HashMap<String, List<Integer>> _listDataChildIndex;                                     // index of each lens within the overall lensArray
        private String _manufName;                                                                      // manufacturer name

        public SecondLevelListViewAdapter(Context context, List<String> listDataHeader,
                                          HashMap<String, List<String>> listChildData,
                                          HashMap<String, List<String>> listDataChildSerial,
                                          HashMap<String, List<String>> listDataChildStatus,
                                          HashMap<String, List<Integer>> listDataChildIndex,
                                          String manufName) {
            this._context = context;
            this._listDataHeader = listDataHeader;
            this._listDataChild = listChildData;
            this._listDataChildSerial = listDataChildSerial;
            this._listDataChildStatus = listDataChildStatus;
            this._listDataChildIndex = listDataChildIndex;
            this._manufName = manufName;
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

        public Object getChildSerial(int groupPosition, int childPosition) {
            return this._listDataChildSerial.get(this._listDataHeader.get(groupPosition)).get(childPosition);
        }

        public Object getChildStatus(int groupPosition, int childPosition) {
            return this._listDataChildStatus.get(this._listDataHeader.get(groupPosition)).get(childPosition);
        }

        public Object getChildTag(int groupPosition, int childPosition) {
            return this._listDataChildIndex.get(this._listDataHeader.get(groupPosition)).get(childPosition);
        }

        @Override
        public View getChildView(int groupPosition, final int childPosition,
                                 boolean isLastChild, View convertView, ViewGroup parent)
        {
            // get the lens manufacturer and type strings. will be used if the user opens the edit lens dialog
            final String manufTitle = this._manufName;
            final String typeTitle = (String) getGroup(groupPosition);

            // get the strings that will be used to populate the lens row
            final String childText = (String) getChild(groupPosition, childPosition);                                                       // "24-290mm"
            final String childSerialText = (String) getChildSerial(groupPosition, childPosition);                                           // "111 ANA"
            final String childStatusText = (String) getChildStatus(groupPosition, childPosition);                                           // "Cal: F \n MyList: A"

            Timber.d("childText: " + childText);
            Timber.d("childSerialText: " + childSerialText);

            if (convertView == null) {
                LayoutInflater headerInflater = (LayoutInflater) this._context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                convertView = headerInflater.inflate(R.layout.lens_list_lens, null);                                                        // inflate the view used to display the lens
            }

            TextView lensView = (TextView) convertView.findViewById(R.id.lensListLensTextView);                                             // the textView that displays the lens focal length
            TextView serialView = (TextView) convertView.findViewById(R.id.lensListSerialTextView);                                         // the textView that displays the lens serial number
            TextView statusView = (TextView) convertView.findViewById(R.id.lensListStatusTextView);                                         // the textView that displays the lens status

            final ImageView editLensImageView = (ImageView) convertView.findViewById(R.id.editLensImageView);                               // the imageView used to contain the edit icon (pencil)

            lensView.setText(childText);                                                                                                    // set the focal length string text
            serialView.setText(childSerialText);                                                                                            // set the serial string text
            statusView.setText(childStatusText);                                                                                            // set the status string text

            editLensImageView.setTag(getChildTag(groupPosition, childPosition));                                                            // set the tag, which is the lens' index in the overall array (lensArray var)

            // onClickListener for when the user taps on the edit lens icon. This is used to inflate the dialog where the use can actually edit the lens.
            editLensImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            // logic to separate out the focal length strings, including the serial, so we can populate the dialog appropriately
                            String[] fLengthSplit = childText.split("mm");                                                                  // split on "mm"
                            String fLengthString = fLengthSplit[0];                                                                         // the string w/ just focal lenths. will be "24" if prime, or "24-290" if zoom
//                            int serialIndex = childText.indexOf("mm") + 2;                                                                // get the index of "mm" in the string. everything after this (+2) is considered the serial
//                            final String serialString = childText.substring(serialIndex).trim();
                            final String fLength1, fLength2;                                                                                // initialize the two FL strings to be used later

                            if (fLengthString.contains("-")) {                                                                              // check if it's a zoom lens
                                fLength1 = fLengthString.split("-")[0];                                                                     // if so, split the focal length string to get the wide focal length
                                fLength2 = fLengthString.split("-")[1];                                                                     // and the long focal length
                            }
                            else {                                                                                                          // lens is a prime, just use the first one
                                fLength1 = fLengthString;
                                fLength2 = "";
                            }

                            LayoutInflater dialogInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            final View editLensView = dialogInflater.inflate(R.layout.dialog_edit_lens, null);                               // inflate the view to use as the edit dialog

                            TextView lensManufTextView = (TextView) editLensView.findViewById(R.id.lensManufTextView);                       // textView to display the lens manufacturer name
                            TextView lensSeriesTextView = (TextView) editLensView.findViewById(R.id.lensSeriesTextView);                     // textView to display the lens series
                            TextView lensDashTextView = (TextView) editLensView.findViewById(R.id.LensFocalDashTextView);                    // textView to show the dash in between the two focal lengths if it's a zoom lens

                            // initialize the UI components so we can access their contents when the user presses "Save"
                            final TextView lensFLength1 = (TextView) editLensView.findViewById(R.id.LensFocal1TextView);
                            final TextView lensFLength2 = (TextView) editLensView.findViewById(R.id.LensFocal2TextView);
                            final EditText lensSerialEditText = (EditText) editLensView.findViewById(R.id.LensSerialEditText);
                            final CheckBox myListACheckBox = (CheckBox) editLensView.findViewById(R.id.MyListACheckBox);
                            final CheckBox myListBCheckBox = (CheckBox) editLensView.findViewById(R.id.MyListBCheckBox);
                            final CheckBox myListCCheckBox = (CheckBox) editLensView.findViewById(R.id.MyListCCheckBox);

                            // the hidden textView where we'll store the lens index (in the form of the view's tag)
                            final TextView lensIndexTextView = (TextView) editLensView.findViewById(R.id.lensIndexTextView);

                            // check the status string to see if the lens is part of a list
                            boolean myListA = false;
                            boolean myListB = false;
                            boolean myListC = false;
                            if (childStatusText.contains("My List")) {
                                String myLists = childStatusText.split("List:")[1];
                                myListA = myLists.contains("A");
                                myListB = myLists.contains("B");
                                myListC = myLists.contains("C");
                            }

//                            Timber.d("serialString: " + serialString);

                            // populate the text fields with existing values from the lens
                            lensManufTextView.setText(manufTitle);
                            lensSeriesTextView.setText(typeTitle);
                            lensSerialEditText.setText(childSerialText);
                            lensFLength1.setText(fLength1);
                            lensFLength2.setText(fLength2);

                            // erroneous housekeeping to make the serial editText user-friendly
                            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);       // get the keyboard display service
                            imm.showSoftInput(lensSerialEditText, InputMethodManager.SHOW_IMPLICIT);                            // force the keyboard to be displayed
//                            lensSerialEditText.setText(serialString);                           // set the text string
                            lensSerialEditText.setSelection(childSerialText.length());             // position the cursor at the end of the serial string

                            // check the mylist checkboxes according to whether it's a member of the appropriate list
                            myListACheckBox.setChecked(myListA);
                            myListBCheckBox.setChecked(myListB);
                            myListCCheckBox.setChecked(myListC);

                            if (isPrime(typeTitle)) {
                                lensDashTextView.setVisibility(View.GONE);
                                lensFLength2.setVisibility(View.GONE);
                            }

                            // add the tag from the lens item in the listView to the hidden textView so we can retrieve it later
                            lensIndexTextView.setText(String.valueOf(editLensImageView.getTag()));

                            final AlertDialog dialog = new AlertDialog.Builder(_context)
                                    .setTitle("Edit Lens")
                                    .setView(editLensView)
                                    .setPositiveButton("Save", null)
                                    .setNegativeButton("Cancel", null)
                                    .setCancelable(true)
                                    .create();
                            // custom onShowListener so we can do some checks before saving the lens. prevents "Save" button from automatically closing the dialog
                            dialog.setOnShowListener(new DialogInterface.OnShowListener() {

                                @Override
                                public void onShow(final DialogInterface dialog) {
                                    Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                    Button negButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);

                                    // perform some housekeeping before closing the dialog. Specifically, make sure the lens serial/note is not more than 14 chars long
                                    posButton.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            Timber.d("check if the serial length is correct");
                                            String fLength1 = lensFLength1.getText().toString().trim();                                             // get the first focal length string
                                            String fLength2 = (isPrime(typeTitle) ? fLength1 : lensFLength2.getText().toString().trim());           // second focal length (if it's a zoom)
                                            String serial = lensSerialEditText.getText().toString().trim();                                         // serial of the lens

                                            // get the (potentially new) my list assignments for the lens
                                            boolean myListA = myListACheckBox.isChecked();
                                            boolean myListB = myListBCheckBox.isChecked();
                                            boolean myListC = myListCCheckBox.isChecked();

                                            // returns true if the lens serial + note is 14 chars or less
                                            boolean readyToSave = checkSerialLength(typeTitle, fLength1, fLength2, serial);

                                            // get the tag from the hidden textView. This is the index of the lens in the overall lensArray variable that we actually need to update
                                            int lensInd = Integer.parseInt(lensIndexTextView.getText().toString());

                                            // if everything is OK, dismiss dialog
                                            if (readyToSave == true) {
                                                dialog.dismiss();
                                                Timber.d("Edit this lens. New attributes for lens index " + String.valueOf(lensInd) + ": ");
                                                boolean editSuccessful = editLens(lensInd, childPosition, manufTitle, typeTitle, fLength1, fLength2, serial, myListA, myListB, myListC);
                                            }

                                            else {
                                                CharSequence toastText = "Error: Lens name too long.";
                                                int duration = Toast.LENGTH_LONG;

                                                Toast toast = Toast.makeText(_context, toastText, duration);
                                                toast.show();
                                            }
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
            return this._listDataChild.get(this._listDataHeader.get(groupPosition)).size();
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

            if (getChildrenCount(groupPosition) == 0) {
                typeTextView.setTextColor(0xFFBBBBBB);
                typeImageView.setImageResource(R.drawable.ic_expand_more_empty_24dp);
            }
            else {
                // depending on the isExpanded state of the group (and if there are > 0 children in the group), display the appropriate up/down chevron icon
                typeImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);
            }

            addImageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            LayoutInflater dialogInflater = (LayoutInflater) _context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                            final View addLensView = dialogInflater.inflate(R.layout.dialog_add_lens, null);

                            TextView lensManufTextView = (TextView) addLensView.findViewById(R.id.lensManufTextView);
                            TextView lensSeriesTextView = (TextView) addLensView.findViewById(R.id.lensSeriesTextView);
                            TextView lensDashTextView = (TextView) addLensView.findViewById(R.id.LensFocalDashTextView);

                            // initialize the UI components so we can access their contents when the user presses "Save"
                            final EditText lensFLength1 = (EditText) addLensView.findViewById(R.id.LensFocal1EditText);
                            final EditText lensFLength2 = (EditText) addLensView.findViewById(R.id.LensFocal2EditText);
                            final EditText lensSerialEditText = (EditText) addLensView.findViewById(R.id.LensSerialEditText);
                            final CheckBox myListACheckBox = (CheckBox) addLensView.findViewById(R.id.MyListACheckBox);
                            final CheckBox myListBCheckBox = (CheckBox) addLensView.findViewById(R.id.MyListBCheckBox);
                            final CheckBox myListCCheckBox = (CheckBox) addLensView.findViewById(R.id.MyListCCheckBox);

                            lensManufTextView.setText(manufTitle);
                            lensSeriesTextView.setText(typeTitle);

                            if (isPrime(typeTitle)) {
                                lensDashTextView.setVisibility(View.GONE);
                                lensFLength2.setVisibility(View.GONE);
                            }

                            final AlertDialog dialog = new AlertDialog.Builder(_context)
                                    .setTitle("Add New Lens")
                                    .setView(addLensView)
                                    .setPositiveButton("Save", null)
                                    .setNegativeButton("Cancel", null)
                                    .setCancelable(true)
                                    .create();

                            dialog.setOnShowListener(new DialogInterface.OnShowListener() {

                                @Override
                                public void onShow(final DialogInterface dialog) {
                                    Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                    posButton.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            Timber.d("check if the serial length is correct");
                                            String fLength1 = lensFLength1.getText().toString().trim();
                                            String fLength2 = (isPrime(typeTitle) ? fLength1 : lensFLength2.getText().toString().trim());
                                            String serial = lensSerialEditText.getText().toString().trim();

                                            boolean myListA = myListACheckBox.isChecked();
                                            boolean myListB = myListBCheckBox.isChecked();
                                            boolean myListC = myListCCheckBox.isChecked();

                                            boolean readyToSave = checkSerialLength(typeTitle, fLength1, fLength2, serial);

                                            // if everything is OK, dismiss dialog
                                            if (readyToSave == true) {
                                                dialog.dismiss();
                                                saveNewLens(manufTitle, typeTitle, fLength1, fLength2, serial, myListA, myListB, myListC);
                                            }

                                            else {
                                                CharSequence toastText = "Error: Lens name too long.";
                                                int duration = Toast.LENGTH_LONG;

                                                Toast toast = Toast.makeText(_context, toastText, duration);
                                                toast.show();
                                            }
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

        public void setNewItems(List<String> listDataHeader, HashMap<String, List<String>> listChildData) {
            this._listDataHeader = listDataHeader;
            this._listDataChild = listChildData;
            notifyDataSetChanged();
        }

    }
}

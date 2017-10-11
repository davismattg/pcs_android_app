package com.prestoncinema.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.v4.content.FileProvider;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.TypedValue;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.prestoncinema.app.settings.MqttUartSettingsActivity;
import com.prestoncinema.ble.BleManager;
import com.prestoncinema.mqtt.MqttManager;
import com.prestoncinema.mqtt.MqttSettings;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;

import timber.log.Timber;

import static android.R.id.input;
import static android.icu.lang.UCharacter.GraphemeClusterBreak.T;
import static android.util.Log.d;

/**
 * Created by MATT on 3/9/2017.
 * This activity is used to transfer lenses to/from the HU3. It also has buttons to add/remove lenses from a saved file,
 * and delete saved lens files. There's a lot of commented out stuff left over from the adafruit app
 */


public class LensActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener {
    // Log
    private final static String TAG = LensActivity.class.getSimpleName();
    private static final int LENS_IMPORT_CODE = 69;

    private Handler mMqttMenuItemAnimationHandler;
    private Button mImportLensesButton;
    private Button mExportLensesButton;
    private Intent mResultIntent;

    // UI TextBuffer (refreshing the text buffer is managed with a timer because a lot of changes an arrive really fast and could stall the main thread)
    private Handler mUIRefreshTimerHandler = new Handler();
    private Runnable mUIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUITimerRunning) {
                updateTextDataUI();
                // Timber.d("updateDataUI");
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;

    private boolean lensMode = false;
    private boolean lensSendMode = false;
    private boolean lensModeConnected = false;
    private boolean startLensTransfer = false;
    private boolean lensDone = false;
    private boolean lensFileLoaded = false;
    private boolean numLensesSent = false;

    private boolean startConnectionSetup = true;
    private boolean isConnectionReady = false;
    private boolean baudRateSent = false;
    private boolean isConnected = false;

    private int progressStatus = 0;
    private ArrayList<String> lensArray = new ArrayList<String>();
    private ArrayList<String> lensFileImportArray = new ArrayList<String>();
    private int baudRate = 19200;
    private boolean baudRateSet = false;
    private String responseExpected = "";
    private StringBuilder lensSBuilder = new StringBuilder("");
    private ProgressBar lensProgress;
    private Handler handler = new Handler();
    private int numLenses = 0;
    private int currentLens = 0;

    private byte[] ACK = {06};
    private byte[] NAK = {15};
    private byte[] EOT = {04};
    private byte[] ACK_SYN = {06, 16};
    private String EOTStr = new String(EOT);
    private String ACKStr = new String(ACK);
    private String NAKStr = new String(NAK);
    private String lastDataSent = "";

    private MqttManager mMqttManager;

    private ProgressDialog mProgressDialog;
    private int baudRateWait = 0;
    private int packetsToWait = 2;

    private ArrayList<String> lensFilesLocal;
    private ListView mLensFilesListView;
    private ArrayAdapter<String> lensFileAdapter;

    private TextView mConnectedTextView;

    public LensActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lens);
        lensProgress = (ProgressBar) findViewById(R.id.lensProgress);
        mImportLensesButton = (Button) findViewById(R.id.ImportLensesButton);
        mExportLensesButton = (Button) findViewById(R.id.ExportLensesButton);

        mLensFilesListView = (ListView) findViewById(R.id.LensFilesListView);
        lensFilesLocal = getLensFiles();

        Timber.d("lensFilesLocal: " + lensFilesLocal);
        lensFileAdapter = new ArrayAdapter<String>(this, R.layout.lens_file_list_item, R.id.lensFileTextView, lensFilesLocal);
        mLensFilesListView.setAdapter(lensFileAdapter);
        registerForContextMenu(mLensFilesListView);

        // onClick listener that takes the user to the other activity where they actually view/edit the lenses within a file
        mLensFilesListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String itemValue = (String) mLensFilesListView.getItemAtPosition(position);
                Timber.d("file clicked: " + itemValue);
                Intent intent = new Intent(LensActivity.this, ManageLensesActivity.class);
                intent.putExtra("lensFile", getLensFileAtIndex(position).toString());
                startActivity(intent);
            }
        });

        mBleManager = BleManager.getInstance(this);
//        mDownloadCompleteListener = new DownloadCompleteListener();
//        restoreRetainedDataFragment();
        isConnected = (mBleManager.getState() == 2);
        mConnectedTextView = (TextView) findViewById(R.id.ConnectedTextView);

        BluetoothDevice device = mBleManager.getConnectedDevice();
        if (device != null) {
            updateConnectedTextView(isConnected, device.getName());
        }
        else {
            updateConnectedTextView(isConnected, "");
        }
        // Get default_lenses theme colors
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
//        mTxColor = typedValue.data;
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);
//        mRxColor = typedValue.data;
        // UI
//        mBufferListView = (ListView) findViewById(R.id.bufferListView);
//        mBufferListAdapter = new TimestampListAdapter(this, R.layout.layout_uart_datachunkitem);
//        mBufferListView.setAdapter(mBufferListAdapter);
//        mBufferListView.setDivider(null);

//        mBufferTextView = (EditText) findViewById(R.id.bufferTextView);
//        if (mBufferTextView != null) {
//            mBufferTextView.setKeyListener(null);     // make it not editable
//        }

//        mSendEditText = (EditText) findViewById(R.id.sendEditText);
//        mSendEditText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
//            @Override
//            public boolean onEditorAction(TextView textView, int actionId, KeyEvent keyEvent) {
//                if (actionId == EditorInfo.IME_ACTION_SEND) {
//                    onClickSend(null);
//                    return true;
//                }
//
//                return false;
//            }
//        });
//        mSendEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
//            public void onFocusChange(View view, boolean hasFocus) {
//                if (!hasFocus) {
//                    // Dismiss keyboard when sendEditText loses focus
//                    dismissKeyboard(view);
//                }
//            }
//        });

//        mSentBytesTextView = (TextView) findViewById(R.id.sentBytesTextView);
//        mReceivedBytesTextView = (TextView) findViewById(R.id.receivedBytesTextView);

        // Read shared preferences
//        maxPacketsToPaintAsText = PreferencesFragment.getUartTextMaxPackets(this);
        //Timber.d("maxPacketsToPaintAsText: "+maxPacketsToPaintAsText);

        // Read local preferences
//        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
//        SharedPreferences firmwareVersionsPref = getSharedPreferences(kPreferences_versions, MODE_PRIVATE);

//        mShowDataInHexFormat = !preferences.getBoolean(kPreferences_asciiMode, true);
//        final boolean isTimestampDisplayMode = preferences.getBoolean(kPreferences_timestampDisplayMode, false);
//        setDisplayFormatToTimestamp(isTimestampDisplayMode);
//        mIsEchoEnabled = preferences.getBoolean(kPreferences_echo, true);
//        mIsEolEnabled = preferences.getBoolean(kPreferences_eol, true);
        invalidateOptionsMenu();        // udpate options menu with current values

        // Continue
        onServicesDiscovered();

        // Product Array Setup
//        productNameArray.add("DM3\n");
//        productNameArray.add("DMF\n");
//        productNameArray.add("F_I\n");
//        productNameArray.add("Tr4\n");
//        productNameArray.add("Hand3\n");
//        productNameArray.add("MDR\n");
//        productNameArray.add("MDR3\n");
//        productNameArray.add("MDR4\n");
//        productNameArray.add("MLink\n");
//        productNameArray.add("LightR\n");
//        productNameArray.add("Tr4\n");
//        productNameArray.add("VLC\n");
//        productNameArray.add("WMF\n");


        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }

        // Set up an Intent to send back to apps that request a file
        mResultIntent = new Intent("com.prestoncinema.app.ACTION_SEND");

        // Set the Activity's result to null to begin with
        setResult(Activity.RESULT_CANCELED, null);

//        // set the BLE module's baudrate to 19200 for connecting to the HU3
//        setBaudRate(baudRate);
        // check if the network is available, and if so, initiate download of latest firmware files
//        if (isNetworkAvailable()) {
//            mProgressDialog = new ProgressDialog(this);
//            mProgressDialog.setMessage("Checking for the latest firmware...");
//            mProgressDialog.setCancelable(false);
//            mProgressDialog.show();
//
//            startDownload();
//        } else {
//            new AlertDialog.Builder(this)
//                    .setTitle("No Internet Connection")
//                    .setMessage("Please turn on your network connection to download the latest firmware files.")
//                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
//                        public void onClick(DialogInterface dialog, int which) {
//                        }
//                    }).setIcon(android.R.drawable.ic_dialog_alert).show();
//        }
    }

//    @Override
//    public void onStart() {
//        super.onStart();
//
//    }

    @Override
    public void onResume() {
        super.onResume();

        // set the BLE module's baudrate to 19200 for connecting to the HU3
        if (!baudRateSet) {
            setBaudRate(baudRate);
        }

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();

        // Start UI refresh
        //Timber.d("add ui timer");
        updateUI();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }

    @Override
    public void onPause() {
        super.onPause();

        //Timber.d("remove ui timer");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

        // Save preferences
//        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
//        SharedPreferences.Editor editor = preferences.edit();
//        editor.putBoolean(kPreferences_echo, mIsEchoEnabled);
//        editor.putBoolean(kPreferences_eol, mIsEolEnabled);
//        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);
//        editor.putBoolean(kPreferences_timestampDisplayMode, mIsTimestampDisplayMode);
//
//        editor.apply();
    }

    @Override
    public void onDestroy() {
        // Disconnect mqtt
        if (mMqttManager != null) {
            mMqttManager.disconnect();
        }

        // Retain data
//        saveRetainedDataFragment();

        super.onDestroy();
    }

    // the menu that's created when the user long-presses on a lens within the lens list
    // TODO: if file selected is the default file, don't let the user rename or delete it
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.lens_file_context_menu, menu);
//        String fileName = v.findViewById(R.id.lensFileTextView).
    }

    // handle the user's item selection
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        int id = (int) info.id;
        switch (item.getItemId()) {
            case R.id.renameLensFile:
                Timber.d("rename lens file " + item.toString());
                renameLensFile(getLensFileAtIndex(id), lensFilesLocal.get(id));
                return true;
            case R.id.shareLensFile:
                Timber.d("share lens file " + item.toString());
                shareLensFile(getLensFileAtIndex(id), lensFilesLocal.get(id));
                return true;
            case R.id.duplicateLensFile:
                Timber.d("duplicate lens file " + item.toString());
                duplicateLensFile(getLensFileAtIndex(id), lensFilesLocal.get(id));
                return true;
            case R.id.deleteLensFile:
                Timber.d("confirm delete for lens file " + info.toString());
                confirmLensFileDelete(getLensFileAtIndex(id), lensFilesLocal.get(id));
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void renameLensFile(final File file, final String fullName) {
        Timber.d("Rename lens file: " + file.toString());

        // fullName comes in as "filename.lens", so use the regex to split on the . so you're left with "filename"
        String[] names = fullName.split("\\.");
        final String name = names[0];

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens, null);
                final EditText mRenameLensEditText = (EditText) renameLensView.findViewById(R.id.renameLensEditText);

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
                    .setCancelable(false);

                // set the text to the existing filename and select it
                mRenameLensEditText.setText(name);
                mRenameLensEditText.setSelection(name.length());

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
                        String enteredName = mRenameLensEditText.getText().toString();
                        String newName;

                        // check if they erroneously included ".lens" in their entry, and if so, don't append ".lens"
                        if (enteredName.indexOf(".lens") != -1) {
                            newName = enteredName.trim();
                        }
                        else {
                            newName = enteredName.trim() + ".lens";
                        }

                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {
                            // rename the file
                            file.renameTo(getLensStorageDir(newName));

                            // refresh the file array to reflect the new name so the UI updates
                            updateLensFiles();
                            alert.dismiss();
                        }

                        else {
                            Timber.d("file " + newName + "already exists.");

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

    // duplicate a given lens file (with a new name)
    private void duplicateLensFile(final File file, final String fullName) {
        Timber.d("duplicate lens file: " + fullName);

        // fullName comes in as "filename.lens", so use the regex to split on the . so you're left with "filename"
        String[] names = fullName.split("\\.");
        final String name = names[0];

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens, null);
                final EditText mRenameLensEditText = (EditText) renameLensView.findViewById(R.id.renameLensEditText);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(renameLensView)
                        .setTitle("Enter a name for the new file")
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // don't do anything here since we're going to use a custom onClick listener so we can check if the filename is already in use
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                            }
                        })
                        .setCancelable(false);

                // set the text to the existing filename and select it
                mRenameLensEditText.setText(name);
                mRenameLensEditText.selectAll();

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
                        String enteredName = mRenameLensEditText.getText().toString();
                        String newName;

                        // check if they erroneously included ".lens" in their entry, and if so, don't append ".lens"
                        if (enteredName.indexOf(".lens") != -1) {
                            newName = enteredName.trim();
                        }
                        else {
                            newName = enteredName.trim() + ".lens";
                        }

                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {
                            // create the file with the new name
                            File dupFile = getLensStorageDir(newName);

                            // copy the existing file to one with the new name
                            try {
                                copy(file, dupFile);
                                updateLensFiles();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            // refresh the file array to reflect the new name so the UI updates
                            updateLensFiles();
                            alert.dismiss();
                        }

                        else {
                            Timber.d("file " + newName + "already exists.");

                            // make a toast to inform the user the filename is already in use
                            Context context = getApplicationContext();
                            CharSequence toastText = newName + " already exists";
                            int duration = Toast.LENGTH_LONG;

                            Toast toast = Toast.makeText(context, toastText, duration);
                            toast.show();
                        }
                    }
                });
            }
        });
    }

    private void shareLensFile(File file, String name) {
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.setType("text/plain");
        try {
            Uri fileUri = FileProvider.getUriForFile(LensActivity.this, "com.prestoncinema.app.fileprovider", file);
            Timber.d("fileUri: " + fileUri);
            if (fileUri != null) {
                shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
                startActivity(Intent.createChooser(shareIntent, "Share via"));
            } else {
                // TODO: add a toast to alert user to error sharing file
            }
        }
        catch (IllegalArgumentException e) {
            Log.e("File Selector", "The selected file can't be shared: " + name + ": " + e);
        }
    }

    private boolean checkLensFileNames(String newFile) {
        ArrayList<String> currentFileNames = getLensFiles();
        if (currentFileNames.contains(newFile.trim().toLowerCase())) {
            return false;
        }
        else {
            return true;
        }
    }

    private void copy(File src, File dst) throws IOException {
        FileInputStream inStream = new FileInputStream(src);
        FileOutputStream outStream = new FileOutputStream(dst);
        FileChannel inChannel = inStream.getChannel();
        FileChannel outChannel = outStream.getChannel();
        inChannel.transferTo(0, inChannel.size(), outChannel);
        inStream.close();
        outStream.close();
    }

    // prompt the user to confirm that they want to delete the lens file
    private void confirmLensFileDelete(final File file, final String name) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                builder.setMessage("Are you sure you want to delete " + name + "?")
                    .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            // if they click OK, delete the file
                            deleteLensFile(file, name);
                        }
                    })
                    .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {

                        }
                    })
                    .show();
            }

        });
    }

    // remove the lens file specified in "file"
    private void deleteLensFile(File file, final String fileName) {
        // delete that shit
        boolean deleted = file.delete();
        if (deleted) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Timber.d("Delete successful");

                    // refresh the files array so the UI updates to reflect the changes
                    updateLensFiles();

                    // make a toast to inform the user the file was deleted
                    Context context = getApplicationContext();
                    CharSequence toastText = fileName + " deleted";
                    int duration = Toast.LENGTH_LONG;

                    Toast toast = Toast.makeText(context, toastText, duration);
                    toast.show();
                }
            });
        }
        // if the file wasn't deleted for some reason
        else {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Timber.d("Delete not successful");
                    new AlertDialog.Builder(LensActivity.this)
                            .setTitle("Unable to delete file")
                            .setMessage("Please try again")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        }
    }

    public void importLensFile(View view) {
        Timber.d("open file explorer to select and import lens file");
        // create the intent to launch the system's file explorer window
        Intent importLensFileIntent = new Intent(Intent.ACTION_GET_CONTENT);

        // limit the search to plain text files
        importLensFileIntent.setType("*/*");

        // only show files that can be opened (expluding things like a list of contacts or timezones)
        importLensFileIntent.addCategory(Intent.CATEGORY_OPENABLE);

        // start the activity. After user selects a file, onActivityResult action fires. Read the request_code there and import the file
        startActivityForResult(importLensFileIntent, LENS_IMPORT_CODE);
    }

    private void importAndSaveLensFile(Uri uri) throws IOException {
        Timber.d("importing URI: " + uri);

        Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        final String importedName = returnCursor.getString(nameIndex).split("\\.")[0];

        lensFileImportArray.clear();
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.length() > 0) {
                Timber.d("Importing lens file: " + line);                             // one lens at a time
                lensFileImportArray.add(line);                                      // add the read lens into the array
            }
        }

        inputStream.close();
        reader.close();

        // TODO: Add check here to make sure copied file is same size as original file
        if (lensFileImportArray.size() > 0) {
            Timber.d("file imported. size: " + lensFileImportArray.size());
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // building the custom alert dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                    LayoutInflater inflater = getLayoutInflater();

                    // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                    View renameLensView = inflater.inflate(R.layout.dialog_rename_lens, null);
                    final EditText mRenameLensEditText = (EditText) renameLensView.findViewById(R.id.renameLensEditText);

                    // set the custom view to be the view in the alert dialog and add the other params
                    builder.setView(renameLensView)
                            .setTitle(lensFileImportArray.size() + " lenses imported")
                            .setMessage("Please enter a new file name")
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
                            .setCancelable(false);

                    // set the text to the existing filename and select it
                    mRenameLensEditText.setText(importedName);
                    mRenameLensEditText.selectAll();

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
                            String enteredName = mRenameLensEditText.getText().toString();
                            String newName;

                            // check if they erroneously included ".lens" in their entry, and if so, don't append ".lens"
                            if (enteredName.contains(".lens")) {
                                newName = enteredName.trim();
                            }
                            else {
                                newName = enteredName.trim() + ".lens";
                            }

                            // check for duplicate filenames
                            boolean save = checkLensFileNames(newName);

                            if (save) {
                                // save the file
                                saveLensFile(newName, true);
//                                file.renameTo(getLensStorageDir(newName));

                                alert.dismiss();
                            }

                            else {
                                Timber.d("file " + newName + "already exists.");

                                // make a toast to inform the user the filename is already in use
                                Context context = getApplicationContext();
                                CharSequence toastText = newName + " already exists";
                                int duration = Toast.LENGTH_LONG;

                                Toast toast = Toast.makeText(context, toastText, duration);
                                toast.show();
                            }
                        }
                    });
                }
            });
        }
    }

    // save the data stored in lensArray to a text file (.lens)
    private void saveLensFile(String fileString, boolean saveAs) {
        Timber.d("Save lensArray to file, saveAs: " + saveAs);
        if (isExternalStorageWritable()) {
            Timber.d("Number of lenses in array: " + lensFileImportArray.size());
            File lensFile;

            if (saveAs) {           // if the customer wants to save as a new file, create new filename
                lensFile = new File(getExternalFilesDir(null), fileString);
            }
            else {                  // save w/ same name as before
                lensFile = new File(fileString);
            }

            Timber.d("lensFile: " + lensFile.toString());
            try {
                FileOutputStream fos = new FileOutputStream(lensFile);
                for (String lens : lensFileImportArray) {
                    Timber.d("current lens: " + lens);
                    String lensOut = lens + "\n";
                    try {
                        fos.write(lensOut.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fos.close();
//                    currentLens = 0;
                    Timber.d("File saved successfully, make toast and update adapter");
                    // refresh the file array to reflect the new name so the UI updates
                    updateLensFiles();
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

    // function to update the lens files array after changes are made (rename, duplicate, delete) so the UI refreshes
    private void updateLensFiles() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                lensFilesLocal.clear();
                lensFilesLocal.addAll(getLensFiles());
                Timber.d("lensFilesLocal: " + lensFilesLocal.toString());
                lensFileAdapter.notifyDataSetChanged();
            }
        });
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {
        lastDataSent = data;
        Timber.d("lastDataSent: " + lastDataSent + "--");

        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(LensActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
//        if (mIsEolEnabled) {
            // Add newline character if checked
            data += "\n";
//        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
//            mSentBytes += data.length();
        }

        // Add to current buffer
//        byte[] bytes = new byte[0];
//        try {
//            bytes = data.getBytes("UTF-8");
//        } catch (UnsupportedEncodingException e) {
//            e.printStackTrace();
//        }
//        UartDataChunk dataChunk = new UartDataChunk(System.currentTimeMillis(), UartDataChunk.TRANSFERMODE_TX, bytes);
//        mDataBuffer.add(dataChunk);
//
//        final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);
//        if (mIsTimestampDisplayMode) {
//            final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
//            mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] TX: " + formattedData, mTxColor));
//            mBufferListView.setSelection(mBufferListAdapter.getCount());
//        }

        // Update UI
//        updateUI();
    }

    private void uartSendData(byte[] data, boolean wasReceivedFromMqtt) {
//        lastDataSentByte = data;
        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(LensActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data.toString(), qos);
            }
        }

//        // Add eol
//        if (mIsEolEnabled) {
//            // Add newline character if checked
//            data += "\n";
//        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
//            mSentBytes += data.length();
        }
    }

//    public void startDownload() {
//        Timber.d("----------------- downloading firmware versions -------------------------");
//        new DownloadFirmwareTask(getApplicationContext(), new DownloadCompleteListener() {
//            @Override
//            public void downloadComplete(Map<String, Map<String, File>> firmwareFilesMap) {
//                Timber.d("downloadComplete inner entered");
//                if (mProgressDialog != null) {
//                    mProgressDialog.hide();
//                }
//            }
//        }).execute(pcsPath);
//    }

//    @Override
//    public void downloadComplete(Map<String, Map<String, File>> firmwareFilesMap) {
//        Timber.d("downloadComplete outer entered");
//        if (mProgressDialog != null) {
//            mProgressDialog.hide();
//        }
//    }

    private void updateConnectedTextView(boolean status, final String deviceName) {
        Timber.d("updateConnectedTextView: status = " + status);
        if (status) {
            Timber.d("updateConnectedTextView true");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mConnectedTextView.setText(deviceName);
                    mConnectedTextView.setTextColor(Color.WHITE);
                    mConnectedTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.icon_connected, 0);
                }
            });
        }
        else {
            Timber.d("updateConnectedTextView false");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mConnectedTextView.setText("Disconnected");
                    mConnectedTextView.setTextColor(0xFF666666);
                    mConnectedTextView.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.icon_disconnected, 0);
                }
            });
        }
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_lens, menu);

        // Mqtt
//        mMqttMenuItem = menu.findItem(R.id.action_mqttsettings);
        mMqttMenuItemAnimationHandler = new Handler();
        mMqttMenuItemAnimationRunnable.run();

        // DisplayMode
//        MenuItem displayModeMenuItem = menu.findItem(R.id.action_displaymode);
//        displayModeMenuItem.setTitle(String.format(getString(R.string.uart_action_displaymode_format), getString(mIsTimestampDisplayMode ? R.string.uart_displaymode_timestamp : R.string.uart_displaymode_text)));
//        SubMenu displayModeSubMenu = displayModeMenuItem.getSubMenu();
//        if (mIsTimestampDisplayMode) {
//            MenuItem displayModeTimestampMenuItem = displayModeSubMenu.findItem(R.id.action_displaymode_timestamp);
//            displayModeTimestampMenuItem.setChecked(true);
//        } else {
//            MenuItem displayModeTextMenuItem = displayModeSubMenu.findItem(R.id.action_displaymode_text);
//            displayModeTextMenuItem.setChecked(true);
//        }

        // DataMode
//        MenuItem dataModeMenuItem = menu.findItem(R.id.action_datamode);
//        dataModeMenuItem.setTitle(String.format(getString(R.string.uart_action_datamode_format), getString(mShowDataInHexFormat ? R.string.uart_format_hexadecimal : R.string.uart_format_ascii)));
//        SubMenu dataModeSubMenu = dataModeMenuItem.getSubMenu();
//        if (mShowDataInHexFormat) {
//            MenuItem dataModeHexMenuItem = dataModeSubMenu.findItem(R.id.action_datamode_hex);
//            dataModeHexMenuItem.setChecked(true);
//        } else {
//            MenuItem dataModeAsciiMenuItem = dataModeSubMenu.findItem(R.id.action_datamode_ascii);
//            dataModeAsciiMenuItem.setChecked(true);
//        }

        // Echo
//        MenuItem echoMenuItem = menu.findItem(R.id.action_echo);
//        echoMenuItem.setTitle(R.string.uart_action_echo);
//        echoMenuItem.setChecked(mIsEchoEnabled);

        // Eol
//        MenuItem eolMenuItem = menu.findItem(R.id.action_eol);
//        eolMenuItem.setTitle(R.string.uart_action_eol);
//        eolMenuItem.setChecked(mIsEolEnabled);

        return true;
    }

    private Runnable mMqttMenuItemAnimationRunnable = new Runnable() {
        @Override
        public void run() {
            updateMqttStatus();
            mMqttMenuItemAnimationHandler.postDelayed(mMqttMenuItemAnimationRunnable, 500);
        }
    };
    private int mMqttMenuItemAnimationFrame = 0;

    private void updateMqttStatus() {
//        if (mMqttMenuItem == null)
//            return;      // Hack: Sometimes this could have not been initialized so we don't update icons
//
//        MqttManager mqttManager = MqttManager.getInstance(this);
//        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();
//
//        if (status == MqttManager.MqqtConnectionStatus.CONNECTING) {
//            final int kConnectingAnimationDrawableIds[] = {R.drawable.mqtt_connecting1, R.drawable.mqtt_connecting2, R.drawable.mqtt_connecting3};
//            mMqttMenuItem.setIcon(kConnectingAnimationDrawableIds[mMqttMenuItemAnimationFrame]);
//            mMqttMenuItemAnimationFrame = (mMqttMenuItemAnimationFrame + 1) % kConnectingAnimationDrawableIds.length;
//        } else if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
//            mMqttMenuItem.setIcon(R.drawable.mqtt_connected);
//            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
//        } else {
//            mMqttMenuItem.setIcon(R.drawable.mqtt_disconnected);
//            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
//        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        switch (id) {
            case R.id.lensModeToggle:
                uartSendData("+++", false);
                responseExpected = "1\nOK\n";
                return true;
//            case R.id.action_help:
//                startHelp();
//                return true;
//
//            case R.id.action_connected_settings:
//                startConnectedSettings();
//                return true;
//
//            case R.id.action_refreshcache:
//                if (mBleManager != null) {
//                    mBleManager.refreshDeviceCache();
//                }
//                break;

//            case R.id.action_mqttsettings:
//                Intent intent = new Intent(this, MqttUartSettingsActivity.class);
//                startActivityForResult(intent, kActivityRequestCode_MqttSettingsActivity);
//                break;

//            case R.id.action_displaymode_timestamp:
//                setDisplayFormatToTimestamp(true);
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;

//            case R.id.action_displaymode_text:
//                setDisplayFormatToTimestamp(false);
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;

//            case R.id.action_datamode_hex:
//                mShowDataInHexFormat = true;
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;
//
//            case R.id.action_datamode_ascii:
//                mShowDataInHexFormat = false;
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;
//
//            case R.id.action_echo:
//                mIsEchoEnabled = !mIsEchoEnabled;
//                invalidateOptionsMenu();
//                return true;
//
//            case R.id.action_eol:
//                mIsEolEnabled = !mIsEolEnabled;
//                invalidateOptionsMenu();
//                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void startConnectedSettings() {
        // Launch connected settings activity
//        Intent intent = new Intent(this, ConnectedSettingsActivity.class);
//        startActivityForResult(intent, kActivityRequestCode_ConnectedSettingsActivity);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == kActivityRequestCode_ConnectedSettingsActivity && resultCode == RESULT_OK) {
//            finish();
//        } else if (requestCode == kActivityRequestCode_MqttSettingsActivity && resultCode == RESULT_OK) {

//        } else if (requestCode == kActivityRequestCode_SelectLensFile && resultCode == RESULT_OK) {
//            Uri selectedFile = intent.getData();
//            loadLensFile(selectedFile);
//        }

        if (requestCode == LENS_IMPORT_CODE && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using intent.getData().
            Uri uri = null;
            if (intent != null) {
                uri = intent.getData();
                Log.i(TAG, "Uri: " + uri.toString());
                try {
                    importAndSaveLensFile(uri);
                } catch (IOException e) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            new AlertDialog.Builder(LensActivity.this)
                                    .setTitle("Error importing lens file")
                                    .setMessage("Please try again")
                                    .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                        }
                                    })
                                    .setCancelable(false)
                                    .show();
                        }
                    });
                    e.printStackTrace();
                }
            }
        }
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, CommonHelpActivity.class);
        intent.putExtra("title", getString(R.string.uart_help_title));
        intent.putExtra("help", "uart_help.html");
        startActivity(intent);
    }
    // endregion

    // region BleManagerListener
    /*
    @Override
    public void onConnected() {

    }

    @Override
    public void onConnecting() {

    }
*/
    @Override
    public void onDisconnected() {
        super.onDisconnected();
        Timber.d("Disconnected. Back to previous activity");
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////
    // this function is called when data is available from the ble module. this is the key function //
    // to parse the incoming data and do the appropriate task
    //////////////////////////////////////////////////////////////////////////////////////////////////
    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();

                // startConnectionSetup flag is true if the connection isn't ready
                if (startConnectionSetup) {
                    String lensString = buildLensPacket(bytes);     // buffering the input data
                    processRxData(lensString);                      // analyze the buffered data
                }

                // lensMode is always true, this boolean check is left over from old code. this is the
                // main situation to handle incoming lens data from the HU3
                if (lensMode) {
                    String lensString = buildLensPacket(bytes);
                    if (lensString.length() > 0 && isConnectionReady) {
                        receiveLensData(lensString);
                    }
                }

                // boolean toggled true when you click "Export lenses to HU3"
                if (lensSendMode) {
                    String text = bytesToText(bytes, true);
                    transmitLensData(text);
                }

                // MQTT publish to RX
                MqttSettings settings = MqttSettings.getInstance(LensActivity.this);
                if (settings.isPublishEnabled()) {
                    String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_RX);
                    final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_RX);
                    final String text = bytesToText(bytes, false);
                    mMqttManager.publish(topic, text, qos);
                }
            }
        }
    }

    ////////////////////////////////////////////////////////////////////////////////
    ////////// ++++          ++++++++++++  +++++++      ++++   +++++++++  //////////
    ////////// ++++          ++++++++++++  ++++++++     ++++  ++++++++++  //////////
    ////////// ++++          ++++          ++++ ++++    ++++   +++++      //////////
    ////////// ++++          ++++++++      ++++  ++++   ++++     ++++     //////////
    ////////// ++++          ++++++++      ++++   ++++  ++++      ++++    //////////
    ////////// ++++          ++++          ++++    ++++ ++++      +++++   //////////
    ////////// ++++++++++++  ++++++++++++  ++++     ++++++++  ++++++++++  //////////
    ////////// ++++++++++++  ++++++++++++  ++++      +++++++  +++++++++   //////////
    ////////////////////////////////////////////////////////////////////////////////

    // buffer the input from the BLE module so we are always dealing with complete packets
    private String buildLensPacket(byte[] bytes) {
        String text = bytesToText(bytes, false);
        if (text.contains(EOTStr)) {        //EOT is sent after transfer is complete
            Timber.d("EOT detected. Returning EOT");
            lensSBuilder.setLength(0);      // clear the buffer
            return EOTStr;
        }
        else {          // if no newline character detected, add response to the buffer
            lensSBuilder.append(text);
            if (text.contains("\r")) {
                String lensString = lensSBuilder.toString();
                lensSBuilder.setLength(0);
                return lensString;          // if newline detected, add the text to the buffer, then return the whole buffer
            } else {
                return "";
            }
        }
    }

    // function to handle incoming data from BLE module during connection setup. The case statements are used //
    // so that we can change behavior based on the last data we sent to the BLE module. So every time you send //
    // a command to the BLE module, make sure you also set what response you expect and the last data you sent. //
    // for me, that's the variables 'responseExpected' and 'lastDataSent' //
    private void processRxData(String text) {
        Timber.d("processRxData (" + text + "):\nstartConnectionSetup: " + startConnectionSetup + "\nisConnectionReady: " +
                isConnectionReady + "\nresponseExpected: " + responseExpected + "\nbaudRateWait: " + baudRateWait);

        if (startConnectionSetup) {             // baud rate not set
            Timber.d(lastDataSent + " sent, expect " + responseExpected + " in return");
            switch (lastDataSent) {         // filter based on what we last sent
                case "AT":
                    Timber.d("AT detected");
                    if (baudRateWait < packetsToWait) {                     // wait a couple packets incase the message isn't received right away
                        if (text.contains(responseExpected)) {
                            Timber.d("OK found after sending AT, we're in command mode. Send baudRate");
                            Timber.d("Sending baudrate to device: " + baudRate);
                            uartSendData("AT+BAUDRATE=" + baudRate, false);
                            responseExpected = "OK";
                            baudRateWait = 0;
                        }
                        else {
                            baudRateWait += 1;              // if didn't receive the message, increase the counter so we wait a few more packets before officially declaring that we didn't receive the correct response
                        }
                    }
                    else {
                        if (responseExpected.equals("readyToGo")) {
                            Timber.d("Device confirmed to be in data mode, check for product string");
                            startConnectionSetup = false;
                            baudRateWait = 0;
                        }
                        else {
                            Timber.d("OK not received, change to command mode");
                            uartSendData("+++", false);
                            responseExpected = "1\nOK\n";
                            baudRateWait = 0;
                        }
                    }
                    break;
                case "+++":
                    Timber.d("+++ detected");
//                    if (baudRateWait < packetsToWait) {
//                        Timber.d("baudRateWait all good");
                        if (text.contains("1")) {              // if is 1, in command mode. 0 = data mode
                            Timber.d("1 OK response found");
//                            if (responseExpected.contains("1")) {
                                //in command mode since module returned 1 OK
                                Timber.d("1 OK found and expected. Device in command mode, send new baud rate");
//                                int currBaudRate = baudRateArray[baudRateIndex];
                                Timber.d("Sending baudrate to device: " + baudRate);
                                uartSendData("AT+BAUDRATE=" + baudRate, false);
//                                uartSendData("+++\nATZ+++", false);
                                responseExpected = "OK";
//                                baudRateWait = 0;
                        }
                        else if (text.contains("0")) {                          //in data mode since module returned 0 from +++
                            Timber.d("Device in data mode");
                            if (responseExpected == "1\nOK\n") {
                                Timber.d("expected 1 OK, but got 0 OK. Switch to command mode");
                                uartSendData("+++", false);
//                                responseExpected = "1\nOK\n";
                            }
                            else {
                                Timber.d("device in data mode which is what we want. Cnxn should be g2g");
//                                uartSendData("AT", false);
//                                responseExpected = "readyToGo";
                                startConnectionSetup = false;
                                isConnectionReady = true;
                                baudRateSet = true;
//                            baudRateWait = 0;
                            }
                        } else {                                                // text.contains("ERROR")
                            Timber.d("error detected");
                            uartSendData("+++", false);
                            responseExpected = "1\nOK\n";
                            baudRateWait = 0;
                        }
//                    }
//                    else {
//                        // in opposite mode than we want, so send +++
//                        Timber.d("response not as expected from +++. Check mode w/ AT");
//                        baudRateWait = 0;
//                        uartSendData("AT", false);
//                        responseExpected = "OK";
//                    }
                    break;
                case "AT+BAUDRATE=19200":
                    Timber.d("baudrate detected");
                    if (baudRateWait < packetsToWait) {
                        if (text.contains(responseExpected)) {              // expect "OK" if baudrate changed successfully
                            Timber.d("OK received; baudrate changed successfully. Switch back to data mode and check for productString");
                            uartSendData("+++", false);
                            responseExpected = "0\nOK\n";
                            baudRateWait = 0;
                        } else {                                            // expected response not received, increment packet wait counter
                            baudRateWait += 1;
                        }
                    }
                    else {
                        Timber.d("Baudrate not changed successfully. Check mode w/ AT");
                        baudRateWait = 0;
                        uartSendData("AT", false);
                        responseExpected = "OK";
                    }
                    break;
                default:
                    Timber.d("default_lenses case");
            }
        }
    }

    // function to receive lens transfer data from HU3. activates a popup to display progress.
    // this just follows mirko's lens transfer protocol
    private void receiveLensData(String text) {
            if (!startLensTransfer) {
                if (text.contains("Hand")) {
                    Timber.d("Hand detected");
                    lensModeConnected = true;
                    byte[] new_byte = {0x11, 0x05};
                    uartSendData(new_byte, false);
                } else {
                    uartSendData(ACK, false);
                    startLensTransfer = true;
                    String trimmedString = text.replaceAll("[^\\w]", "");
                    numLenses = Integer.valueOf(trimmedString, 16);
                    Timber.d("Number of lenses detected: " + numLenses);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activateLensTransferProgress("RX");
                        }
                    });
                }
            } else {
                if (text.contains(EOTStr)) {
                    Timber.d("EOT detected");
                    uartSendData(ACK_SYN, false);
                    lensModeConnected = false;
                    lensMode = false;
                    startLensTransfer = false;
                    askToSaveLenses();
                } else {
                    Timber.d("Lens string: " + text);
                    lensArray.add(text);
                    currentLens += 1;
                    uartSendData(ACK, false);
                }

            }
//        }
    }

    // function to send the lenses to HU3
    private void transmitLensData(String text) {
        if (lensSendMode) {
            if (text.contains(ACKStr)) {
                if (!lensDone) {
                    if (numLensesSent) {
                        Timber.d("ACK. Index: " + currentLens + " of " + numLenses);
                        if (currentLens < numLenses) {
                            byte[] STX = {0x02};
                            byte[] ETX = {0x0A, 0x0D};
                            String lensInfo = lensArray.get(currentLens);
                            uartSendData(STX, false);
                            uartSendData(lensArray.get(currentLens), false);
                            uartSendData(ETX, false);
                            currentLens += 1;
                        } else if (currentLens == numLenses) {
                            Timber.d("Done sending lenses. Sending EOT");
                            uartSendData(EOT, false);
                            lensDone = true;
                            currentLens = 0;
                            numLensesSent = false;
                        }
                    }
                    else {
                        String numLensesHexString = Integer.toHexString(numLenses);
                        Timber.d("Sending number of lenses: " + numLensesHexString);
                        byte[] STX = {0x0E};
                        byte[] ETX = {0x0A, 0x0D};
                        uartSendData(STX, false);
                        uartSendData(numLensesHexString, false);
                        uartSendData(ETX, false);

                        numLensesSent = true;
                    }
                }
                else {
                    Timber.d("HU3 successfully received lenses");
                    lensSendMode = false;
                    lensDone = false;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mProgressDialog.hide();
//                            lensProgress.setVisibility(View.INVISIBLE);
                        }
                    });
                }
            }
            else if (text.contains(NAKStr)) {
                Timber.d("NAK received from HU3. Re-sending lens " + currentLens);
                uartSendData(ACK, false);
                uartSendData(lensArray.get(currentLens), false);
            }
        }
    }

//    public void confirmLensTransmit(View view) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                new AlertDialog.Builder(LensActivity.this)
//                        .setMessage("Would you like to select the file to upload?")
//                        .setPositiveButton("For sure!", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                            selectLensFile();
//                            }
//                        })
//                        .setNegativeButton("Nope.", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                            }
//                        })
//                        .setCancelable(false)
//                        .show();
//            }
//        });
//    }

    // select the lens file to be sent to HU3
    public void selectLensFile(View view) {
        File path = new File(getExternalFilesDir(null), "");                       // the external files directory is where the lens files are stored
        final File[] savedLensFiles = path.listFiles();

        if (savedLensFiles.length > 0) {
            final String[] fileStrings = new String[savedLensFiles.length];
            for (int i = 0; i < savedLensFiles.length; i++) {
                String[] splitArray = savedLensFiles[i].toString().split("/");
                fileStrings[i] = splitArray[splitArray.length - 1];
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                    builder.setTitle("Select the lens file to upload")
                            .setItems(fileStrings, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    importLensFile(savedLensFiles[which]);
                                }
                            })
                            .show();
                }

            });
        }
    }

    private ArrayList<String> getLensFiles() {
        InputStream defaultLensFileInputStream = getResources().openRawResource(R.raw.default_lenses);              // create an InputStream for the default lens file
        BufferedReader reader;                                                                                      // initialize the BufferedReader
        reader = new BufferedReader(
                new InputStreamReader(defaultLensFileInputStream));
        String line;
        lensArray.clear();                                                                                          // clear the lens array in case there's some junk in there
        try {
            while ((line = reader.readLine()) != null) {                                                            // read the file one line at a time
                if (line.length() > 0) {
                    line += "\r\n";
                    lensArray.add(line);                                                                            // add the read lens into the array
                }
            }

            Timber.d("imported default lenses. size: " + lensArray.size());
            if (lensArray.size() > 0) {                                                                             // make sure something was actually imported
                lensFileLoaded = true;                                                                              // set the flag
                numLenses = lensArray.size();                                                                       // the number of lenses, used for loops and display on the UI
                currentLens = 0;                                                                                    // index mostly used for looping

                saveLensList(lensArray, "Default Lenses");                                                          // once the lensArray is populated, save it to internal memory
            }
        }
        catch (Exception ex){
            Timber.d("getLensFiles()", ex);
        }
        
        File extPath = new File(getExternalFilesDir(null), "");                                                     // the external files directory is where the lens files are stored
        File[] savedLensFiles = extPath.listFiles();
        if (savedLensFiles.length > 0) {
            ArrayList<String> fileStrings = new ArrayList<String>();
            for (int i = 0; i < savedLensFiles.length; i++) {
                String[] splitArray = savedLensFiles[i].toString().split("/");
                fileStrings.add(i, splitArray[splitArray.length - 1].toLowerCase());
            }
            Timber.d("fileStrings from import: ");
            Timber.d(fileStrings.toString());
            return fileStrings;
        }
        else {
            return new ArrayList<String>();
        }
    }

    private File getLensFileAtIndex(int index) {
        File path = new File(getExternalFilesDir(null), "");                       // the external files directory is where the lens files are stored
        File[] savedLensFiles = path.listFiles();
        return savedLensFiles[index];
    }

    // import the lens file from the text file into an array that can be sent to the HU3
    private void importLensFile(File lensFile) {
        Timber.d("Customer selected lens file: " + lensFile.toString());
        BufferedReader reader = null;
        lensArray.clear();

        try {
            FileInputStream lensIn = new FileInputStream(lensFile);
            reader = new BufferedReader(
                    new InputStreamReader(lensIn));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0) {
                    Timber.d("Reading lens file: " + line);
                    lensArray.add(line);
                }
            }
            if (lensArray.size() > 0) {
                lensFileLoaded = true;
                numLenses = lensArray.size();
                currentLens = 0;
            }

            Timber.d("lensArray loaded successfully. NumLenses: " + numLenses);

            lensSendMode = true;
            lensDone = false;
            byte[] start_byte = {01, 05};
            uartSendData(start_byte, false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activateLensTransferProgress("TX");
                }
            });
        } catch (Exception ex) {
            Timber.d("importLensFile()", ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                }   catch (Exception e) {
                    Timber.d("reader exception", e);
                }
            }
        }
    }

    // ask the user to input a name to save the newly imported lenses to a text file
    private void askToSaveLenses() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.hide();
                final EditText input = new EditText(LensActivity.this);
                input.setSelectAllOnFocus(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT);

                new AlertDialog.Builder(LensActivity.this)
                        .setMessage("Import successful. Please enter a file name to save the lenses.")
                        .setView(input)
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                saveLensList(lensArray, input.getText().toString());
                            }
                        })
//                    .setNegativeButton("Just Import", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                        }
//                    })
                        .setCancelable(false)
                        .show();
            }
        });
    }

    // save the lenses stored in lensArray to a text file
    private void saveLensList(ArrayList<String> lensArray, String fileName) {
        if (isExternalStorageWritable()) {
            Timber.d("Number of lenses in array: " + lensArray.size());

            String lensFileName = "";
            if (fileName.length() > 0) {
                lensFileName = fileName + ".lens";
            }
            else {
                String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
                lensFileName = currentDateTimeString + ".lens";
            }
            File lensFile = getLensStorageDir(lensFileName);
            try {
                FileOutputStream fos = new FileOutputStream(lensFile);
                for (String lens : lensArray) {
                    try {
                        fos.write(lens.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fos.close();
                    currentLens = 0;
                    Timber.d("File created successfully");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
    }

    public File getLensStorageDir(String lens) {
        // Create the directory for the saved lens files
        File file = new File(getExternalFilesDir(null), lens);
        Timber.d("File: " + file);
        return file;
    }

    // function to change the lens transfer progress popup box depending on the mode (TX or RX)
    private void activateLensTransferProgress(String direction) {
        mProgressDialog = new ProgressDialog(this);
        if (direction.equals("RX")) {
            mProgressDialog.setMessage("Importing lenses from HU3...");
        }
        else {      // direction = "TX"
            mProgressDialog.setMessage("Sending lenses to HU3...");
        }
        mProgressDialog.setCancelable(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setIndeterminate(false);
        mProgressDialog.setMax(numLenses);
        mProgressDialog.show();
//        lensProgress.setMax(numLenses);
//        lensProgress.setVisibility(View.VISIBLE);

        new Thread(new Runnable() {
            public void run() {
                while (currentLens <= numLenses) {
                    // Update the progress bar
                    handler.post(new Runnable() {
                        public void run() {
//                        lensProgress.setProgress(currentLens);
                            mProgressDialog.setProgress(currentLens);
                        }
                    });
                    try {
                        // Sleep for 200 milliseconds.
                        //Just to display the progress slowly
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void enableLensImport(View view) {
        lensMode = true;
        lensArray.clear();
        byte[] syn_byte = {0x16};
        uartSendData(syn_byte, false);
    }

//    public void enableLensExport(View view) {
//        lensSendMode = true;
//        lensDone = false;
//        byte[] start_byte = {01, 05};
//        uartSendData(start_byte, false);
//    }

    // button handler to send the user to ManageLensesActivity, where they can edit lens files
    public void manageLenses(View view) {
        Timber.d("switch to LensManagementActivity");
        File path = new File(getExternalFilesDir(null), "");                       // the external files directory is where the lens files are stored
        final File[] savedLensFiles = path.listFiles();

        if (savedLensFiles.length > 0) {
            final String[] fileStrings = new String[savedLensFiles.length];
            for (int i = 0; i < savedLensFiles.length; i++) {
                String[] splitArray = savedLensFiles[i].toString().split("/");
                fileStrings[i] = splitArray[splitArray.length - 1];
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                    builder.setTitle("Select the Lens File To View")
                            .setItems(fileStrings, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    Intent intent = new Intent(LensActivity.this, ManageLensesActivity.class);
                                    intent.putExtra("lensFile", savedLensFiles[which].toString());
                                    startActivity(intent);
//                                    importLensFile(savedLensFiles[which]);
                                }
                            })
                            .show();
                }

            });
        }
    }

    // function that finds all the lens files on the phone and gives the user the option to delete them
    public void ManageLensFiles(View view) {
        File path = new File(getExternalFilesDir(null), "");    // the external files directory is where the lens files are stored
        final File[] savedLensFiles = path.listFiles();
        final ArrayList<String> filesToDelete = new ArrayList<String>();

        if (savedLensFiles.length > 0) {
            final String[] fileStrings = new String[savedLensFiles.length];
            for (int i = 0; i < savedLensFiles.length; i++) {
                String[] splitArray = savedLensFiles[i].toString().split("/");
                fileStrings[i] = splitArray[splitArray.length - 1];
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                    builder.setTitle("Select files for deletion")
                            .setMultiChoiceItems(fileStrings, null, new DialogInterface.OnMultiChoiceClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                    if (isChecked) {
                                        filesToDelete.add(savedLensFiles[which].toString());
                                        Timber.d("Items to delete: ");
                                        for (String file : filesToDelete) {
                                            Timber.d(file);
                                        }
                                    }
                                    else {
                                        filesToDelete.remove(filesToDelete.indexOf(savedLensFiles[which].toString()));
                                        Timber.d("Items to delete: ");
                                        for (String file : filesToDelete) {
                                            Timber.d(file);
                                        }
                                    }
                                }
                            })
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    deleteLensFiles(filesToDelete);
                                }
                            })
                            .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int id) {
                                    filesToDelete.clear();
                                }
                            })
                            .show();

                }

            });
        }
    }

    // delete the selected files
    private void deleteLensFiles(ArrayList<String> files) {
        int numToDelete = files.size();
        int numDeleted = 0;
        for (String file : files) {
            File lensFile = new File(file);
            boolean deleted = lensFile.delete();
            Timber.d("Deleted file: " + deleted);
            if (deleted) {
                numDeleted += 1;
            }
        }
        if (numDeleted == numToDelete) {
            final int numDel = numDeleted;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Timber.d("Delete successful");
                    new AlertDialog.Builder(LensActivity.this)
                            .setMessage(numDel + " files deleted")
                            .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            })
//                    .setNegativeButton("No, I like bugs.", new DialogInterface.OnClickListener() {
//                        @Override
//                        public void onClick(DialogInterface dialog, int which) {
//                        programMode = false;
//                        programLoaded = false;
//                        isConnectionReady = false;
//                        updateConfirmed = false;
//                        }
//                    })
                            .setCancelable(false)
                            .show();
                }
            });
        }
    }

    private void setBaudRate(int baudRate) {
        Timber.d("baud rate being set");
        uartSendData("+++", false);
        responseExpected = "1\nOK\n";
    }


    private String bytesToText(byte[] bytes, boolean simplifyNewLine) {
        String text = new String(bytes, Charset.forName("UTF-8"));
        if (simplifyNewLine) {
            text = text.replaceAll("(\\r\\n|\\r)", "\n");
        }
        return text;
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder stringBuffer = new StringBuilder();
        for (byte aByte : bytes) {
            String charString = String.format("%02X", (byte) aByte);

            stringBuffer.append(charString).append(" ");
        }
        return stringBuffer.toString();
    }



    private void updateUI() {
//        mSentBytesTextView.setText(String.format(getString(R.string.uart_sentbytes_format), mSentBytes));
//        mReceivedBytesTextView.setText(String.format(getString(R.string.uart_receivedbytes_format), mReceivedBytes));
    }


    private int mDataBufferLastSize = 0;

    private void updateTextDataUI() {

//        if (!mIsTimestampDisplayMode) {
//            if (mDataBufferLastSize != mDataBuffer.size()) {
//
//                final int bufferSize = mDataBuffer.size();
//                if (bufferSize > maxPacketsToPaintAsText) {
//                    mDataBufferLastSize = bufferSize - maxPacketsToPaintAsText;
//                    mTextSpanBuffer.clear();
//                    addTextToSpanBuffer(mTextSpanBuffer, getString(R.string.uart_text_dataomitted) + "\n", mInfoColor);
//                }
//
//                // Timber.d("update packets: "+(bufferSize-mDataBufferLastSize));
//                for (int i = mDataBufferLastSize; i < bufferSize; i++) {
//                    final UartDataChunk dataChunk = mDataBuffer.get(i);
//                    final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
//                    final byte[] bytes = dataChunk.getData();
//                    final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);
//                    addTextToSpanBuffer(mTextSpanBuffer, formattedData, isRX ? mRxColor : mTxColor);
//                }
//
//                mDataBufferLastSize = mDataBuffer.size();
//                mBufferTextView.setText(mTextSpanBuffer);
//                mBufferTextView.setSelection(0, mTextSpanBuffer.length());        // to automatically scroll to the end
//            }
//        }
    }

    private void recreateDataView() {

//        if (mIsTimestampDisplayMode) {
//            mBufferListAdapter.clear();
//
//            final int bufferSize = mDataBuffer.size();
//            for (int i = 0; i < bufferSize; i++) {
//
//                final UartDataChunk dataChunk = mDataBuffer.get(i);
//                final boolean isRX = dataChunk.getMode() == UartDataChunk.TRANSFERMODE_RX;
//                final byte[] bytes = dataChunk.getData();
//                final String formattedData = mShowDataInHexFormat ? bytesToHex(bytes) : bytesToText(bytes, true);
//
//                final String currentDateTimeString = DateFormat.getTimeInstance().format(new Date(dataChunk.getTimestamp()));
//                mBufferListAdapter.add(new TimestampData("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData, isRX ? mRxColor : mTxColor));
////                mBufferListAdapter.add("[" + currentDateTimeString + "] " + (isRX ? "RX" : "TX") + ": " + formattedData);
//            }
//            mBufferListView.setSelection(mBufferListAdapter.getCount());
//        } else {
//            mDataBufferLastSize = 0;
//            mTextSpanBuffer.clear();
//            mBufferTextView.setText("");
//        }
    }



    // region DataFragment
    public static class DataFragment extends Fragment {
        private boolean mShowDataInHexFormat;
        private SpannableStringBuilder mTextSpanBuffer;
        private ArrayList<UartDataChunk> mDataBuffer;
        private int mSentBytes;
        private int mReceivedBytes;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }
    }

//    private void restoreRetainedDataFragment() {
//        // find the retained fragment
//        FragmentManager fm = getFragmentManager();
//        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);
//
//        if (mRetainedDataFragment == null) {
//            // Create
//            mRetainedDataFragment = new DataFragment();
//            fm.beginTransaction().add(mRetainedDataFragment, TAG).commit();
//
//            mDataBuffer = new ArrayList<>();
//            mTextSpanBuffer = new SpannableStringBuilder();
//        } else {
//            // Restore status
//            mShowDataInHexFormat = mRetainedDataFragment.mShowDataInHexFormat;
//            mTextSpanBuffer = mRetainedDataFragment.mTextSpanBuffer;
//            mDataBuffer = mRetainedDataFragment.mDataBuffer;
//            mSentBytes = mRetainedDataFragment.mSentBytes;
//            mReceivedBytes = mRetainedDataFragment.mReceivedBytes;
//        }
//    }
//
//    private void saveRetainedDataFragment() {
//        mRetainedDataFragment.mShowDataInHexFormat = mShowDataInHexFormat;
//        mRetainedDataFragment.mTextSpanBuffer = mTextSpanBuffer;
//        mRetainedDataFragment.mDataBuffer = mDataBuffer;
//        mRetainedDataFragment.mSentBytes = mSentBytes;
//        mRetainedDataFragment.mReceivedBytes = mReceivedBytes;
//    }
    // endregion


    // region MqttManagerListener

    @Override
    public void onMqttConnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttDisconnected() {
        updateMqttStatus();
    }

    @Override
    public void onMqttMessageArrived(String topic, MqttMessage mqttMessage) {
        final String message = new String(mqttMessage.getPayload());

        //Timber.d("Mqtt messageArrived from topic: " +topic+ " message: "+message);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                uartSendData(message, true);       // Don't republish to mqtt something received from mqtt
            }
        });

    }

    // endregion


    // region TimestampAdapter
    private class TimestampData {
        String text;
        int textColor;

        TimestampData(String text, int textColor) {
            this.text = text;
            this.textColor = textColor;
        }
    }

    private class TimestampListAdapter extends ArrayAdapter<TimestampData> {

        TimestampListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.layout_uart_datachunkitem, parent, false);
            }

            TimestampData data = getItem(position);
            TextView textView = (TextView) convertView;
            textView.setText(data.text);
            textView.setTextColor(data.textColor);

            return convertView;
        }
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

    // region Utils
    private boolean isNetworkAvailable() {
        ConnectivityManager connMgr = (ConnectivityManager)
                getSystemService(Context.CONNECTIVITY_SERVICE); // 1
        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo(); // 2
        return networkInfo != null && networkInfo.isConnected(); // 3
    }
    // endregion
}


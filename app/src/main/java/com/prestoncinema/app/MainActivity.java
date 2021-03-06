package com.prestoncinema.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.provider.Settings;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.text.Html;
import android.text.Spanned;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;
import com.prestoncinema.app.settings.SettingsActivity;
import com.prestoncinema.app.update.FirmwareUpdater;
import com.prestoncinema.app.update.ProgressFragmentDialog;
import com.prestoncinema.app.update.ReleasesParser;
import com.prestoncinema.ble.BleDevicesScanner;
import com.prestoncinema.ble.BleManager;
import com.prestoncinema.ble.BleUtils;
import com.prestoncinema.ui.utils.DialogUtils;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import timber.log.Timber;

import static android.support.v4.widget.SwipeRefreshLayout.OnRefreshListener;

public class MainActivity extends AppCompatActivity implements BleManager.BleManagerListener, BleUtils.ResetBluetoothAdapterListener, FirmwareUpdater.FirmwareUpdaterListener {
    // Constants
    private final static String TAG = MainActivity.class.getSimpleName();
    private final static long kMinDelayToUpdateUI = 1500;    // in milliseconds
    private static final String kGenericAttributeService = "00001801-0000-1000-8000-00805F9B34FB";
    private static final String kServiceChangedCharacteristic = "00002A05-0000-1000-8000-00805F9B34FB";

    private static final int PERMISSION_REQUEST_FINE_LOCATION = 1;
    private static final int PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE = 2;

    private final static String kPreferences = "MainActivity_prefs";
    private final static String kPreferences_filtersPanelOpen = "filtersPanelOpen";

     // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_EnableBluetooth = 1;
    private static final int kActivityRequestCode_Settings = 2;
    private static final int kActivityRequestCode_ConnectedActivity = 3;

    private static final int kActivityRequestCode_Devices = 4;

    // UI
    private AlertDialog firmwareUpdateAlertDialog;
    private ListView mScannedDevicesListView;
    private BLEModuleListViewAdapter mScannedDevicesAdapter;
    //private TextView mConnectedTextView;
    private long mLastUpdateMillis;
    private TextView mNoDevicesTextView;
//    private TextView mDevicesFoundTextView;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private ProgressDialog pDialog;
    public static final int progress_bar_type = 0;

    private AlertDialog mConnectingDialog;
    private AlertDialog connectingDialog;
    private Button connectedButton;

    // Data
    private BleManager mBleManager;
    private boolean mIsScanPaused = true;
    private BleDevicesScanner mScanner;
    private FirmwareUpdater mFirmwareUpdater;
    private PeripheralList mPeripheralList;
//    private Context mContext;
//    private DownloadTask mDownloadTask;

    private BluetoothDevice currentDevice;
    private ArrayList<BluetoothDeviceData> mScannedDevices;
    private BluetoothDeviceData mSelectedDeviceData;
    private Class<?> mComponentToStartWhenConnected;
    private boolean mShouldEnableWifiOnQuit = false;
    private String mLatestCheckedDeviceAddress;

    private DataFragment mRetainedDataFragment;
    private boolean mDownloading = false;

    private Context mContext;
    private Activity mParentActivity;
    private ProgressFragmentDialog mProgressDialog;

    private boolean isConnected = false;

    private boolean autoConnect = false;
    private boolean notifyFirmwareUpdate;
    private boolean rememberDevice;

    private ArrayAdapter<String> firmwareUpdateChangesAdapter;

    private String CHANNEL_ID = "firmwareUpdates";

    private String fullTitle;

//    private TextView swipeRefreshTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Timber.d("onCreate -------------------");
        firmwareUpdateChangesAdapter = new ArrayAdapter<>(MainActivity.this, R.layout.firmware_change_list_item);

        fullTitle = getResources().getString(R.string.title_activity_main_colored);
        setTitle(fullTitle);

        // Get the intent that started the activity. If it was launched from firmware update notification,
        // prepare the alert dialog to the user showing the changes for that version.
        Intent intent = getIntent();
        String intentType = intent.getStringExtra("type");
        Timber.d("intent type: " + intentType);
        if (intentType != null) {
            switch(intentType) {
                case "firmwareUpdate":
//                    newIntent.setClass(MainActivity.this, FirmwareUpdateActivity.class);
//                    startActivity(newIntent);
                    Bundle extras = intent.getExtras();
                    String firmwareUpdateChangesString = extras.getString("changes");
                    String firmwareUpdateUnit = extras.getString("unit");
                    String firmwareUpdateVersion = extras.getString("version");

                    String[] firmwareUpdateChanges = firmwareUpdateChangesString.split("\\n");
                    showFirmwareUpdateDialog(firmwareUpdateUnit, firmwareUpdateVersion, firmwareUpdateChanges);
            }
        }
//        if (data.toString().length() > 0) {
//            Timber.d(data.toString());
//        }
//        intent.setClass(MainActivity.this, FirmwareUpdateActivity.class);
//        startActivity(intent); //, kActivityRequestCode_Settings);
//        return true;


        // Init variables
        mBleManager = BleManager.getInstance(this);
        isConnected = (mBleManager.getState() == 2);

        restoreRetainedDataFragment();
        mPeripheralList = new PeripheralList();

        // UI
//        swipeRefreshTextView = findViewById(R.id.swipeRefreshTextView);
        mScannedDevicesListView = findViewById(R.id.scannedDevicesListView);
        mScannedDevicesAdapter = new BLEModuleListViewAdapter(getApplicationContext(), R.layout.layout_scan_item_title, mScannedDevices); //getApplicationContext(), mScannedDevices);
        if (mScannedDevicesAdapter != null) {
            mScannedDevicesListView.setAdapter(mScannedDevicesAdapter);
        }

        //mConnectedTextView = findViewById(R.id.ConnectedTextView);
        //registerForContextMenu(mConnectedTextView);

        BluetoothDevice device = mBleManager.getConnectedDevice();
        if (device != null) {
            updateConnectedButton(isConnected, device.getName(), false);
        }
        else {
            updateConnectedButton(isConnected, "", false);
        }

        mNoDevicesTextView = findViewById(R.id.noModulesDetectedTextView);
//        mDevicesFoundTextView =  findViewById(R.id.devicesFoundTextView);

        mSwipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        mSwipeRefreshLayout.setOnRefreshListener(new OnRefreshListener() {
            @Override
            public void onRefresh() {
                mScannedDevices.clear();
                if (mBleManager != null) {
                    mBleManager.disconnect();
                }
//                if (!isConnected) {
                    startScan(null);
//                }

                mSwipeRefreshLayout.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        mSwipeRefreshLayout.setRefreshing(false);
                    }
                }, 500);
            }
        });

//        if (isConnected) {
//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    mSwipeRefreshLayout.setVisibility(View.GONE);
//                    mDevicesFoundTextView.setVisibility(View.GONE);
////                    swipeRefreshTextView.setVisibility(View.GONE);
//                }
//            });
//        }


//        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);

        // Setup when activity is created for the first time
        if (savedInstanceState == null) {
            // Read preferences from default settings
            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);

            // Setting to autoconnect to remembered devices
            autoConnect = sharedPreferences.getBoolean("pref_remember_and_autoconnect", false);

            // Setting to remember devices upon connection
            rememberDevice = sharedPreferences.getBoolean("pref_remember_device", true);

            // Subscribe the app to the "firmware" topic by default.
            notifyFirmwareUpdate = sharedPreferences.getBoolean("pref_subscribe_to_firmware_updates", true);

            if (notifyFirmwareUpdate) {
                Timber.d("subscribe to firmware updates");
                FirebaseMessaging.getInstance().subscribeToTopic("firmware-android");
//                FirebaseMessaging.getInstance().subscribeToTopic("test");
//                FirebaseMessaging.getInstance().subscribeToTopic("ios-test");
            }
            else {  // unsubscribe
                Timber.d("unsubscribe from firmware updates");
                FirebaseMessaging.getInstance().unsubscribeFromTopic("firmware-android");
//                FirebaseMessaging.getInstance().unsubscribeFromTopic("test");
//                FirebaseMessaging.getInstance().unsubscribeFromTopic("ios-test");
            }
        }

        // Create the channel for notifications, applicable for Android V8.0+
        createNotificationChannel();

        // Request Bluetooth scanning permissions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
        }
//        requestLocationPermissionIfNeeded();
//        requestExternalStoragePermissionIfNeeded();
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        ContextMenu.ContextMenuInfo info = (ContextMenu.ContextMenuInfo) menuInfo;
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.ble_device_context_menu, menu);

        if (!isConnected) {
            menu.findItem(R.id.DisconnectBLEMenuItem).setVisible(false);
            menu.findItem(R.id.ForgetBLEMenuItem).setVisible(false);
        }
    }

    // handle the user's item selection
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        ContextMenu.ContextMenuInfo info = (ContextMenu.ContextMenuInfo) item.getMenuInfo();

        switch (item.getItemId()) {
            case R.id.ScanBLEMenuItem:
                Timber.d("Scan for new devices");
                autostartScan();
                return true;
            case R.id.DisconnectBLEMenuItem:
                Timber.d("Disconnect from this device");
                autostartScan();
                return true;
            case R.id.ForgetBLEMenuItem:
                Timber.d("Forget this device");
                forgetBLEDevice(mBleManager.getConnectedDevice());
                autostartScan();
                return true;
            case R.id.MyDevicesMenuItem:
                Timber.d("Go to my devices");
                Intent intent = new Intent();
                intent.setClass(MainActivity.this, DevicesActivity.class);
                startActivityForResult(intent, kActivityRequestCode_Devices);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_help) {
            startHelp();
            return true;
        }
        else if (id == R.id.action_scan) {
            autostartScan();
            return true;
        }
        else if (id == R.id.action_devices) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, DevicesActivity.class);
            startActivityForResult(intent, kActivityRequestCode_Devices);
        }
        else if (id == R.id.action_settings) {
            Intent intent = new Intent();
            intent.setClass(MainActivity.this, SettingsActivity.class);
            startActivityForResult(intent, kActivityRequestCode_Settings);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        Timber.d("onResume called @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        super.onResume();

        isConnected = (mBleManager.getState() == 2);
//        isConnected = getSharedPreferences("connectedStatus", MODE_PRIVATE).getBoolean("connected", false);
        Timber.d("onResume - connected: " + isConnected);

        BluetoothDevice device = mBleManager.getConnectedDevice();

        if (device != null) {
            BluetoothDeviceData deviceData;
            deviceData = new BluetoothDeviceData();
            deviceData.device = device;
            deviceData.rssi = 127;

            if (currentDevice == null) {
                mScannedDevices.clear();
                mScannedDevices.add(deviceData);
                currentDevice = device;
                mScannedDevicesAdapter.notifyDataSetChanged();
                updateConnectedButton(isConnected, device.getName(), false);
            }
        }

        else {
            updateConnectedButton(isConnected, "", false);
            autostartScan();
        }

        // Set listener
        mBleManager.setBleListener(this);

        if (!isConnected) {
            // Autostart scan
            autostartScan();
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name);
            String description = getString(R.string.channel_description);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);

            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    /**
     * Create the popup that shows the user what's new in a given firmware version.
     * Called when MainActivity is launched via an Intent from a notification.
     * @param unit
     * @param version
     * @param changes
     */
    private void showFirmwareUpdateDialog(final String unit, final String version, final String[] changes) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                final View firmwareUpdateDialogView = inflater.inflate(R.layout.dialog_firmware_update, null);
                final String versionToInstall = "V" + version;
                firmwareUpdateAlertDialog = builder.setView(firmwareUpdateDialogView)
                        .setPositiveButton("Cool", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                            }
                        })
                        .setCancelable(false)
                        .create();
                firmwareUpdateAlertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(DialogInterface dialogInterface) {
                        ImageView productImageView = firmwareUpdateDialogView.findViewById(R.id.productTypeImageView);
//                        TextView productNameTextView = firmwareUpdateDialogView.findViewById(R.id.currentFirmwareProductTextView);
                        LinearLayout currentVersionLinearLayout = firmwareUpdateDialogView.findViewById(R.id.currentFirmwareLinearLayout);
                        LinearLayout toInstallLinearLayout = firmwareUpdateDialogView.findViewById(R.id.toInstallFirmwareLinearLayout);
                        TextView whatsNewTextView = firmwareUpdateDialogView.findViewById(R.id.firmwareUpdateWhatsNewTextView);
                        ListView changesListView = firmwareUpdateDialogView.findViewById(R.id.firmwareUpdateChangesListView);

                        productImageView.setImageResource(SharedHelper.getProductImage(unit));

                        currentVersionLinearLayout.setVisibility(View.GONE);
                        toInstallLinearLayout.setVisibility(View.GONE);

//                        String productNameText = productNameTextView.getText() + productDetected + ":";
                        String whatsNewText = "What's new in " + versionToInstall + ":";

//                        productNameTextView.setText(productNameText);
//                        currentVersionTextView.setText(currentVersion);
//                        toInstallVersionTextView.setText(versionToInstall);
                        whatsNewTextView.setText(whatsNewText);

                        firmwareUpdateChangesAdapter.addAll(changes);
                        changesListView.setAdapter(firmwareUpdateChangesAdapter);
                        firmwareUpdateChangesAdapter.notifyDataSetChanged();
                    }
                });

                firmwareUpdateAlertDialog.show();
            }
        });
    }


    private void autostartScan() {
        Timber.d("autoStartScan() called -------------------");
//        mScannedDevices.clear();
        mBleManager.disconnect();

        isConnected = false;
        updateConnectedButton(false, "", false);

        if (BleUtils.getBleStatus(this) == BleUtils.STATUS_BLE_ENABLED) {
            // If was connected, disconnect
            //mBleManager.disconnect();

//            // Force restart scanning
//            if (mScannedDevices != null) {      // Fixed a weird bug when resuming the app (this was null on very rare occasions even if it should not be)
//                mScannedDevices.clear();
//            }

//            runOnUiThread(new Runnable() {
//                @Override
//                public void run() {
//                    mSwipeRefreshLayout.setVisibility(View.VISIBLE);
//                    mDevicesFoundTextView.setVisibility(View.VISIBLE);
////                    swipeRefreshTextView.setVisibility(View.VISIBLE);
////                    mDevicesScrollView.setVisibility(View.VISIBLE);
//                }
//            });
            startScan(null);
        }
    }

    @Override
    public void onPause() {
        Timber.d("onPause called @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        // Stop scanning
        if (mScanner != null && mScanner.isScanning()) {
            mIsScanPaused = true;
            stopScanning();
        }

        SharedPreferences.Editor sharedPrefEditor = getSharedPreferences("connectedStatus", MODE_PRIVATE).edit();
        sharedPrefEditor.putBoolean("connected", isConnected);
        sharedPrefEditor.apply();
        Timber.d("sharedPref - connected = " + getSharedPreferences("connectedStatus", MODE_PRIVATE).getBoolean("connected", false));

        super.onPause();
    }

//    @Override
//    public void onSaveInstanceState(Bundle savedInstanceState) {
//        Timber.d("onSaveInstanceState called @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
//        savedInstanceState.putBoolean("Connected", isConnected);
////        savedInstanceState.putString("Address",)
//        Timber.d("savedInstaceState Connected: " + savedInstanceState.getBoolean("Connected"));
//        super.onSaveInstanceState(savedInstanceState);
//    }

    public void onStop() {
        Timber.d("onStop called @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
            mConnectingDialog = null;
        }

        if (connectingDialog != null) {
            connectingDialog.cancel();
            connectingDialog = null;
        }

        currentDevice = null;

        super.onStop();
    }

    @Override
    public void onBackPressed() {
        Timber.d("onBackPressed called @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        if (mShouldEnableWifiOnQuit) {
            mShouldEnableWifiOnQuit = false;
            new AlertDialog.Builder(this)
                    .setTitle(getString(R.string.settingsaction_confirmenablewifi_title))
                    .setMessage(getString(R.string.settingsaction_confirmenablewifi_message))
                    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            Timber.d("enableNotification wifi");
                            BleUtils.enableWifi(true, MainActivity.this);
                            MainActivity.super.onBackPressed();
                        }

                    })
                    .setNegativeButton(android.R.string.no, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            MainActivity.super.onBackPressed();
                        }

                    })
                    .show();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        Timber.d("onDestroy called @@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@@");
        // Stop ble adapter reset if in progress
        BleUtils.cancelBluetoothAdapterReset(this);

        // Retain data
        saveRetainedDataFragment();

        // Clean
        if (mConnectingDialog != null) {
            mConnectingDialog.cancel();
        }

        if (connectingDialog != null) {
            connectingDialog.cancel();
            connectingDialog = null;
        }

        currentDevice = null;

        super.onDestroy();
    }

//    // region Permissions
//    @TargetApi(Build.VERSION_CODES.M)
//    private void requestLocationPermissionIfNeeded() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            // Android M Permission check 
//            if (this.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
//                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                builder.setTitle("This app needs location access");
//                builder.setMessage("Please grant location access so this app can scan for Bluetooth peripherals");
//                builder.setPositiveButton(android.R.string.ok, null);
//                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                    public void onDismiss(DialogInterface dialog) {
//                        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, PERMISSION_REQUEST_FINE_LOCATION);
//                    }
//                });
//                builder.show();
//            }
//        }
//    }
//
//    @TargetApi(Build.VERSION_CODES.M)
//    private void requestExternalStoragePermissionIfNeeded() {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            // Android M Permission check 
//            if (this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
//                final AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                builder.setTitle("This app needs external read/write permission");
//                builder.setMessage("Please grant permission to read/write lens files");
//                builder.setPositiveButton(android.R.string.ok, null);
//                builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                    public void onDismiss(DialogInterface dialog) {
//                        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE);
//                    }
//                });
//                builder.show();
//            }
//        }
//    }
//
//    @Override
//    public void onRequestPermissionsResult(int requestCode, @NonNull String permissions[], @NonNull int[] grantResults) {
//        switch (requestCode) {
//            case PERMISSION_REQUEST_FINE_LOCATION: {
//                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Timber.d("Location permission granted");
//                    if (!isConnected) {
//                        // Autostart scan
//                        autostartScan();
//                    }
////                    runOnUiThread(new Runnable() {
////                        @Override
////                        public void run() {
////                            updateUI();
////                        }
////                    });
//                } else {
//                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                    builder.setTitle("Bluetooth Scanning not available");
//                    builder.setMessage("Since location access has not been granted, the app will not be able to scan for Bluetooth peripherals");
//                    builder.setPositiveButton(android.R.string.ok, null);
//                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
//
//                        @Override
//                        public void onDismiss(DialogInterface dialog) {
//                        }
//
//                    });
//                    builder.show();
//                }
//                break;
//            }
//            case PERMISSION_REQUEST_WRITE_EXTERNAL_STORAGE: {
//                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Timber.d("External storage permission granted");
//                    if (!isConnected) {
//                    // Autostart scan
//                    autostartScan();
//                    }
////                    runOnUiThread(new Runnable() {
////                        @Override
////                        public void run() {
////                            updateUI();
////                        }
////                    });
//                } else {
//                    final AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                    builder.setTitle("External storage not available");
//                    builder.setMessage("Since external storage access has not been granted, the app will not be able to save HU3 lens files");
//                    builder.setPositiveButton(android.R.string.ok, null);
//                    builder.setOnDismissListener(new DialogInterface.OnDismissListener() {
//
//                        @Override
//                        public void onDismiss(DialogInterface dialog) {
//                        }
//
//                    });
//                    builder.show();
//                }
//                break;
//            }
//            default:
//                break;
//        }
//    }

    // endregion


    // region Filters
//    private void openFiltersPanel(final boolean isOpen, boolean animated) {
//        SharedPreferences.Editor preferencesEditor = getSharedPreferences(kPreferences, MODE_PRIVATE).edit();
//        preferencesEditor.putBoolean(kPreferences_filtersPanelOpen, isOpen);
//        preferencesEditor.apply();
//
//        mFiltersExpandImageView.setImageResource(isOpen ? R.drawable.ic_expand_less_black_24dp : R.drawable.ic_expand_more_black_24dp);
//
//        mFiltersPanelView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
//
//        mFiltersPanelView.animate()
//                .alpha(isOpen ? 1.0f : 0)
//                .setDuration(300)
//                .setListener(new AnimatorListenerAdapter() {
//                    @Override
//                    public void onAnimationEnd(Animator animation) {
//                        super.onAnimationEnd(animation);
//                        mFiltersPanelView.setVisibility(isOpen ? View.VISIBLE : View.GONE);
//                    }
//                });
//
//    }

//    public void onClickExpandFilters(View view) {
//        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
//        boolean filtersIsPanelOpen = preferences.getBoolean(kPreferences_filtersPanelOpen, false);
//
//        openFiltersPanel(!filtersIsPanelOpen, true);
//    }

//    public void onClickRemoveFilters(View view) {
//        mPeripheralList.setDefaultFilters();
//        mFiltersNameEditText.setText(mPeripheralList.getFilterName());
//        setRssiSliderValue(mPeripheralList.getFilterRssiValue());
//        mFiltersUnnamedCheckBox.setChecked(mPeripheralList.isFilterUnnamedEnabled());
//        mFiltersUartCheckBox.setChecked(mPeripheralList.isFilterOnlyUartEnabled());
//        updateFilters();
//    }

//    public void onClickFilterNameSettings(View view) {
//        PopupMenu popup = new PopupMenu(this, view);
//        popup.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
//            @Override
//            public boolean onMenuItemClick(MenuItem item) {
//                boolean processed = true;
//                switch (item.getItemId()) {
//                    case R.id.scanfilter_name_contains:
//                        mPeripheralList.setFilterNameExact(false);
//                        break;
//                    case R.id.scanfilter_name_exact:
//                        mPeripheralList.setFilterNameExact(true);
//                        break;
//                    case R.id.scanfilter_name_sensitive:
//                        mPeripheralList.setFilterNameCaseInsensitive(false);
//                        break;
//                    case R.id.scanfilter_name_insensitive:
//                        mPeripheralList.setFilterNameCaseInsensitive(true);
//                        break;
//                    default:
//                        processed = false;
//                        break;
//                }
//                updateFilters();
//                return processed;
//            }
//        });
//        MenuInflater inflater = popup.getMenuInflater();
//        Menu menu = popup.getMenu();
//        inflater.inflate(R.menu.menu_scan_filters_name, menu);
//        final boolean isFilterNameExact = mPeripheralList.isFilterNameExact();
//        menu.findItem(isFilterNameExact ? R.id.scanfilter_name_exact : R.id.scanfilter_name_contains).setChecked(true);
//        final boolean isFilterNameCaseInsensitive = mPeripheralList.isFilterNameCaseInsensitive();
//        menu.findItem(isFilterNameCaseInsensitive ? R.id.scanfilter_name_insensitive : R.id.scanfilter_name_sensitive).setChecked(true);
//        popup.show();
//    }


//    private void updateFiltersTitle() {
//        final String filtersTitle = mPeripheralList.filtersDescription();
//        mFiltersTitleTextView.setText(filtersTitle != null ? String.format(Locale.ENGLISH, getString(R.string.scan_filters_title_filter_format), filtersTitle) : getString(R.string.scan_filters_title_nofilter));
//        mFiltersClearButton.setVisibility(mPeripheralList.isAnyFilterEnabled() ? View.VISIBLE : View.GONE);
//    }

//    private void updateFilters() {
//        updateFiltersTitle();
//        mScannedDevicesAdapter.notifyDataSetChanged();
//    }
//
//    private void setRssiSliderValue(int value) {
//        mFiltersRssiSeekBar.setProgress(-value);
//        updateRssiValue();
//    }

//    private void updateRssiValue() {
//        final int value = -mFiltersRssiSeekBar.getProgress();
//        mFiltersRssiValueTextView.setText(String.format(Locale.ENGLISH, getString(R.string.scan_filters_rssi_value_format), value));
//    }

    // endregion

    private void resumeScanning() {
        if (mIsScanPaused) {
            if (!isConnected) {
                startScan(null);
                mIsScanPaused = mScanner == null;
            }
        }
    }

    private boolean manageBluetoothAvailability() {
        boolean isEnabled = true;

        // Check Bluetooth HW status
        int errorMessageId = 0;
        final int bleStatus = BleUtils.getBleStatus(getBaseContext());
        switch (bleStatus) {
            case BleUtils.STATUS_BLE_NOT_AVAILABLE:
                errorMessageId = R.string.dialog_error_no_ble;
                isEnabled = false;
                break;
            case BleUtils.STATUS_BLUETOOTH_NOT_AVAILABLE: {
                errorMessageId = R.string.dialog_error_no_bluetooth;
                isEnabled = false;      // it was already off
                break;
            }
            case BleUtils.STATUS_BLUETOOTH_DISABLED: {
                isEnabled = false;      // it was already off
                // if no enabled, launch settings dialog to enable it (user should always be prompted before automatically enabling bluetooth)
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, kActivityRequestCode_EnableBluetooth);
                // execution will continue at onActivityResult()
                break;
            }
        }
        if (errorMessageId != 0) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            AlertDialog dialog = builder.setMessage(errorMessageId)
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            DialogUtils.keepDialogOnOrientationChanges(dialog);
        }

        return isEnabled;
    }

    private boolean manageLocationServiceAvailabilityForScanning() {

        boolean areLocationServiceReady = true;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {        // Location services are only needed to be enabled from Android 6.0
            int locationMode = Settings.Secure.LOCATION_MODE_OFF;
            try {
                locationMode = Settings.Secure.getInt(getContentResolver(), Settings.Secure.LOCATION_MODE);

            } catch (Settings.SettingNotFoundException e) {
                e.printStackTrace();
            }
            areLocationServiceReady = locationMode != Settings.Secure.LOCATION_MODE_OFF;

            if (!areLocationServiceReady) {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                AlertDialog dialog = builder.setMessage(R.string.dialog_error_nolocationservices_requiredforscan_marshmallow)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);
            }
        }

        return areLocationServiceReady;
    }

    private void connect(BluetoothDevice device, boolean knownDevice) {
        Timber.d("connect method &&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&&");

        boolean isConnecting = mBleManager.connect(this, device.getAddress(), knownDevice);

//        if (!isConnected) {
//            showConnectingDialog(isConnecting);
//        }
    }

    /**
     * Show a dialog while attempting to connect to the Bluetooth module
     * @param connecting
     */
    private void showConnectingDialog(boolean connecting) {
        Timber.d("show\n\nconnecting\n\ndialog\n\n");

        final String connStr = connecting ? getResources().getString(R.string.connecting) : getResources().getString(R.string.disconnecting);

        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        LayoutInflater inflater = getLayoutInflater();

        final View connectingDialogView = inflater.inflate(R.layout.dialog_connecting, null);
        TextView strTextView = connectingDialogView.findViewById(R.id.connectingTextView);
        strTextView.setText(connStr);

        connectingDialog = builder.setView(connectingDialogView)
            .setCancelable(true)
            .create();
        connectingDialog.show();
    }

    private void startHelp() {
        // Launch app help activity
        Intent intent = new Intent(this, com.prestoncinema.app.MainHelpActivity.class);
        startActivity(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == kActivityRequestCode_ConnectedActivity) {
            if (resultCode < 0) {
                Toast.makeText(this, R.string.scan_unexpecteddisconnect, Toast.LENGTH_LONG).show();
            }
        } else if (requestCode == kActivityRequestCode_EnableBluetooth) {
            if (resultCode == Activity.RESULT_OK) {
                // Bluetooth was enabled, resume scanning
                resumeScanning();
            } else if (resultCode == Activity.RESULT_CANCELED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                AlertDialog dialog = builder.setMessage(R.string.dialog_error_no_bluetooth)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                DialogUtils.keepDialogOnOrientationChanges(dialog);

            }
        } else if (requestCode == kActivityRequestCode_Settings) {
            // Return from activity settings. Update app behaviour if needed
//            SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
//            boolean updatesEnabled = sharedPreferences.getBoolean("pref_updatesenabled", true);
//            if (updatesEnabled) {
//                mLatestCheckedDeviceAddress = null;
//                mFirmwareUpdater.refreshSoftwareUpdatesDatabase();
//            } else {
//                mFirmwareUpdater = null;
//            }
        }
    }

    private void showConnectionStatus(boolean enable) {
        showStatusDialog(enable, R.string.scan_connecting);
    }

    private void showGettingUpdateInfoState() {
        showConnectionStatus(false);
//        showStatusDialog(true, R.string.scan_gettingupdateinfo);
    }

    private void showStatusDialog(boolean show, int stringId) {
        if (show) {

            // Remove if a previous dialog was open (maybe because was clicked 2 times really quick)
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }

            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(stringId);

            // Show dialog
            mConnectingDialog = builder.create();
            mConnectingDialog.setCanceledOnTouchOutside(false);

            mConnectingDialog.setOnKeyListener(new DialogInterface.OnKeyListener() {
                @Override
                public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                    if (keyCode == KeyEvent.KEYCODE_BACK) {
                        mBleManager.disconnect();
                        mConnectingDialog.cancel();
                    }
                    return true;
                }
            });
            mConnectingDialog.show();
        } else {
            if (mConnectingDialog != null) {
                mConnectingDialog.cancel();
            }
        }
    }

    // region Actions
    public void onClickScannedDevice(final View view) {
        Timber.d("scanned device click");
    }

    // function called when you click "Connect" next to device name
    public void onClickDeviceConnect(int scannedDeviceIndex) {
        stopScanning();

        Timber.d("device clicked at tag " + scannedDeviceIndex);
        ArrayList<BluetoothDeviceData> filteredPeripherals = mScannedDevices; //mPeripheralList.filteredPeripherals(false);
        Timber.d("filteredPeripherals: " + filteredPeripherals.toString());

        if (scannedDeviceIndex < filteredPeripherals.size()) {
            mSelectedDeviceData = mScannedDevices.get(scannedDeviceIndex);
            Timber.d("filteredPeripherals device: " + mSelectedDeviceData.getName());
            BluetoothDevice device = mSelectedDeviceData.device;
            mBleManager.setBleListener(MainActivity.this);           // Force set listener (could be still checking for updates...)

            connect(device, false);
        } else {
            Log.w(TAG, "onClickDeviceConnect tag does not exist: " + scannedDeviceIndex);
        }
    }

    public void onClickScan(View view) {
        boolean isScanning = mScanner != null && mScanner.isScanning();
        if (isScanning) {
            stopScanning();
        } else {
            startScan(null);
        }
    }
    // endregion

    // region Scan
    ////////////////////////////////////////////////////////////////////////////////////////////////
    //   ######  ########    ###    ########  ########  ######   ######     ###    ##    ##
    //  ##    ##    ##      ## ##   ##     ##    ##    ##    ## ##    ##   ## ##   ###   ##
    //  ##          ##     ##   ##  ##     ##    ##    ##       ##        ##   ##  ####  ##
    //   ######     ##    ##     ## ########     ##     ######  ##       ##     ## ## ## ##
    //        ##    ##    ######### ##   ##      ##          ## ##       ######### ##  ####
    //  ##    ##    ##    ##     ## ##    ##     ##    ##    ## ##    ## ##     ## ##   ###
    //   ######     ##    ##     ## ##     ##    ##     ######   ######  ##     ## ##    ##
    ////////////////////////////////////////////////////////////////////////////////////////////////
    private void startScan(final UUID[] servicesToScan) {
        Timber.d("startScan");

        //isConnected = false;

        // Stop current scanning (if needed)
        stopScanning();

        // Configure scanning
        BluetoothAdapter bluetoothAdapter = BleUtils.getBluetoothAdapter(getApplicationContext());

//        // hack to make devices disappear from the list if they're no longer present
//        if (mScannedDevices != null) {
//            mScannedDevices.clear();
//
//            if (mScannedDevicesAdapter != null) {
//                mScannedDevicesAdapter.notifyDataSetChanged();
//            }
//        }

        // check if Bluetooth adapter is enabled and ready to go
        if (BleUtils.getBleStatus(this) != BleUtils.STATUS_BLE_ENABLED) {
            Log.w(TAG, "startScan: BluetoothAdapter not initialized or unspecified address.");
        }
        else {              // it's good to scan
            // clear the list of scanned devices and reset the UI
//            if (mScannedDevices != null) {
//                mScannedDevices.clear();
//                mScannedDevicesAdapter.notifyDataSetChanged();
//
//                updateUI();
//            }

            mScanner = new BleDevicesScanner(bluetoothAdapter, servicesToScan, new BluetoothAdapter.LeScanCallback() {          // initialize the scanner
                @Override
                public void onLeScan(final BluetoothDevice device, final int rssi, byte[] scanRecord) {
                    // hack to make devices disappear from the list if they're no longer present
                    long currentMillis = SystemClock.uptimeMillis();
//                    if (currentMillis - mLastUpdateMillis > kMinDelayToUpdateUI) {          // Avoid updating when not a new device has been found and the time from the last update is really short to avoid updating UI so fast that it will become unresponsive
//                        mLastUpdateMillis = currentMillis;
//                        if (mScannedDevices != null) {
//                            mScannedDevices.clear();
//
//                            updateUI();
////                            if (mScannedDevicesAdapter != null) {
////                                mScannedDevicesAdapter.notifyDataSetChanged();
////                            }
//                        }
//                    }

                    final String deviceName = device.getName();                                                                 // get scanned device name

                    if (deviceName != null) {
                        Timber.d("found device: " + deviceName);
                    }

                    if (deviceName != null && (deviceName.contains("Preston") || deviceName.contains("PCS"))) {                                            // check for the PCS string to only show applicable devices
                        boolean knownDevice = checkForDeviceInPreferences(device.getAddress(), deviceName);                 // check if the device address is already stored in SharedPreferences
                        if (knownDevice && autoConnect) {                                                                                  // if remembered, connect to it
                            stopScanning();
                            Timber.d("Remembered device detected, connecting");
                            mBleManager.setBleListener(MainActivity.this);                                                  // Force set listener (could be still checking for updates...)
                            connect(device, true);                                                              // connect.
                            return;
                        }

                        BluetoothDeviceData previouslyScannedDeviceData = null;
                        if (mScannedDevices == null) {
                            mScannedDevices = new ArrayList<>();       // Safeguard
                        }
//                        else {
//                            mScannedDevices.clear();
//                        }

                        // Check that the device was not previously found
                        for (BluetoothDeviceData deviceData : mScannedDevices) {
                            if (deviceData.device.getAddress().equals(device.getAddress())) {
                                previouslyScannedDeviceData = deviceData;
                                break;
                            }
                        }

                        BluetoothDeviceData deviceData;
                        if (previouslyScannedDeviceData == null) {
                            // Add it to the mScannedDevice list
                            deviceData = new BluetoothDeviceData();
                            mScannedDevices.add(deviceData);
                        } else {
                            deviceData = previouslyScannedDeviceData;
                        }

                        deviceData.device = device;
                        deviceData.rssi = rssi;
                        deviceData.scanRecord = scanRecord;
                        decodeScanRecords(deviceData);

                        // Update device data

                        if (previouslyScannedDeviceData == null || currentMillis - mLastUpdateMillis > kMinDelayToUpdateUI) {          // Avoid updating when not a new device has been found and the time from the last update is really short to avoid updating UI so fast that it will become unresponsive
                            mLastUpdateMillis = currentMillis;

                            runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateUI();
                                }
                            });
                        }
                    }

                }
            });

            // Start scanning
            mScanner.start();
//            mDevicesFoundTextView.setVisibility(View.VISIBLE);
        }

        // Update UI
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                updateUI();
//            }
//        });
    }

    private void stopScanning() {
        // Stop scanning
        if (mScanner != null) {
            mScanner.stop();
            mScanner = null;
        }

//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                updateUI();
//            }
//        });
    }
    // endregion

    private void decodeScanRecords(BluetoothDeviceData deviceData) {
        // based on http://stackoverflow.com/questions/24003777/read-advertisement-packet-in-android
        final byte[] scanRecord = deviceData.scanRecord;

        ArrayList<UUID> uuids = new ArrayList<>();
        byte[] advertisedData = Arrays.copyOf(scanRecord, scanRecord.length);
        int offset = 0;
        deviceData.type = BluetoothDeviceData.kType_Unknown;

        // Check if is an iBeacon ( 0x02, 0x0x1, a flag byte, 0x1A, 0xFF, manufacturer (2bytes), 0x02, 0x15)
        final boolean isBeacon = advertisedData[0] == 0x02 && advertisedData[1] == 0x01 && advertisedData[3] == 0x1A && advertisedData[4] == (byte) 0xFF && advertisedData[7] == 0x02 && advertisedData[8] == 0x15;

        // Check if is an URIBeacon
        final byte[] kUriBeaconPrefix = {0x03, 0x03, (byte) 0xD8, (byte) 0xFE};
        final boolean isUriBeacon = Arrays.equals(Arrays.copyOf(scanRecord, kUriBeaconPrefix.length), kUriBeaconPrefix) && advertisedData[5] == 0x16 && advertisedData[6] == kUriBeaconPrefix[2] && advertisedData[7] == kUriBeaconPrefix[3];

        if (isBeacon) {
            deviceData.type = BluetoothDeviceData.kType_Beacon;

            // Read uuid
            offset = 9;
            UUID uuid = BleUtils.getUuidFromByteArrayBigEndian(Arrays.copyOfRange(scanRecord, offset, offset + 16));
            uuids.add(uuid);
            offset += 16;

            // Skip major minor
            offset += 2 * 2;   // major, minor

            // Read txpower
            final int txPower = advertisedData[offset++];
            deviceData.txPower = txPower;
        } else if (isUriBeacon) {
            deviceData.type = BluetoothDeviceData.kType_UriBeacon;

            // Read txpower
            final int txPower = advertisedData[9];
            deviceData.txPower = txPower;
        } else {
            // Read standard advertising packet
            while (offset < advertisedData.length - 2) {
                // Length
                int len = advertisedData[offset++];
                if (len == 0) break;

                // Type
                int type = advertisedData[offset++];
                if (type == 0) break;

                // Data
//            Timber.d("record -> lenght: " + length + " type:" + type + " data" + data);

                switch (type) {
                    case 0x02:          // Partial list of 16-bit UUIDs
                    case 0x03: {        // Complete list of 16-bit UUIDs
                        while (len > 1) {
                            int uuid16 = advertisedData[offset++] & 0xFF;
                            uuid16 |= (advertisedData[offset++] << 8);
                            len -= 2;
                            uuids.add(UUID.fromString(String.format("%08x-0000-1000-8000-00805f9b34fb", uuid16)));
                        }
                        break;
                    }

                    case 0x06:          // Partial list of 128-bit UUIDs
                    case 0x07: {        // Complete list of 128-bit UUIDs
                        while (len >= 16) {
                            try {
                                // Wrap the advertised bits and order them.
                                UUID uuid = BleUtils.getUuidFromByteArraLittleEndian(Arrays.copyOfRange(advertisedData, offset, offset + 16));
                                uuids.add(uuid);

                            } catch (IndexOutOfBoundsException e) {
                                Log.e(TAG, "BlueToothDeviceFilter.parseUUID: " + e.toString());
                            } finally {
                                // Move the offset to read the next uuid.
                                offset += 16;
                                len -= 16;
                            }
                        }
                        break;
                    }

                    case 0x09: {
                        byte[] nameBytes = new byte[len - 1];
                        for (int i = 0; i < len - 1; i++) {
                            nameBytes[i] = advertisedData[offset++];
                        }

                        String name = null;
                        try {
                            name = new String(nameBytes, "utf-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }
                        deviceData.advertisedName = name;
                        break;
                    }

                    case 0x0A: {        // TX Power
                        final int txPower = advertisedData[offset++];
                        deviceData.txPower = txPower;
                        break;
                    }

                    default: {
                        offset += (len - 1);
                        break;
                    }
                }
            }

            // Check if Uart is contained in the uuids
            boolean isUart = false;
            for (UUID uuid : uuids) {
                if (uuid.toString().equalsIgnoreCase(com.prestoncinema.app.UartInterfaceActivity.UUID_SERVICE)) {
                    isUart = true;
                    break;
                }
            }
            if (isUart) {
                deviceData.type = BluetoothDeviceData.kType_Uart;
            }
        }

        deviceData.uuids = uuids;
    }


    private void updateUI() {
        if (mScannedDevices != null) {
            Timber.d("updating UI - num of devices: " + mScannedDevices.size());
        }

        // Scan button
        final boolean isListEmpty = mScannedDevices == null || mScannedDevices.size() == 0;

        // Show list and hide "no menu_devices" label
        if (mNoDevicesTextView != null) {
            mNoDevicesTextView.setVisibility(isListEmpty ? View.VISIBLE : View.GONE);
        }

        if (mScannedDevicesAdapter != null) {
            mScannedDevicesAdapter.notifyDataSetChanged();
        }
    }

    // region ResetBluetoothAdapterListener
    @Override
    public void resetBluetoothCompleted() {
        Timber.d("Reset completed -> Resume scanning");
        resumeScanning();
    }
    // endregion

    // launch the selected activity (UART)
    private void launchComponentActivity() {
        // Enable generic attribute service
        final BluetoothGattService genericAttributeService = mBleManager.getGattService(kGenericAttributeService);
        if (genericAttributeService != null) {
            Timber.d("kGenericAttributeService found. Check if kServiceChangedCharacteristic exists");

            final UUID characteristicUuid = UUID.fromString(kServiceChangedCharacteristic);
            final BluetoothGattCharacteristic dataCharacteristic = genericAttributeService.getCharacteristic(characteristicUuid);
            if (dataCharacteristic != null) {
                Timber.d("kServiceChangedCharacteristic exists. Enable indication");
                mBleManager.enableIndication(genericAttributeService, kServiceChangedCharacteristic, true);
            } else {
                Timber.d("Skip enable indications for kServiceChangedCharacteristic. Characteristic not found");
            }
        } else {
            Timber.d("Skip enable indications for kServiceChangedCharacteristic. kGenericAttributeService not found");
        }

        // Launch activity
        showConnectionStatus(false);
    }

    // region BleManagerListener
    @Override
    public void onConnected() {
        Timber.d("\n\n-----------------------------------\nonConnected\n\n-----------------------------------");

        isConnected = true;


        if (connectingDialog != null) {
            Timber.d("dismissing connectingDialog");
            connectingDialog.cancel();
            connectingDialog = null;
        }

        else {
            Timber.d("connectingDialog == null");
        }

        boolean isScanning = mScanner != null && mScanner.isScanning();

        if (isScanning) {
            stopScanning();
        }

        if (rememberDevice) {
            Timber.d("rememberDevice true, add to prefs");
            SharedPreferences deviceHistoryPreferences = getSharedPreferences("deviceHistory", MODE_PRIVATE);
            SharedPreferences deviceNamePreferences = getSharedPreferences("deviceNameHistory", MODE_PRIVATE);
            if (mBleManager.getConnectedDevice() != null) {
                saveDeviceToPreferences(mBleManager.getConnectedDevice(), deviceHistoryPreferences, deviceNamePreferences);
            }
        }

        if (mBleManager != null && mBleManager.getConnectedDevice() != null) {
            updateConnectedButton(isConnected, mBleManager.getConnectedDevice().getName(), true);
        }

        currentDevice = mBleManager.getConnectedDevice();

        mScannedDevices.clear();
        BluetoothDeviceData connectedDeviceData = new BluetoothDeviceData();
        connectedDeviceData.device = currentDevice;
        connectedDeviceData.rssi = -1;
        mScannedDevices.add(connectedDeviceData);

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mScannedDevicesAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onConnecting() {
    }

    @Override
    public void onDisconnected() {
        Timber.d("MainActivity onDisconnected called --------------------");

        isConnected = false;
        updateConnectedButton(isConnected, "", true);

        int connStat = mBleManager.getState();                      // should be 0 for STATE_DISCONNECTED
        Timber.d("connection status: " + connStat);

        // if we're truly disconnected, start scanning again for new modules
        if (connStat == 0) {
            if (connectingDialog != null) {
                connectingDialog.cancel();
                connectingDialog = null;
            }

//            mScannedDevices.remove(mSelectedDeviceData);
            mScannedDevices.clear();

            currentDevice = null;

            boolean isScanning = mScanner != null && mScanner.isScanning();
            if (isScanning) {
                stopScanning();
            } else {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        autostartScan();
                    }
                });
            }
        }
        else {          // otherwise, disconnect
            mBleManager.disconnect();
        }
    }

    @Override
    public void onServicesDiscovered() {
        Timber.d("services discovered");

        // Check if there is a failed installation that was stored to retry
        boolean isFailedInstallationDetected = FirmwareUpdater.isFailedInstallationRecoveryAvailable(this, mBleManager.getConnectedDeviceAddress());
        if (isFailedInstallationDetected) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Timber.d("Failed installation detected");
                    // Ask user if should update
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.scan_failedupdatedetected_title)
                            .setMessage(R.string.scan_failedupdatedetected_message)
                            .setPositiveButton(R.string.scan_failedupdatedetected_ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showConnectionStatus(false);        // hide current dialogs because software update will display a dialog
                                    stopScanning();

                                    mFirmwareUpdater.startFailedInstallationRecovery(MainActivity.this);
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    FirmwareUpdater.clearFailedInstallationRecoveryParams(MainActivity.this);
                                    launchComponentActivity();
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        } else {
            // Check if a firmware update is available
//            boolean isCheckingFirmware = false;
//            if (mFirmwareUpdater != null) {
//                // Don't bother the user waiting for checks if the latest connected device was this one too
//                String deviceAddress = mBleManager.getConnectedDeviceAddress();
//                if (!deviceAddress.equals(mLatestCheckedDeviceAddress)) {
//                    mLatestCheckedDeviceAddress = deviceAddress;
//
                    // Check if should update device software
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            showGettingUpdateInfoState();
                        }
                    });
////                    mFirmwareUpdater.checkFirmwareUpdatesForTheCurrentConnectedDevice();        // continues asynchronously in onFirmwareUpdatesChecked
////                    isCheckingFirmware = true;
//                } else {
//                    Timber.d("Updates: Device already checked previously. Skipping...");
//                }
//            }
//
//            if (!isCheckingFirmware) {
//                onFirmwareUpdatesChecked(false, null, null, null);
//            }
        }
    }

    @Override
    public void onDataAvailable(BluetoothGattCharacteristic characteristic) {
    }

    @Override
    public void onDataAvailable(BluetoothGattDescriptor descriptor) {
    }

    @Override
    public void onReadRemoteRssi(int rssi) {

    }
    // endregion

    private void updateConnectedButton(boolean status, final String deviceName, final boolean showToast) {
        Timber.d("update connected button, status: " + status + ", device name: " + deviceName);
        Timber.d("scannedDevices: " + mScannedDevices.toString());

        final String connectedString = getResources().getString(R.string.disconnect);
        String disconnectedString = getResources().getString(R.string.connect);
        final String statusString = status ? connectedString : disconnectedString;

        String connectedToastString = "Connected to " + deviceName;
        String disconnectedToastString = "Module disconnected";
        final String toastString = status ? connectedToastString : disconnectedToastString;

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (connectedButton != null) {
                    if (showToast) {
                        SharedHelper.makeToast(MainActivity.this, toastString, Toast.LENGTH_LONG);
                    }
                    connectedButton.setText(statusString);
                }

                // TODO: figure out why this keeps getting called (i think mBleManager.connect() keeps getting called)
                if (connectingDialog != null) {
                    connectingDialog.cancel();
                    connectingDialog = null;
                }

                updateUI();
            }
        });
    }

    // region SoftwareUpdateManagerListener
    @Override
    public void onFirmwareUpdatesChecked(boolean isUpdateAvailable, final ReleasesParser.FirmwareInfo latestRelease, FirmwareUpdater.DeviceInfoData deviceInfoData, Map<String, ReleasesParser.BoardInfo> allReleases) {
        mBleManager.setBleListener(this);           // Restore listener

        if (isUpdateAvailable) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    // Ask user if should update
                    String message = String.format(getString(R.string.scan_softwareupdate_messageformat), latestRelease.version);
                    new AlertDialog.Builder(MainActivity.this)
                            .setTitle(R.string.scan_softwareupdate_title)
                            .setMessage(message)
                            .setPositiveButton(R.string.scan_softwareupdate_install, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    showConnectionStatus(false);        // hide current dialogs because software update will display a dialog
                                    stopScanning();
                                    //BluetoothDevice device = mBleManager.getConnectedDevice();
                                    mFirmwareUpdater.downloadAndInstall(MainActivity.this, latestRelease);
                                }
                            })
                            .setNeutralButton(R.string.scan_softwareupdate_notnow, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    launchComponentActivity();
                                }
                            })
                            .setNegativeButton(R.string.scan_softwareupdate_dontask, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mFirmwareUpdater.ignoreVersion(latestRelease.version);
                                    launchComponentActivity();
                                }
                            })
                            .setCancelable(false)
                            .show();
                }
            });
        } else {
            Timber.d("onFirmwareUpdatesChecked: No software update available");
            launchComponentActivity();
        }
    }

    @Override
    public void onUpdateCancelled() {
        Timber.d("Software version installation cancelled");

        mLatestCheckedDeviceAddress = null;

        mScannedDevices.clear();
        startScan(null);
    }

    @Override
    public void onUpdateCompleted() {
        Timber.d("Software version installation completed successfully");

        Toast.makeText(this, R.string.scan_softwareupdate_completed, Toast.LENGTH_LONG).show();

        mScannedDevices.clear();
        startScan(null);
    }

    @Override
    public void onUpdateFailed(boolean isDownloadError) {
        Timber.d("Software version installation failed");
        Toast.makeText(this, isDownloadError ? R.string.scan_softwareupdate_downloaderror : R.string.scan_softwareupdate_updateerror, Toast.LENGTH_LONG).show();

        mLatestCheckedDeviceAddress = null;

        mScannedDevices.clear();
        startScan(null);
    }

    @Override
    public void onUpdateDeviceDisconnected() {
        Timber.d("onUpdateDeviceDisconnected() called -------------------");
        // Update UI
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                onDisconnected();

                mLatestCheckedDeviceAddress = null;

                mScannedDevices.clear();
                startScan(null);
            }
        });
    }



    private boolean checkForDeviceInPreferences(String address, String name) {
//        Timber.d("Checking for device in preferences: " + address + " & " + name);
        SharedPreferences preferences = getSharedPreferences("deviceHistory", MODE_PRIVATE);
        String deviceName = preferences.getString(address, "none");
        boolean productFound = (deviceName.equals(name));
//        Timber.d("looked @ preferences, found address: " + deviceName + "$$");
//        Timber.d("looked @ preferences, productFound: " + productFound);

        return productFound;
//        if (!productFound) {                    // 1 = check after connection
//            saveDeviceToPreferences(address, preferences);
//        }
    }

    private void saveDeviceToPreferences(BluetoothDevice device, SharedPreferences addressPreferences, SharedPreferences namePreferences) {
        // get the device name and address
        String address = device.getAddress();
        String name = device.getName();

        // look for the device's address in shared preferences. if not found, will return "none" and thus boolean will be false
        boolean productFound = (addressPreferences.getString(address, "none") == name);

        // if the device isn't found, add it to the shared preferences
        if (!productFound) {
            SharedPreferences.Editor addressEditor = addressPreferences.edit();                 // open the address preferences for editing
//            SharedPreferences.Editor nameEditor = namePreferences.edit();                       // open the name preferences for editing

            addressEditor.putString(address, name);                                             // add the address
            addressEditor.commit();                                                             // save the address

//            nameEditor.putString()

            Timber.d("Device address written to preferences");
        }
        // if it is found, no need to add it again
        else {
            Timber.d("Device already in preferences");
        }
    }

    public void goToFirmwareUpdateActivity(View view) {
        Intent intent = new Intent(MainActivity.this, FirmwareUpdateActivity.class);
        startActivity(intent);
    }

    public void goToFirmwareCheckActivity(View view) {
        Intent intent = new Intent(MainActivity.this, FirmwareUpdateActivity.class);
        startActivity(intent);
    }

    public void goToLensTransferActivity(View view) {
        Intent intent = new Intent(MainActivity.this, AllLensListsActivity.class);
        startActivity(intent);
    }

    private void forgetBLEDevice(BluetoothDevice device) {
        SharedPreferences sharedPref = getSharedPreferences("deviceHistory", MODE_PRIVATE);         // retrieve the file
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(device.getAddress());
        editor.apply();
        Timber.d("shared pref: " + sharedPref.toString());
        Timber.d("forget BLE device: " + device.getAddress());
    }


    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

//    public void downloadLatestFirmwareVersions(Activity activity, int type, String hexUri) {
//    public void downloadLatestFirmwareVersions() {
//        Timber.d("----------------- downloading firmware versions -------------------------");
////        if (isNetworkAvailable()) {
////        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(mContext);
////        String releasesXml = sharedPreferences.getString("updatemanager_releasesxml", null);
//        String releasesXml = "http://prestoncinema.com/Upgrades/src/firmware.xml";
//        Map<String, PCSReleaseParser.ProductInfo> parsedReleases = PCSReleaseParser.parseReleasesXml(releasesXml);
////            PCSReleaseParser.BasicVersionInfo release = new PCSReleaseParser.BasicVersionInfo();
////            release.fileType = type;
////            release.hexFileUrl = hexUri;
////            release.iniFileUrl = iniUri;
////            downloadAndInstall(activity, release);
////        }
//    }

//    public void downloadAndInstall(Activity activity, PCSReleaseParser.BasicVersionInfo originalRelease) {
//        // Hack to use only hex files if the detected bootloader version is 0x0000
////        String bootloaderVersion = mDeviceInfoData.getBootloaderVersion();
////        boolean useHexOnly = bootloaderVersion.equals(kDefaultBootloaderVersion);
//
//        PCSReleaseParser.BasicVersionInfo release;
////        if (useHexOnly) {
//            // Copy minimum fields required (and don't use the init file)
//            release = new PCSReleaseParser.BasicVersionInfo();
////            release.fileType = originalRelease.fileType;
//            release.hexFileUrl = originalRelease.hexFileUrl;
////        } else {
////            release = originalRelease;
////        }
//
//        // Cancel previous download task if still running
////        if (mDownloadTask != null) {
////            mDownloadTask.cancel(true);
////        }
//
//        // Download files
//        if (isNetworkAvailable()) {
//            mParentActivity = activity;
//
//            mProgressDialog = new ProgressFragmentDialog();
//            Bundle arguments = new Bundle();
//            arguments.putString("message", "Downloading firmware"); //mContext.getString(release.fileType == DfuService.TYPE_APPLICATION ? R.string.firmware_downloading : R.string.bootloader_downloading));          // message should be set before oncreate
//            mProgressDialog.setArguments(arguments);
//
//            mProgressDialog.show(activity.getFragmentManager(), null);
//            activity.getFragmentManager().executePendingTransactions();
//
//            mProgressDialog.setIndeterminate(true);
//            mProgressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
//                @Override
//                public void onCancel(DialogInterface dialog) {
//                    mDownloadTask.cancel(true);
//                    cleanInstallationAttempt(false);
//                    mListener.onUpdateCancelled();
//                }
//            });
//
//            Timber.d("Downloading " + release.hexFileUrl);
//            mDownloadTask = new DownloadTask(mContext, this, kDownloadOperation_Software_Hex);
//            mDownloadTask.setTag(release);
//            mDownloadTask.execute(release.hexFileUrl);          // calls onDownloadCompleted when finished
//        } else {
//            Log.w(TAG, "Can't install latest version. Internet connection not found");
//            Toast.makeText(mContext, mContext.getString(R.string.firmware_connectionnotavailable), Toast.LENGTH_LONG).show();
//        }
//    }

        // endregion

    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
    ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // region Helpers
    private class BluetoothDeviceData {
        BluetoothDevice device;
        public int rssi;
        byte[] scanRecord;
        private String advertisedName;           // Advertised name
        private String cachedNiceName;
        private String cachedName;

        // Decoded scan record (update R.array.scan_devicetypes if this list is modified)
        static final int kType_Unknown = 0;
        static final int kType_Uart = 1;
        static final int kType_Beacon = 2;
        static final int kType_UriBeacon = 3;

        public int type;
        int txPower;
        ArrayList<UUID> uuids;

        String getName() {
            if (cachedName == null) {
                cachedName = device.getName();
                if (cachedName == null) {
                    cachedName = advertisedName;      // Try to get a name (but it seems that if device.getName() is null, this is also null)
                }
            }

            return cachedName;
        }

        String getNiceName() {
            if (cachedNiceName == null) {
                cachedNiceName = getName();
                if (cachedNiceName == null) {
                    cachedNiceName = device.getAddress();
                }
            }

            return cachedNiceName;
        }
    }
    //endregion

    // region Peripheral List
    private class PeripheralList {
        // Constants
        private final static int kMaxRssiValue = -100;

        private final static String kPreferences = "PeripheralList_prefs";
        private final static String kPreferences_filtersName = "filtersName";
        private final static String kPreferences_filtersIsNameExact = "filtersIsNameExact";
        private final static String kPreferences_filtersIsNameCaseInsensitive = "filtersIsNameCaseInsensitive";
        private final static String kPreferences_filtersRssi = "filtersRssi";
        private final static String kPreferences_filtersUnnamedEnabled = "filtersUnnamedEnabled";
        private final static String kPreferences_filtersUartEnabled = "filtersUartEnabled";

        // Data
        private String mFilterName;
        private boolean mIsFilterNameExact;
        private boolean mIsFilterNameCaseInsensitive;
        private int mRssiFilterValue;
        private boolean mIsUnnamedEnabled;
        private boolean mIsOnlyUartEnabled;
        private ArrayList<BluetoothDeviceData> mCachedFilteredPeripheralList;
        private boolean mIsFilterDirty;

        private SharedPreferences.Editor preferencesEditor = getSharedPreferences(kPreferences, MODE_PRIVATE).edit();

        PeripheralList() {
            mIsFilterDirty = true;
            mCachedFilteredPeripheralList = new ArrayList<>();

            SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
            mFilterName = preferences.getString(kPreferences_filtersName, null);
            mIsFilterNameExact = preferences.getBoolean(kPreferences_filtersIsNameExact, false);
            mIsFilterNameCaseInsensitive = preferences.getBoolean(kPreferences_filtersIsNameCaseInsensitive, true);
            mRssiFilterValue = preferences.getInt(kPreferences_filtersRssi, kMaxRssiValue);
            mIsUnnamedEnabled = preferences.getBoolean(kPreferences_filtersUnnamedEnabled, false);
            mIsOnlyUartEnabled = preferences.getBoolean(kPreferences_filtersUartEnabled, true);
        }

        String getFilterName() {
            return mFilterName;
        }

        void setFilterName(String name) {
            mFilterName = name;
            mIsFilterDirty = true;

            preferencesEditor.putString(kPreferences_filtersName, name);
            preferencesEditor.apply();
        }

        boolean isFilterNameExact() {
            return mIsFilterNameExact;
        }

        void setFilterNameExact(boolean exact) {
            mIsFilterNameExact = exact;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersIsNameExact, exact);
            preferencesEditor.apply();
        }

        boolean isFilterNameCaseInsensitive() {
            return mIsFilterNameCaseInsensitive;
        }

        void setFilterNameCaseInsensitive(boolean caseInsensitive) {
            mIsFilterNameCaseInsensitive = caseInsensitive;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersIsNameCaseInsensitive, caseInsensitive);
            preferencesEditor.apply();
        }

        int getFilterRssiValue() {
            return mRssiFilterValue;
        }

        void setFilterRssiValue(int value) {
            mRssiFilterValue = value;
            mIsFilterDirty = true;

            preferencesEditor.putInt(kPreferences_filtersRssi, value);
            preferencesEditor.apply();
        }

        boolean isFilterUnnamedEnabled() {
            return mIsUnnamedEnabled;
        }

        void setFilterUnnamedEnabled(boolean enabled) {
            mIsUnnamedEnabled = enabled;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersUnnamedEnabled, enabled);
            preferencesEditor.apply();
        }


        boolean isFilterOnlyUartEnabled() {
            return mIsOnlyUartEnabled;
        }

        void setFilterOnlyUartEnabled(boolean enabled) {
            mIsOnlyUartEnabled = enabled;
            mIsFilterDirty = true;

            preferencesEditor.putBoolean(kPreferences_filtersUartEnabled, enabled);
            preferencesEditor.apply();
        }


        void setDefaultFilters() {
            mFilterName = null;
            mIsFilterNameExact = false;
            mIsFilterNameCaseInsensitive = true;
            mRssiFilterValue = kMaxRssiValue;
            mIsUnnamedEnabled = false;
            mIsOnlyUartEnabled = true;
        }

        boolean isAnyFilterEnabled() {
            return (mFilterName != null && !mFilterName.isEmpty()) || mRssiFilterValue > kMaxRssiValue || mIsOnlyUartEnabled || !mIsUnnamedEnabled;
        }

        ArrayList<BluetoothDeviceData> filteredPeripherals(boolean forceUpdate) {
            if (mIsFilterDirty || forceUpdate) {
                mCachedFilteredPeripheralList = calculateFilteredPeripherals();
                mIsFilterDirty = false;
            }

            return mCachedFilteredPeripheralList;
        }

        private ArrayList<BluetoothDeviceData> calculateFilteredPeripherals() {

            ArrayList<BluetoothDeviceData> peripherals = (ArrayList<BluetoothDeviceData>) mScannedDevices.clone();

            // Sort menu_devices alphabetically
            Collections.sort(peripherals, new Comparator<BluetoothDeviceData>() {
                @Override
                public int compare(BluetoothDeviceData o1, BluetoothDeviceData o2) {
                    return o1.getNiceName().compareToIgnoreCase(o2.getNiceName());
                }
            });

            // Apply filters
            if (mIsOnlyUartEnabled) {
                for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                    if (it.next().type != BluetoothDeviceData.kType_Uart) {
                        it.remove();
                    }
                }
            }

            if (!mIsUnnamedEnabled) {
                for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                    if (it.next().getName() == null) {
                        it.remove();
                    }
                }
            }

            if (mFilterName != null && !mFilterName.isEmpty()) {
                for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                    String name = it.next().getName();
                    boolean testPassed = false;
                    if (name != null) {
                        if (mIsFilterNameExact) {
                            if (mIsFilterNameCaseInsensitive) {
                                testPassed = name.compareToIgnoreCase(mFilterName) == 0;
                            } else {
                                testPassed = name.compareTo(mFilterName) == 0;
                            }
                        } else {
                            if (mIsFilterNameCaseInsensitive) {
                                testPassed = name.toLowerCase().contains(mFilterName.toLowerCase());
                            } else {
                                testPassed = name.contains(mFilterName);
                            }
                        }
                    }
                    if (!testPassed) {
                        it.remove();
                    }
                }
            }

            for (Iterator<BluetoothDeviceData> it = peripherals.iterator(); it.hasNext(); ) {
                if (it.next().rssi < mRssiFilterValue) {
                    it.remove();
                }
            }

            return peripherals;
        }

        String filtersDescription() {
            String filtersTitle = null;

            if (mFilterName != null && !mFilterName.isEmpty()) {
                filtersTitle = mFilterName;
            }

            if (mRssiFilterValue > kMaxRssiValue) {
                String rssiString = String.format(Locale.ENGLISH, getString(R.string.scan_filters_name_rssi_format), mRssiFilterValue);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + rssiString;
                } else {
                    filtersTitle = rssiString;
                }
            }

            if (!mIsUnnamedEnabled) {
                String namedString = getString(R.string.scan_filters_name_named);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + namedString;
                } else {
                    filtersTitle = namedString;
                }
            }

            if (mIsOnlyUartEnabled) {
                String uartString = getString(R.string.scan_filters_name_uart);
                if (filtersTitle != null && !filtersTitle.isEmpty()) {
                    filtersTitle = filtersTitle + ", " + uartString;
                } else {
                    filtersTitle = uartString;
                }
            }

            return filtersTitle;
        }
    }

    // endregion

    // region adapters
    private class BLEModuleListViewAdapter extends ArrayAdapter {
        private ArrayList<BluetoothDeviceData> devices;
//        private Context context;

//        private class GroupViewHolder {
//            TextView nameTextView;
//            ImageView rssiImageView;
//            Button connectButton;
//        }

//        public BLEModuleListViewAdapter(Context context, ArrayList<BluetoothDeviceData> devices) {
//            super(context, -1, devices);
//            this.devices = devices;
//            this.context = context;
//        }

//        private final int resId;

        public BLEModuleListViewAdapter(Context context, int resource, ArrayList<BluetoothDeviceData> BLEDevices) {
            super(context, -1, BLEDevices);
//            resId = resource;
            this.devices = BLEDevices;
//            Timber.d("resource tag: " + resId);
        }

        @Override
        public Object getItem(int position) {
            return devices.get(position);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_title, parent, false);
            }

            TextView nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
            ImageView rssiImageView = (ImageView) convertView.findViewById(R.id.rssiImageView);
            final Button connectButton = (Button) convertView.findViewById(R.id.connectButton);

            convertView.setTag(position);
            connectButton.setTag(position);

            // Get the Bluetooth Device data from the list
            final BluetoothDeviceData data = (BluetoothDeviceData) getItem(position);

            final String deviceName = data.getNiceName();
            nameTextView.setText(deviceName);

            if (data.device == currentDevice) {
                connectButton.setText(getResources().getString(R.string.disconnect));
            }

            int rrsiDrawableResource = getDrawableIdForRssi(data.rssi);
            rssiImageView.setImageResource(rrsiDrawableResource);

            connectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Timber.d("connect button clicked");
                    connectedButton = connectButton;

                    if (isConnected) {
                        mBleManager.disconnect();
                    }
                    else {
                        onClickDeviceConnect((int) connectButton.getTag());
                    }
                }
            });
//            connectButton.setOnTouchListener(new View.OnTouchListener() {
//                @Override
//                public boolean onTouch(View view, MotionEvent motionEvent) {
//                    if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
//                        Timber.d("onTouch: Device name = " + deviceName);
//                        onClickDeviceConnect((int) connectButton.getTag());
//                        view.performClick();
//                        return true;
//                    }
//
//                    return false;
//                }
//            });

            return convertView;
        }

        private int getDrawableIdForRssi(int rssi) {
            int index;
            if (rssi == 127 || rssi <= -84) {       // 127 reserved for RSSI not available
                index = 0;
            } else if (rssi <= -72) {
                index = 1;
            } else if (rssi <= -60) {
                index = 2;
            } else if (rssi <= -48) {
                index = 3;
            } else {
                index = 4;
            }

            final int kSignalDrawables[] = {
                    R.drawable.signalstrength0,
                    R.drawable.signalstrength1,
                    R.drawable.signalstrength2,
                    R.drawable.signalstrength3,
                    R.drawable.signalstrength4};

            return kSignalDrawables[index];
        }
    }

    private class ExpandableListAdapter extends BaseExpandableListAdapter {
        // Data
        private ArrayList<BluetoothDeviceData> mFilteredPeripherals;

        private class GroupViewHolder {
            TextView nameTextView;
//            TextView descriptionTextView;
            ImageView rssiImageView;
            TextView rssiTextView;
            Button connectButton;
        }

        @Override
        public int getGroupCount() {
            mFilteredPeripherals = mPeripheralList.filteredPeripherals(true);
            return mFilteredPeripherals.size();
        }

        @Override
        public int getChildrenCount(int groupPosition) {
            return 1;
        }

        @Override
        public Object getGroup(int groupPosition) {
            return mFilteredPeripherals.get(groupPosition);
        }

        @Override
        public Spanned getChild(int groupPosition, int childPosition) {
            BluetoothDeviceData deviceData = mFilteredPeripherals.get(groupPosition);

            String text;
            switch (deviceData.type) {
                case BluetoothDeviceData.kType_Beacon:
                    text = getChildBeacon(deviceData);
                    break;

                case BluetoothDeviceData.kType_UriBeacon:
                    text = getChildUriBeacon(deviceData);
                    break;

                default:
                    text = getChildCommon(deviceData);
                    break;
            }

            Spanned result;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                result = Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY);
            } else {
                result = Html.fromHtml(text);
            }
            return result;
        }


        private String getChildUriBeacon(BluetoothDeviceData deviceData) {
            StringBuilder result = new StringBuilder();

            String name = deviceData.getName();
            if (name != null) {
                result.append(getString(R.string.scan_device_localname)).append(": <b>").append(name).append("</b><br>");
            }

            String address = deviceData.device.getAddress();
            result.append(getString(R.string.scan_device_address) + ": <b>" + (address == null ? "" : address) + "</b><br>");

            String uri = UriBeaconUtils.getUriFromAdvertisingPacket(deviceData.scanRecord) + "</b><br>";
            result.append(getString(R.string.scan_device_uribeacon_uri)).append(": <b>").append(uri);

            result.append(getString(R.string.scan_device_txpower)).append(": <b>").append(deviceData.txPower).append("</b>");

            return result.toString();
        }


        private String getChildCommon(BluetoothDeviceData deviceData) {
            StringBuilder result = new StringBuilder();

            String name = deviceData.getName();
            if (name != null) {
                result.append(getString(R.string.scan_device_localname)).append(": <b>").append(name).append("</b><br>");
            }
            String address = deviceData.device.getAddress();
            result.append(getString(R.string.scan_device_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

            StringBuilder serviceText = new StringBuilder();
            if (deviceData.uuids != null) {
                int i = 0;
                for (UUID uuid : deviceData.uuids) {
                    if (i > 0) serviceText.append(", ");
                    serviceText.append(uuid.toString().toUpperCase());
                    i++;
                }
            }
            if (!serviceText.toString().isEmpty()) {
                result.append(getString(R.string.scan_device_services)).append(": <b>").append(serviceText).append("</b><br>");
            }
            result.append(getString(R.string.scan_device_txpower)).append(": <b>").append(deviceData.txPower).append("</b>");

            return result.toString();
        }

        private String getChildBeacon(BluetoothDeviceData deviceData) {
            StringBuilder result = new StringBuilder();

            String name = deviceData.getName();
            if (name != null) {
                result.append(getString(R.string.scan_device_localname)).append(": <b>").append(name).append("</b><br>");
            }
            String address = deviceData.device.getAddress();
            result.append(getString(R.string.scan_device_address)).append(": <b>").append(address == null ? "" : address).append("</b><br>");

            final byte[] manufacturerBytes = {deviceData.scanRecord[6], deviceData.scanRecord[5]};      // Little endan
            String manufacturer = BleUtils.bytesToHex(manufacturerBytes);

            // Check if the manufacturer is known, and replace the tag for a name
            String kKnownManufacturers[] = getResources().getStringArray(R.array.beacon_manufacturers_ids);
            int knownIndex = Arrays.asList(kKnownManufacturers).indexOf(manufacturer);
            if (knownIndex >= 0) {
                String kManufacturerNames[] = getResources().getStringArray(R.array.beacon_manufacturers_names);
                manufacturer = kManufacturerNames[knownIndex];
            }

            result.append(getString(R.string.scan_device_beacon_manufacturer)).append(": <b>").append(manufacturer == null ? "" : manufacturer).append("</b><br>");

            StringBuilder text = new StringBuilder();
            if (deviceData.uuids != null && deviceData.uuids.size() == 1) {
                UUID uuid = deviceData.uuids.get(0);
                text.append(uuid.toString().toUpperCase());
            }
            result.append(getString(R.string.scan_device_uuid)).append(": <b>").append(text).append("</b><br>");

            final byte[] majorBytes = {deviceData.scanRecord[25], deviceData.scanRecord[26]};           // Big endian
            String major = BleUtils.bytesToHex(majorBytes);
            result.append(getString(R.string.scan_device_beacon_major)).append(": <b>").append(major).append("</b><br>");

            final byte[] minorBytes = {deviceData.scanRecord[27], deviceData.scanRecord[28]};           // Big endian
            String minor = BleUtils.bytesToHex(minorBytes);
            result.append(getString(R.string.scan_device_beacon_minor)).append(": <b>").append(minor).append("</b><br>");

            result.append(getString(R.string.scan_device_txpower)).append(": <b>").append(deviceData.txPower).append("</b>");

            return result.toString();
        }


        @Override
        public long getGroupId(int groupPosition) {
            return groupPosition;
        }

        @Override
        public long getChildId(int groupPosition, int childPosition) {
            return childPosition;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public View getGroupView(final int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
            GroupViewHolder holder;

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_title, parent, false);

                holder = new GroupViewHolder();

                holder.nameTextView = (TextView) convertView.findViewById(R.id.nameTextView);
//                holder.nameTextView.setTextColor(0xFFFFFFFF);
//                holder.descriptionTextView = (TextView) convertView.findViewById(R.tag.descriptionTextView);
//                holder.descriptionTextView.setTextColor(0xFFFFFFFF);
                holder.rssiImageView = (ImageView) convertView.findViewById(R.id.rssiImageView);
                holder.connectButton = (Button) convertView.findViewById(R.id.connectButton);
//                holder.connectButton.setTextColor(0xFFFFFFFF);
//                holder.connectButton.setBackgroundColor(0xFF0000DD);

                convertView.setTag(R.string.scan_tag_id, holder);

            } else {
                holder = (GroupViewHolder) convertView.getTag(R.string.scan_tag_id);
            }

            convertView.setTag(groupPosition);
            holder.connectButton.setTag(groupPosition);

            convertView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickScannedDevice(v);
                }
            });

            /*
            holder.connectButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    onClickDeviceConnect(groupPosition);
                }
            });

            convertView.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN ) {
                        onClickScannedDevice(v);
                        return true;
                    }
                    return false;
                }
            });
            */

//            holder.connectButton.setOnTouchListener(new View.OnTouchListener() {
//                @Override
//                public boolean onTouch(View v, MotionEvent event) {
//                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
//                        onClickDeviceConnect(groupPosition);
//                        return true;
//                    }
//                    return false;
//                }
//            });


            BluetoothDeviceData deviceData = mFilteredPeripherals.get(groupPosition);
            holder.nameTextView.setText(deviceData.getNiceName());
//            holder.descriptionTextView.setVisibility(View.GONE);
//            holder.descriptionTextView.setVisibility(deviceData.type != BluetoothDeviceData.kType_Unknown ? View.VISIBLE : View.INVISIBLE);
//            holder.descriptionTextView.setText(getResources().getStringArray(R.array.scan_devicetypes)[deviceData.type]);
//            holder.rssiTextView.setVisibility(View.GONE);
//            holder.rssiTextView.setText(deviceData.rssi == 127 ? getString(R.string.scan_device_rssi_notavailable) : String.valueOf(deviceData.rssi));

            int rrsiDrawableResource = getDrawableIdForRssi(deviceData.rssi);
            holder.rssiImageView.setImageResource(rrsiDrawableResource);

            return convertView;
        }

        private int getDrawableIdForRssi(int rssi) {
            int index;
            if (rssi == 127 || rssi <= -84) {       // 127 reserved for RSSI not available
                index = 0;
            } else if (rssi <= -72) {
                index = 1;
            } else if (rssi <= -60) {
                index = 2;
            } else if (rssi <= -48) {
                index = 3;
            } else {
                index = 4;
            }

            final int kSignalDrawables[] = {
                    R.drawable.signalstrength0,
                    R.drawable.signalstrength1,
                    R.drawable.signalstrength2,
                    R.drawable.signalstrength3,
                    R.drawable.signalstrength4};
            return kSignalDrawables[index];
        }

        @Override
        public View getChildView(final int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = getLayoutInflater().inflate(R.layout.layout_scan_item_child, parent, false);
            }

            // We don't expect many items so for clarity just find the views each time instead of using a ViewHolder
            TextView textView = (TextView) convertView.findViewById(R.id.dataTextView);
            Spanned text = getChild(groupPosition, childPosition);
            textView.setText(text);

            Button rawDataButton = (Button) convertView.findViewById(R.id.rawDataButton);
            rawDataButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ArrayList<BluetoothDeviceData> filteredPeripherals = mPeripheralList.filteredPeripherals(false);
                    if (groupPosition < filteredPeripherals.size()) {
                        final BluetoothDeviceData deviceData = filteredPeripherals.get(groupPosition);
                        final byte[] scanRecord = deviceData.scanRecord;
                        final String packetText = BleUtils.bytesToHexWithSpaces(scanRecord);
                        final String clipboardLabel = getString(R.string.scan_device_advertising_title);

                        new AlertDialog.Builder(MainActivity.this)
                                .setTitle(R.string.scan_device_advertising_title)
                                .setMessage(packetText)
                                .setPositiveButton(android.R.string.ok, null)
                                .setNeutralButton(android.R.string.copy, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                                        ClipData clip = ClipData.newPlainText(clipboardLabel, packetText);
                                        clipboard.setPrimaryClip(clip);
                                    }
                                })
                                .show();
                    }

                }
            });

            return convertView;
        }

        @Override
        public boolean isChildSelectable(int groupPosition, int childPosition) {
            return false;
        }
    }

    //endregion

    // region DataFragment
    public static class DataFragment extends Fragment {
        private ArrayList<BluetoothDeviceData> mScannedDevices;
        private Class<?> mComponentToStartWhenConnected;
        private boolean mShouldEnableWifiOnQuit;
        private FirmwareUpdater mFirmwareUpdater;
        private String mLatestCheckedDeviceAddress;
        private BluetoothDeviceData mSelectedDeviceData;
        //private PeripheralList mPeripheralList;

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setRetainInstance(true);
        }

    }

    private void restoreRetainedDataFragment() {
        // find the retained fragment
        FragmentManager fm = getFragmentManager();
        mRetainedDataFragment = (DataFragment) fm.findFragmentByTag(TAG);

        if (mRetainedDataFragment == null) {
            // Create
            mRetainedDataFragment = new DataFragment();
            fm.beginTransaction().add(mRetainedDataFragment, TAG).commitAllowingStateLoss();        // http://stackoverflow.com/questions/7575921/illegalstateexception-can-not-perform-this-action-after-onsaveinstancestate-h

            mScannedDevices = new ArrayList<>();
            // mPeripheralList = new PeripheralList();

        } else {
            // Restore status
            mScannedDevices = mRetainedDataFragment.mScannedDevices;
            mComponentToStartWhenConnected = mRetainedDataFragment.mComponentToStartWhenConnected;
            mShouldEnableWifiOnQuit = mRetainedDataFragment.mShouldEnableWifiOnQuit;
            mFirmwareUpdater = mRetainedDataFragment.mFirmwareUpdater;
            mLatestCheckedDeviceAddress = mRetainedDataFragment.mLatestCheckedDeviceAddress;
            mSelectedDeviceData = mRetainedDataFragment.mSelectedDeviceData;
            //mPeripheralList = mRetainedDataFragment.mPeripheralList;

            if (mFirmwareUpdater != null) {
                mFirmwareUpdater.changedParentActivity(this);       // set the new activity
            }
        }
    }

    private void saveRetainedDataFragment() {
        mRetainedDataFragment.mScannedDevices = mScannedDevices;
        mRetainedDataFragment.mComponentToStartWhenConnected = mComponentToStartWhenConnected;
        mRetainedDataFragment.mShouldEnableWifiOnQuit = mShouldEnableWifiOnQuit;
        mRetainedDataFragment.mFirmwareUpdater = mFirmwareUpdater;
        mRetainedDataFragment.mLatestCheckedDeviceAddress = mLatestCheckedDeviceAddress;
        mRetainedDataFragment.mSelectedDeviceData = mSelectedDeviceData;
        //mRetainedDataFragment.mPeripheralList = mPeripheralList;
    }
    // endregion

    // region Utils
    private boolean isNetworkAvailable() {
        ConnectivityManager connectivityManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
    // endregion


}
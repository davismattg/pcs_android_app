package com.prestoncinema.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.OpenableColumns;
import android.support.v4.content.FileProvider;
import android.support.v7.widget.RecyclerView;
import android.util.DisplayMetrics;
import android.util.Log;
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
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.DataGenerator;
import com.prestoncinema.app.db.LensListAdapter;
import com.prestoncinema.app.db.LensListClickCallback;
//import com.prestoncinema.app.db.LocalLensListDataSource;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;
import com.prestoncinema.app.model.LensList;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by MATT on 3/9/2017.
 * This activity is used to transfer lenses to/from the HU3. It also has buttons to add/remove lenses from a saved file,
 * and delete saved lens files. There's a lot of commented out stuff left over from the adafruit app
 */


public class LensActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener,
                                AllLensesFragment.OnLensAddedListener, AllLensesFragment.OnChildLensChangedListener,
                                AllLensesFragment.OnLensSelectedListener {
    // Log
    private final static String TAG = LensActivity.class.getSimpleName();
    private static final int LENS_IMPORT_CODE = 69;
    private static final int kActivityRequestCode_Devices = 4;

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
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;

    private boolean lensReceiveMode = false;
    private boolean lensSendMode = false;
    private boolean lensModeConnected = false;
    private boolean startLensTransfer = false;
    private boolean lensDone = false;
    private boolean lensFileLoaded = false;
    private boolean numLensesSent = false;
    private boolean showAllLenses = false;

    private boolean startConnectionSetup = true;
    private boolean isConnectionReady = false;
    private boolean baudRateSent = false;
    private boolean isConnected = false;
    private boolean needToImportDefaultLenses = true;

    private boolean addToExistingLenses = true;
    private ArrayList<String> lensArray = new ArrayList<String>();
    private ArrayList<String> lensFileImportArray = new ArrayList<String>();
    private int baudRate = 19200;
    private boolean baudRateSet = false;
    private String responseExpected = "";
    private StringBuilder lensSBuilder = new StringBuilder("");
    private ProgressBar lensProgress;
    private Handler handler = new Handler();
    private int numAllLenses = 0;
    private int numLenses = 0;
    private int currentLens = 0;
    private int numSelectedLenses = 0;

    private int bottomMarginPx = 70;
    private int bottomMarginDp = 0;

    private byte[] ACK = {06};
    private byte[] NAK = {15};
    private byte[] EOT = {04};
    private byte[] ACK_SYN = {06, 16};
    private byte[] STX = {0x0E};
    private byte[] ETX = {0x0A, 0x0D};
    private String EOTStr = new String(EOT);
    private String ACKStr = new String(ACK);
    private String NAKStr = new String(NAK);
    private String lastDataSent = "";

    private MqttManager mMqttManager;

    private ProgressDialog mProgressDialog;
    private int baudRateWait = 0;
    private int packetsToWait = 2;

    private int MODE_HU3 = 0;
    private int MODE_SHARE = 1;
    private int MODE_SELECTED = 2;

    private ArrayList<String> lensFilesLocal;
    private RecyclerView lensFilesRecyclerView;
    private LensListAdapter lensFileAdapter;
    private LensListParentExpListViewAdapter allLensesAdapter;
    private LinearLayout lensListsLayout;

    private TextView mConnectedTextView;

    private boolean transmitDataAfterSetup = false;
    private boolean transmitAfterReceive = false;

    private AppDatabase database;
    private List<LensEntity> lensesToInsert;
    private ArrayList<LensEntity> lensesToManage;
    private List<LensEntity> lensesToDelete;
    private LensListEntity lensListToInsert;
    private LensListEntity currentLensList;
    private List<LensListEntity> allLensLists;
    private ArrayList<LensEntity> allLenses;
    private ArrayList<LensEntity> selectedLenses;

    private AppExecutors appExecutors;

    private long lastListInsertedId;
    private long lastLensInsertedId;

    private TextView allLensesCountTextView;
    private TextView selectedLensesCountTextView;
    private ImageView allLensesDetailsImageView;
    private ImageView selectedLensesDetailsImageView;

    private Subscription lensListsSubscription;
    private Subscription allLensesSubscription;
    private Subscription selectedLensesSubscription;

    private Observer<List<LensList>> lensListObserver;

    private RelativeLayout selectedLensesLayout;

    private BluetoothDevice device;

    private Menu optionsMenu;

    public LensActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("onCreate called ----------");
        setContentView(R.layout.activity_lens);

        appExecutors = new AppExecutors();
        database = AppDatabase.getInstance(this, appExecutors);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;

        bottomMarginDp = (int) Math.ceil(bottomMarginPx * density);

        if (savedInstanceState != null) {
            Timber.d("bundle is present");
            baudRateSet = savedInstanceState.getBoolean("baudRateSet");
            baudRate = savedInstanceState.getInt("baudRate");
            if (baudRateSet) {
                Timber.d("baudRateSet remembered as true");
            }
            else {
                Timber.d("baudRateSet still false");
            }

            Timber.d("baudRate: " + baudRate);
        }
        else {
            Timber.d("savedInstanceState = null");
//            LensListFragment lensListFragment = new LensListFragment();
//            getSupportFragmentManager().beginTransaction().add(R.id.lens_list_fragment_container,
//                    lensListFragment, LensListFragment.TAG).commit();

        }

        /* UI Initialization */
        selectedLensesLayout = (RelativeLayout) findViewById(R.id.selectedLensesLayout);
        lensProgress = (ProgressBar) findViewById(R.id.lensProgress);
        mImportLensesButton = (Button) findViewById(R.id.ImportLensesButton);
        mExportLensesButton = (Button) findViewById(R.id.ExportLensesButton);
        allLensesCountTextView = (TextView) findViewById(R.id.allLensesCountTextView);
        allLensesDetailsImageView = (ImageView) findViewById(R.id.allLensesDetailsImageView);
        selectedLensesCountTextView = (TextView) findViewById(R.id.selectedLensesCountTextView);
        selectedLensesDetailsImageView = (ImageView) findViewById(R.id.selectedLensesDetailsImageView);
        lensFilesRecyclerView = (RecyclerView) findViewById(R.id.LensFilesRecyclerView);
        lensListsLayout = findViewById(R.id.lensListsLayout);

        selectedLensesDetailsImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PopupMenu menu = new PopupMenu(LensActivity.this, view);
                menu.setOnMenuItemClickListener(selectedLensesDetailsListener);
                MenuInflater inflater = menu.getMenuInflater();
                inflater.inflate(R.menu.menu_selected_lenses, menu.getMenu());
                menu.show();
            }
        });

//        registerForContextMenu(selectedLensesDetailsImageView);

        createLensListsObservable();
        createAllLensesObservable();
        createSelectedLensesObservable();

        lensFileAdapter = new LensListAdapter(lensListClickCallback);
        lensFilesRecyclerView.setAdapter(lensFileAdapter);

        mBleManager = BleManager.getInstance(this);
        isConnected = (mBleManager.getState() == 2);
        Timber.d("isConnected = " + isConnected);
        mConnectedTextView = (TextView) findViewById(R.id.ConnectedTextView);
        registerForContextMenu(mConnectedTextView);

        device = mBleManager.getConnectedDevice();
        if (device != null) {
            updateConnectedIcon();
        }
//        else {
//            updateConnectedIcon();
//        }


//        // Get default_lenses theme colors
//        TypedValue typedValue = new TypedValue();
//        Resources.Theme theme = getTheme();
//        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
//        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);

        invalidateOptionsMenu();        // update options menu with current values

        // Continue
        onServicesDiscovered();

        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }

        if (!baudRateSet) {
            setBaudRate();
        }

        /* Handle Intent to send lenses from LensListActivity */
        Intent intent = getIntent();
        Timber.d("intent: " + intent);

        if (intent.hasExtra("lensTransferInfo")) {
            Timber.d("lensTransferInfo detected");
            Bundle bundle = intent.getBundleExtra("lensTransferInfo");
            if (bundle != null) {
                addToExistingLenses = bundle.getBoolean("addToExisting");
                lensArray = bundle.getStringArrayList("lensArray");

                if (lensArray.size() > 0) {
                    lensFileLoaded = true;
                    numLenses = lensArray.size();
                    currentLens = 0;
                }

                Timber.d("lensArray loaded from intent. NumLenses: " + numLenses);

                if (addToExistingLenses) {
                    startLensTransfer = false;
                    transmitAfterReceive = true;
                }
                else {
                    lensSendMode = true;
                    lensDone = false;
                }

                if (!baudRateSet) {
                    setBaudRate();
                }

                transmitDataAfterSetup = true;
            }
        }
        else {
            Timber.d("populate lensArray normally");
        }


        // Set up an Intent to send back to apps that request a file
        mResultIntent = new Intent("com.prestoncinema.app.ACTION_SEND");

        lensesToManage = new ArrayList<LensEntity>();
        // Set the Activity's result to null to begin with
        setResult(Activity.RESULT_CANCELED, null);

        Timber.d("onCreate finished --------------");
    }

    @Override
    public void onResume() {
        super.onResume();

        Timber.d("onResume called ------");
        // set the BLE module's baudrate to 19200 for connecting to the HU3
//        if (!baudRateSet) {
//            setBaudRate();
//        }

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }

    @Override
    public void onPause() {
        super.onPause();

        Timber.d("onPause called ----------");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);
    }

    /* Method called when the activity begins to stop. We can use this to save certain variables */
    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        Timber.d("onSaveInstanceState called --------------------------");
        savedInstanceState.putBoolean("baudRateSet", baudRateSet);
        savedInstanceState.putInt("baudRate", baudRate);
        // Call the superclass so it can save the view hierarchy state
        super.onSaveInstanceState(savedInstanceState);
    }

//    /* Method called when restoring an activity with a Bundle saved */
//    @Override
//    public void onRestoreInstanceState(Bundle savedInstanceState) {
//        Timber.d("onRestoreInstanceState called --------------");
//        super.onRestoreInstanceState(savedInstanceState);
//
//        baudRateSet = savedInstanceState.getBoolean("baudRateSet");
//        baudRate = savedInstanceState.getInt("baudRate");
//    }

    @Override
    public void onDestroy() {
        // Disconnect mqtt
        if (mMqttManager != null) {
            mMqttManager.disconnect();
        }

        if (lensListsSubscription != null && !lensListsSubscription.isUnsubscribed()) {
            lensListsSubscription.unsubscribe();
        }

        if (allLensesSubscription != null && !allLensesSubscription.isUnsubscribed()) {
            allLensesSubscription.unsubscribe();
        }

        if (selectedLensesSubscription != null && !selectedLensesSubscription.isUnsubscribed()) {
            selectedLensesSubscription.unsubscribe();
        }

        // Retain data
//        saveRetainedDataFragment();

        super.onDestroy();
    }

    /** onLensesSelected handles sending/receiving only selected lenses from the list
     *
     * @param lens
     */
    public void onLensSelected(LensEntity lens) {
        Timber.d("selected lens: " + lens.getTag() + ", checked: " + lens.getChecked());
//        showOrHideFab(lens.getChecked());
        SharedHelper.updateLensChecked(lens);
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
        Timber.d("build and add a new lens");
//        buildLensData(manuf, series, focal1, focal2, serial, note, false, false, false);
    }
    /** onLensDeleted handles when the user deletes a lens from the popup within one of the "My List" tabs
     *
     * @param lens
     */
    public void onLensDeleted(LensEntity lens) {
        Timber.d("delete lens " + lens.getId());
//        deleteLens(lens.getTag());
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
    public void onChildLensChanged(LensEntity lens, String serial, String note, boolean listA, boolean listB, boolean listC) {
        Timber.d("update the lens in the databse - id: " + lens.getId());
//        editLens(lens, focal, serial, note, listA, listB, listC);
    }

    /** onChildLensDeleted handles deleting a lens when the user selects "Delete" from the Edit Lens dialog
     *
     * @param lens
     */
    public void onChildLensDeleted(LensEntity lens) {
        Timber.d("delete child lens " + lens.getId());
//        deleteLens(lens.getTag());
    }

    // the menu that's created when the user long-presses on a lens within the lens list
    // TODO: if file selected is the default file, don't let the user rename or delete it
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
//        AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();

        /* Display the correct ContextMenu depending on whether the user clicked on the lens file ListView
           or the BLE connected TextView */
        switch (v.getId()) {
            case R.id.ConnectedTextView:
                inflater.inflate(R.menu.ble_device_context_menu, menu);
                if (!isConnected) {
                    menu.findItem(R.id.DisconnectBLEMenuItem).setVisible(false);
                    menu.findItem(R.id.ForgetBLEMenuItem).setVisible(false);
                }
                break;
            case R.id.LensFilesRecyclerView:
                inflater.inflate(R.menu.lens_file_context_menu, menu);
                break;

            case R.id.selectedLensesDetailsImageView:
                inflater.inflate(R.menu.menu_selected_lenses, menu);
                break;
        }
    }

    // handle the user's item selection
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo adapterInfo;
        ContextMenu.ContextMenuInfo contextInfo;
        int id;

        Context context = getApplicationContext();
        CharSequence toastText = "This feature coming soon";
        int duration = Toast.LENGTH_SHORT;
        Toast toast = Toast.makeText(context, toastText, duration);
        switch (item.getItemId()) {
//            /* Lens files ListView */
//            case R.id.confirmLensListRename:
//                adapterInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
//                id = (int) adapterInfo.id;
//                confirmLensListRename(getLensFileAtIndex(id), lensFilesLocal.get(id));
//                return true;
//            case R.id.shareLensFile:
//                adapterInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
//                id = (int) adapterInfo.id;
//                shareLensFile(getLensFileAtIndex(id), lensFilesLocal.get(id));
//                return true;
//            case R.id.duplicateLensFile:
//                adapterInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
//                id = (int) adapterInfo.id;
//                duplicateLensFile(getLensFileAtIndex(id), lensFilesLocal.get(id));
//                return true;
//            case R.id.deleteLensFile:
//                confirmLensListDelete(); //getLensListByName(lensFilesLocal.get(id)), lensFilesLocal.get(id));
//                return true;

            // TODO: add ability to disconnect, forget, etc from this activity. Might require putting connection stuff in background service or something
            /* Lens connected TextView */
            case R.id.ScanBLEMenuItem:
                Timber.d("Scan for new devices");
                contextInfo = (ContextMenu.ContextMenuInfo) item.getMenuInfo();
                toast.show();

                return true;
            case R.id.DisconnectBLEMenuItem:
                Timber.d("Disconnect from this device");
                contextInfo = (ContextMenu.ContextMenuInfo) item.getMenuInfo();
                toast.show();

                return true;
            case R.id.ForgetBLEMenuItem:
                Timber.d("Forget this device");
                contextInfo = (ContextMenu.ContextMenuInfo) item.getMenuInfo();
                toast.show();

//                MainActivity main = new MainActivity();
//                forgetBLEDevice(mBleManager.getConnectedDevice());
                return true;
            case R.id.MyDevicesMenuItem:
                Timber.d("Go to my devices");
                contextInfo = (ContextMenu.ContextMenuInfo) item.getMenuInfo();
                Intent intent = new Intent();
                intent.setClass(LensActivity.this, DevicesActivity.class);
                startActivityForResult(intent, kActivityRequestCode_Devices);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * This method creates the Observabe used with RxAndroid to update the UI when the Lens Lists
     * are retrieved from the database
     */
    private void createLensListsObservable() {
        Timber.d("creating observable for lens lists");
        Observable<List<LensListEntity>> lensListsObservable = Observable.fromCallable(new Callable<List<LensListEntity>>() {
            @Override
            public List<LensListEntity> call() {
                return database.lensListDao().loadAllLensLists();
            }
        });

        lensListsSubscription = lensListsObservable
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
                        displayLensLists(lensLists);
                    }
                });
    }

    /**
     * This method displays a collection of Lens Lists to the user. It usually displays all, but could be
     * used to implement sorting or showing a subset of all the Lens Lists on the phone.
     * @param lensLists
     */
    private void displayLensLists(List<LensListEntity> lensLists) {
        Timber.d("displayLensLists (" + lensLists.size() + "): ");

        allLensLists = lensLists;
        lensFileAdapter.setLensLists(allLensLists);
        lensFileAdapter.notifyDataSetChanged();
    }

    /**
     * This method creates the Observabe used with RxAndroid to update the UI when the Lens Lists
     * are retrieved from the database
     */
    private void createAllLensesObservable() {
        Timber.d("creating observable for all lenses");
        Observable<List<LensEntity>> lensListsObservable = Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                return database.lensDao().loadAll();
            }
        });

        allLensesSubscription = lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("All Lenses Observable onCompleted");
                        setAllLensesCount();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("All Lenses Observable onError: " + e);
                    }

                    @Override
                    public void onNext(List<LensEntity> lenses) {
                        Timber.d("All Lenses Observable onNext");
//                        allLenses = new ArrayList(lenses);
                    }
                });
    }

    /**
     * This method creates the Observabe used with RxAndroid to update the UI when the Lens Lists
     * are retrieved from the database
     */
    private void createSelectedLensesObservable() {
        Timber.d("creating observable for selected lenses");
        Observable<List<LensEntity>> lensListsObservable = Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                return database.lensDao().loadSelected();
            }
        });

        selectedLensesSubscription = lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("Selected Lenses Observable onCompleted");
                        if (selectedLenses.size() > 0) {
                            setSelectedLensesCount();
                        }
//                        else {
//
//                        }
//                        selectedLensesLayout.setVisibility(View.VISIBLE);
//                        selectedLensesCountTextView.setText(numSelectedLenses);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("Selected Lenses Observable onError: " + e);
                    }

                    @Override
                    public void onNext(List<LensEntity> lenses) {
                        Timber.d("Selected Lenses Observable onNext");
                        selectedLenses = new ArrayList(lenses);
                    }
                });
    }


//    /**
//     * This method displays a collection of Lens Lists to the user. It usually displays all, but could be
//     * used to implement sorting or showing a subset of all the Lens Lists on the phone.
//     * @param lenses
//     */
//    private void displayLenses(List<LensEntity> lenses) {
//        Timber.d("displayLenses (" + lenses.size() + ")");
//
//        populateAllLenses(lenses);
////        allLenses = new ArrayList(lenses);
////        allLensesExpListViewAdapter.setLenses(allLenses);
////        allLensesExpListViewAdapter.notifyDataSetChanged();
////        lensFileAdapter.setLensLists(allLensLists);
////        lensFileAdapter.notifyDataSetChanged();
//    }

//    private void populateAllLenses(List<LensEntity> lenses) {
//        Timber.d("populating lenses (" + lenses.size() + ")");
//        allLenses.clear();
//        allLenses = new ArrayList<>(lenses);
//        initializeLensListHeaderCount();
//        for (int i=0; i < lenses.size(); i++) {
//            String len = lenses.get(i).getDataString();
//            if (len.length() > 16) {
//                countLensLine(len);
//            }
//        }
//
//        allLensesExpListViewAdapter.setLenses(allLenses);
//        allLensesExpListViewAdapter.notifyDataSetChanged();
//
//        setAllLensesListeners();
//        // TODO: add lens sorting logic
////        sortAllLenses();
//
//        Timber.d("allLenses populated (still need to sort by FL)");
//    }

    private void setAllLensesCount() {
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                numAllLenses = database.lensDao().getCount();
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<Void>() {
            @Override
            public void onSuccess(Void value) {
                Timber.d("number of lenses in database: " + numAllLenses);
                allLensesCountTextView.setText(String.valueOf(numAllLenses));
            }

            @Override
            public void onError(Throwable error) {
                Timber.e("Unable to set all lenses count");

            }
        });
    }

    private void setSelectedLensesCount() {
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                numSelectedLenses = database.lensDao().getSelectedCount();
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        Timber.d("number of selected lenses in database: " + numSelectedLenses);
                        selectedLensesCountTextView.setText(String.valueOf(numSelectedLenses));
                        selectedLensesLayout.setVisibility(View.VISIBLE);
                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) lensListsLayout.getLayoutParams();
                        params.setMargins(0, 0, 0, bottomMarginDp);
                        lensListsLayout.setLayoutParams(params);
                    }

                    @Override
                    public void onError(Throwable error) {
                        Timber.e("Unable to set selected lenses count");

                    }
                });
    }

//    private void setAllLensesListeners() {
//        /* Set the listener for changes made to the "Parent" level of the ExpandableListView - adding a new lens within a given series */
//        allLensesExpListViewAdapter.setParentListener(new LensListParentExpListViewAdapter.LensAddedListener() {
//            @Override
//            public void onAdd(String manuf, String series, int focal1, int focal2, String serial, String note) {
//                parentListener.onLensAdded(manuf, series, focal1, focal2, serial, note);
//            }
//        });
//
//        /* Set the listener for changes made to the "Child" level of the ExpandableListView - editing an existing lens */
//        allLensesExpListViewAdapter.setChildListener(new LensListParentExpListViewAdapter.LensChangedListener() {
//            @Override
//            public void onChange(LensEntity lens, String focal, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
//                childListener.onChildLensChanged(lens, focal, serial, note, myListA, myListB, myListC);
//            }
//
//            @Override
//            public void onDelete(LensEntity lens) {
//                childListener.onChildLensDeleted(lens);
//            }
//        });
//
//        /* Set the listener for sending/receiving only a selected few lenses */
//        allLensesExpListViewAdapter.setSelectedListener(new LensListParentExpListViewAdapter.LensSelectedListener() {
//            @Override
//            public void onSelected(LensEntity lens) {
//                selectedListener.onLensSelected(lens);
//            }
//        });
//    }

//    private void populateMyLists() {
//        myListDataChild.get("My List A").clear();
//        myListDataChild.get("My List B").clear();
//        myListDataChild.get("My List C").clear();
//
//        for (LensEntity thisLens : lensObjectArray) {
//            if (thisLens.getMyListA()) {
//                temporaryLensList = myListDataChild.get("My List A");
//                temporaryLensList.add(thisLens);
//                myListDataChild.put("My List A", temporaryLensList);
//            }
//
//            if (thisLens.getMyListB()) {
//                temporaryLensList = myListDataChild.get("My List B");
//                temporaryLensList.add(thisLens);
//                myListDataChild.put("My List B", temporaryLensList);
//            }
//
//            if (thisLens.getMyListC()) {
//                List<LensEntity> myListCLenses = myListDataChild.get("My List C");
//                myListCLenses.add(thisLens);
//                myListDataChild.put("My List C", myListCLenses);
//            }
//        }
//    }

//    private void initializeLensListHeaderCount() {
//        allLensesTypeHeaderCount.clear();
//        for (int i = 0; i < allLensesTypeHeader.size(); i++) {
//            allLensesTypeHeaderCount.put(i, 0);
//        }
//    }
//
//    private void countLensLine(String lens) {
//        int sub_ind = 16;                                                                           // the tag to chop the lens strings (16 for manufacturer)
//        int key = 0;
//
//        String subLensString = lens.substring(sub_ind, sub_ind + 1).trim();
//        switch(subLensString) {
//            case "0":
//                key = 0;
//                break;
//            case "1":
//                key = 1;
//                break;
//            case "2":
//                key = 2;
//                break;
//            case "3":
//                key = 3;
//                break;
//            case "4":
//                key = 4;
//                break;
//            case "5":
//                key = 5;
//                break;
//            case "6":
//                key = 6;
//                break;
//            case "F":
//                key = 7;
//                break;
//            default:
//                key = 0;
//                break;
//        }
//
//        int currCount = allLensesTypeHeaderCount.get(key);
//        allLensesTypeHeaderCount.put(key, currCount + 1);
//    }

    private void forgetBLEDevice(BluetoothDevice device) {
        SharedPreferences sharedPref = getSharedPreferences("deviceHistory", MODE_PRIVATE);         // retrieve the file
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(device.getAddress());
        editor.apply();
        Timber.d("shared pref: " + sharedPref.toString());
        Timber.d("forget BLE device: " + device.getAddress());
    }

    // TODO: adapt this for use with the database
    private void confirmLensListRename(final String fullName, final String note) {
        Timber.d("Rename lens file: " + fullName);

//        // fullName comes in as "filename.lens", so use the regex to split on the . so you're left with "filename"
//        String[] names = fullName.split("\\.");
//        final String name = names[0];

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens, null);
                final EditText mRenameLensEditText = (EditText) renameLensView.findViewById(R.id.renameLensListNameEditText);
                final EditText mRenameLensNoteEditText = renameLensView.findViewById(R.id.renameLensListNoteEditText);

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
                mRenameLensEditText.setText(fullName);
                mRenameLensEditText.setSelection(fullName.length());

                mRenameLensNoteEditText.setText(note);

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
                        String enteredName = mRenameLensEditText.getText().toString().trim();
                        String enteredNote = mRenameLensNoteEditText.getText().toString().trim();
                        String newName = enteredName;

                        // check if they erroneously included ".lens" in their entry, and if so, don't append ".lens"
                        if (enteredName.contains(".lens")) {
                            newName = enteredName.trim().split(".lens")[0];
                        }

                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {     // rename the lens list
                            renameLensList(fullName, newName, enteredNote);
                            alert.dismiss();
                        }

                        else {
                            Timber.d("file " + newName + "already exists.");

                            // make a toast to inform the user the filename is already in use
                            Context context = getApplicationContext();
                            CharSequence toastText = "Error: invalid filename";
                            int duration = Toast.LENGTH_LONG;

                            Toast toast = Toast.makeText(context, toastText, duration);
                            toast.show();
                        }
                    }
                });
            }
        });
    }

    private void renameLensList(String oldName, String newName, String note) {
        getLensListByName(oldName, newName, note);                                                 // sets currentLensList global variable to the one we want to rename
//        currentLensList.setName(newName);                                           // change the list's name
//
//        updateLensList(currentLensList);
//        insertLensList(currentLensList);                                            // insert the list (w/ new name) into the database
    }

    // duplicate a given lens file (with a new name)
    // TODO: adapt this for use with the database
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
                final EditText mRenameLensEditText = (EditText) renameLensView.findViewById(R.id.renameLensListNameEditText);

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
                            CharSequence toastText = newName + " is an invalid filename";
                            int duration = Toast.LENGTH_LONG;

                            Toast toast = Toast.makeText(context, toastText, duration);
                            toast.show();
                        }
                    }
                });
            }
        });
    }

    private void shareLensFile(File file) {
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
            Timber.e("File Selector", "The selected file can't be shared: " + file.toString() + ": " + e);
        }
    }

    // function to check if filename is already in use
    // TODO: make sure that it checks case-insensitively
    private boolean checkLensFileNames(String newFile) {
        if (newFile.length() == 0) {
            return false;
        }
        else {
            for (LensListEntity list : allLensLists) {
                if (list.getName().equals(newFile.trim())) {
                    return false;
                }
            }
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

    /**
     * Prompt the user for confirmation that the really want to delete a Lens List
     */
    private void confirmLensListDelete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View deleteLensListView = inflater.inflate(R.layout.dialog_delete_lens_list, null);
                final RadioGroup radioGroup = deleteLensListView.findViewById(R.id.lensListDeleteRadioGroup);
                final RadioButton deleteListButton = deleteLensListView.findViewById(R.id.justDeleteListRadioButton);
                final RadioButton deleteListAndLensesButton = deleteLensListView.findViewById(R.id.deleteListAndLensesRadioButton);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(deleteLensListView)
                        .setTitle("File: " + currentLensList.getName())
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                boolean deleteLenses = (radioGroup.getCheckedRadioButtonId() == deleteListAndLensesButton.getId());
                                deleteLensList(currentLensList, deleteLenses);
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

                // force the keyboard to be shown when the alert dialog appears
                alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                alert.show();
            }

        });
    }

    /**
     * This method deletes a LensList from the database. It's called after the user confirms the
     * deletion in the dialog box from confirmLensListDelete()
     * @param list
     */
    private void deleteLensList(final LensListEntity list, final boolean delete) {
        Timber.d("delete list " + list.getName() + "\nDelete lenses also? " + delete);

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                if (delete) {
                    lensesToDelete = database.lensListLensJoinDao().getLensesForList(list.getId());
                }
                database.lensListLensJoinDao().deleteByListId(list.getId());
                database.lensListDao().delete(list);
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<Void>() {
            @Override
            public void onSuccess(Void value) {

                if (delete) {
                    deleteLenses(lensesToDelete, list);
                    setAllLensesCount();
                }
                else {
                    removeLensList(list);
                    lensFileAdapter.notifyDataSetChanged();

                    CharSequence toastText = list.getName() + " deleted.";
                    SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
                }

            }

            @Override
            public void onError(Throwable error) {
                CharSequence toastText = "Error deleting list. Please try again.";
                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
            }
        });
    }

    /**
     * Method to delete the specified lens list from the collection of LensListEntity that represents everything in the DB
     * @param list
     */
    private void removeLensList(LensListEntity list) {
        for (LensListEntity currentList : allLensLists) {
            if (currentList.getId() == list.getId()) {
                allLensLists.remove(currentList);
                return;
            }
        }
    }

    /**
     * Make sure the user actually wants to clear the database of all lenses and lists
     */
    private void askToConfirmLensDelete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View deleteAllLensesView = inflater.inflate(R.layout.dialog_delete_all_lenses, null);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(deleteAllLensesView)
                        .setPositiveButton("I'm Sure", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteEverything();
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

                // force the keyboard to be shown when the alert dialog appears
                alert.show();
            }

        });
    }

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
                allLensLists.clear();
//                allLenses.clear();
                lensFileAdapter.notifyDataSetChanged();

                CharSequence text = "Database cleared successfully";
                SharedHelper.makeToast(LensActivity.this, text, Toast.LENGTH_LONG);
            }

            @Override
            public void onError(Throwable error) {

            }
        });
    }

    private void deleteLenses(final List<LensEntity> lenses, final LensListEntity list) {
        Timber.d("delete lenses from " + list.getName() + " (" + lenses.size() + ")");
        final LensEntity[] lensArr = new LensEntity[lenses.size()];
        for (int i=0; i < lenses.size(); i++) {
            lensArr[i] = lenses.get(i);
        }

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                database.lensDao().delete(lensArr);
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<Void>() {
            @Override
            public void onSuccess(Void value) {
                Timber.d("lenses deleted after list");
                removeLensList(list);
                lensFileAdapter.notifyDataSetChanged();

                CharSequence toastText = list.getName() + " deleted.";
                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
            }

            @Override
            public void onError(Throwable error) {
                CharSequence toastText = "Error deleting list. Please try again.";
                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_SHORT);
            }
        })
        ;
    }

    private int getLensListIndex(LensListEntity list) {
        for (int i=0; i < allLensLists.size(); i++) {
            if (allLensLists.get(i).getId() == list.getId()) {
                return i;
            }
        }

        return -1;
    }

    private void showDeviceInformation() {
        CharSequence connStatus = isConnected ? "Connected to " + device.getName() : "Not Connected";
        SharedHelper.makeToast(LensActivity.this, connStatus, Toast.LENGTH_LONG);
    }

    // TODO: adapt this for use with the database
    public void importLensFile() {
        Timber.d("open file explorer to select and import lens file");
        // create the intent to launch the system's file explorer window
        Intent importLensFileIntent = new Intent(Intent.ACTION_GET_CONTENT);

        // limit the search to plain text files
        importLensFileIntent.setType("*/*");

        // only show files that can be opened (excluding things like a list of contacts or timezones)
        importLensFileIntent.addCategory(Intent.CATEGORY_OPENABLE);

        // start the activity. After user selects a file, onActivityResult action fires. Read the request_code there and import the file
        startActivityForResult(importLensFileIntent, LENS_IMPORT_CODE);
    }

    // TODO: adapt this for use with the database
    private void exportLensFile() {
        Timber.d("Select the lens file to export/share");

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
                    builder.setTitle("Select the lens file to export")
                            .setItems(fileStrings, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
//                                    shareLensFile(savedLensFiles[which], fileStrings[which]);
                                }
                            })
                            .show();
                }

            });
        }

//        shareLensFile(getLensFileAtIndex(tag), lensFilesLocal.get(tag));
    }

    // TODO: get this updating the UI
    private void importDefaultLensList() {
        final LensListEntity list = DataGenerator.generateDefaultLensList();
        final List<LensEntity> lenses = DataGenerator.generateDefaultLenses(list);

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                long listId = database.lensListDao().insert(list);
                long lensId;
                for (LensEntity lens : lenses) {
                    lensId = database.lensDao().insert(lens);
                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(listId, lensId));
                }
                return null;
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<Void>() {
            @Override
            public void onSuccess(Void value) {
                CharSequence text = "Default lenses restored to database";
                SharedHelper.makeToast(LensActivity.this, text, Toast.LENGTH_LONG);
            }

            @Override
            public void onError(Throwable error) {

            }
        });
    }

    // function to import a lens file not received from the HU3. This retrieves a file URI obtained by the file explorer
    // TODO: adapt this for use with the database
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
                Timber.d("Importing lens file: " + line + "$$");                             // one lens at a time
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
                    final EditText renameLensListNameEditText = (EditText) renameLensView.findViewById(R.id.renameLensListNameEditText);
                    final EditText renameLensListNoteEditText = renameLensView.findViewById(R.id.renameLensListNoteEditText);

                    // set the custom view to be the view in the alert dialog and add the other params
                    builder.setView(renameLensView)
                            .setTitle(lensFileImportArray.size() + " lenses imported")
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
                    renameLensListNameEditText.setText(importedName);
                    renameLensListNameEditText.selectAll();

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
                            String enteredName = renameLensListNameEditText.getText().toString().trim();
                            String enteredNote = renameLensListNoteEditText.getText().toString().trim();
                            String newName = enteredName;

                            // check if they erroneously included ".lens" in their entry, and if so, don't append ".lens"
                            if (enteredName.contains(".lens")) {
                                newName = enteredName.replace(".lens", "").trim();
                            }
//                            else {
//                                newName = enteredName.trim() + ".lens";
//                            }

                            // check for duplicate filenames
                            boolean save = checkLensFileNames(newName);

                            if (save) {
                                saveLensListAndLenses(lensFileImportArray, newName, enteredNote, lensFileImportArray.size());
                                alert.dismiss();
                            }

                            else {
                                Timber.d("file " + newName + "already exists.");

                                // make a toast to inform the user the filename is already in use
                                CharSequence toastText = newName + " already exists";
                                SharedHelper.makeToast(LensActivity.this, toastText, Toast.LENGTH_LONG);

                            }
                        }
                    });
                }
            });
        }
    }

    // save the data stored in lensArray to a text file (.lens)
    // TODO: adapt this for use with the database
    private File createTextLensFile(String fileName) {
//        Timber.d("Save lensArray to file, saveAs: " + saveAs);

//        lensListToInsert = new LensListEntity();
//        lensListToInsert.setName(fileString);
        File lensFile = new File(getExternalFilesDir(null), fileName + ".lens");

        if (isExternalStorageWritable()) {
            Timber.d("Number of lenses in array: " + lensArray.size());
//            File lensFile;

//            if (saveAs) {           // if the customer wants to save as a new file, create new filename
//                lensFile = new File(getExternalFilesDir(null), fileString);
//            }
//            else {                  // save w/ same name as before
//                lensFile = new File(fileString);
//            }

//            Timber.d("lensFile: " + lensFile.toString());
            try {
                FileOutputStream fos = new FileOutputStream(lensFile);
                for (String lens : lensArray) {
                    Timber.d("current lens: " + lens);
                    String lensOut = lens; // + "\n";
                    try {
                        fos.write(lensOut.getBytes());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                try {
                    fos.close();
//                    currentLens = 0;
//                    Timber.d("File saved successfully, make toast and update adapter");
                    // refresh the file array to reflect the new name so the UI updates
//                    updateLensFiles();
//                    Intent intent = new Intent(LensListActivity.this, LensActivity.class);
//                    startActivity(intent);

                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return lensFile;
    }

    // function to update the lens files array after changes are made (rename, duplicate, delete) so the UI refreshes
    // TODO: adapt this for use with the database (if necessary)
    private void updateLensFiles() {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                lensFilesLocal.clear();
//                lensFilesLocal.addAll(getLensFiles());
//                Timber.d("lensFilesLocal: " + lensFilesLocal.toString());
//                lensFileAdapter.notifyDataSetChanged();
//            }
//        });
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
        }
    }

    private void uartSendData(byte[] data, boolean wasReceivedFromMqtt) {
        MqttSettings settings = MqttSettings.getInstance(LensActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data.toString(), qos);
            }
        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
//            mSentBytes += data.length();
        }
    }

    private void updateConnectedIcon() {
        Timber.d("updateConnectedIcon: status = " + isConnected);
        if (optionsMenu != null) {
            onPrepareOptionsMenu(optionsMenu);
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(final Menu menu) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (isConnected) {
                    menu.getItem(0).setIcon(R.drawable.ic_bluetooth_connected_blue);
                    menu.getItem(1).setIcon(R.drawable.ic_download_red_24dp);
                }

                else {
                    menu.getItem(0).setIcon(R.drawable.ic_bluetooth_disabled_gray_24dp);
                    menu.getItem(1).setIcon(R.drawable.ic_download_gray_24dp);
                }
            }
        });

        return true;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_lens, menu);

        if (isConnected) {
            menu.getItem(0).setIcon(getResources().getDrawable(R.drawable.ic_bluetooth_connected_blue));
        }

        optionsMenu = menu;

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
//            case R.id.newLensListMenuItem:
//                createNewLensList();
//                return true;
            case R.id.importFromHu3MenuItem:
                enableLensImport();
                return true;
            case R.id.deviceInfoMenuItem:
                showDeviceInformation();
                return true;
            case R.id.importFileMenuItem:
                importLensFile();
                return true;
            case R.id.exportLensesMenuItem:
                exportLensFile();
                return true;
            case R.id.restoreDefaultLensesMenuItem:
                importDefaultLensList();
                return true;
            case R.id.deleteAllLensesMenuItem:
                askToConfirmLensDelete();
                return true;
//            case R.tag.action_help:
//                startHelp();
//                return true;
//
//            case R.tag.action_connected_settings:
//                startConnectedSettings();
//                return true;
//
//            case R.tag.action_refreshcache:
//                if (mBleManager != null) {
//                    mBleManager.refreshDeviceCache();
//                }
//                break;

//            case R.tag.action_mqttsettings:
//                Intent intent = new Intent(this, MqttUartSettingsActivity.class);
//                startActivityForResult(intent, kActivityRequestCode_MqttSettingsActivity);
//                break;

//            case R.tag.action_displaymode_timestamp:
//                setDisplayFormatToTimestamp(true);
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;

//            case R.tag.action_displaymode_text:
//                setDisplayFormatToTimestamp(false);
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;

//            case R.tag.action_datamode_hex:
//                mShowDataInHexFormat = true;
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;
//
//            case R.tag.action_datamode_ascii:
//                mShowDataInHexFormat = false;
//                recreateDataView();
//                invalidateOptionsMenu();
//                return true;
//
//            case R.tag.action_echo:
//                mIsEchoEnabled = !mIsEchoEnabled;
//                invalidateOptionsMenu();
//                return true;
//
//            case R.tag.action_eol:
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

    @Override
    public void onDisconnected() {
        super.onDisconnected();
        isConnected = false;
        Timber.d("Disconnected. Update connected icon");

        updateConnectedIcon();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                CharSequence toastText = "Disconnected from Preston Updater";
                SharedHelper.makeToast(LensActivity.this, toastText, Toast.LENGTH_LONG);
            }
        });
//        finish();
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

                String dataReceived = new String(bytes);

                Timber.d("onDataAvailable: " + dataReceived + "$$");
//                Timber.d("onDataAvailable bytes: " + Arrays.toString(bytes));
                Timber.d("booleans: ");
                Timber.d("startConnectionSetup: " + startConnectionSetup);
                Timber.d("lensReceiveMode: " + lensReceiveMode);
                Timber.d("lensSendMode: " + lensSendMode);

                // startConnectionSetup flag is true if the connection isn't ready
                if (startConnectionSetup) {
                    String lensString = buildLensPacket(bytes);     // buffering the input data
                    processRxData(lensString);                      // analyze the buffered data
                }

                // this is the main situation to handle incoming lens data from the HU3
                if (lensReceiveMode) {
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
                        if (text.contains("1")) {              // if is 1, in command mode. 0 = data mode
                            Timber.d("1 OK response found");
                                //in command mode since module returned 1 OK
                                Timber.d("1 OK found and expected. Device in command mode, send new baud rate");
                                Timber.d("Sending baudrate to device: " + baudRate);
                                uartSendData("AT+BAUDRATE=" + baudRate, false);
                                responseExpected = "OK";
                        }
                        else if (text.contains("0")) {                          //in data mode since module returned 0 from +++
                            Timber.d("Device in data mode");
                            if (responseExpected == "1\nOK\n") {
                                Timber.d("expected 1 OK, but got 0 OK. Switch to command mode");
                                uartSendData("+++", false);
                            }
                            else {
                                Timber.d("device in data mode which is what we want. Cnxn should be g2g");
                                startConnectionSetup = false;
                                isConnectionReady = true;
                                baudRateSet = true;

                                if (transmitDataAfterSetup) {
                                    if (transmitAfterReceive) {
                                        startReceiveFromIntent();
                                    }
                                    else {
                                        startTransmitFromIntent();
                                    }
                                }
                            }
                        } else {                                                // text.contains("ERROR")
                            Timber.d("error detected");
                            uartSendData("+++", false);
                            responseExpected = "1\nOK\n";
                            baudRateWait = 0;
                        }
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
            }
            else if (text.contains("OK")) {
                Timber.d("OK detected, not sending anything");
            }
            else {
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
                lensReceiveMode = false;
                startLensTransfer = false;

                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }

                if (transmitAfterReceive) {
                    startTransmitFromIntent();
                }
                else {
                    askToSaveLenses();
                }
            } else {
                Timber.d("Lens string: " + text);
                lensArray.add(text);
                currentLens += 1;
                uartSendData(ACK, false);
            }

        }
    }

    // function to send the lenses to HU3
    private void transmitLensData(String text) {
        Timber.d("transmitLensData: " + text);
        if (lensSendMode) {
            if (text.contains(ACKStr)) {
                if (!lensDone) {
                    if (numLensesSent) {
                        Timber.d("ACK. Index: " + currentLens + " of " + numLenses);
                        if (currentLens < numLenses) {
                            byte[] startByte = {0x02};

                            uartSendData(startByte, false);
                            uartSendData(SharedHelper.checkLensChars(lensArray.get(currentLens)), false);

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
                        uartSendData(STX, false);
                        uartSendData(numLensesHexString, false);
                        uartSendData(ETX, false);

                        numLensesSent = true;
                    }
                }
                else {
                    Timber.d("HU3 received lenses");
                    lensSendMode = false;
                    lensDone = false;

                    transmitDataAfterSetup = false;

                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            mProgressDialog.hide();
                            CharSequence lensPlural = ((numLenses == 1) ? "lens" : "lenses");
                            // make a toast to inform the user the file was deleted
                            Context context = getApplicationContext();
                            CharSequence toastText = "HU3 successfully received " + numLenses + " " + lensPlural;
                            int duration = Toast.LENGTH_LONG;

                            Toast toast = Toast.makeText(context, toastText, duration);
                            toast.show();
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

    private void startTransmitFromIntent() {
        Timber.d("starting transmit of lenses from Intent");
        currentLens = 0;
        numLenses = lensArray.size();
        byte[] start_byte = {01, 05};
        String startString = new String(start_byte);

        Timber.d("lensArray: " + lensArray.toString());

        if (isConnectionReady) {
            lensSendMode = true;
            uartSendData(startString, false);
            Timber.d("activating lens transfer progress");
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activateLensTransferProgress("TX");
                }
            });
        }
    }

    private void startReceiveFromIntent() {
        Timber.d("starting receive of lenses from Intent");
        lensReceiveMode = true;
        byte[] syn_byte = {0x16};
        String synString = new String(syn_byte);
        uartSendData(synString, false);
    }

    // select the lens file to be sent to HU3
    public void selectLensFile(View view) {
        if (isConnected) {
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

        else {
            makeToast("No Bluetooth module detected. Please connect first.", 0);
        }
    }

    private void createNewLensList() {
        Timber.d("create new lens list");
    }

    /** This method is a helper method to display toasts for various things.
     *
     * @param message
     * @param length
     */
    private void makeToast(final CharSequence message, final int length) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Context context = getApplicationContext();
                int duration = ((length == 0) ? Toast.LENGTH_SHORT : Toast.LENGTH_LONG);
                Toast toast = Toast.makeText(context, message, duration);
                toast.show();
            }
        });
    }

//    private ArrayList<String> getLensFiles() {
//        checkForDefaultLensFile();

//        if (needToImportDefaultLenses) {
//            Timber.d("we need to import default lenses");
//            InputStream defaultLensFileInputStream = getResources().openRawResource(R.raw.default_lenses);              // create an InputStream for the default lens file
//            BufferedReader reader;                                                                                      // initialize the BufferedReader
//            reader = new BufferedReader(
//                    new InputStreamReader(defaultLensFileInputStream));
//            String line;
//            lensArray.clear();                                                                                          // clear the lens array in case there's some junk in there
//            try {
//                while ((line = reader.readLine()) != null) {                                                            // read the file one line at a time
//                    if (line.length() > 0) {
//                        lensArray.add(SharedHelper.checkLensChars(line));                                                                            // add the read lens into the array
//                    }
//                }
//
//                Timber.d("imported default lenses. size: " + lensArray.size());
//                if (lensArray.size() > 0) {                                                                             // make sure something was actually imported
//                    lensFileLoaded = true;                                                                              // set the flag
//                    numLenses = lensArray.size();                                                                       // the number of lenses, used for loops and display on the UI
//                    currentLens = 0;                                                                                    // tag mostly used for looping
//
//                    needToImportDefaultLenses = false;
//                    saveLensListAndLenses(lensArray, "Default Lenses");                                                          // once the lensArray is populated, save it to internal memory
//
//                }
//            } catch (Exception ex) {
//                Timber.d("getLensFiles()", ex);
//            }
//        }

//        /* Import the rest of the lens files */
//        File extPath = new File(getExternalFilesDir(null), "");                                                     // the external files directory is where the lens files are stored
//        File[] savedLensFiles = extPath.listFiles();
//        ArrayList<String> fileStrings = new ArrayList<String>();
//        if (savedLensFiles.length > 0) {
//            for (int i = 0; i < savedLensFiles.length; i++) {
//                String[] splitArray = savedLensFiles[i].toString().split("/");
//                String fString = splitArray[splitArray.length - 1];
//                if (fString.indexOf(".lens") > 0) {
//                    fileStrings.add(i, fString.substring(0, fString.length() - 5));
//                }
//                else {
//                    fileStrings.add(i, fString);
//                }
//            }
//            Timber.d("fileStrings from import: ");
//            Timber.d(fileStrings.toString());
//            return fileStrings;
//        }
//
//        else {
//            return new ArrayList<>();
//        }
//    }

//    private void addLensFilesToDatabase(ArrayList<String> files) {
//        Timber.d("add lens files to database (nothing implemented yet)");
//
////        for (String file : files) {
////            lensListPresenter.insertLensList(file);
////        }
//
//    }

    private void checkForDefaultLensFile() {
        Timber.d("checking if default lenses are present");

        Single.fromCallable(new Callable<LensListEntity>() {
            @Override
            public LensListEntity call() throws Exception {
                return database.lensListDao().loadLensListByName("Default Lenses");
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<LensListEntity>() {
            @Override
            public void onSuccess(LensListEntity value) {
                if (value != null) {
                    needToImportDefaultLenses = !(value.getName().equals("Default Lenses"));
                }
                else {
                    needToImportDefaultLenses = true;
                }
            }

            @Override
            public void onError(Throwable error) {

            }
        });
    }

    /**
     * This method retrieves a lens list from the database using the list's name as the search parameter
     * @param name
     * @return
     */
    private void getLensListByName(final String name, final String newName, final String note) {
        Observable.fromCallable(new Callable<LensListEntity>() {
            @Override
            public LensListEntity call() {
                return database.lensListDao().loadLensListByName(name);
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new Observer<LensListEntity>() {
            @Override
            public void onCompleted() {
                currentLensList.setName(newName);                                           // change the list's name
                currentLensList.setNote(note);
                updateLensList(currentLensList);
            }

            @Override
            public void onError(Throwable e) {

            }

            @Override
            public void onNext(LensListEntity listEntity) {
                currentLensList = listEntity;
            }
        });
    }

    private void insertLensList(final LensListEntity list) {
        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                lastListInsertedId = database.lensListDao().insert(list);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        int lensListIndex = getLensListIndex(list);
                        allLensLists.remove(lensListIndex);
                        allLensLists.add(lensListIndex, list);
                        lensFileAdapter.notifyDataSetChanged();
                        CharSequence toastText = "List saved successfully.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);

                        Timber.d("Last inserted list id = " + lastListInsertedId);
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error saving lens list, please import again.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }
                });
    }

    private void updateLensList(final LensListEntity list) {
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
                        updateLensListInAllLensLists(list);
//                        allLensLists.set(list.getId(), list);
                        lensFileAdapter.notifyDataSetChanged();
//                        CharSequence toastText = "List saved successfully.";
//                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);

//                        Timber.d("Last inserted list id = " + lastListInsertedId);
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

//    private File getLensFileByName(final String name) {
//        File path = new File(getExternalFilesDir(null), name);
//        File[] list = path.listFiles(new FileFilter() {
//            @Override
//            public boolean accept(File pathname) {
//                return pathname.getName().equals(name);
//            }
//        });
//        if (list.length == 1) {
//            return list[0];
//        }
//        else {
//            return new File;
//        }
//    }

    // import the lens file from the text file into an array that can be sent to the HU3
    // TODO: adapt this for use with the database
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

//                askToAddOrReplaceLenses();
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

    private void prepareLensesForExport(String fileName, int mode) {
        lensArray.clear();

        for (LensEntity lens : lensesToManage) {
            String dataStr = lens.getDataString();

            if (dataStr.length() > 100) {
                lensArray.add(dataStr);
            }
        }

        numLenses = lensArray.size();
        currentLens = 0;

        if (mode == MODE_HU3) {
            Timber.d("Send lenses to HU3");
            if (isConnected) {
                confirmLensAddOrReplace();
            }

            else {
                CharSequence text = "No Bluetooth module detected. Please connect and try again";
                SharedHelper.makeToast(LensActivity.this, text, Toast.LENGTH_LONG);
            }
        }

        else if (mode == MODE_SHARE) {
            Timber.d("share the lenses - need to build file");
            File listToExport = createTextLensFile(fileName);
            shareLensFile(listToExport);
        }


    }

    private void confirmLensAddOrReplace() {
        Timber.d("confirmLensSend");

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
                final RadioButton addLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.addLensesRadioButton);
                final RadioButton replaceLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.replaceLensesRadioButton);

                final int numLensesToSend = lensArray.size();
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

                                sendSelectedLenses(addLenses);
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
                alert.show();
            }
        });
    }

    private void beginLensTransmit() {
        Timber.d("beginning lens transmission");

        lensDone = false;
        if (addToExistingLenses) {
            lensReceiveMode = true;
            byte[] syn_byte = {0x16};
            uartSendData(syn_byte, false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activateLensTransferProgress("RX");
                }
            });
        }
        else {
            lensSendMode = true;
            byte[] start = {0x01, 0x05};
            uartSendData(start, false);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    activateLensTransferProgress("TX");
                }
            });
        }
    }

    private void askToAddOrReplaceLenses() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
                final RadioButton addLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.addLensesRadioButton);
                final RadioButton replaceLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.replaceLensesRadioButton);

                final int numLensesToSend = lensArray.size();

                String lensPluralString = ((lensArray.size() == 1) ? "Lens" : "Lenses");

                final String title = "Ready To Send " + numLensesToSend + " " + lensPluralString;

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(sendLensView)
                        .setTitle(title)
                        .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Timber.d("Start the intent to send these lenses");
                                boolean addLenses = addLensesRadioButton.isChecked();
                                sendSelectedLenses(addLenses);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                lensArray.clear();
                            }
                        })
                        .setCancelable(false);

                // create the alert dialog
                final AlertDialog alert = builder.create();
                alert.show();
            }
        });
    }

    private void sendSelectedLenses(boolean add) {
        addToExistingLenses = add;

        if (lensArray.size() > 0) {
            lensFileLoaded = true;
            numLenses = lensArray.size();
            currentLens = 0;
        }

        Timber.d("lensArray loaded. NumLenses: " + numLenses);

        if (addToExistingLenses) {
            startLensTransfer = false;
            transmitAfterReceive = true;
        }
        else {
            lensSendMode = true;
            lensDone = false;
        }

        if (!baudRateSet) {
            setBaudRate();
        }

        transmitDataAfterSetup = true;

        beginLensTransmit();

//        if (add) {              // user wants to add to existing lenses, so
//
//        }
//        else {
//
//        }
    }

    // ask the user to input a name to save the newly imported lenses to a text file
    private void askToSaveLenses() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.hide();

                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(LensActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens.xml, which we'll inflate to the dialog
                View importAndNameLensFileView = inflater.inflate(R.layout.dialog_import_lens_file, null);
                final EditText fileNameEditText = (EditText) importAndNameLensFileView.findViewById(R.id.LensImportFileNameEditText);
                final EditText fileNoteEditText = (EditText) importAndNameLensFileView.findViewById(R.id.LensImportFileNoteEditText);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(importAndNameLensFileView)
                        .setTitle(numLenses + " lenses imported")
                        .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                saveLensListAndLenses(lensArray, fileNameEditText.getText().toString(), fileNoteEditText.getText().toString(), numLenses);
                            }
                        })
                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                currentLens = 0;
                                lensArray.clear();
                            }
                        })
                        .setCancelable(false);

                // create the alert dialog
                final AlertDialog alert = builder.create();

                // force the keyboard to be shown when the alert dialog appears
                alert.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
                alert.show();
            }
        });
    }

    /**
     * This method saves a Lens List and its associated lenses to the database.
     * @param lensArray
     * @param fileName
     */
    private void saveLensListAndLenses(ArrayList<String> lensArray, String fileName, String note, int count) {
//        lensesToInsert.clear();
        lensListToInsert = new LensListEntity();
        lensListToInsert.setName(fileName);
        lensListToInsert.setNote(note);
        lensListToInsert.setCount(count);

        lensesToInsert = SharedHelper.buildLenses(lensArray);

        CharSequence progressText = "Saving lenses to database...";
        final ProgressDialog pd = SharedHelper.createProgressDialog(LensActivity.this, progressText);
        pd.show();

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                lastListInsertedId = database.lensListDao().insert(lensListToInsert);
                for (LensEntity lens : lensesToInsert) {
                    lastLensInsertedId = database.lensDao().insert(lens);
                    Timber.d("inserted lens, returned id = " + lastLensInsertedId);

                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(lastListInsertedId, lastLensInsertedId));
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

                allLensLists.add(lensListToInsert);
                lensFileAdapter.notifyDataSetChanged();
                CharSequence toastText = "List saved successfully.";
                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);

                Timber.d("Last inserted list id = " + lastListInsertedId);
                setAllLensesCount();
            }

            @Override
            public void onError(Throwable error) {
                CharSequence toastText = "Error saving lens list, please import again.";
                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
            }
        });
    }

    public File getLensStorageDir(String lens) {
        // Create the directory for the saved lens files
        File file = new File(getExternalFilesDir(null), lens);
        Timber.d("File: " + file);
        return file;
    }

    // TODO: make sure progress dialog is cleared if user cancels the save of an imported lens. Should pop up as fresh dialog box but currently is stuck at 100
    // function to change the lens transfer progress popup box depending on the mode (TX or RX)
    private void activateLensTransferProgress(String direction) {
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }

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

        new Thread(new Runnable() {
            public void run() {
                mProgressDialog.setProgress(0);
                while (currentLens <= numLenses) {
                    // Update the progress bar
                    handler.post(new Runnable() {
                        public void run() {
                            mProgressDialog.setProgress(currentLens);
                        }
                    });
                    try {
                        // Sleep for 200 milliseconds.
                        //Just to display the progress slowly
                        Thread.sleep(250);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void enableLensImport() {
        if (isConnected && isConnectionReady) {
            Timber.d("enabling lens import");
            lensReceiveMode = true;
            lensArray.clear();
            currentLens = 0;
            byte[] syn_byte = {0x16};
            uartSendData(syn_byte, false);
        }
        else {
            Timber.d("not connected, show toast");
            CharSequence toastText = "Error: Not connected to Preston Updater";
            SharedHelper.makeToast(LensActivity.this, toastText, Toast.LENGTH_SHORT);
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

    private void setBaudRate() {
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



//    private void updateUI() {
////        mSentBytesTextView.setText(String.format(getString(R.string.uart_sentbytes_format), mSentBytes));
////        mReceivedBytesTextView.setText(String.format(getString(R.string.uart_receivedbytes_format), mReceivedBytes));
//    }



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

    private void getLensesForList(final LensList list, final boolean manage, final int mode) {
        Timber.d("getting lenses for list: " + list.getName());
        if (lensesToManage != null) {
            lensesToManage.clear();
        }

        long id = list.getId();

        if (id == 0 && !(list.getName().contains("Default"))) {
            id = lastListInsertedId;
        }

        final long listId = id;
        Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                Timber.d("calling database - list id: " + listId);
                return database.lensListLensJoinDao().getLensesForList(listId);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("getLensesForList onCompleted");

                        if (manage) {
                            Timber.d("lenses to send to other activity: ");
                            for (LensEntity lens : lensesToManage) {
                                Timber.d("lens id = " + lens.getId() + ", " + SharedHelper.constructFocalLengthString(lens.getFocalLength1(), lens.getFocalLength2()));
                            }

                            Intent intent = new Intent(LensActivity.this, LensListActivity.class);
                            intent.putExtra("lensFile", list.getName());
                            intent.putExtra("listId", listId);
                            intent.putExtra("connected", isConnected);
                            intent.putParcelableArrayListExtra("lenses", lensesToManage);
                            startActivity(intent);
                        }

                        else {
                            prepareLensesForExport(list.getName(), mode);
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("getLensesForList onError");
                    }

                    @Override
                    public void onNext(List<LensEntity> lensesEntity) {
                        Timber.d("lenses: " + lensesEntity.toString());
                        lensesToManage = new ArrayList(lensesEntity);
                    }
                });
    }

    private final LensListClickCallback lensListClickCallback = new LensListClickCallback() {
        @Override
        public void onClick(LensListEntity list, View view) {
            Timber.d("file clicked: " + list.getName());
            getLensesForList(list, true, -1);
        }

        /* Handle clicks on the 3 dots ImageView to rename, share, duplicate, or delete the lens list */
        @Override
        public void onClickDetails(LensListEntity list, View v) {
            Timber.d("click on details, list: " + list.getName() + " (" + list.getId() + ")");
            currentLensList = new LensListEntity(list);
            PopupMenu menu = new PopupMenu(LensActivity.this, v);
            menu.setOnMenuItemClickListener(lensListDetailsListener);
            MenuInflater inflater = menu.getMenuInflater();
            inflater.inflate(R.menu.lens_file_context_menu, menu.getMenu());
            menu.show();
        }
    };

    private PopupMenu.OnMenuItemClickListener lensListDetailsListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            Timber.d("current lens list: " + currentLensList.getName());
            switch (item.getItemId()) {
                case R.id.sendLensFile:
                    Timber.d("send the file to HU3");
                    getLensesForList(currentLensList, false, MODE_HU3);
                    return true;
                case R.id.renameLensFile:
                    Timber.d("rename lens list");
                    confirmLensListRename(currentLensList.getName(), currentLensList.getNote());
                    return true;
                case R.id.shareLensFile:
                    Timber.d("share the lens list");
                    getLensesForList(currentLensList, false, MODE_SHARE);
                    return true;
                case R.id.duplicateLensFile:
                    Timber.d("duplicate the lens list");
//                    duplicateLensFile();
                    return true;
                case R.id.deleteLensFile:
                    Timber.d("delete the lens list");
                    confirmLensListDelete();
                    return true;
            }

            return false;
        }
    };

    private PopupMenu.OnMenuItemClickListener selectedLensesDetailsListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            Timber.d("onMenuItemClick for selected lenses");
            switch (item.getItemId()) {
                case R.id.shareSelectedLensesMenuItem:
                    Timber.d("share selected lenses");
//                    getLensesForList(currentLensList, false, MODE_HU3);
                    return true;
                case R.id.clearSelectedLensesMenuItem:
                    Timber.d("clear selected lenses");
                    clearSelectedLenses();
//                    confirmLensListRename(currentLensList.getName());
                    return true;
            }

            return false;
        }
    };

    public void getAllLenses(View view) {
        Timber.d("show all lenses fragment");

        Observable<List<LensEntity>> lensListsObservable = Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                return database.lensDao().loadAll();
            }
        });

        allLensesSubscription = lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("getAllLenses Observable onCompleted");
                        Intent intent = new Intent(LensActivity.this, AllLensesActivity.class);
                        Collections.sort(allLenses);
                        intent.putParcelableArrayListExtra("lenses", allLenses);
                        startActivity(intent);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("All Lenses Observable onError: " + e);
                    }

                    @Override
                    public void onNext(List<LensEntity> lenses) {
                        Timber.d("All Lenses Observable onNext");
                        allLenses = new ArrayList(lenses);
                    }
                });

    }

    public void getSelectedLenses(View view) {
        Timber.d("show selected lenses fragment");

        Observable<List<LensEntity>> lensListsObservable = Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                return database.lensDao().loadSelected();
            }
        });

        selectedLensesSubscription = lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("getSelectedLenses Observable onCompleted");
                        Intent intent = new Intent(LensActivity.this, AllLensesActivity.class);
                        Collections.sort(selectedLenses);
                        intent.putParcelableArrayListExtra("lenses", selectedLenses);
                        intent.putExtra("title", "Selected Lenses");
                        startActivity(intent);
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("All Lenses Observable onError: " + e);
                    }

                    @Override
                    public void onNext(List<LensEntity> lenses) {
                        Timber.d("All Lenses Observable onNext");
                        selectedLenses = new ArrayList(lenses);
                    }
                });

    }

    private void clearSelectedLenses() {
        LensEntity[] lensesArr = new LensEntity[selectedLenses.size()];
        int index = 0;
        for (LensEntity lens : selectedLenses) {
            lens.setChecked(false);
            lensesArr[index] = lens;
            index++;
        }

        updateLenses(lensesArr, MODE_SELECTED);
    }

    private void updateLenses(final LensEntity[] lenses, final int mode) {
        Single.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() {
                return database.lensDao().updateAll(lenses);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Integer>() {
                    @Override
                    public void onSuccess(Integer value) {
                        CharSequence toastText = "";
                        if (value == lenses.length) {
                            // all lenses successfully updated
                            if (mode == MODE_SELECTED) {
                                selectedLenses.clear();
                                numSelectedLenses = 0;

                                selectedLensesCountTextView.setText(String.valueOf(numSelectedLenses));
                                selectedLensesLayout.setVisibility(View.GONE);

                                RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) lensListsLayout.getLayoutParams();
                                params.setMargins(0, 0, 0, 0);
                                lensListsLayout.setLayoutParams(params);

                                toastText = "Selected lenses cleared";
                            }
                        }

                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error saving lens list, please import again.";
                        SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
                    }
                });
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

//    // region Utils
//    private boolean isNetworkAvailable() {
//        ConnectivityManager connMgr = (ConnectivityManager)
//                getSystemService(Context.CONNECTIVITY_SERVICE); // 1
//        NetworkInfo networkInfo = connMgr.getActiveNetworkInfo(); // 2
//        return networkInfo != null && networkInfo.isConnected(); // 3
//    }
    // endregion
}


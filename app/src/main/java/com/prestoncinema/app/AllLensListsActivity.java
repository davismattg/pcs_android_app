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
import android.widget.ListView;
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
import com.prestoncinema.app.db.DatabaseHelper;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
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


public class AllLensListsActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener,
                                LensListFragment.OnLensAddedListener, LensListFragment.OnChildLensChangedListener,
                                LensListFragment.OnLensSelectedListener {
    // Log
    private final static String TAG = AllLensListsActivity.class.getSimpleName();
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
    private boolean checkNumberOfLenses = false;

    private boolean startConnectionSetup = true;
    private boolean isConnectionReady = false;
    private boolean baudRateSent = false;
    private boolean isConnected = false;
    private boolean needToImportDefaultLenses = true;

    private boolean addToExistingLenses = true;
    private ArrayList<String> lensArray = new ArrayList<String>();
    private ArrayList<String> corruptedLensesArray = new ArrayList<String>();
    private ArrayList<String> lensFileImportArray = new ArrayList<String>();
    private int baudRate = 19200;
    private boolean baudRateSet = false;
    private String responseExpected = "";
    private StringBuilder lensSBuilder = new StringBuilder("");
    private ProgressBar lensProgress;
    private Handler handler = new Handler();
    private int numAllLenses = 0;
    private int numLenses = 0;
    private int numLensesToSend = 0;
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

    private String allLensesTitle = "Lens Database";
    private String importedLensesTitle = "Received Lenses";
    private String selectedLensesTitle = "Selected Lenses";

    private MqttManager mMqttManager;

    private ProgressDialog mProgressDialog;
    private int baudRateWait = 0;
    private int packetsToWait = 2;

    private int MODE_HU3 = 0;
    private int MODE_SHARE = 1;
    private int MODE_SELECTED = 2;
    private int MODE_SAVE = 3;

    private String STRING_DELETE = "DELETE";
    private int MAX_LENS_COUNT = 255;

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

    private HashMap<String, Object> lensListAndLensesMap;
    private LensListEntity currentLensList;
    private List<LensListEntity> allLensLists;
    private List<LensListEntity> listsToUpdate;
    private ArrayList<LensListEntity> selectedLensLists;
    private ArrayList<LensEntity> allLenses;
    private ArrayList<LensEntity> selectedLenses;

    private AppExecutors appExecutors;
    private DatabaseHelper databaseHelper;

    private long lastListInsertedId;
    private long lastLensInsertedId;

    private TextView allLensesCountTextView;
    private TextView selectedLensesCountTextView;
    private ImageView allLensesDetailsImageView;
    private ImageView selectedLensesDetailsImageView;

    private Subscription lensListsSubscription;
    private Subscription lensListsCountSubscription;
    private Subscription allLensesSubscription;
    private Subscription selectedLensesSubscription;

    private Observer<List<LensList>> lensListObserver;

    private RelativeLayout selectedLensesLayout;

    private BluetoothDevice device;

    private Menu optionsMenu;

    private TextView lensListsEmptyTextView;

    private AllLensListsArrayAdapter allLensListsArrayAdapter;
    private AllLensListsArrayAdapter.ListSelectedListener allLensListsListener;

    public AllLensListsActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.d("onCreate called ----------");
        setContentView(R.layout.activity_all_lens_lists);

        appExecutors = new AppExecutors();
        database = AppDatabase.getInstance(this, appExecutors);
        databaseHelper = new DatabaseHelper(database);

        DisplayMetrics metrics = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(metrics);
        float density = metrics.density;

        bottomMarginDp = (int) Math.ceil(bottomMarginPx * density);

        lensListsEmptyTextView = findViewById(R.id.allLensListsEmptyTextView);

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
                PopupMenu menu = new PopupMenu(AllLensListsActivity.this, view);
                menu.setOnMenuItemClickListener(selectedLensesDetailsListener);
                MenuInflater inflater = menu.getMenuInflater();
                inflater.inflate(R.menu.menu_selected_lenses, menu.getMenu());
                menu.show();
            }
        });

//        registerForContextMenu(selectedLensesDetailsImageView);

//        createLensListsObservable();
//        createAllLensesObservable();
//        createSelectedLensesObservable();
        resetUI();

        lensFileAdapter = new LensListAdapter(lensListClickCallback);
        lensFilesRecyclerView.setAdapter(lensFileAdapter);

        mBleManager = BleManager.getInstance(this);
        isConnected = (mBleManager.getState() == 2);
        Timber.d("isConnected = " + isConnected);
        mConnectedTextView = (TextView) findViewById(R.id.ConnectedTextView);

        registerForContextMenu(mConnectedTextView);

        allLensesDetailsImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Timber.d("onClick for all Lenses");
                showAllLensesActions(view);
            }
        });

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

        /* Handle Intent to send lenses from LensListDetailsActivity */
        Intent intent = getIntent();
        Timber.d("intent: " + intent);

        if (intent.hasExtra("lensTransferInfo")) {
            Timber.d("lensTransferInfo detected");
            Bundle bundle = intent.getBundleExtra("lensTransferInfo");
            if (bundle != null) {
                addToExistingLenses = bundle.getBoolean("addToExisting");
                lensArray = bundle.getStringArrayList("lensArray");

                checkNumberOfLenses = addToExistingLenses;

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

        selectedLensLists = new ArrayList<>();

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

        if (lensListsCountSubscription != null && !lensListsCountSubscription.isUnsubscribed()) {
            lensListsCountSubscription.unsubscribe();
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

    /** onLensesSelected handles selecting/deselecting lenses from the list
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
    public void onChildLensChanged(LensListEntity lensList, LensEntity lens, String serial, String note, boolean listA, boolean listB, boolean listC) {
        Timber.d("update the lens in the databse - id: " + lens.getId());
//        editListAndLens(lens, focal, serial, note, listA, listB, listC);
    }

    /** onChildLensDeleted handles deleting a lens when the user selects "Delete" from the Edit Lens dialog
     *
     * @param lens
     */
    public void onChildLensDeleted(LensListEntity lensList, LensEntity lens) {
        Timber.d("delete child lens " + lens.getId());
//        deleteLens(lens.getTag());
    }

//    /**
//     * Listener to handle clicks on checkboxes selected when user wants to add the selected lenses to
//     * existing list(s). Once they've selected all their lists and confirmed, get the IDs from
//     * selectedLensLists and enter a record in the join table for that lens/list combo.
//     * @param list
//     * @param isSelected
//     */
//    public void onListSelected(LensListEntity list, boolean isSelected) {
//        Timber.d(list.getName() + " selected: " + isSelected);
//        if (isSelected) {
//            selectedLensLists.add(list);
//        }
//        else {
//            selectedLensLists.remove(list);
//        }
//    }

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

            case R.id.allLensesDetailsImageView:
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
                intent.setClass(AllLensListsActivity.this, DevicesActivity.class);
                startActivityForResult(intent, kActivityRequestCode_Devices);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    /**
     * This method creates the Observable used with RxAndroid to update the UI when the Lens Lists
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
                        Timber.d("createLensListsObservable onCompleted");

                        createLensListsCountObservable();
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

        if (allLensLists.size() == 0) {
            lensListsEmptyTextView.setVisibility(View.VISIBLE);
        }
        else {
            lensListsEmptyTextView.setVisibility(View.GONE);
        }
    }

    /**
     * This method gets the number of lenses for each list from the database. It then sets the
     * LensListEntity.count value for each list in allLensLists and updates the adapter to display
     * the lists with the proper counts.
     */
    private void createLensListsCountObservable() {
        Timber.d("creating observable for lens list counts");
        Observable<Integer> lensListsCountObservable = Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() {
                int count = 0;

                for (LensListEntity list : allLensLists) {
                    count = database.lensListLensJoinDao().getLensCountForList(list.getId());
                    list.setCount(count);
                }

                return count;
            }
        });

        lensListsCountSubscription = lensListsCountObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("createLensListsCountObservable onCompleted");
                        lensFileAdapter.setLensLists(allLensLists);
                        lensFileAdapter.notifyDataSetChanged();
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("Observable onError: " + e);
                    }

                    @Override
                    public void onNext(Integer count) {
                        Timber.d("Observable onNext");
                    }
                });
    }

    /**
     * This method creates the Observable used with RxAndroid to update the UI when the Lens Lists
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
     * This method creates the Observable used with RxAndroid to update the UI when the Lens Lists
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
                            populateListsToUpdate();
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

    private void populateListsToUpdate() {
        final long[] lensIds = new long[selectedLenses.size()];
        int index = 0;

        for (LensEntity lens : selectedLenses) {
            lensIds[index] = lens.getId();
            index++;
        }

        Single.fromCallable(new Callable<List<LensListEntity>>() {
            @Override
            public List<LensListEntity> call() throws Exception {
                return database.lensListLensJoinDao().getListsForLenses(lensIds);
            }
        })
        .subscribeOn(Schedulers.io())
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(new SingleSubscriber<List<LensListEntity>>() {
            @Override
            public void onSuccess(List<LensListEntity> lists) {
                Timber.d("found " + lists.size() + " lists containing selected lenses");
                listsToUpdate = lists;
            }

            @Override
            public void onError(Throwable error) {
                Timber.d(error.getMessage());
                CharSequence text = "Error updating lens list count - please try again";
                SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_SHORT);
            }
        });
    }

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
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens_list.xmlt.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens_list, null);
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
            Uri fileUri = FileProvider.getUriForFile(AllLensListsActivity.this, "com.prestoncinema.app.fileprovider", file);
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
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_delete_lens_list.xml, which we'll inflate to the dialog
                View deleteLensListView = inflater.inflate(R.layout.dialog_delete_lens_list, null);
                final RadioGroup radioGroup = deleteLensListView.findViewById(R.id.lensListDeleteRadioGroup);
                final RadioButton deleteListButton = deleteLensListView.findViewById(R.id.justDeleteListRadioButton);
                final RadioButton deleteListAndLensesButton = deleteLensListView.findViewById(R.id.deleteListAndLensesRadioButton);

                // hide the radio buttons since we don't need to give the user the choice. just keep the lenses in the database
                radioGroup.setVisibility(View.GONE);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(deleteLensListView)
                        .setTitle("File: " + currentLensList.getName())
                        .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
//                                boolean deleteLenses = (radioGroup.getCheckedRadioButtonId() == deleteListAndLensesButton.getId());
                                boolean deleteLenses = false;               // TODO: change this if you unhide radio buttons
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
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_delete_all_lenses.xml, which we'll inflate to the dialog
                View deleteAllLensesView = inflater.inflate(R.layout.dialog_delete_all_lenses, null);
                final EditText deleteEditText = deleteAllLensesView.findViewById(R.id.confirmLensDeleteEditText);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(deleteAllLensesView)
                        .setPositiveButton("I'm Sure", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                String confirmationText = deleteEditText.getText().toString().trim();
                                if (confirmationText.equals(STRING_DELETE)) {
                                    deleteEverything();
                                } else {
                                    CharSequence toastText = "Error: make sure you typed \"DELETE\"";
                                    SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_LONG);
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

                // force the keyboard to be shown when the alert dialog appears
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
                Timber.d("deleteEverything() succeeded");

                CharSequence text = "Database cleared successfully";
                SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_LONG);

                resetUI();
            }

            @Override
            public void onError(Throwable error) {
                Timber.d("deleteEverything() encountered an error");
                Timber.d(error.getMessage());
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

    /**
     * This method shows a toast when the user clicks on the Bluetooth "Connected" icon. It either
     * lets the user see which device they're connected to, or lets them know that no device was found.
     */
    private void showDeviceInformation() {
        CharSequence connStatus;

        if (device != null) {
            connStatus = isConnected ? "Connected to " + device.getName() : "Not Connected";

        }
        else {
            connStatus = "No module found. Try connecting again";
        }

        SharedHelper.makeToast(AllLensListsActivity.this, connStatus, Toast.LENGTH_LONG);
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
                    AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
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

    /**
     * This method restores the 65-lens default list.
     * TODO: make sure this isn't creating duplicates
     */
    private void importDefaultLensList() {
        final LensListEntity list = DataGenerator.generateDefaultLensList();
        final List<LensEntity> lenses = DataGenerator.generateDefaultLenses(list);

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                long listId = database.lensListDao().insert(list);
                long lensId;
                for (LensEntity lens : lenses) {
                    String dataString = LensHelper.removeMyListFromDataString(lens.getDataString());
                    int countInDb = database.lensDao().lensExists(dataString);

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
                resetUI();

                CharSequence text = "Default lenses restored to database";
                SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_LONG);
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

        ArrayList<String> lensArrayFromImport = new ArrayList<String>();

        Cursor returnCursor = getContentResolver().query(uri, null, null, null, null);
        int nameIndex = returnCursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
        int sizeIndex = returnCursor.getColumnIndex(OpenableColumns.SIZE);
        returnCursor.moveToFirst();
        final String importedName = returnCursor.getString(nameIndex).split("\\.")[0].trim();

//        lensFileImportArray.clear();
        InputStream inputStream = getContentResolver().openInputStream(uri);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line;

        while ((line = reader.readLine()) != null) {
            if (line.length() > 0) {
                Timber.d("Importing lens file: " + line + "$$");                             // one lens at a time
                lensArrayFromImport.add(line);                                      // add the read lens into the array
            }
        }

        inputStream.close();
        reader.close();

        askToSaveLenses(lensArrayFromImport, importedName);
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
            Timber.d("Number of lenses in array: " + lensArray.size());
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
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        return lensFile;
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {
        lastDataSent = data;
        Timber.d("lastDataSent: " + lastDataSent + "--");

        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(AllLensListsActivity.this);
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
        MqttSettings settings = MqttSettings.getInstance(AllLensListsActivity.this);
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
                    menu.getItem(1).setIcon(R.drawable.ic_download_white_24dp);
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
            case R.id.newLensListMenuItem:
                createNewLensList();
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
                            new AlertDialog.Builder(AllLensListsActivity.this)
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
                SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_LONG);
            }
        });
//        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    /**
     * This method is called whenever there is data available from the BLE module. To process the data,
     * we first look at the characteristic and service the data is from (UUID_SERVICE) and make sure
     * the UUID is UUID_RX. If so, we get the data as a byte[] and then process it according the
     * boolean flags
     * @param characteristic
     */
    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                // the raw data
                final byte[] bytes = characteristic.getValue();

                // startConnectionSetup flag is true if the connection isn't ready
                if (startConnectionSetup) {
                    String lensString = buildLensPacket(bytes);                                             // buffering the input data
                    processRxData(lensString);                                                              // analyze the buffered data
                }

                // this is the main situation to handle incoming lens data from the HU3
                if (lensReceiveMode) {
                    String lensString = buildLensPacket(bytes);                                             // buffer the input data
                    if (lensString.length() > 0 && isConnectionReady) {
                        receiveLensData(lensString);
                    }
                }

                // boolean toggled true when you want to send lenses to the HU3
                if (lensSendMode) {
                    String text = bytesToText(bytes, true);
                    transmitLensData(text);
                }

                // MQTT publish to RX
                MqttSettings settings = MqttSettings.getInstance(AllLensListsActivity.this);
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
                            if (responseExpected.equals("1\nOK\n")) {
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

    /**
     * This is the main method to handle the incoming data from the HU3 during lens import. It also
     * handles setting up the connection with the HU3 by following Mirko's handshake protocol to
     * determine if an HU3 is attached (startLensTransfer == false).
     * @param text
     */
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
                String trimmedString = text.replaceAll("[^\\w]", "");
                int numLensesOnHU3 = Integer.valueOf(trimmedString, 16);

                // if we are first requesting lenses from the HU3 (i.e. to add to existing lenses),
                // make sure the total won't be over 255
                if (checkNumberOfLenses) {
                    Timber.d("Number of lenses on HU3 already: " + numLensesOnHU3);
                    Timber.d("numLenses: " + numLenses);

                    // the total that will be on the HU3 when all is said and done
                    int numberToAdd = numLensesOnHU3 + numLenses;

                    // if the total will be more than 255
                    if (numberToAdd > MAX_LENS_COUNT) {
                        Timber.d("TOO MANY LENSES!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

                        // let the user know why they can't add more lenses
                        String toastText1 = "Error: HU3 has " + String.valueOf(numLensesOnHU3) + " lenses already. ";
                        String toastText2 = numberToAdd == 255 ? "Cannot add any more." : "Can only add " + String.valueOf(MAX_LENS_COUNT - numLensesOnHU3);
                        final CharSequence toastText = toastText1 + toastText2;

                        // show the toast
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_LONG);
                            }
                        });

                        // set the flag to we don't send anything else to the HU3
                        lensReceiveMode = false;
                    } else {                                                // total <= 255
                        numLenses = numLensesOnHU3;
                        uartSendData(ACK, false);
                        startLensTransfer = true;

                        checkNumberOfLenses = false;
                        Timber.d("Number of lenses detected: " + numLenses);
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                activateLensTransferProgress("RX");
                            }
                        });
                    }
                }

                // not adding to existing lenses, so just send ACK to HU3
                else {
                    numLenses = numLensesOnHU3;

                    uartSendData(ACK, false);
                    startLensTransfer = true;

                    Timber.d("Number of lenses detected: " + numLenses);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            activateLensTransferProgress("RX");
                        }
                    });
                }
            }
        }

        // startLensTransfer == true (ready to import from HU3)
        else {
            // lens transfer completed, HU3 sent EOT. Now we ask the user to save the lenses
            if (text.contains(EOTStr)) {
                Timber.d("EOT detected");
                uartSendData(ACK_SYN, false);
                lensModeConnected = false;
                lensReceiveMode = false;
                startLensTransfer = false;

                if (mProgressDialog != null) {
                    mProgressDialog.dismiss();
                }

                // if we're trying to add lenses to the HU3, transmit now
                if (transmitAfterReceive) {
                    startTransmitFromIntent();
                }

                // otherwise, ask the user how they want to save the lenses
                else {
                    askToSaveLenses(lensArray, "Received Lenses");
                }
            }

            // in process of importing lenses. add the received string and send ACK
            else {
                Timber.d("Lens string: " + text);
                String trimmedString = SharedHelper.checkLensChars(text);

                // check if the lens string is corrupted
                if (SharedHelper.isLensOK(trimmedString)) {
                    lensArray.add(trimmedString);
                }
                else {
                    corruptedLensesArray.add(trimmedString);
                }
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
                        AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
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
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_rename_lens_list.xmlt.xml, which we'll inflate to the dialog
                View renameLensView = inflater.inflate(R.layout.dialog_rename_lens_list, null);
                final EditText renameLensListNameEditText = (EditText) renameLensView.findViewById(R.id.renameLensListNameEditText);
                final EditText renameLensListNoteEditText = renameLensView.findViewById(R.id.renameLensListNoteEditText);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(renameLensView)
                        .setTitle("Create new lens list")
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

                // create the alert dialog
                final AlertDialog alert = builder.create();
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

                        // check for duplicate filenames
                        boolean save = checkLensFileNames(newName);

                        if (save) {
                            LensListEntity newList = new LensListEntity();
                            newList.setName(newName);
                            newList.setNote(enteredNote);
                            newList.setCount(0);

                            insertLensList(newList);
                            alert.dismiss();
                        }

                        else {
                            Timber.d("file " + newName + "already exists.");

                            // make a toast to inform the user the filename is already in use
                            CharSequence toastText = newName + " already exists, please choose a different name";
                            SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_LONG);

                        }
                    }
                });
            }
        });
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

    /**
     * This method retrieves a lens list from the database using the list's ID as the search parameter
     * @param id
     * @return
     */
    private void getLensListById(final long id) {
        Observable.fromCallable(new Callable<LensListEntity>() {
            @Override
            public LensListEntity call() {
                return database.lensListDao().loadLensList(id);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<LensListEntity>() {
                    @Override
                    public void onCompleted() {
                        getLensesForList(currentLensList, false, MODE_SAVE);
//                        currentLensList.setName(newName);                                           // change the list's name
//                        currentLensList.setNote(note);
//                        updateLensList(currentLensList);
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
//                        int lensListIndex = getLensListIndex(list);
//                        allLensLists.remove(lensListIndex);
//                        allLensLists.add(list);
//                        lensFileAdapter.notifyDataSetChanged();
//                        createLensListsObservable();
                        resetUI();
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
                        setAllLensesCount();
                        lensFileAdapter.notifyDataSetChanged();
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

    /**
     * This method gets the data strings ready for export to either the HU3 or lens list file.
     * It uses SharedHelper.buildLensDataString to construct the string, which adds MyList
     * assignments as needed.
     * @param list
     * @param mode
     */
    private void prepareLensesForExport(LensListEntity list, int mode) {
        lensArray.clear();

        // initialize the file name that will be used to export the file (not used for HU3)
        String fileName;
        if (list != null) {
            fileName = list.getName().trim();
        }
        else {
            String timeStamp = new SimpleDateFormat("MM-dd-yyyy HH.mm.ss").format(new Date());
            fileName = "Lens Database - " + timeStamp;
        }

        // get the data strings that will be sent or saved. This makes sure to add/remove My List A/B/C
        for (LensEntity lens : lensesToManage) {
            String dataStr = SharedHelper.buildLensDataString(list, lens);

            if (dataStr.length() > 100) {
                lensArray.add(dataStr);
            }
        }

        numLenses = lensArray.size();
        currentLens = 0;

        // send lenses to the HU3
        if (mode == MODE_HU3) {
            if (isConnected) {
                if (numLenses > MAX_LENS_COUNT) {
                    CharSequence toastText = "Error: HU3 can accept 255 lenses max";
                    SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_LONG);
                }
                else {
                    confirmLensAddOrReplace();
                }
            }

            else {
                CharSequence text = "No Bluetooth module detected. Please connect and try again";
                SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_LONG);
            }
        }

        // send the lenses to a file for export
        else if (mode == MODE_SHARE) {
            File listToExport = createTextLensFile(fileName);
            shareLensFile(listToExport);
        }
    }

    /**
     * This method prompts the user to add the selected lenses to existing ones on the HU3, or replace
     * all existing lenses with the selected ones. Once the user has made their selection, it calls
     * sendSelectedLenses to begin the transmission.
     */
    private void confirmLensAddOrReplace() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_send_lenses.xml, which we'll inflate to the dialog
                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
                final RadioButton addLensesRadioButton = sendLensView.findViewById(R.id.addLensesRadioButton);

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

    /**
     * This method does some last-minute housekeeping on the lenses before actually transmitting to
     * the HU3. It makes sure that the user isn't trying to send more than 255 lenses, and sets up
     * some of the flags that are used to process incoming data from the HU3. It also sets the baud
     * rate in case that wasn't set before. Finally, the lens transfer is initiated by calling
     * beginLensTransmit()
     * @param add
     */
    private void sendSelectedLenses(boolean add) {
        addToExistingLenses = add;

        if (lensArray.size() > 0) {
            lensFileLoaded = true;
            numLenses = lensArray.size();
            currentLens = 0;

            if (addToExistingLenses) {
                checkNumberOfLenses = true;
                startLensTransfer = false;
                transmitAfterReceive = true;
                lensSendMode = true;
            } else {
                lensSendMode = LensHelper.isLensCountOK(numLenses);

                if (lensSendMode) {
                    lensDone = false;
                } else {
                    CharSequence toastText = "Error: HU3 can accept 255 lenses max";
                    SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_LONG);
                }
            }

            if (!baudRateSet) {
                setBaudRate();
            }

            transmitDataAfterSetup = true;

            if (lensSendMode) {
                beginLensTransmit();
            }
        }
    }

    private void beginLensTransmit() {
        lensDone = false;
        if (addToExistingLenses) {
            lensReceiveMode = true;
            byte[] syn_byte = {0x16};
            uartSendData(syn_byte, false);
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

//    private void askToAddOrReplaceLenses() {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                // building the custom alert dialog
//                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
//                LayoutInflater inflater = getLayoutInflater();
//
//                // the custom view is defined in dialog_send_lenses.xml, which we'll inflate to the dialog
//                View sendLensView = inflater.inflate(R.layout.dialog_send_lenses, null);
//                final RadioButton addLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.addLensesRadioButton);
//                final RadioButton replaceLensesRadioButton = (RadioButton) sendLensView.findViewById(R.id.replaceLensesRadioButton);
//
//                final int numLensesToSend = lensArray.size();
//
//                String lensPluralString = ((lensArray.size() == 1) ? "Lens" : "Lenses");
//
//                final String title = "Ready To Send " + numLensesToSend + " " + lensPluralString;
//
//                // set the custom view to be the view in the alert dialog and add the other params
//                builder.setView(sendLensView)
//                        .setTitle(title)
//                        .setPositiveButton("Send", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                Timber.d("Start the intent to send these lenses");
//                                boolean addLenses = addLensesRadioButton.isChecked();
//                                sendSelectedLenses(addLenses);
//                            }
//                        })
//                        .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                lensArray.clear();
//                            }
//                        })
//                        .setCancelable(false);
//
//                // create the alert dialog
//                final AlertDialog alert = builder.create();
//                alert.show();
//            }
//        });
//    }

    /**
     * After importing the lenses from the HU3 or a saved lens list file, let the user pick which
     * ones they actually want to save to the database. This is done by building an ArrayList<LensEntity>
     * of all the imported lenses and passing them to AllLensesActivity. From there, the user selects
     * which lenses they want to keep, as well as what action to take with those lenses.
     */
    private void askToSaveLenses(ArrayList<String> lensData, String title) {
        // update the numLenses variable since some of the imported lenses might have been corrupted
        numLenses = lensData.size();

        // build LensEntity objects from the raw data string from the HU3
        ArrayList<LensEntity> importedLenses = new ArrayList<>(SharedHelper.buildLenses(lensData));

        // get the lens lists currently on phone in case user wants to add these lenses to existing list
        ArrayList<LensListEntity> lensLists = new ArrayList<>(allLensLists);

        // add some extras into the intent (lenses and a couple flags)
        Intent intent = new Intent(AllLensListsActivity.this, AllLensesActivity.class);
        intent.putParcelableArrayListExtra("lenses", importedLenses);
        intent.putParcelableArrayListExtra("lists", lensLists);
        intent.putExtra("isConnected", isConnected);
        intent.putExtra("fromImport", true);
        intent.putExtra("title", title);
        intent.putExtra("listNote", "");

        // launch the activity
        startActivity(intent);
    }

//    /**
//     * This method saves a Lens List and its associated lenses to the database.
//     * @param lensArray
//     * @param fileName
//     */
//    private void saveLensListAndLenses(ArrayList<String> lensArray, String fileName, String note, int count) {
////        lensesToInsert.clear();
////        lensListToInsert = new LensListEntity();
////        lensListToInsert.setName(fileName);
////        lensListToInsert.setNote(note);
////        lensListToInsert.setCount(count);
//
//        lensListAndLensesMap = SharedHelper.buildLensListAndLenses(fileName, note, count, lensArray);
//        lensListToInsert = (LensListEntity) lensListAndLensesMap.get("list");
//        lensesToInsert = (List<LensEntity>) lensListAndLensesMap.get("lenses");
////        lensesToInsert = SharedHelper.buildLenses(lensArray);
//
//        CharSequence progressText = "Saving lenses to database...";
//        final ProgressDialog pd = SharedHelper.createProgressDialog(AllLensListsActivity.this, progressText);
//        pd.show();
//
//        Single.fromCallable(new Callable<Void>() {
//            @Override
//            public Void call() {
//                lastListInsertedId = database.lensListDao().insert(lensListToInsert);
//                for (LensEntity lens : lensesToInsert) {
//                    lastLensInsertedId = database.lensDao().insert(lens);
//                    Timber.d("inserted lens, returned id = " + lastLensInsertedId);
//
//                    database.lensListLensJoinDao().insert(new LensListLensJoinEntity(lastListInsertedId, lastLensInsertedId));
//                }
//                return null;
//            }
//        })
//        .subscribeOn(Schedulers.io())
//        .observeOn(AndroidSchedulers.mainThread())
//        .subscribe(new SingleSubscriber<Void>() {
//            @Override
//            public void onSuccess(Void value) {
//                pd.dismiss();
//
//                getLensListById(lastListInsertedId);
////                allLensLists.add(lensListToInsert);
////                lensFileAdapter.notifyDataSetChanged();
//                createLensListsObservable();
//                CharSequence toastText = "List saved successfully.";
//                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
//
//                Timber.d("Last inserted list id = " + lastListInsertedId);
//                setAllLensesCount();
//            }
//
//            @Override
//            public void onError(Throwable error) {
//                CharSequence toastText = "Error saving lens list, please import again.";
//                SharedHelper.makeToast(getApplicationContext(), toastText, Toast.LENGTH_LONG);
//            }
//        });
//    }

    private void prepareLensesForImport(LensListEntity list) {
        currentLensList = SharedHelper.buildLensList(list, lensesToManage);
        updateLensList(currentLensList);
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
            CharSequence toastText = "Error: Not connected to PCS Bluetooth Module";
            SharedHelper.makeToast(AllLensListsActivity.this, toastText, Toast.LENGTH_SHORT);
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
                    new AlertDialog.Builder(AllLensListsActivity.this)
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

    private void getLensesForList(final LensListEntity list, final boolean manage, final int mode) {
        long id;

        if (list != null) {
            Timber.d("getting lenses for list: " + list.getName());
            if (lensesToManage != null) {
                lensesToManage.clear();
            }

            id = list.getId();

            if (id == 0 && !(list.getName().contains("Default"))) {
                id = lastListInsertedId;
            }
        }

        else {
            id = 0;
        }

        final long listId = id;

        Observable.fromCallable(new Callable<List<LensEntity>>() {
            @Override
            public List<LensEntity> call() {
                if (list != null) {
                    return database.lensListLensJoinDao().getLensesForList(listId);
                }
                else {
                    return database.lensDao().loadAll();
                }
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<List<LensEntity>>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("getLensesForList onCompleted");

                        if (manage) {
                            ArrayList<LensListEntity> allLensListsArrayList = new ArrayList<>(allLensLists);

                            Intent intent = new Intent(AllLensListsActivity.this, LensListDetailsActivity.class);
                            intent.putExtra("lensFile", list.getName());
                            intent.putExtra("listId", listId);
                            intent.putExtra("listNote", list.getNote());
                            intent.putExtra("connected", isConnected);
                            intent.putExtra("allLensLists", allLensListsArrayList);
                            intent.putParcelableArrayListExtra("lenses", lensesToManage);
                            startActivity(intent);
                        }

                        else {
                            String name;
                            if (list != null) {
                                name = list.getName();
                            }
                            else {
                                String timeStamp = new SimpleDateFormat("MM-dd-yyyy HH.mm.ss").format(new Date());
                                name = "Lens Database - " + timeStamp;
                            }

                            if (mode == MODE_SAVE) {
                                prepareLensesForImport(list);
                            }

                            else {
                                prepareLensesForExport(list, mode);
                            }
                        }
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("getLensesForList onError");
                    }

                    @Override
                    public void onNext(List<LensEntity> lensesEntity) {
                        lensesToManage = new ArrayList(lensesEntity);
                    }
                });
    }

    /**
     * This method shows the PopupMenu when the user clicks the 3 dots on the Lens Database.
     * This gives them the option to Share or delete all the lenses. The user's selection is handled
     * by allLensesActionsListener.
     * @param v
     */
    private void showAllLensesActions(View v) {
        PopupMenu menu = new PopupMenu(AllLensListsActivity.this, v);
        menu.setOnMenuItemClickListener(allLensesActionsListener);
        MenuInflater inflater = menu.getMenuInflater();
        inflater.inflate(R.menu.all_lenses_context_menu, menu.getMenu());
        menu.show();
    }

    private PopupMenu.OnMenuItemClickListener allLensesActionsListener = new PopupMenu.OnMenuItemClickListener() {
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case R.id.shareAllLenses:
                    Timber.d("share the lens database");
                    getLensesForList(null, false, MODE_SHARE);
                    return true;
                case R.id.deleteAllLenses:
                    Timber.d("delete the database of lenses");
                    askToConfirmLensDelete();
                    return true;
            }

            return false;
        }
    };

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
            PopupMenu menu = new PopupMenu(AllLensListsActivity.this, v);
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
                    if (isConnected) {
                        Timber.d("send the file to HU3");
                        getLensesForList(currentLensList, false, MODE_HU3);
                    }
                    else {
                        CharSequence text = "Error: No module connected";
                        SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_LONG);
                    }
                    return true;
                case R.id.renameLensFile:
                    Timber.d("rename lens list");
                    confirmLensListRename(currentLensList.getName(), currentLensList.getNote());
                    return true;
                case R.id.shareLensFile:
                    Timber.d("share the lens list");
                    getLensesForList(currentLensList, false, MODE_SHARE);
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
                    getSelectedLensesShareAction();
                    return true;
                case R.id.clearSelectedLensesMenuItem:
                    Timber.d("clear selected lenses");
                    clearSelectedLenses();
                    return true;
                case R.id.deleteSelectedLensesMenuItem:
                    Timber.d("delete selected lenses from database");
                    confirmSelectedLensesDelete();
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
                        goToAllLensesActivity();
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

    private void goToAllLensesActivity() {
        Intent intent = new Intent(AllLensListsActivity.this, AllLensesActivity.class);
        intent.putExtra("isConnected", isConnected);
        intent.putExtra("fromImport", false);
        intent.putExtra("title", allLensesTitle);
        intent.putExtra("listNote", "");
        intent.putParcelableArrayListExtra("lists", new ArrayList<LensListEntity>());
        startActivity(intent);
    }

    private void getSelectedLensesShareAction() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // building the custom alert dialog
                final AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
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

                lensesToManage = selectedLenses;

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
                            SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_SHORT);
                        }
                    }
                });

                toNew.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("send selected lenses to new list");
                        createNewListAndLenses();
                        alert.dismiss();
                    }
                });

                toExisting.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        Timber.d("send selected lenses to existing list");
                        selectExistingLensLists();
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

    private void askToNameExportedLensFile() {
        CharSequence title = "Please enter a file name";

        AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
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

                        LensListEntity lensList = new LensListEntity(); //SharedHelper.buildLensList(null, name, "", lensesToManage.size(), lensesToManage);
                        lensList.setName(name);

                        prepareLensesForExport(lensList, MODE_SHARE);
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

    private void createNewListAndLenses() {
        CharSequence title = "Create new lens list";

        AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
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
                        String name = listNameEditText.getText().toString();
                        String note = listNoteEditText.getText().toString();

                        LensListEntity lensList = SharedHelper.buildLensList(null, name, note, selectedLenses.size(), selectedLenses);

                        // insert the lenses and list into the database
                        DatabaseHelper.insertLensesAndList(AllLensListsActivity.this, selectedLenses, lensList); //name, note, selectedLenses.size(), false);
//                        createLensListsObservable();
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

    private void selectExistingLensLists() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                View existingLensListsView = inflater.inflate(R.layout.dialog_add_to_existing_lens_list, null);
                ListView existingLensListsListView = existingLensListsView.findViewById(R.id.existingLensListsListView);
                allLensListsArrayAdapter = new AllLensListsArrayAdapter(AllLensListsActivity.this, new ArrayList<>(allLensLists));
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
                            insertLensListJoins(selectedLenses, selectedLensLists);
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
                        resetUI();
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

    public void getSelectedLenses(View view) {
        Timber.d("show selected lenses fragment");

        final ArrayList<LensListEntity> allLensListsArrayList = new ArrayList<>(allLensLists);
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
                        Intent intent = new Intent(AllLensListsActivity.this, AllLensesActivity.class);
                        intent.putParcelableArrayListExtra("lenses", selectedLenses);
                        intent.putParcelableArrayListExtra("lists", allLensListsArrayList);
                        intent.putExtra("fromImport", false);
                        intent.putExtra("title", selectedLensesTitle);
                        intent.putExtra("listNote", "");
                        intent.putExtra("isConnected", isConnected);
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

    /**
     * Make sure the user actually wants to clear the database of the selected lenses
     */
    private void confirmSelectedLensesDelete() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlertDialog.Builder builder = new AlertDialog.Builder(AllLensListsActivity.this);
                LayoutInflater inflater = getLayoutInflater();

                // the custom view is defined in dialog_delete_selected_lenses.xml, which we'll inflate to the dialog
                View deleteAllLensesView = inflater.inflate(R.layout.dialog_delete_selected_lenses, null);

                // set the custom view to be the view in the alert dialog and add the other params
                builder.setView(deleteAllLensesView)
                        .setPositiveButton("I'm Sure", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                deleteLenses();
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

    private void deleteLenses() {
        Timber.d("delete the selected lenses");

        final LensEntity[] toDelete = populateLensesToDelete(selectedLenses);

        Single.fromCallable(new Callable<Void>() {
            @Override
            public Void call() {
                database.lensDao().delete(toDelete);
                return null;
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Void>() {
                    @Override
                    public void onSuccess(Void value) {
//                        for (LensEntity lens : selectedLenses) {
//                            updateLensListCount(lens, true);
//                        }

//                        updateLensLists(listsToUpdate);

                        selectedLenses.clear();
                        numSelectedLenses = 0;

//                        selectedLensesCountTextView.setText(String.valueOf(numSelectedLenses));
                        selectedLensesLayout.setVisibility(View.GONE);

                        RelativeLayout.LayoutParams params = (RelativeLayout.LayoutParams) lensListsLayout.getLayoutParams();
                        params.setMargins(0, 0, 0, 0);
                        lensListsLayout.setLayoutParams(params);

                        CharSequence text = toDelete.length + " lenses deleted";
                        SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_LONG);

                        resetUI();
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence text = "Error deleting selected lenses";
                        SharedHelper.makeToast(AllLensListsActivity.this, text, Toast.LENGTH_LONG);
                    }
                });
    }

    private LensEntity[] populateLensesToDelete(List<LensEntity> lenses) {
        LensEntity[] lensesArr = new LensEntity[lenses.size()];
        int i = 0;
        for (LensEntity lens : lenses) {
            lensesArr[i] = lens;
            i++;
        }

        return lensesArr;
    }

    private void updateLensListCount(final LensEntity lens, final boolean remove) {
        Timber.d("update lens list count for lens id = " + lens.getId());

        int count;
        if (remove) {
            for (int i = 0; i < listsToUpdate.size(); i++) {
                LensListEntity list = listsToUpdate.get(i);
                count = (list.getCount()) - 1;
                list.setCount(count);
            }
        }

        else {
            count = currentLensList.getCount() + 1;
            currentLensList.setCount(count);
        }
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

    /**
     * This method updates the UI with the latest data from the database by re-creating all the
     * subscriptions
     */
    private void resetUI() {
        if (allLensLists != null) {
            allLensLists.clear();
        }

        if (allLenses != null) {
            allLenses.clear();
        }

        if (selectedLenses != null) {
            selectedLenses.clear();
        }

        if (lensFileAdapter != null) {
            lensFileAdapter.notifyDataSetChanged();
        }

        createAllLensesObservable();
        createLensListsObservable();
        createLensListsCountObservable();
        createSelectedLensesObservable();
    }
}


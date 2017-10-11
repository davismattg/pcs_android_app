package com.prestoncinema.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.text.InputType;
import android.text.SpannableStringBuilder;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.prestoncinema.app.R;
import com.prestoncinema.app.CommonHelpActivity;
import com.prestoncinema.app.DownloadCompleteListener;
import com.prestoncinema.app.DownloadFirmwareTask;
import com.prestoncinema.app.UartInterfaceActivity;
import com.prestoncinema.app.settings.ConnectedSettingsActivity;
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
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;

//import com.adafruit.bluefruit.le.connect.app.update.DownloadTask;


public class UartActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener, DownloadCompleteListener {
    // Log
    private final static String TAG = UartActivity.class.getSimpleName();

    // Configuration
    private final static boolean kUseColorsForData = true;
    public final static int kDefaultMaxPacketsToPaintAsText = 500;

    // Activity request codes (used for onActivityResult)
    private static final int kActivityRequestCode_ConnectedSettingsActivity = 0;
    private static final int kActivityRequestCode_MqttSettingsActivity = 1;
    private static final int kActivityRequestCode_SelectLensFile = 2;

    // Constants
    private final static String kPreferences = "UartActivity_prefs";
    private final static String kPreferences_eol = "eol";
    private final static String kPreferences_echo = "echo";
    private final static String kPreferences_asciiMode = "ascii";
    private final static String kPreferences_timestampDisplayMode = "timestampdisplaymode";
    private final static String kPreferences_versions = "firmwareVersions";

    // Colors
    private int mTxColor;
    private int mRxColor;
    private int mInfoColor = Color.parseColor("#F21625");

    // UI
//    private EditText mBufferTextView;
//    private ListView mBufferListView;
    private TimestampListAdapter mBufferListAdapter;
//    private EditText mSendEditText;
    private MenuItem mMqttMenuItem;
    private Handler mMqttMenuItemAnimationHandler;
//    private TextView mSentBytesTextView;
//    private TextView mReceivedBytesTextView;

    // UI TextBuffer (refreshing the text buffer is managed with a timer because a lot of changes an arrive really fast and could stall the main thread)
    private Handler mUIRefreshTimerHandler = new Handler();
    private Runnable mUIRefreshTimerRunnable = new Runnable() {
        @Override
        public void run() {
            if (isUITimerRunning) {
                updateTextDataUI();
                // Log.d(TAG, "updateDataUI");
                mUIRefreshTimerHandler.postDelayed(this, 200);
            }
        }
    };
    private boolean isUITimerRunning = false;
    private boolean readyToProgram = false;
    private boolean erased = false;
    private boolean programMode = false;
    private boolean isProgramming = false;
    private boolean lensMode = false;
    private boolean lensSendMode = false;
    private boolean lensModeConnected = false;
    private boolean startLensTransfer = false;
    private boolean lensDone = false;
    private boolean lensFileLoaded = false;
    private boolean numLensesSent = false;
    private boolean programLoaded = false;
    private boolean startConnectionSetup = false;
    private boolean isConnectionReady = false;
    private boolean baudRateSent = false;
    private boolean isFirstGoodPacket = true;
    private boolean commandMode = false;
    private boolean updateConfirmed = false;
    private boolean updateConfirmEntered = false;
    private int currentLine = 0;
//    private int currentLensInd = 0;
    private int progressStatus = 0;
    private String responseExpected = "";
    private ArrayList<String> fileArray = new ArrayList<String>();
    private ArrayList<String> productNameArray = new ArrayList<String>();
    private ArrayList<String> lensArray = new ArrayList<String>();
    private int[] baudRateArray = {9600, 19200, 115200};
    private int baudRateIndex = 0;
    private StringBuilder sBuilder = new StringBuilder("");
    private StringBuilder lensSBuilder = new StringBuilder("");
    private ProgressBar uploadProgress;
    private ProgressBar lensProgress;
    private Handler handler = new Handler();
    private int fileSize = 0;
    private int numLenses = 0;
    private int currentLens = 0;
    private URL pcsURL = new URL("http://www.prestoncinema.com/");

    private int baudRateWait = 0;          // number of data packets to wait after changing baud rate

    private byte[] ACK = {06};
    private byte[] NAK = {15};
    private byte[] EOT = {04};
    private byte[] ACK_SYN = {06, 16};
    private String EOTStr = new String(EOT);
    private String ACKStr = new String(ACK);
    private String NAKStr = new String(NAK);
    private String lastDataSent = "";
    private byte[] lastDataSentByte;

    private String s19FileName = "";
    private String productRxString = "";
    private String pcsPath = "http://prestoncinema.com/Upgrades/src/firmware-new.xml";
    private String latestVersion = "";
    private boolean isDownloadDone = false;

    private int baudRate = 9600;

    private DownloadCompleteListener downloadCompleteListener;

    // Data
    private boolean mShowDataInHexFormat;
    private boolean mIsTimestampDisplayMode;
    private boolean mIsEchoEnabled;
    private boolean mIsEolEnabled;

    private volatile SpannableStringBuilder mTextSpanBuffer;
    private volatile ArrayList<UartDataChunk> mDataBuffer;
    private volatile int mSentBytes;
    private volatile int mReceivedBytes;

    private DataFragment mRetainedDataFragment;

    private MqttManager mMqttManager;

    private int maxPacketsToPaintAsText;

//    private ListFragment mListFragment;
    private ProgressDialog mProgressDialog;

    public UartActivity() throws MalformedURLException {
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_uart);
        uploadProgress = (ProgressBar) findViewById(R.id.uploadProgress);
        lensProgress = (ProgressBar) findViewById(R.id.lensProgress);

        mBleManager = BleManager.getInstance(this);
//        mDownloadCompleteListener = new DownloadCompleteListener();
//        restoreRetainedDataFragment();

        // Get default_lenses theme colors
        TypedValue typedValue = new TypedValue();
        Resources.Theme theme = getTheme();
        theme.resolveAttribute(R.attr.colorPrimaryDark, typedValue, true);
        mTxColor = typedValue.data;
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);
        mRxColor = typedValue.data;
        // UI
//        mBufferListView = (ListView) findViewById(R.id.bufferListView);
        mBufferListAdapter = new TimestampListAdapter(this, R.layout.layout_uart_datachunkitem);
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
        //Log.d(TAG, "maxPacketsToPaintAsText: "+maxPacketsToPaintAsText);

        // Read local preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
//        SharedPreferences firmwareVersionsPref = getSharedPreferences(kPreferences_versions, MODE_PRIVATE);

        mShowDataInHexFormat = !preferences.getBoolean(kPreferences_asciiMode, true);
//        final boolean isTimestampDisplayMode = preferences.getBoolean(kPreferences_timestampDisplayMode, false);
//        setDisplayFormatToTimestamp(isTimestampDisplayMode);
        mIsEchoEnabled = preferences.getBoolean(kPreferences_echo, true);
        mIsEolEnabled = preferences.getBoolean(kPreferences_eol, true);
        invalidateOptionsMenu();        // udpate options menu with current values

        // Continue
        onServicesDiscovered();

        // Product Array Setup
        productNameArray.add("DM3\n");
        productNameArray.add("DMF\n");
        productNameArray.add("F_I\n");
        productNameArray.add("Tr4\n");
        productNameArray.add("Hand3\n");
        productNameArray.add("MDR\n");
        productNameArray.add("MDR3\n");
        productNameArray.add("MDR4\n");
        productNameArray.add("MLink\n");
        productNameArray.add("LightR\n");
        productNameArray.add("Tr4\n");
        productNameArray.add("VLC\n");
        productNameArray.add("WMF\n");


        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }

        // check if the network is available, and if so, initiate download of latest firmware files
        if (isNetworkAvailable()) {
            mProgressDialog = new ProgressDialog(this);
            mProgressDialog.setMessage("Checking for the latest firmware...");
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();

            startDownload();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("No Internet Connection")
                    .setMessage("Please turn on your network connection to download the latest firmware files.")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).setIcon(android.R.drawable.ic_dialog_alert).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
        updateMqttStatus();

        // Start UI refresh
        //Log.d(TAG, "add ui timer");
        updateUI();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }

    @Override
    public void onPause() {
        super.onPause();

        //Log.d(TAG, "remove ui timer");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

        // Save preferences
        SharedPreferences preferences = getSharedPreferences(kPreferences, MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putBoolean(kPreferences_echo, mIsEchoEnabled);
        editor.putBoolean(kPreferences_eol, mIsEolEnabled);
        editor.putBoolean(kPreferences_asciiMode, !mShowDataInHexFormat);
        editor.putBoolean(kPreferences_timestampDisplayMode, mIsTimestampDisplayMode);

        editor.apply();
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

    public void dismissKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {
        lastDataSent = data;
        Log.d(TAG, "lastDataSent: " + lastDataSent + " --");
        if (!isConnectionReady) {
            sBuilder.setLength(0);
        }

        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
        if (mIsEolEnabled) {
            // Add newline character if checked
            data += "\n";
        }

        // Send to uart
        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
            sendData(data);
            mSentBytes += data.length();
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
        lastDataSentByte = data;
        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
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

    public void startDownload() {
        Log.d(TAG, "----------------- downloading firmware versions -------------------------");
            new DownloadFirmwareTask(getApplicationContext(), new DownloadCompleteListener() {
                @Override
                public void downloadComplete(Map<String, Map<String, PCSReleaseParser.ProductInfo>> firmwareFilesMap) {
                    Log.d(TAG, "downloadComplete inner entered");
                    if (mProgressDialog != null) {
                        mProgressDialog.hide();
                    }
                }
            }).execute(pcsPath);
    }

    @Override
    public void downloadComplete(Map<String, Map<String, PCSReleaseParser.ProductInfo>> firmwareFilesMap) {
        Log.d(TAG, "downloadComplete outer entered");
        if (mProgressDialog != null) {
            mProgressDialog.hide();
        }
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_uart, menu);

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
        if (mMqttMenuItem == null)
            return;      // Hack: Sometimes this could have not been initialized so we don't update icons

        MqttManager mqttManager = MqttManager.getInstance(this);
        MqttManager.MqqtConnectionStatus status = mqttManager.getClientStatus();

        if (status == MqttManager.MqqtConnectionStatus.CONNECTING) {
            final int kConnectingAnimationDrawableIds[] = {R.drawable.mqtt_connecting1, R.drawable.mqtt_connecting2, R.drawable.mqtt_connecting3};
            mMqttMenuItem.setIcon(kConnectingAnimationDrawableIds[mMqttMenuItemAnimationFrame]);
            mMqttMenuItemAnimationFrame = (mMqttMenuItemAnimationFrame + 1) % kConnectingAnimationDrawableIds.length;
        } else if (status == MqttManager.MqqtConnectionStatus.CONNECTED) {
            mMqttMenuItem.setIcon(R.drawable.mqtt_connected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        } else {
            mMqttMenuItem.setIcon(R.drawable.mqtt_disconnected);
            mMqttMenuItemAnimationHandler.removeCallbacks(mMqttMenuItemAnimationRunnable);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        switch (id) {
            case R.id.action_help:
                startHelp();
                return true;

            case R.id.action_connected_settings:
                startConnectedSettings();
                return true;

            case R.id.action_refreshcache:
                if (mBleManager != null) {
                    mBleManager.refreshDeviceCache();
                }
                break;

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
        Intent intent = new Intent(this, ConnectedSettingsActivity.class);
        startActivityForResult(intent, kActivityRequestCode_ConnectedSettingsActivity);
    }

    @Override
    protected void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
//        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == kActivityRequestCode_ConnectedSettingsActivity && resultCode == RESULT_OK) {
            finish();
        } else if (requestCode == kActivityRequestCode_MqttSettingsActivity && resultCode == RESULT_OK) {

//        } else if (requestCode == kActivityRequestCode_SelectLensFile && resultCode == RESULT_OK) {
//            Uri selectedFile = intent.getData();
//            loadLensFile(selectedFile);
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
        Log.d(TAG, "Disconnected. Back to previous activity");
        finish();
    }

    @Override
    public void onServicesDiscovered() {
        super.onServicesDiscovered();
        enableRxNotifications();
    }

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        // UART RX
        if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
            if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                final byte[] bytes = characteristic.getValue();
                if (programMode) {
                    String convData = bytesToText(bytes, true);
                    Log.d(TAG, "convData: " + convData + "$$");
                    String newRxData = buildRxPacket(bytes);
                    Log.d(TAG, "newRxData:" + newRxData + "$$");

                    if (startConnectionSetup) {
                        if (newRxData.length() > 0) {
                            setUpConnection(newRxData);
                        }
                    }

                    if (isConnectionReady) {
                        if (newRxData.length() > 0) {
                            checkRxData(newRxData);
                        }
                    } else {
                        checkConnection(newRxData);
                    }
                }

                if (lensMode) {
                    String lensString = buildLensPacket(bytes);
                    if (lensString.length() > 0) {
                        receiveLensData(lensString);
                    }
                }

                if (lensSendMode) {
                    String text = bytesToText(bytes, true);
                    transmitLensData(text);
                }

                // MQTT publish to RX
                MqttSettings settings = MqttSettings.getInstance(UartActivity.this);
                if (settings.isPublishEnabled()) {
                    String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_RX);
                    final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_RX);
                    final String text = bytesToText(bytes, false);
                    mMqttManager.publish(topic, text, qos);
                }
            }
        }
    }

    private String buildRxPacket(byte[] bytes) {
        int len = bytes.length;
        byte last_char = bytes[len-1];
        String text = bytesToText(bytes, false);
        String response = text.replaceAll("[^A-Za-z0-9?.* \n]", "");
        Log.d(TAG, "buildRxPacket - response: " + response + "$$");

        if (response.contains("Done")) {
            if (response.contains("Not")) {
                return "";
            }
            else {
                sBuilder.setLength(0);
                return "Done\n";
            }
        }

        else if (response.contains("*")) {
            sBuilder.setLength(0);
            return "*";
        }

        else {
            if (response.contains("\n")) {
                if (response.contains("OK")) {
                    sBuilder.append(response);
                }
                else {
                    Log.d(TAG, "newline detected. splitting...length = " + response.length());
                    if (response.length() > 1) {
                        sBuilder.append(response.split("\n")[0] + "\n");
                    }
                    else {
                        sBuilder.append(response);
                    }
                }
                String packet = sBuilder.toString();
                sBuilder.setLength(0);
                return packet;
            }
            else {
                sBuilder.append(response);
                return "";
            }
//            if (last_char == 0x0A) {
//                Log.d(TAG, "line feed detected as last character");
//                String packet = sBuilder.toString();
//                sBuilder.setLength(0);
//                return packet;
//            } else {
//                return "";
//            }
        }
    }

    private void setUpConnection(String text) {
        Log.d(TAG, "setUpConnection: " + text + ", baudRateWait: " + baudRateWait);
        String productDetected = checkForProductString(text);
        if (productDetected.length() > 0) {
            Log.d(TAG, "product detected in setUpConnection: " + productDetected);
            productRxString = productDetected;
            String filePath = getS19Path(productRxString);
            startConnectionSetup = false;
            loadProgramFile(filePath);
            isConnectionReady = true;
        } else {
            if (text.contains("0\nOK\n")) {
                if (lastDataSent == "+++") {
                    commandMode = false;
                }
                if (baudRateSent) {
                    startConnectionSetup = false;
                    programMode = true;
                    Log.d(TAG, "programMode set to true - ready to program!");
                } else {
                    uartSendData("+++", false);
                }
            } else if (text.contains("1\nOK\n")) {
                if (lastDataSent == "+++") {
                    commandMode = true;
                }
                if (baudRateSent) {
                    uartSendData("+++", false);
                } else {
                    int currBaudRate = baudRateArray[baudRateIndex];
                    Log.d(TAG, "Sending baudrate to device: " + currBaudRate);
                    uartSendData("AT+BAUDRATE=" + currBaudRate, false);
                    baudRateSent = true;
                    //                    baudRateWait = 0;
                    startConnectionSetup = false;
                    isFirstGoodPacket = true;
                }
            } else {
                uartSendData("+++", false);
            }
        }
    }

    private String checkForProductString(String text) {
        Log.d(TAG, "checkForProductString entered");
        String returnVal = "";
        String[] stringArr = text.split("\n");
        for (String str : stringArr) {
            int prodInd = productNameArray.indexOf(str + "\n");
            Log.d(TAG, "Index of " + str + " in productArray: " + prodInd);
            if (prodInd > -1) {
                returnVal = productNameArray.get(prodInd);
            }
        }
        return returnVal;
    }

    private void checkConnection(String text) {
        Log.d(TAG, "checkConnection w/: " + text + "$$");
        if (productNameArray.contains(text)) {
            Log.d(TAG, "Product in Array already");
            productRxString = text;
            String firmwarePath = getS19Path(productRxString);
            startConnectionSetup = false;
            baudRateWait = 0;
            loadProgramFile(firmwarePath);
            isConnectionReady = true;
        }
        else {
            baudRateWait += 1;
            if (baudRateWait == 10) {
                baudRateWait = 0;
                Log.d(TAG, "checkConn else, baudRateWait 10");

                if (baudRateSent) {
                    baudRateIndex += 1;
                    if (baudRateIndex > 2) {
                        baudRateIndex = 0;
                    }
                    Log.d(TAG, "baudRateIndex: " + baudRateIndex);
                    baudRateSent = false;
                }

                if (startConnectionSetup) {
                    setUpConnection(text);
                } else {
                    uartSendData("+++", false);
                    baudRateWait = 0;
                    startConnectionSetup = true;
                }
            }
        }
    }

    // examine the UART response to implement handshake before programming and s19 file sending
    private void checkRxData(String text) {
        Log.d(TAG, "booleans: " + programLoaded + " and " + programMode);
        if (programLoaded && programMode) {
            if (productNameArray.contains(text)) {
                Log.d(TAG, "Product detected");
                uartSendData("Y\n", false);
            }
            else if (text.contains("V")) {
                Log.d(TAG, "Firmware version detected");
                if (updateConfirmed) {
                    uartSendData("Y\n", false);
                }
                else {
                    if (!updateConfirmEntered) {
                        confirmUpdate(text);
                    }
                }
            }
            else if (text.contains("*")) {
                if (updateConfirmEntered) {
                    updateConfirmEntered = false;
                }
                Log.d(TAG, "* detected. currentLine: " + currentLine + " of " + fileArray.size());
                sendProgramFile(currentLine);
            }
            else if (text.contains("Erased")) {
                Log.d(TAG, "Erased detected");
                erased = true;
                if (updateConfirmed) {
                    uartSendData("Y\n", false);
                }
            }
            else if (text.contains("Done")) {
                Log.d(TAG, "Done detected");
                programMode = false;
                programLoaded = false;
                isConnectionReady = false;
                currentLine = 0;
                updateConfirmed = false;
                updateConfirmEntered = false;
                isProgramming = false;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        uploadProgress.setVisibility(View.INVISIBLE);
                    }
                });
            }
            else if (text.contains("E or P?\n")) {
                Log.d(TAG, "E or P detected");
                if (erased) {
                    uartSendData("P\n", false);
                }
                else {
                    if (!updateConfirmed) {
                        if (!updateConfirmEntered) {
                            confirmUpdate(text);
                        }
                    }
                    else {
                        uartSendData("E\n", false);
                    }
                }
            }
            else if (text.contains("ERROR\n")) {
                Log.d(TAG, "In Command Mode ----");
                uartSendData("+++", false);
                startConnectionSetup = true;
                isConnectionReady = false;
            }
            else {
                Log.d(TAG, "--------------- Unknown: " + text + "$$");
            }
        }
    }

    public void confirmUpdate(String ver) {
        updateConfirmEntered = true;
        final String response = ver;
        final String text = response.replaceAll("\n", " ");
        sBuilder.setLength(0);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "Confirm update entered");
                new AlertDialog.Builder(UartActivity.this)
                        .setMessage("Would you like to update " + text + "to V" + latestVersion + "?")
                        .setPositiveButton("Let's do this.", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                updateConfirmed = true;
                                activateUploadProgress();
                                checkRxData(response);
                            }
                        })
                        .setNegativeButton("No, I like bugs.", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                programMode = false;
                                programLoaded = false;
                                isConnectionReady = false;
                                updateConfirmed = false;
                            }
                        })
                        .setCancelable(false)
                        .show();
            }
        });
    }

    private void activateUploadProgress() {
        uploadProgress.setVisibility(View.VISIBLE);
        new Thread(new Runnable() {
            public void run() {
                while (currentLine < fileArray.size()) {
                    progressStatus = Math.round(((float)currentLine / fileSize)*100);

                    // Update the progress bar
                    handler.post(new Runnable() {
                        public void run() {
                            uploadProgress.setProgress(progressStatus);
                        }
                    });
                    try {
                        // Sleep for 200 milliseconds. Just to display the progress slowly
                        Thread.sleep(200);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    private String getS19Path(String product) {
        Log.d(TAG, "getS19Path: " + product + "$$");
        String trimmedString = product.replaceAll("[^A-Za-z0-9]", "");
        Log.d(TAG, "trimmedString: " + trimmedString + "$$");
        SharedPreferences sharedPref = this.getBaseContext().getSharedPreferences("firmwareURLs", Context.MODE_PRIVATE);
        String productString = sharedPref.getString(trimmedString, "Not Found");
        Log.d(TAG, "productString: " + productString);
        String pathName = "";
        if (productString.equals("Not Found")) {
            Log.d(TAG, "Firmware file not found");
            // let user know file not found, try downloading again
        }
        else {
            latestVersion = productString.split("=")[0].replaceAll("[{}]", "");
            pathName = productString.split("=")[1].replaceAll("[{}]", "");

            Log.d(TAG, "pathName: " + pathName);
        }
//        String pathName = sharedPref.getString(trimmedString, "Not Found");
        return pathName;
    }

    // read in the s19 file so we can send it line-by-line to the UART
    public void loadProgramFile(String filePath) {
        Log.d(TAG, "loadProgramFile() entered: " + filePath);
        BufferedReader reader = null;

        fileArray.clear();
        try {
            // open the file for reading
//            reader = new BufferedReader(
//                    new InputStreamReader(getAssets().open(s19FileName)));
            FileInputStream fileIn = new FileInputStream(filePath);
            Log.d(TAG, "fileIn: " + fileIn);

            reader = new BufferedReader(
                    new InputStreamReader(fileIn));
            String line;
            while ((line = reader.readLine()) != null) {
                fileArray.add(line);
            }
            if (fileArray.size() > 0) {
                programLoaded = true;
                fileSize = fileArray.size();
            }
        } catch (Exception ex) {
            Log.d(TAG, "loadProgramFile()", ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                }   catch (Exception e) {
                    Log.d(TAG, "reader exception", e);
                }
            }
        }
    }

    public void sendProgramFile(int line) {
        if (currentLine == 0) {
            isProgramming = true;
        }
        if (currentLine < fileArray.size()) {
            uartSendData(fileArray.get(line), false);
            currentLine += 1;
        }
        else if (currentLine == fileArray.size()) {
            Log.d(TAG, "End of array detected");
            programMode = false;
            programLoaded = false;
            isConnectionReady = false;
            currentLine = 0;
        }
    }

    public void enableProgramMode(View view) {
        programMode = true;
        Log.d(TAG, "programMode clicked: " + programMode);
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

    private String buildLensPacket(byte[] bytes) {
        String text = bytesToText(bytes, false);
        if (text.contains(EOTStr)) {
            Log.d(TAG, "EOT detected. Returning EOT");
            lensSBuilder.setLength(0);
            return EOTStr;
        }
        else {
            lensSBuilder.append(text);
            if (text.contains("\r")) {
                String lensString = lensSBuilder.toString();
                lensSBuilder.setLength(0);
                return lensString;
            } else {
                return "";
            }
        }
    }

    private void receiveLensData(String text) {
        if (!startLensTransfer) {
            if (text.contains("Hand")) {
                Log.d(TAG, "Hand detected");
                lensModeConnected = true;
                byte[] new_byte = {0x11, 0x05};
                uartSendData(new_byte, false);
            } else {
                uartSendData(ACK, false);
                startLensTransfer = true;
                String trimmedString = text.replaceAll("[^\\w]", "");
                numLenses = Integer.valueOf(trimmedString, 16);
                Log.d(TAG, "Number of lenses detected: " + numLenses);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        activateLensTransferProgress("RX");
                    }
                });
            }
        }
        else {          // lens transfer in progress, so need to add responses to a buffer
            if (text.contains(EOTStr)) {
                Log.d(TAG, "EOT detected");
                uartSendData(ACK_SYN, false);
                lensModeConnected = false;
                lensMode = false;
                startLensTransfer = false;
                askToSaveLenses();
            }
            else {
                Log.d(TAG, "Lens string: " + text);
                lensArray.add(text);
                currentLens += 1;
                uartSendData(ACK, false);
            }

        }
    }

    private void transmitLensData(String text) {
        if (lensSendMode) {
            if (text.contains(ACKStr)) {
                if (!lensDone) {
                    if (numLensesSent) {
                        Log.d(TAG, "ACK. Index: " + currentLens + " of " + numLenses);
                        if (currentLens < numLenses) {
                            byte[] STX = {0x02};
                            byte[] ETX = {0x0A, 0x0D};
                            String lensInfo = lensArray.get(currentLens);
                            Log.d(TAG, "LENS INFO: " + lensInfo);
                            uartSendData(STX, false);
                            uartSendData(lensArray.get(currentLens), false);
                            uartSendData(ETX, false);
                            currentLens += 1;
                        } else if (currentLens == numLenses) {
                            Log.d(TAG, "Done sending lenses. Sending EOT");
                            uartSendData(EOT, false);
                            lensDone = true;
                            currentLens = 0;
                            numLensesSent = false;
                        }
                    }
                    else {
                        String numLensesHexString = Integer.toHexString(numLenses);
                        Log.d(TAG, "Sending number of lenses: " + numLensesHexString);
                        byte[] STX = {0x0E};
                        byte[] ETX = {0x0A, 0x0D};
                        uartSendData(STX, false);
                        uartSendData(numLensesHexString, false);
                        uartSendData(ETX, false);

                        numLensesSent = true;
                    }
                }
                else {
                    Log.d(TAG, "HU3 successfully received lenses");
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
                Log.d(TAG, "NAK received from HU3. Re-sending lens " + currentLens);
                uartSendData(ACK, false);
                uartSendData(lensArray.get(currentLens), false);
            }
        }
    }

//    public void confirmLensTransmit(View view) {
//        runOnUiThread(new Runnable() {
//            @Override
//            public void run() {
//                new AlertDialog.Builder(UartActivity.this)
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
                    AlertDialog.Builder builder = new AlertDialog.Builder(UartActivity.this);
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

    private void importLensFile(File lensFile) {
        Log.d(TAG, "Customer selected lens file: " + lensFile.toString());
        BufferedReader reader = null;
        lensArray.clear();

        try {
            FileInputStream lensIn = new FileInputStream(lensFile);
            reader = new BufferedReader(
                    new InputStreamReader(lensIn));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.length() > 0) {
                    Log.d(TAG, "Reading lens file: " + line);
                    lensArray.add(line);
                }
            }
            if (lensArray.size() > 0) {
                lensFileLoaded = true;
                numLenses = lensArray.size();
                currentLens = 0;
            }

            Log.d(TAG, "lensArray loaded successfully. NumLenses: " + numLenses);

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
            Log.d(TAG, "importLensFile()", ex);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                }   catch (Exception e) {
                    Log.d(TAG, "reader exception", e);
                }
            }
        }
    }

    private void askToSaveLenses() {
//        String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
//        final String lensFileName = currentDateTimeString;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mProgressDialog.hide();
//                lensProgress.setVisibility(View.INVISIBLE);
                final EditText input = new EditText(UartActivity.this);
                input.setSelectAllOnFocus(true);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
//                input.setText(lensFileName);

                new AlertDialog.Builder(UartActivity.this)
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

    private void saveLensList(ArrayList<String> lensArray, String fileName) {
        if (isExternalStorageWritable()) {
            Log.d(TAG, "Number of lenses in array: " + lensArray.size());

            String lensFileName = "";
            if (fileName.length() > 0) {
                lensFileName = fileName + ".pcl";
            }
            else {
                String currentDateTimeString = DateFormat.getDateTimeInstance().format(new Date());
                lensFileName = currentDateTimeString + ".pcl";
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
                    Log.d(TAG, "File created successfully");

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
        Log.d(TAG, "File: " + file);
        return file;
    }

    private void activateLensTransferProgress(String direction) {
        mProgressDialog = new ProgressDialog(this);
        if (direction.equals("RX")) {
            mProgressDialog.setMessage("Importing lenses from HU3...");
        }
        else {      // direction = "TX"
            mProgressDialog.setMessage("Sending lenses to HU3...");
        }
        mProgressDialog.setCancelable(false);
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






    public void enableLensMode(View view) {
        lensMode = true;
        lensArray.clear();
        byte[] syn_byte = {0x16};
        uartSendData(syn_byte, false);
    }

//    public void enableSendLenses(View view) {
//        lensSendMode = true;
//        lensDone = false;
//        byte[] start_byte = {01, 05};
//        uartSendData(start_byte, false);
//    }

    public void manageLenses(View view) {
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
                AlertDialog.Builder builder = new AlertDialog.Builder(UartActivity.this);
                builder.setTitle("Select files for deletion")
                    .setMultiChoiceItems(fileStrings, null, new DialogInterface.OnMultiChoiceClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                            if (isChecked) {
                                filesToDelete.add(savedLensFiles[which].toString());
                                Log.d(TAG, "Items to delete: ");
                                for (String file : filesToDelete) {
                                    Log.d(TAG, file);
                                }
                            }
                            else {
                                filesToDelete.remove(filesToDelete.indexOf(savedLensFiles[which].toString()));
                                Log.d(TAG, "Items to delete: ");
                                for (String file : filesToDelete) {
                                    Log.d(TAG, file);
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

    private void deleteLensFiles(ArrayList<String> files) {
        int numToDelete = files.size();
        int numDeleted = 0;
        for (String file : files) {
            File lensFile = new File(file);
            boolean deleted = lensFile.delete();
            Log.d(TAG, "Deleted file: " + deleted);
            if (deleted) {
                numDeleted += 1;
            }
        }
        if (numDeleted == numToDelete) {
            final int numDel = numDeleted;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                Log.d(TAG, "Delete successful");
                new AlertDialog.Builder(UartActivity.this)
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
//                // Log.d(TAG, "update packets: "+(bufferSize-mDataBufferLastSize));
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

        //Log.d(TAG, "Mqtt messageArrived from topic: " +topic+ " message: "+message);

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

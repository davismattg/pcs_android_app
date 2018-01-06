package com.prestoncinema.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothDevice;
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
import android.support.v4.widget.SwipeRefreshLayout;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.prestoncinema.app.settings.MqttUartSettingsActivity;
import com.prestoncinema.ble.BleManager;
import com.prestoncinema.mqtt.MqttManager;
import com.prestoncinema.mqtt.MqttSettings;

import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
* Created by MATT on 2/23/2017.
* This is the activity to perform firmware updates for the Preston Units
*/

public class FirmwareUpdateActivity extends UartInterfaceActivity implements MqttManager.MqttManagerListener, DownloadCompleteListener {
    // Log
    private final static String TAG = FirmwareUpdateActivity.class.getSimpleName();

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
    private boolean erased = false;
    private boolean programMode = true; //false;
    private boolean programLoaded = false;
    private boolean startConnectionSetup = true;
    private boolean isConnectionReady = false;
    private boolean baudRateSent = false;
    private boolean expectingDone = false;
    private boolean updateConfirmed = false;
    private boolean updateConfirmEntered = false;
    private int currentLine = 0;
    private int progressStatus = 0;
    private ArrayList<String> fileArray = new ArrayList<String>();
    private List<String> productNameArray = new ArrayList<String>();
    private int[] baudRateArray = {19200, 57600, 115200, 9600};
    private int baudRateIndex = 0;
    private StringBuilder sBuilder = new StringBuilder("");
    private Handler handler = new Handler();

    private boolean isConnected = false;
    private int baudRateWait = 0;          // number of data packets to wait after changing baud rate
    private int packetsToWait = 1;
    private String lastDataSent = "";
    private String responseExpected = "Hand3\n";
    private String productDetected;

    private String productRxString = "";
//    private String pcsPath = "https://prestoncinema.com/Upgrades/src/firmware-new.xml";
    private String pcsPath = "https://firebasestorage.googleapis.com/v0/b/preston-42629.appspot.com/o/firmware_app%2Ffirmware.xml?alt=media&token=85f014ae-9252-4e69-b1f4-9e3993a57451";
    private String latestVersion = "";

//    private ArrayList<String> firmwareChangesArrayList;
//    private String[] firmwareChangesArray;
    private ArrayAdapter<String> firmwareChangesAdapter;

    private DownloadCompleteListener downloadCompleteListener;
    private boolean firmwareFilesDownloaded = false;

    private DataFragment mRetainedDataFragment;

    private MqttManager mMqttManager;

    private AlertDialog updateDialog;

    private ProgressDialog mProgressDialog;
    private TextView mConnectedTextView;
    private ListView mFirmwareListView;
    private ArrayAdapter<String> firmwareArrayAdapter;
    private SwipeRefreshLayout mSwipeRefreshLayout;

    private ArrayList<String> firmwareArrayList = new ArrayList<String>();

    private List<Map<String, String>> firmwareMap = new ArrayList<Map<String, String>>();
    private SimpleAdapter firmwareAdapter;

    public FirmwareUpdateActivity() throws MalformedURLException {
    }

    // TODO: Add version history and other relevant info in this activity so users can see it all at once
    // TODO: Add alert dialog when firmware update finished
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firmware_update);

        mConnectedTextView = (TextView) findViewById(R.id.ConnectedTextView);

        mProgressDialog = new ProgressDialog(FirmwareUpdateActivity.this);
//        uploadDialog = new AlertDialog(FirmwareUpdateActivity.this);

        mBleManager = BleManager.getInstance(this);
        isConnected = (mBleManager.getState() == 2);

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
        theme.resolveAttribute(R.attr.colorControlActivated, typedValue, true);

        invalidateOptionsMenu();        // udpate options menu with current values

        // Continue
        onServicesDiscovered();

        // Product Array Setup
        productNameArray = Arrays.asList(getResources().getStringArray(R.array.product_names));
//        productNameArray.add("Hand3\n");
//        productNameArray.add("MDR4\n");
//        productNameArray.add("MDR3\n");
////        productNameArray.add("LightR\n");
//        productNameArray.add("MLink\n");
//        productNameArray.add("MDR\n");
//        productNameArray.add("DM3\n");
//        productNameArray.add("DMF\n");
//        productNameArray.add("F_I\n");
//        productNameArray.add("Tr4\n");
//        productNameArray.add("Tr4\n");
//        productNameArray.add("VLC\n");
//        productNameArray.add("WMF\n");

        firmwareChangesAdapter = new ArrayAdapter<String>(FirmwareUpdateActivity.this, R.layout.firmware_change_list_item);

        // Mqtt init
        mMqttManager = MqttManager.getInstance(this);
        if (MqttSettings.getInstance(this).isConnected()) {
            mMqttManager.connectFromSavedSettings(this);
        }

        // check if the network is available, and if so, initiate download of latest firmware files
        if (isNetworkAvailable()) {
            startDownload();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("No Internet Connection")
                    .setMessage("Please turn on your network connection to download the latest firmware files. You may still upload firmware already stored on your phone.")
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                        }
                    }).setIcon(android.R.drawable.ic_dialog_alert).show();
        }

        getFirmwareVersions(productNameArray);
//        mFirmwareListView = (ListView) findViewById(R.id.firmwareListView);
//        firmwareAdapter = new SimpleAdapter(FirmwareUpdateActivity.this, firmwareMap, R.layout.firmware_list_item, new String[] {"productString", "versionString"}, new int[] {R.id.firmwareProductTextView, R.id.firmwareVersionTextView});
//        mFirmwareListView.setAdapter(firmwareAdapter);
//        firmwareAdapter.notifyDataSetChanged();
    }

    @Override
    public void onResume() {
        super.onResume();

        // Setup listeners
        mBleManager.setBleListener(this);

        mMqttManager.setListener(this);
//        updateMqttStatus();

        // Start UI refresh
        //Timber.d("add ui timer");
        updateUI();

        isUITimerRunning = true;
        mUIRefreshTimerHandler.postDelayed(mUIRefreshTimerRunnable, 0);

    }

    @Override
    public void onPause() {
        super.onPause();
//        mProgressDialog.dismiss();

        //Timber.d("remove ui timer");
        isUITimerRunning = false;
        mUIRefreshTimerHandler.removeCallbacksAndMessages(mUIRefreshTimerRunnable);

//        // Save preferences
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

//    public void dismissKeyboard(View view) {
//        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
//        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
//    }

    private void uartSendData(String data, boolean wasReceivedFromMqtt) {
        lastDataSent = data;

        if (!isConnectionReady) {
            sBuilder.setLength(0);
        }

        // MQTT publish to TX
        MqttSettings settings = MqttSettings.getInstance(com.prestoncinema.app.FirmwareUpdateActivity.this);
        if (!wasReceivedFromMqtt) {
            if (settings.isPublishEnabled()) {
                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
                mMqttManager.publish(topic, data, qos);
            }
        }

        // Add eol
//        if (data.contains("+++")) {
            // Add newline character if checked
//            data += "\n";
//        }

        // TODO: if sending Y, E, or P to MDR-4, don't add \r\n cuz it might reject it as junk
        if (productRxString.equals("MDR4\n")) {
            if (expectingDone) {
//                Timber.d("MDR4 detected, add r and n");
                data += "\r\n";
            }
//            else {
//                Timber.d("MDR4 detected, adding newline only!");
//                data += "\n";
//            }
            byte[] dataBytes = data.getBytes();
//            Timber.d("Step 1: MDR4 data in bytes: " + Arrays.toString(dataBytes));
        }
        else {
            data += "\n";
        }

        Timber.d("lastDataSent: " + data + "$$");
//        Timber.d("data bytes: " + Arrays.toString(data.getBytes()));

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

//    private void uartSendData(byte[] data, boolean wasReceivedFromMqtt) {
////        lastDataSentByte = data;
//        // MQTT publish to TX
//        MqttSettings settings = MqttSettings.getInstance(com.prestoncinema.app.FirmwareUpdateActivity.this);
//        if (!wasReceivedFromMqtt) {
//            if (settings.isPublishEnabled()) {
//                String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_TX);
//                final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_TX);
//                mMqttManager.publish(topic, data.toString(), qos);
//            }
//        }
//
////        // Add eol
////        if (mIsEolEnabled) {
////            // Add newline character if checked
////            data += "\n";
////        }
//
////        // Send to uart
//        if (!wasReceivedFromMqtt || settings.getSubscribeBehaviour() == MqttSettings.kSubscribeBehaviour_Transmit) {
//            sendData(data);
////            mSentBytes += data.length();
//        }
//    }

    public void startDownload() {
        Timber.d("----------------- downloading firmware versions -------------------------");
        new DownloadFirmwareTask(getApplicationContext(), new DownloadCompleteListener() {
            @Override
            public void downloadComplete(Map<String, Map<String, PCSReleaseParser.ProductInfo>> firmwareFilesMap) {
                Timber.d("downloadComplete inner entered");
                if (mProgressDialog != null) {
                    mProgressDialog.hide();
                }
                firmwareFilesDownloaded = true;
            }
        }).execute(pcsPath);
    }

    @Override
    public void downloadComplete(Map<String, Map<String, PCSReleaseParser.ProductInfo>> firmwareFilesMap) {
        Timber.d("downloadComplete outer entered");
        if (mProgressDialog != null) {
            mProgressDialog.hide();
        }
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_firmware, menu);

        // Mqtt
//        mMqttMenuItem = menu.findItem(R.id.action_mqttsettings);
//        mMqttMenuItemAnimationHandler = new Handler();
//        mMqttMenuItemAnimationRunnable.run();

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

//    private Runnable mMqttMenuItemAnimationRunnable = new Runnable() {
//        @Override
//        public void run() {
//            updateMqttStatus();
//            mMqttMenuItemAnimationHandler.postDelayed(mMqttMenuItemAnimationRunnable, 500);
//        }
//    };
//    private int mMqttMenuItemAnimationFrame = 0;

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
            case R.id.myFirmwareFilesMenuItem:
                Timber.d("show my firmware files");       // TODO: make this bring up an AlertDialog with current versions on user's phone
                Intent intent = new Intent(this, FirmwareInfoActivity.class);
                intent.putStringArrayListExtra("firmwareArrayList", firmwareArrayList);
//                intent.putExtra("help", "uart_help.html");
                startActivity(intent);
//                showFirmwareAlertDialog(firmwareMap);
                return true;
            case R.id.firmwareUpdateCheckMenuItem:
                Timber.d("check for firmware updates");
                // check if the network is available, and if so, initiate download of latest firmware files
                if (isNetworkAvailable()) {
                    mProgressDialog.setMessage("Checking for the latest firmware...");
                    mProgressDialog.setCancelable(false);
                    mProgressDialog.show();

                    startDownload();
                } else {
                    new AlertDialog.Builder(FirmwareUpdateActivity.this)
                            .setTitle("No Internet Connection")
                            .setMessage("Please turn on your network connection to download the latest firmware files.")
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                }
                            }).setIcon(android.R.drawable.ic_dialog_alert).show();
                }
                return true;
            case R.id.firmwareVersionHistoryMenuItem:
                Timber.d("show revision history activity");
                return true;
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
    }

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

    @Override
    public synchronized void onDataAvailable(BluetoothGattCharacteristic characteristic) {
        super.onDataAvailable(characteristic);
        if (firmwareFilesDownloaded) {
            // UART RX
            if (characteristic.getService().getUuid().toString().equalsIgnoreCase(UUID_SERVICE)) {
                if (characteristic.getUuid().toString().equalsIgnoreCase(UUID_RX)) {
                    final byte[] bytes = characteristic.getValue();
//                    Timber.d("data received: " + bytesToText(bytes, false));
                    String convData = bytesToText(bytes, true);
//                    Timber.d("convData: " + convData + "$$");
                    String newRxData = buildRxPacket(bytes);
                    Timber.d("newRxData:" + newRxData + "$$");

                    processRxData(newRxData);

                    // MQTT publish to RX
                    MqttSettings settings = MqttSettings.getInstance(com.prestoncinema.app.FirmwareUpdateActivity.this);
                    if (settings.isPublishEnabled()) {
                        String topic = settings.getPublishTopic(MqttUartSettingsActivity.kPublishFeed_RX);
                        final int qos = settings.getPublishQos(MqttUartSettingsActivity.kPublishFeed_RX);
                        final String text = bytesToText(bytes, false);
                        mMqttManager.publish(topic, text, qos);
                    }
                }
            }
        }
    }

    private String buildRxPacket(byte[] bytes) {
        String text = bytesToText(bytes, false);
        String response = text.replaceAll("[^A-Za-z0-9?.* \n]", "");
//        Timber.d("buildRxPacket - response: " + response + "$$");

        if (expectingDone) {
            if (response.contains("*")) {
                sBuilder.setLength(0);
                return "*";
            }
            else {
                if (response.contains("Not")) {
                    return "";
                } else {
                    sBuilder.setLength(0);
                    return "Done\n";
                }
            }
        }
        else {                                  // not actively programming
            if (response.contains("*")) {
                sBuilder.setLength(0);
                return "*";
            }
            else {
                if (response.contains("\n")) {
                    if (response.contains("OK")) {
                        sBuilder.append(response);
                    } else {
//                        Timber.d("newline detected. splitting...length = " + response.length());
                        if (response.length() > 1) {
                            sBuilder.append(response.split("\n")[0] + "\n");
                        } else {
                            sBuilder.append(response);
                        }
                    }
                    String packet = sBuilder.toString();
                    sBuilder.setLength(0);
                    return packet;
                } else {
                    if (response.contains("V")) {
//                        Timber.d("Version detected early on, passing through");
                        sBuilder.append(response);
                        String packet = sBuilder.toString() + "\n";
                        sBuilder.setLength(0);
                        return packet;
                    }
                    else {
                        sBuilder.append(response);
                        return "";
                    }
                }
            }
        }
    }

    private void processRxData(String text) {
//        Timber.d("processRxData (" + text + "):\nstartConnectionSetup: " + startConnectionSetup + "\nisConnectionReady: " +
//                isConnectionReady + "\nresponseExpected: " + responseExpected + "\nbaudRateWait: " + baudRateWait);
        if (!programLoaded) {
            if (text.length() > 2) {
                String productCheck = checkForProductString(text);
                if (productCheck.length() > 0) {
//                    Timber.d("productRxString found before anything else");
                    startConnectionSetup = false;
                    productRxString = productCheck;
                    final String filePath = getS19Path(productRxString);
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadProgramFile(filePath);
                        }
                    });
                    isConnectionReady = true;
                    responseExpected = "";
                }
            }
        }
        if (startConnectionSetup) {
//            Timber.d(lastDataSent + " sent, expect " + responseExpected + " in return");
            switch (lastDataSent) {
                case "AT":
                    if (baudRateWait < packetsToWait) {
                        if (text.contains(responseExpected)) {              // "OK" in this case
//                            Timber.d("OK found after sending AT, we're in command mode. Send baudRate");
                            int currBaudRate = baudRateArray[baudRateIndex];
//                            Timber.d("Sending baudrate to device: " + currBaudRate);
                            uartSendData("AT+BAUDRATE=" + currBaudRate, false);
                            baudRateIndex += 1;
                            if (baudRateIndex == 4) {
                                baudRateIndex = 0;
                            }
                            responseExpected = "OK";
                            baudRateWait = 0;
                        }
                        else {
                            baudRateWait += 1;
                        }
                    }
                    else {
                        if (responseExpected.equals("readyToGo")) {
//                            Timber.d("Device confirmed to be in data mode, check for product string");
                            startConnectionSetup = false;
                            baudRateWait = 0;
                        }
                        else {
//                            Timber.d("OK not received, change to command mode");
                            uartSendData("+++", false);
                            responseExpected = "1\nOK\n";
                            baudRateWait = 0;
                        }
                    }
                    break;
                case "+++":
                    if (baudRateWait < packetsToWait) {
                        if (text.contains(responseExpected)) {
                            if (responseExpected.contains("1")) {               // if is 1, in command mode. 0 = data mode
                                //in command mode since module returned 1 OK
//                                Timber.d("1 OK found and expected. Device in command mode, send new baud rate");
                                int currBaudRate = baudRateArray[baudRateIndex];
//                                Timber.d("Sending baudrate to device: " + currBaudRate);
                                uartSendData("AT+BAUDRATE=" + currBaudRate, false);
                                baudRateIndex += 1;
                                if (baudRateIndex == 4) {
                                    baudRateIndex = 0;
                                }
                                responseExpected = "OK";
                                baudRateWait = 0;
                            } else {
                                //in data mode since module returned 0 from +++
//                                Timber.d("Device probably in data mode, check w/ AT command");
                                uartSendData("AT", false);
                                responseExpected = "readyToGo";
                                baudRateWait = 0;
                            }
                        } else {                    // expected response not received, increment packet wait counter
                            baudRateWait += 1;
                        }
                    }
                    else {
                        // in opposite mode than we want, so send +++
//                        Timber.d("response not as expected from +++. Check mode w/ AT");
                        baudRateWait = 0;
                        uartSendData("AT", false);
                        responseExpected = "OK";
                    }
                    break;
                case "AT+BAUDRATE=9600": case "AT+BAUDRATE=19200": case "AT+BAUDRATE=57600": case "AT+BAUDRATE=115200": case "ATZ":
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
//                        Timber.d("Baudrate not changed successfully. Check mode w/ AT");
                        baudRateWait = 0;
                        uartSendData("AT", false);
                        responseExpected = "OK";
                    }
                    break;
                case "+++\nATZ\n+++":               // command to reset the BLE module, executed after user cancels previous update
                    if (baudRateWait < packetsToWait) {
                        if (text.contains(responseExpected)) {
//                            Timber.d("system reset performed, don't send anything else out");
                            baudRateWait = 0;
                        }
                        else {
                            baudRateWait +=1;
                        }
                    }
                    else {
//                        Timber.d("system update response not received properly. check mode:");
                        baudRateWait = 0;
                        responseExpected = "OK";
                        uartSendData("AT", false);
                    }
                    break;
                default:
//                    Timber.d("default_lenses case");
                    setUpConnection(text);
            }
        }

        else if (isConnectionReady) {
            checkRxData(text);
        }

        else {      // startConnectionSetup == false, check for the productString
            String productCheck = checkForProductString(text);
//            Timber.d("checked for product string in productArray. Result: " + productCheck + "$$");
            if (baudRateWait < packetsToWait) {
                if (productCheck.length() > 0) {
//                    Timber.d("Product found in productArray! :))))))))))))))))))))))))))))))))))))))))))))))");
                    baudRateWait = 0;
                    productRxString = productCheck;
                    final String filePath = getS19Path(productRxString);
                    startConnectionSetup = false;
                    isConnectionReady = true;
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            loadProgramFile(filePath);
                        }
                    });

                } else {
                    baudRateWait += 1;
                }
            } else {
//                Timber.d("productString not found. startConnectionSetup set to true.");
                baudRateWait = 0;
                startConnectionSetup = true;
                lastDataSent = "";
                responseExpected = "Hand3";
            }
        }
    }

    private void setUpConnection(String text) {
        Timber.d("setUpConnection: " + text + ", baudRateWait: " + baudRateWait);
        String productDetected = checkForProductString(text);
        if (productDetected.length() > 0) {
            Timber.d("product detected in setUpConnection: " + productDetected);
            productRxString = productDetected;
            final String filePath = getS19Path(productRxString);
            startConnectionSetup = false;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    loadProgramFile(filePath);
                }
            });
            isConnectionReady = true;
        } else {
            if (text.contains("0\nOK\n")) {
                //                if (lastDataSent == "+++") {
                //                    commandMode = false;
                //                }
                if (baudRateSent) {
                    startConnectionSetup = false;
                    //                    programMode = true;
                    //                    Timber.d("programMode set to true - ready to program!");
                } else {
                    uartSendData("+++", false);
                }
            } else if (text.contains("1\nOK\n")) {
                //                if (lastDataSent == "+++") {
                //                    commandMode = true;
                //                }
                if (baudRateSent) {
                    uartSendData("+++", false);
                } else {
                    int currBaudRate = baudRateArray[baudRateIndex];
                    Timber.d("Sending baudrate to device: " + currBaudRate);
                    uartSendData("AT+BAUDRATE=" + currBaudRate, false);
//                    uartSendData("+++\nATZ+++", false);
                    baudRateSent = true;
                    //                    baudRateWait = 0;
//                    startConnectionSetup = false;
                    //                    isFirstGoodPacket = true;
                }
            } else {
                // ping the device to see if we're in command mode or data mode. command mode should receive "OK" response
                uartSendData("AT", false);
                responseExpected = "OK";
            }
        }
    }

    private String checkForProductString(String text) {
//        Timber.d("checkForProductString entered");
        String returnVal = "";
        String[] stringArr = text.split("\n");
        for (String str : stringArr) {
            int prodInd = productNameArray.indexOf(str + "\n");
//            Timber.d("Index of " + str + " in productArray: " + prodInd);
            if (prodInd > -1) {
                returnVal = productNameArray.get(prodInd);
            }
        }
        return returnVal;
    }

    // examine the UART response to implement handshake before programming and s19 file sending
    private void checkRxData(String text) {
        Timber.d("checkRxData - text = " + text); //, "booleans: " + programLoaded + " and " + programMode);
        if (programLoaded && programMode) {
            if (productNameArray.contains(text)) {
//                Timber.d("Product detected");
                productDetected = convertProductString(text);
                uartSendData("Y", false);
            }
            else if (text.contains("V")) {
//                Timber.d("Firmware version detected");
                if (updateConfirmed) {
                    updateDialog.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.GONE);
                    updateDialog.getButton(DialogInterface.BUTTON_NEGATIVE).setVisibility(View.GONE);
                    uartSendData("Y", false);
                }
                else {
                    sBuilder.setLength(0);
                    if (!updateConfirmEntered) {
                        confirmUpdate(text);
                    }
                }
            }
            else if (text.contains("*")) {
                if (updateConfirmEntered) {
                    updateConfirmEntered = false;
                }
                if (!expectingDone) {
                    expectingDone = true;
                }

//                Timber.d("* detected. currentLine: " + currentLine + " of " + fileArray.size());
                sendProgramFile(currentLine);
            }
            else if (text.contains("Erased")) {
//                Timber.d("Erased detected");
                erased = true;
                if (updateConfirmed) {
                    uartSendData("Y", false);
                }
            }
            else if (text.contains("Done")) {
//                Timber.d("Done detected");

                completeFirmwareUpdate();
                restoreDefaults();
            }
            else if (text.contains("E or P?\n")) {
//                Timber.d("E or P detected");
                if (erased) {
                    uartSendData("P", false);
                }
                else {
                    if (!updateConfirmed) {
                        if (!updateConfirmEntered) {
                            confirmUpdate(text);
                        }
                    }
                    else {
                        uartSendData("E", false);
                    }
                }
            }
            else if (text.contains("ERROR\n")) {
//                Timber.d("ERROR detected, check mode and re-send last command ----");
//                uartSendData(lastDataSent, false);
                uartSendData("+++", false);
                startConnectionSetup = true;
                isConnectionReady = false;
            }
            else {
                Timber.d("--------------- Unknown: " + text + "$$");
            }
        }
    }

    public void confirmUpdate(String ver) {
        updateConfirmEntered = true;
        final String currentVersion = ver.replaceAll("\n", " ").split(" ")[1];
        sBuilder.setLength(0);

        SharedPreferences sharedPref = this.getBaseContext().getSharedPreferences("firmwareURLs", Context.MODE_PRIVATE);
        String productPrefs = sharedPref.getString(trimString(productRxString), "Not found");
        String[] changesArray = new String[0];

        if (productPrefs != "Not Found") {
            changesArray = getChangesFromPreferences(productPrefs);
        }

        final String[] changes = changesArray;


        runOnUiThread(new Runnable() {
            @Override
            public void run() {
               AlertDialog.Builder builder = new AlertDialog.Builder(FirmwareUpdateActivity.this);
               LayoutInflater inflater = getLayoutInflater();

               final View firmwareUpdateDialogView = inflater.inflate(R.layout.dialog_firmware_update, null);
               final String versionToInstall = "V" + latestVersion;
               updateDialog = builder.setView(firmwareUpdateDialogView)
                       .setPositiveButton("Update", new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialogInterface, int i) {
                           }
                       })
                       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                           @Override
                           public void onClick(DialogInterface dialogInterface, int i) {
                           }
                       })
                       .setCancelable(false)
                       .create();

               updateDialog.setOnShowListener(new DialogInterface.OnShowListener() {
                   @Override
                   public void onShow(DialogInterface dialogInterface) {
                       updateDialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(getResources().getColor(R.color.newGreen));
                       updateDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(getResources().getColor(R.color.newRed));

                       ImageView productImageView = firmwareUpdateDialogView.findViewById(R.id.productTypeImageView);
                       TextView productNameTextView = firmwareUpdateDialogView.findViewById(R.id.currentFirmwareProductTextView);
                       TextView currentVersionTextView = firmwareUpdateDialogView.findViewById(R.id.currentFirmwareVersionTextView);
                       TextView toInstallVersionTextView = firmwareUpdateDialogView.findViewById(R.id.toInstallFirmwareVersionTextView);
                       TextView whatsNewTextView = firmwareUpdateDialogView.findViewById(R.id.firmwareUpdateWhatsNewTextView);
                       ListView changesListView = firmwareUpdateDialogView.findViewById(R.id.firmwareUpdateChangesListView);
                       final ProgressBar progressBar = firmwareUpdateDialogView.findViewById(R.id.firmwareUpdateProgressBar);

                       productImageView.setImageResource(getProductImage(productDetected));

                       String productNameText = productNameTextView.getText() + productDetected + ":";
                       String whatsNewText = "What's new in " + versionToInstall + ":";

                       productNameTextView.setText(productNameText);
                       currentVersionTextView.setText(currentVersion);
                       toInstallVersionTextView.setText(versionToInstall);
                       whatsNewTextView.setText(whatsNewText);

                       firmwareChangesAdapter.addAll(changes);
                       changesListView.setAdapter(firmwareChangesAdapter);
                       firmwareChangesAdapter.notifyDataSetChanged();

                       updateDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                           @Override
                           public void onClick(View view) {
                               updateConfirmed = true;
                               progressBar.setVisibility(View.VISIBLE);
                               firmwareUpdateDialogView.findViewById(R.id.uploadingLinearLayout).setVisibility(View.VISIBLE);
                               firmwareUpdateDialogView.findViewById(R.id.updateInfoLinearLayout).setVisibility(View.GONE);

                                activateUploadProgress(progressBar);

                                checkRxData(currentVersion);
                           }
                       });

                       updateDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                           @Override
                           public void onClick(View view) {
                               restoreDefaults();
                               updateDialog.dismiss();
                           }
                       });

                   }
               });

               updateDialog.show();
            }
        });
    }

    private String[] getChangesFromPreferences(String preference) {
        String[] changeSplit = preference.split("=");
        String[] out = new String[0];
        String change = "";

        if (changeSplit.length > 2) {
            change = changeSplit[2];
            out = change.split("&&");
        }

        Timber.d("getChangesFromPreferences: ");
        for (String s : out) {
            Timber.d(s);
        }

        Timber.d("---------------------");
        return out;
    }

    /** This method returns the image for whatever product is detected for firmware updates
     *
     * @param product
     * @return
     */
    private int getProductImage(String product) {
        Timber.d("get product image for " + product + "$$");
        int id;
        switch (product) {
            case "HU3":
                id = R.drawable.hu3_cropped;
                break;
            case "MDR-4":
                id = R.drawable.mdr4_cropped;
                break;
            case "MDR-3":
                id = R.drawable.mdr3_cropped;
                break;
            case "MDR-2":
                id = 0;
//                id = R.drawable.mdr2_cropped;
                break;
            case "VI":
                id = R.drawable.vi_cropped;
            default:
                id = R.drawable.unknown_cropped;
                break;

        }
        return id;
    }

    private void activateUploadProgress(final ProgressBar pb) {
//        mProgressDialog = new ProgressDialog(this);
//        mProgressDialog.setMessage("Uploading firmware to device, please wait...");
//        mProgressDialog.setCancelable(false);
//        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//        mProgressDialog.setIndeterminate(false);
//        mProgressDialog.setMax(fileArray.size());
//        mProgressDialog.setProgressNumberFormat(null);
//        mProgressDialog.show();
        pb.setMax(fileArray.size());

        new Thread(new Runnable() {
            public void run() {
                while (currentLine < fileArray.size()) {
                    // Update the progress bar
                    handler.post(new Runnable() {
                        public void run() {
                            pb.setProgress(currentLine);
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

    private void completeFirmwareUpdate() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                updateDialog.getButton(DialogInterface.BUTTON_POSITIVE).setText("Done");
                updateDialog.getButton(DialogInterface.BUTTON_POSITIVE).setVisibility(View.VISIBLE);
                updateDialog.getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        updateDialog.dismiss();
                    }
                });
                updateDialog.findViewById(R.id.uploadingLinearLayout).setVisibility(View.GONE);
                updateDialog.findViewById(R.id.uploadCompleteTextView).setVisibility(View.VISIBLE);


//                if (updateDialog != null) {
//                    updateDialog.hide();
//                }
//
//                AlertDialog.Builder builder = new AlertDialog.Builder(com.prestoncinema.app.FirmwareUpdateActivity.this);
//                builder.setTitle("Upload complete")
//                        .setMessage("Firmware loaded successfully.")
//                        .setPositiveButton("Done", new DialogInterface.OnClickListener() {
//                            @Override
//                            public void onClick(DialogInterface dialog, int which) {
//                                // send the user back to the main activity
//                                Intent intent = new Intent(FirmwareUpdateActivity.this, MainActivity.class);
//                                startActivity(intent);
//                            }
//                        })
//                        .show();
            }
        });
    }

    private void getFirmwareVersions(List<String> productArray) {
//        List<Map<String, String>> new_arr = new List<Map<String, String>>();
        for (String prod : productArray) {
//            Map<String, String> prod_row = createFirmwareMapRow(prod);
//            firmwareMap.add(prod_row);
//            Timber.d("Added firmware row to firmwareMap:");
//            Timber.d(prod_row.toString());
//            Timber.d("****************************************************************************");
            firmwareArrayList.add(prod);
//            String path = getS19Path(prod);
//            String pathTrimmed
//            String ver = prod.replaceAll("[^A-Za-z0-9]", "") + ": " + latestVersion;
//            Timber.d(prod + " - " + getS19Path(prod));
//            new_arr.add(prod.replaceAll("[^A-Za-z0-9]", "") + ": " + latestVersion);
        }
//        firmwareAdapter.notifyDataSetChanged();
//        return new_arr;
    }

    private void getFirmwareHistory(String product) {
        Timber.d("get firmware history for product: " + product);
    }

//    private Map<String, String> createFirmwareMapRow(String prod) {
//        Timber.d("product string: " + prod);
//
//        Map<String, String> fMap = new HashMap<String, String>();
//        String path = getS19Path(prod);
//
//        Timber.d("s19 path: " + path);
//
//        String fileName = path.split("/")[path.split("/").length - 1];
//        Timber.d("fileName: " + fileName + "^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^");
//        String latestFile = fileName.split("-")[1].split("\\.")[0].replaceAll("_", ".");
//        Timber.d("split latest file:" + latestFile + "******************************************************************************************************");
//        fMap.put("productString", convertProductString(prod));
//        fMap.put("versionString", latestFile);
//        return fMap;
//    }

    private String convertProductString(String prod) {
        String trimmedString = prod.replaceAll("[^A-Za-z0-9_]", "");
        switch (trimmedString) {
            case "Hand3":
                return "HU3";
//            case "DM3":
//                return "DMF 2";
//            case "F_I":
//                return "Focus/Iris";
//            case "Tr4":
//                return "G4 Radio";
            case "MDR":
                return "MDR-2";
            case "MDR3":
                return "MDR-3";
            case "MDR4":
                return "MDR-4";
            case "MLink":
                return "VI";
//            case "LightR":
//                return "LR2";
            default:
                return trimmedString;
        }
    }

    private String trimString(String str) {
        return str.replaceAll("[^A-Za-z0-9_]", "");
    }

    private String getS19Path(String product) {
        Timber.d("getS19Path: " + product + "$$");
        String trimmedString = product.replaceAll("[^A-Za-z0-9_]", "");
        Timber.d("trimmedString: " + trimmedString + "$$");
        SharedPreferences sharedPref = this.getBaseContext().getSharedPreferences("firmwareURLs", Context.MODE_PRIVATE);
        String productString = sharedPref.getString(trimmedString, "Not Found");
        Timber.d("productString: " + productString);
        String pathName = "";
        if (productString.equals("Not Found")) {
            Timber.d("Firmware file not found");
            // TODO: let user know file not found, try downloading again
        }
        else {
            latestVersion = productString.split("=")[0].replaceAll("[{}]", "");
            pathName = productString.split("=")[1].replaceAll("[{}]", "");

            Timber.d("pathName: " + pathName);
        }
        return pathName;
    }

    // read in the s19 file so we can send it line-by-line to the UART
    public void loadProgramFile(String filePath) {
        mProgressDialog = new ProgressDialog(this);
        mProgressDialog.setMessage("Loading firmware file...");
        mProgressDialog.setCancelable(false);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.show();
        Timber.d("loadProgramFile() entered: " + filePath);
        BufferedReader reader = null;

        fileArray.clear();
        byte[] fileBytes = new byte[30];

        try {
//            File firmwareFile = new File(filePath);

//            Timber.d("file size: " + firmwareFile.length());

            FileInputStream fileIn = new FileInputStream(filePath);
//            Timber.d("fileInFD: " + fileIn.getFD());
//            Timber.d("fileIn: " + fileIn.read(fileBytes));

            reader = new BufferedReader(new InputStreamReader(fileIn));

//            Timber.d("reader: " + reader);
            String line;
            while ((line = reader.readLine()) != null) {
//                Timber.d("adding line to fileArray: " + line);
                fileArray.add(line);
            }
            if (fileArray.size() > 0) {
                programLoaded = true;
                if (mProgressDialog != null) {
                    mProgressDialog.hide();
                }
            }
            else {
                if (mProgressDialog != null) {
                    mProgressDialog.hide();
                }
            }
        } catch (Exception ex) {
            Timber.d("loadProgramFile()", ex);
        } finally {
            Timber.d("finally loadProgramFile() OK - fileArray size = " + fileArray.size());
            if (reader != null) {
                try {
                    reader.close();
                }   catch (Exception e) {
                    Timber.d("reader exception", e);
                }
            }
        }
    }

    public void sendProgramFile(int line) {
//        if (currentLine == 0) {
////            isProgramming = true;
//        }
//        Timber.d("sendProgramFile - currentLine: " + line);
        if (currentLine < fileArray.size()) {
//            Timber.d("file line to send: " + fileArray.get(line) + "$$");
            uartSendData(fileArray.get(line), false);
            currentLine += 1;
        }
        else if (currentLine == fileArray.size()) {
//            Timber.d("End of array detected");
//            programMode = false;
            programLoaded = false;
            isConnectionReady = false;
            currentLine = 0;
        }
    }

    public void enableProgramMode(View view) {
//        programMode = true;
        Timber.d("programMode clicked: " + programMode);
    }

    private void restoreDefaults() {
        erased = false;
        programMode = true;
        programLoaded = false;
        startConnectionSetup = true;
        isConnectionReady = false;
        baudRateSent = false;
        expectingDone = false;
        updateConfirmed = false;
        updateConfirmEntered = false;
        currentLine = 0;
        progressStatus = 0;
        baudRateIndex = 0;
        sBuilder.setLength(0);
        baudRateWait = 0;                           // packet wait index
        lastDataSent = "";
        responseExpected = "Hand3\n";
        productDetected = "";
//        productRxString = "";
        Timber.d("ALL VALUES RESTORED TO DEFAULTS");

    }

    public void resetModule(View view) {
        uartSendData("+++\nAT+BLEUARTFIFO=TX\n+++", false);
        responseExpected = "OK";
        resetModule("data");
    }

    private void resetModule(String mode) {
        Timber.d("resetting module, mode: " + mode);
        switch(mode) {
            case "command":
                uartSendData("ATZ", false);
                responseExpected = "OK";
                break;
            case "data":
                uartSendData("+++\nATZ\n+++", false);
                responseExpected = "1\nOK\n";
                break;
            default:
                break;
        }
    }

    private void showFirmwareAlertDialog(List<Map<String, String>> firmwareMap) {

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

    private class TimestampListAdapter extends ArrayAdapter<com.prestoncinema.app.FirmwareUpdateActivity.TimestampData> {

        TimestampListAdapter(Context context, int textViewResourceId) {
            super(context, textViewResourceId);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.layout_uart_datachunkitem, parent, false);
            }

            com.prestoncinema.app.FirmwareUpdateActivity.TimestampData data = getItem(position);
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

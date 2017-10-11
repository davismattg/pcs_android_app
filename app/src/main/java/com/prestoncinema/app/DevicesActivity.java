package com.prestoncinema.app;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by MATT on 7/25/2017.
 */

public class DevicesActivity extends AppCompatActivity {
    // Log
    private final static String TAG = DevicesActivity.class.getSimpleName();
    private ListView mMyDevicesListView;
    private ArrayAdapter deviceAdapter;
    private ArrayList<String> deviceList = new ArrayList<String>();

    public DevicesActivity() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_devices);

        mMyDevicesListView = (ListView) findViewById(R.id.myDevicesListView);
//        deviceAdapter = new SimpleAdapter(DevicesActivity.this, deviceList, R.layout.device_list_item, new String[] {"manufString", "serialString", "flString", "statusString"}, new int[] {R.id.lensTypeTextView, R.id.lensSerialTextView, R.id.lensFocalTextView, R.id.lensStatusTextView});

        SharedPreferences sharedPref = getSharedPreferences("deviceHistory", MODE_PRIVATE);
        Map<String,?> keys = sharedPref.getAll();

        for(Map.Entry<String,?> entry : keys.entrySet()){
            deviceList.add(entry.getValue().toString());
        }

        deviceAdapter = new ArrayAdapter<>(DevicesActivity.this, R.layout.device_list_item, deviceList);
        mMyDevicesListView.setAdapter(deviceAdapter);
    }

    // region Menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_devices, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        final int id = item.getItemId();

        switch (id) {
            case R.id.eraseDeviceHistory:                       // clicked on "Forget all devices" menu option
                boolean wasErased = eraseDeviceHistory();       // call the function that clears device shared preferences

                if (wasErased) {
                    Log.d(TAG, "erased shared pref successfully");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            deviceAdapter.notifyDataSetChanged();       // update the adapter to refresh the
                        }
                    });

                }
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // this function deletes the shared preferences file that holds the addresses of "remembered" bluetooth modules
    private boolean eraseDeviceHistory() {
        SharedPreferences sharedPref = getSharedPreferences("deviceHistory", MODE_PRIVATE);         // retrieve the file
        sharedPref.edit().clear().commit();                                                         // clear it and save in one fell swoop

        deviceList.clear();                                                                         // also need to clear the list, which is tied to the adapter
        return true;
    }
}

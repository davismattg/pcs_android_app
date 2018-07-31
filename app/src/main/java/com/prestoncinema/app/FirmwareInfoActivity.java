package com.prestoncinema.app;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ExpandableListView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static android.icu.lang.UCharacter.GraphemeClusterBreak.L;

public class FirmwareInfoActivity extends AppCompatActivity {
    // Log
    private final static String TAG = FirmwareInfoActivity.class.getSimpleName();
    private ArrayList<String> firmwareList;
    private ExpandableListAdapter listAdapter;
    private ExpandableListView expListView;
    private List<String> listDataHeader;
    HashMap<String, List<String>> listDataChild;

    private HashMap<String, String> currentFirmwareVersions = new HashMap<>();
    private HashMap<String, ArrayList<String>> currentFirmwareChanges = new HashMap<>();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_firmware_info);

        Bundle extras = getIntent().getExtras();

        currentFirmwareVersions = SharedHelper.populateFirmwareVersions(FirmwareInfoActivity.this);
        currentFirmwareChanges = SharedHelper.populateFirmwareChanges(FirmwareInfoActivity.this);

        if (extras != null) {
            firmwareList = extras.getStringArrayList("firmwareArrayList");
        }

        if (findViewById(R.id.fragmentContainer) != null) {
            FirmwareFilesCurrentFragment fragment = new FirmwareFilesCurrentFragment();
            Bundle bundle = new Bundle();
            bundle.putSerializable("firmwareVersions", currentFirmwareVersions);
            bundle.putSerializable("firmwareChanges", currentFirmwareChanges);
            fragment.setArguments(bundle);
            getSupportFragmentManager().beginTransaction().add(R.id.fragmentContainer, fragment).commit();
        }

//        setTitle("Downloaded Firmware Versions");
    }
//        // prepare the list data by populating the arrayLists so the adapter can grab the right info
//        prepareListData();
//
//        // get the expandableListView that will contain the firmware information
//        expListView = (ExpandableListView) findViewById(R.id.firmwareListView);
//
//        // set up the adapter
//        listAdapter = new ExpandableListAdapter(this, listDataHeader, listDataChild);
//
//        // assign the adapter to the expandableListView
//        expListView.setAdapter(listAdapter);
//    }

    // function to prepare the list data to be used by the adapter. this separates the headings from the firmware versions
//    private void prepareListData() {
//        listDataHeader = new ArrayList<String>();
//        listDataChild = new HashMap<String, List<String>>();
//
//        int listSize = firmwareList.size();
//        for (int i=0; i < listSize; i++) {
//            listDataHeader.add(convertProductName(firmwareList.get(i)));
//
//            List<String> prodList = new ArrayList<String>();
//            prodList.add("2.31");
//            prodList.add("2.32");
//            prodList.add("2.35");
//
//            listDataChild.put(listDataHeader.get(i), prodList);
//        }
//    }
//
//    // convert the firmware filenames to user-friendly strings
//    private String convertProductName(String name) {
//        String trimmedString = name.replaceAll("[^A-Za-z0-9_]", "");
//        switch(trimmedString) {
//            case "Hand3":
//                return "Hand Unit 3";
//            case "MDR4":
//                return "MDR-4";
//            case "MDR3":
//                return "MDR-3";
//            case "LightR":
//                return "LR2 Sensor";
//            case "MLink":
//                return "LR2 Video Interface";
//            case "MDR":
//                return "MDR-2";
//            case "DM3":
//                return "Digital Micro Force 3";
//            case "DMF":
//                return "Digital Micro Force";
//            case "F_I":
//                return "Focus/Iris";
//            case "Tr4":
//                return "G4 PCB";
//            case "VLC":
//                return "V+F Lens Control (VLC)";
//            case "WMF":
//                return "Radio Micro Force";
//            default:
//                return trimmedString;
//        }
//    }
}

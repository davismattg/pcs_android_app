package com.prestoncinema.app;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created by MATT on 7/18/2018.
 * This is the Fragment for showing the currently installed firmware versions to the user. It uses
 * an ExpandableListView and custom adapter (FirmwareFilesCurrentAdapter), displayed inside
 * FiremwareUpdateActivity.
 */

public class FirmwareFilesCurrentFragment extends Fragment {
    private ExpandableListView expListView;

    private HashMap<String, ArrayList<String>> firmwareChanges = new HashMap<String, ArrayList<String>>();
    private HashMap<String, String> firmwareVersions = new HashMap<>();
    public FirmwareFilesCurrentFragment() {
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle.getSerializable("firmwareVersions") != null) {
            // noinspection unchecked
            firmwareVersions = (HashMap<String, String>) bundle.getSerializable("firmwareVersions");
        }

        if (bundle.getSerializable("firmwareChanges") != null) {
            //noinspection unchecked
            firmwareChanges = (HashMap<String, ArrayList<String>>) bundle.getSerializable("firmwareChanges");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_firmware_files_current, container, false);

//        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        expListView = view.findViewById(R.id.firmwareFilesExpListView);
        expListView.setAdapter(new FirmwareFilesCurrentAdapter(getContext(), firmwareVersions, firmwareChanges));
        expListView.setGroupIndicator(null);
    }
}

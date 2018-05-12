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
 * Created by MATT on 4/27/2018.
 */

public class FirmwareUpdateInstructionsFragment extends Fragment {
    private ExpandableListView expListView;

    private HashMap<String, ArrayList<String>> instructionsMap = new HashMap<String, ArrayList<String>>();

    public FirmwareUpdateInstructionsFragment() {
    }

    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle bundle = this.getArguments();
        if (bundle.getSerializable("instructions") != null) {
            // noinspection unchecked
            instructionsMap = (HashMap<String, ArrayList<String>>) bundle.getSerializable("instructions");
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_firmware_update_instructions, container, false);

        return rootView;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        expListView = (ExpandableListView) view.findViewById(R.id.firmwareUpdateInstructionsExpListView);
        expListView.setAdapter(new FirmwareUpdateInstructionsAdapter(getContext(), instructionsMap));
        expListView.setGroupIndicator(null);
    }
}

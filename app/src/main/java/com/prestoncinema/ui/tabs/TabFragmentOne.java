package com.prestoncinema.ui.tabs;

import android.app.Fragment;
import android.os.Bundle;
import android.text.Layout;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.prestoncinema.app.R;

/**
 * Created by MATT on 11/13/2017.
 */

public class TabFragmentOne extends Fragment {

    public TabFragmentOne() {
        // required empty constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        // Inflate the layout for this fragment

        return inflater.inflate(R.layout.tab_fragment_one, container, false);
    }
}

package com.prestoncinema.app;

import android.app.Activity;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.widget.ExpandableListAdapter;

import com.prestoncinema.app.databinding.FragmentLensListBinding;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.model.Lens;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 * This Fragment is responsible for displaying the "All Lenses" ExpandableListView
 */

public class AllLensesFragment extends Fragment {
    /** Interface for communicating back to the activity.
     * LensListActivity must implement this interface to communicate with the fragment and
     * receive changes made to the lenses/lists
     */
    OnLensAddedListener parentListener;
    OnChildLensChangedListener childListener;
    OnLensSelectedListener selectedListener;
    OnLensManufacturerSelectedListener manufacturerSelectedListener;
    OnLensSeriesSelectedListener seriesSelectedListener;

    public interface OnLensAddedListener {
        void onLensAdded(String manuf, String series, int focal1, int focal2, String serial, String note);
    }

    public interface OnChildLensChangedListener {
        void onChildLensChanged(LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onChildLensDeleted(LensEntity lens);
    }

    public interface OnLensSelectedListener {
        void onLensSelected(LensEntity lens);
    }

    public interface OnLensManufacturerSelectedListener {
        void onManufacturerSelected(String manufacturer, boolean checked);
//        void updateChildren(String manufacturer, boolean checked);
    }

    public interface OnLensSeriesSelectedListener {
        void onSeriesSelected(String manuf, String series, boolean checked);
    }

    public static final String ARG_PAGE = "ARG_PAGE";

    private Context context;

    /* Initialize the variables used in the 'All Lenses' fragment */
    private List<String> lensListManufHeader;
    private Map<Integer, Integer> lensListDataHeaderCount;
    private HashMap<String, List<String>> lensListTypeHeader;
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap;
    private ArrayList<LensEntity> lensObjectArrayList;

    private LensListParentExpListViewAdapter lensListExpAdapter;
    private ExpandableListView lensListExpListView;

    public static AllLensesFragment newInstance(int page, List<String> lensListManufHeader, HashMap<String, List<String>> lensListTypeHeader,
                                                Map<Integer, Integer> lensListDataHeaderCount, HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap, ArrayList<LensEntity> lensObjectArrayList, Context context) {
//        Bundle args = new Bundle();
//        args.putStringArrayList("lensListManufHeader", lensListManufHeader);
//        args.putInt(ARG_PAGE, page);

        AllLensesFragment fragment = new AllLensesFragment();
        fragment.context = context;
        fragment.lensListManufHeader = lensListManufHeader;
        fragment.lensListDataHeaderCount = lensListDataHeaderCount;
        fragment.lensListTypeHeader = lensListTypeHeader;
        fragment.lensPositionMap = lensPositionMap;
        fragment.lensObjectArrayList = lensObjectArrayList;
//        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceStace) {
        super.onCreate(savedInstanceStace);

//        setRetainInstance(true);

//        if (savedInstanceStace != null) {
//        mPage = getArguments().getInt(ARG_PAGE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        FragmentLensListBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_lens_list, container, false);

//        View view = inflater.inflate(R.layout.fragment_lens_list, container, false);

        /* Initialize the adapter and ExpandableListView to hold the lenses in a multi-level collapsible ExpandableListView */
        lensListExpAdapter = new LensListParentExpListViewAdapter(this.context, this.lensListManufHeader, this.lensListTypeHeader, lensPositionMap, lensObjectArrayList, lensListDataHeaderCount);
        binding.LensListFragmentParentExpListView.setAdapter(lensListExpAdapter);

//        lensListExpListView = view.findViewById(R.id.LensListFragmentParentExpListView);
//        lensListExpListView.setAdapter(lensListExpAdapter);

        /* Set the listener for changes made to the "Parent" level of the ExpandableListView - adding a new lens within a given series */
        lensListExpAdapter.setParentListener(new LensListParentExpListViewAdapter.LensAddedListener() {
            @Override
            public void onAdd(String manuf, String series, int focal1, int focal2, String serial, String note) {
                parentListener.onLensAdded(manuf, series, focal1, focal2, serial, note);
            }
        });

        /* Set the listener for changes made to the "Child" level of the ExpandableListView - editing an existing lens */
        lensListExpAdapter.setChildListener(new LensListParentExpListViewAdapter.LensChangedListener() {
            @Override
            public void onChange(LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
                childListener.onChildLensChanged(lens, serial, note, myListA, myListB, myListC);
            }

            @Override
            public void onDelete(LensEntity lens) {
                childListener.onChildLensDeleted(lens);
            }
        });

        /* Set the listener for sending/receiving only a selected few lenses */
        lensListExpAdapter.setSelectedListener(new LensListParentExpListViewAdapter.LensSelectedListener() {
            @Override
            public void onSelected(LensEntity lens) {
                selectedListener.onLensSelected(lens);
            }
        });

        /* Set the listener for selecting/deselecting lenses at Manufacturer level */
        lensListExpAdapter.setManufacturerSelectedListener(new LensListParentExpListViewAdapter.ManufacturerSelectedListener() {
            @Override
            public void onSelected(String manufacturer, boolean checked) {
                manufacturerSelectedListener.onManufacturerSelected(manufacturer, checked);
            }

//            @Override
//            public void updateChildren(String manufacturer, boolean checked) {
//                manufacturerSelectedListener.updateChildren(manufacturer, checked);
//            }
        });

        /* Set the listener for selecting/deselecting lenses at Series level */
        lensListExpAdapter.setSeriesSelectedListener(new LensListParentExpListViewAdapter.SeriesSelectedListener() {
            @Override
            public void onSelected(String manufacturer, String series, boolean checked) {
                seriesSelectedListener.onSeriesSelected(manufacturer, series, checked);
            }

//            @Override
//            public void updateChildren(String manufacturer, boolean checked) {
//                manufacturerSelectedListener.updateChildren(manufacturer, checked);
//            }
        });
        return binding.getRoot();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Activity activity;

        if (context instanceof Activity) {
            activity = (Activity) context;

            try {
                parentListener = (OnLensAddedListener) activity;
                childListener = (OnChildLensChangedListener) activity;
                selectedListener = (OnLensSelectedListener) activity;
                manufacturerSelectedListener = (OnLensManufacturerSelectedListener) activity;
                seriesSelectedListener = (OnLensSeriesSelectedListener) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString() + " must implement OnLensAddedListener");
            }
        }
    }

    public void updateAdapter() {
        if (lensListExpAdapter != null) {
            Timber.d("updateAdapter");
            lensListExpAdapter.notifyDataSetChanged();
        }
    }

    public ExpandableListAdapter getAdapter() {
        return lensListExpAdapter;
    }

    public void enableLensSelection() {
        Timber.d("AllLensesFragment enableLensSelection reached");
//        if (lensListExpAdapter != null) {
//            Timber.d("change to checkboxes");
            lensListExpAdapter.enableCheckboxes();
//        }
    }
}

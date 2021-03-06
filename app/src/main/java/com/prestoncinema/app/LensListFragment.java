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
import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 * This Fragment is responsible for displaying the "All Lenses" ExpandableListView
 */

public class LensListFragment extends Fragment {
    /** Interface for communicating back to the activity.
     * LensListDetailsActivity must implement this interface to communicate with the fragment and
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
        void onChildLensChanged(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onChildLensDeleted(LensListEntity lensList, LensEntity lens);
    }

    public interface OnLensSelectedListener {
        void onLensSelected(LensEntity lens);
    }

    public interface OnLensManufacturerSelectedListener {
        void onManufacturerSelected(String manufacturer, boolean checked);
//        void updateChildren(String manufacturer, boolean checked);
    }

    public interface OnLensSeriesSelectedListener {
        void onSeriesSelected(String manuf, String series, boolean seriesChecked, boolean checkParent);
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
    private TextView noteTextView;

    private boolean fromImport = false;
    private boolean allLensesChecked = false;
    private int numLensesChecked;
    private int numLensesCheckedOverall;
    private String listNote;

    private ImageView selectAllLensesImageView;
    private TextView numLensesSelectedTextView;

    private FrameLayout selectAllLensesCheckBoxLayout;

    private LensListEntity lensList;

    public static LensListFragment newInstance(
            int page, LensListEntity lensList, List<String> lensListManufHeader, HashMap<String, List<String>> lensListTypeHeader,
            Map<Integer, Integer> lensListDataHeaderCount, HashMap<Integer, HashMap<Integer,
            ArrayList<Integer>>> lensPositionMap, ArrayList<LensEntity> lensObjectArrayList,
            boolean fromImport, String note, int numLensesCheckedOverall, Context context) {

        LensListFragment fragment = new LensListFragment();
        fragment.context = context;
        fragment.lensList = lensList;
        fragment.lensListManufHeader = lensListManufHeader;
        fragment.lensListDataHeaderCount = lensListDataHeaderCount;
        fragment.lensListTypeHeader = lensListTypeHeader;
        fragment.lensPositionMap = lensPositionMap;
        fragment.lensObjectArrayList = lensObjectArrayList;
        fragment.fromImport = fromImport;
        fragment.listNote = note;
        fragment.numLensesCheckedOverall = numLensesCheckedOverall;

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceStace) {
        super.onCreate(savedInstanceStace);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final FragmentLensListBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_lens_list, container, false);

        /* Initialize the adapter and ExpandableListView to hold the lenses in a multi-level collapsible ExpandableListView */
        lensListExpAdapter = new LensListParentExpListViewAdapter(this.context, this.lensList, this.lensListManufHeader, this.lensListTypeHeader, lensPositionMap, lensObjectArrayList, lensListDataHeaderCount);
        binding.LensListFragmentParentExpListView.setAdapter(lensListExpAdapter);

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
            public void onChange(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
                childListener.onChildLensChanged(lensList, lens, serial, note, myListA, myListB, myListC);
            }

            @Override
            public void onDelete(LensListEntity lensList, LensEntity lens) {
                childListener.onChildLensDeleted(lensList, lens);
            }
        });

        /* Set the listener for selecting a single lens */
        lensListExpAdapter.setSelectedListener(new LensListParentExpListViewAdapter.LensSelectedListener() {
            @Override
            public void onSelected(LensEntity lens) {
                selectedListener.onLensSelected(lens);

                if (lens.getChecked()) {
                    numLensesChecked += 1;
                    numLensesCheckedOverall += 1;
                }
                else {
                    numLensesChecked -= 1;
                    numLensesCheckedOverall -= 1;
                }

                if (numLensesChecked < 0) {
                    numLensesChecked = 0;
                }

                if (numLensesCheckedOverall < 0) {
                    numLensesCheckedOverall = 0;
                }

                updateNumLensesChecked();
            }
        });

        /* Set the listener for selecting/deselecting lenses at Manufacturer level */
        lensListExpAdapter.setManufacturerSelectedListener(new LensListParentExpListViewAdapter.ManufacturerSelectedListener() {
            @Override
            public void onSelected(String manufacturer, boolean checked) {
                manufacturerSelectedListener.onManufacturerSelected(manufacturer, checked);

                int numLensesInManufacturer = SharedHelper.getNumLensesForManufacturer(lensObjectArrayList, manufacturer);

                if (checked) {
                    numLensesChecked += numLensesInManufacturer;
                    numLensesCheckedOverall += numLensesInManufacturer;
                }

                else {
                    numLensesChecked -= numLensesInManufacturer;
                    numLensesCheckedOverall -= numLensesInManufacturer;
                }

                if (numLensesChecked < 0) {
                    numLensesChecked = 0;
                }

                if (numLensesCheckedOverall < 0) {
                    numLensesCheckedOverall = 0;
                }

                updateNumLensesChecked();
            }
        });

        /* Set the listener for selecting/deselecting lenses at Series level */
        lensListExpAdapter.setSeriesSelectedListener(new LensListParentExpListViewAdapter.SeriesSelectedListener() {
            @Override
            public void onSelected(String manufacturer, String series, boolean seriesChecked, boolean checkParent) {
                seriesSelectedListener.onSeriesSelected(manufacturer, series, seriesChecked, checkParent);

                int numLensesInSeries = SharedHelper.getNumLensesForSeries(lensObjectArrayList, manufacturer, series);

                if (seriesChecked) {
                    numLensesChecked += numLensesInSeries;
                    numLensesCheckedOverall += numLensesInSeries;
                }

                else {
                    numLensesChecked -= numLensesInSeries;
                    numLensesCheckedOverall -= numLensesInSeries;
                }

                if (numLensesChecked < 0) {
                    numLensesChecked = 0;
                }

                if (numLensesCheckedOverall < 0) {
                    numLensesCheckedOverall = 0;
                }

                updateNumLensesChecked();
            }

//            @Override
//            public void updateChildren(String manufacturer, boolean checked) {
//                manufacturerSelectedListener.updateChildren(manufacturer, checked);
//            }
        });

        /* Check if all lenses in the list are checked, and set the "checkbox" ImageView resource to reflect that */
        allLensesChecked = SharedHelper.areAllLensesChecked(lensObjectArrayList);

        // see how many lenses are checked and set the TextView
        numLensesSelectedTextView = binding.lensListSelectedCountTextView;
        numLensesChecked = SharedHelper.getCheckedLenses(lensObjectArrayList).size();
        updateNumLensesChecked();

        if (allLensesChecked) {
            binding.selectAllLensesImageView.setVisibility(View.INVISIBLE); //ImageResource(R.drawable.ic_check_box_green_checked_24dp);
            binding.selectAllLensesCheckBoxLayout.setVisibility(View.VISIBLE);
        }

        /* OnClickListener for the "Select All" checkbox */
        binding.selectAllLensesImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allLensesChecked = true;

//                // if fromImport == true, none of the lenses are in the DB yet, so just update the ArrayList
//                if (fromImport) {
//                    lensObjectArrayList = SharedHelper.setChecked(lensObjectArrayList, allLensesChecked);
//                    updateAdapter();
//                }
//
//                // otherwise, actually update the checked entry for each lens in the DB
//                else {
                    manufacturerSelectedListener.onManufacturerSelected(null, true);
//                }
                binding.selectAllLensesImageView.setVisibility(View.INVISIBLE);
                binding.selectAllLensesCheckBoxLayout.setVisibility(View.VISIBLE);
                // set the correct checked state for the ImageView
//                binding.selectAllLensesImageView.setImageResource(allLensesChecked ? R.drawable.ic_check_box_green_checked_24dp : R.drawable.ic_check_box_white_unchecked_24dp );
            }
        });

        selectAllLensesImageView = binding.selectAllLensesImageView;

        selectAllLensesCheckBoxLayout = binding.selectAllLensesCheckBoxLayout;

        // OnClickListener for the Select All checkbox when it's selected (i.e. to deselect all)
        binding.selectAllLensesCheckBoxLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                allLensesChecked = false;

                manufacturerSelectedListener.onManufacturerSelected(null, false);

                binding.selectAllLensesImageView.setVisibility(View.VISIBLE);
                binding.selectAllLensesCheckBoxLayout.setVisibility(View.INVISIBLE);
            }
        });
        /* If the fragment is being used to select lenses received from the HU3, make some UI tweaks */
//        if (fromImport) {
////            binding.lensListNoteTextView.setVisibility(View.GONE);
//            binding.selectAllLensesImageView.setImageResource(R.drawable.ic_check_box_green_checked_24dp);
//        }

//        else {
////            binding.lensListNoteTextView.setText(listNote);
//        }

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

    public void updateHeaderCount(Map<Integer, Integer> newCount) {
        lensListExpAdapter.updateHeaderCount(newCount);
    }

    public void updateAdapter() {
        if (lensListExpAdapter != null) {
            Timber.d("updateAdapter");
            lensListExpAdapter.notifyDataSetChanged();
            lensListExpAdapter.initializeCheckedList();
        }
    }

    public void updateAdapterFromSelectAll(boolean selected) {
        if (lensListExpAdapter != null) {
            Timber.d("update adapter from select all");
            lensListExpAdapter.updateSelected(selected);

            //lensListExpAdapter.expandGroups();

            updateNumLensesChecked();
        }
    }

    public void updateSelectAllImageView(boolean checked) {
        selectAllLensesImageView.setImageResource(checked ? R.drawable.ic_check_box_green_checked_24dp : R.drawable.ic_check_box_white_unchecked_24dp );
    }

    /**
     * This method updates the TextView above the lens list that displays the number of selected lenses
     * (across all lists)
     * // TODO: make this update when the individual lenses are checked
     */
    public void updateNumLensesChecked() {
        String text = getResources().getString(R.string.selected_lenses_count);
//        String newText = numLensesChecked + " " + text;
        String newText = numLensesCheckedOverall + " " + text;

        numLensesSelectedTextView.setText(newText);
    }
    public ExpandableListAdapter getAdapter() {
        return lensListExpAdapter;
    }
}

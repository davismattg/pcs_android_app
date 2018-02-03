package com.prestoncinema.app;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

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
     * ManageLensesActivity must implement this interface to communicate with the fragment and
     * receive changes made to the lenses/lists
     */
    OnLensAddedListener parentListener;
    OnChildLensChangedListener childListener;
    OnLensSelectedListener selectedListener;

    public interface OnLensAddedListener {
        public void onLensAdded(String manuf, String series, int focal1, int focal2, String serial, String note);
    }

    public interface OnChildLensChangedListener {
        public void onChildLensChanged(Lens lens, String focal, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        public void onChildLensDeleted(Lens lens);
    }

    public interface OnLensSelectedListener {
        public void onLensSelected(Lens lens);
    }

    public static final String ARG_PAGE = "ARG_PAGE";

    private int mPage;
    private Context context;

    /* Initialize the variables used in the 'All Lenses' fragment */
    private List<String> lensListManufHeader;
    private Map<Integer, Integer> lensListDataHeaderCount;
    private HashMap<String, List<String>> lensListTypeHeader;
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap;
    private ArrayList<Lens> lensObjectArrayList;

    private LensListParentExpListViewAdapter lensListExpAdapter;
    private ExpandableListView lensListExpListView;

    public static AllLensesFragment newInstance(int page, List<String> lensListManufHeader, HashMap<String, List<String>> lensListTypeHeader,
                                                Map<Integer, Integer> lensListDataHeaderCount, HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap, ArrayList<Lens> lensObjectArrayList, Context context) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);

        AllLensesFragment fragment = new AllLensesFragment();
        fragment.context = context;
        fragment.lensListManufHeader = lensListManufHeader;
        fragment.lensListDataHeaderCount = lensListDataHeaderCount;
        fragment.lensListTypeHeader = lensListTypeHeader;
        fragment.lensPositionMap = lensPositionMap;
        fragment.lensObjectArrayList = lensObjectArrayList;
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceStace) {
        super.onCreate(savedInstanceStace);

        setRetainInstance(true);
        mPage = getArguments().getInt(ARG_PAGE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_lens_list, container, false);

        /* Initialize the adapter and ExpandableListView to hold the lenses in a multi-level collapsible ExpandableListView */
        lensListExpAdapter = new LensListParentExpListViewAdapter(this.context, this.lensListManufHeader, this.lensListTypeHeader, lensPositionMap, lensObjectArrayList, lensListDataHeaderCount);
        lensListExpListView = view.findViewById(R.id.LensListFragmentParentExpListView);
        lensListExpListView.setAdapter(lensListExpAdapter);

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
            public void onChange(Lens lens, String focal, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
                childListener.onChildLensChanged(lens, focal, serial, note, myListA, myListB, myListC);
            }

            @Override
            public void onDelete(Lens lens) {
                childListener.onChildLensDeleted(lens);
            }
        });

        /* Set the listener for sending/receiving only a selected few lenses */
        lensListExpAdapter.setSelectedListener(new LensListParentExpListViewAdapter.LensSelectedListener() {
            @Override
            public void onSelected(Lens lens) {
                selectedListener.onLensSelected(lens);
            }
        });

        return view;
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
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString() + " must implement OnLensAddedListener");
            }
        }
    }

    public void updateAdapter() {
        if (lensListExpAdapter != null) {
            Timber.d("updateAdapter");
            lensListExpAdapter.updateChildAdapter();
        }
    }

    public void enableLensSelection() {
        Timber.d("AllLensesFragment enableLensSelection reached");
//        if (lensListExpAdapter != null) {
//            Timber.d("change to checkboxes");
            lensListExpAdapter.enableCheckboxes();
//        }
    }
}

package com.prestoncinema.app.ui;

import android.app.Activity;
import android.arch.lifecycle.ViewModelProviders;
import android.content.Context;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ListView;

import com.prestoncinema.app.MyListViewAdapter;
import com.prestoncinema.app.R;
import com.prestoncinema.app.databinding.FragmentMyListBinding;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.Lens;
import com.prestoncinema.app.viewmodel.LensListViewModel;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

/**
 * Created by MATT on 11/14/2017.
 */

public class MyListFragment extends Fragment {
    /** Interface for communicating back to the activity.
     * LensListDetailsActivity must implement this interface to communicate with the fragment and
     * receive changes made to the lenses/lists
     */
    OnLensChangedListener listener;
    public interface OnLensChangedListener {
        void onLensChanged(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onLensDeleted(LensListEntity lensList, LensEntity lens);
        void onLensSelected(LensEntity lens);
    }

    public static final String ARG_PAGE = "ARG_PAGE";

    private int mPage;
    private Context context;

    private HashMap<String, List<LensEntity>> myListData;
    private ListView myListView;
    private MyListViewAdapter myListViewAdapter;
    private String list;

    private LensListEntity lensList;

    public static MyListFragment newInstance(int page, String myList, HashMap<String, List<LensEntity>> myListData, LensListEntity lensList, Context context) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);

        MyListFragment fragment = new MyListFragment();
        fragment.mPage = page;
        fragment.context = context;
        fragment.myListData = myListData;
        fragment.list = myList;
        fragment.lensList = lensList;
        fragment.setArguments(args);

        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceStace) {
        super.onCreate(savedInstanceStace);

        // TODO: get the viewmodel working for lenses
//        final LensListViewModel viewModel = ViewModelProviders.of(this).get(LensListViewModel.class);

        setRetainInstance(true);
        mPage = getArguments().getInt(ARG_PAGE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_list, container, false);

        /* Border applied after the ListView so the bottom lens looks finished. Hide if list is empty */
//        LinearLayout bottomBorder = view.findViewById(R.id.myListBottomBorder);

        List<LensEntity> lenses = this.myListData.get(this.list);

        Collections.sort(lenses, new Comparator<LensEntity>() {
            @Override
            public int compare(LensEntity lensEntity, LensEntity t1) {
//                return lensEntity.getManufacturer().compareTo(t1.getManufacturer());
                return lensEntity.getManufacturerPosition() - t1.getManufacturerPosition();
            }
        });

        myListViewAdapter = new MyListViewAdapter(this.context, lenses, lensList);
        myListView = view.findViewById(R.id.MyListFragmentListView);
        myListView.setAdapter(myListViewAdapter);

        myListViewAdapter.setListener(new MyListViewAdapter.LensChangedListener() {
            @Override
            public void onChange(LensListEntity lensList, LensEntity lens, String focal, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
                listener.onLensChanged(lensList, lens, serial, note, myListA, myListB, myListC);
            }

            @Override
            public void onDelete(LensListEntity lensList, LensEntity lens) {
                listener.onLensDeleted(lensList, lens);
            }

            @Override
            public void onSelected(LensEntity lens) {
                listener.onLensSelected(lens);
            }
        });

        /* Hide the bottom gray border if the list is empty */
//        if (lenses.size() == 0) {
//            bottomBorder.setVisibility(View.GONE);
//        }

        return view;
//        FragmentMyListBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_my_list, container, false);
//        return binding.getRoot();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        Activity activity;

        if (context instanceof Activity) {
            activity = (Activity) context;

            try {
                listener = (OnLensChangedListener) activity;
            } catch (ClassCastException e) {
                throw new ClassCastException(activity.toString() + " must implement OnLensChangedListener");
            }
        }
    }
}

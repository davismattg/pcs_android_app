package com.prestoncinema.app;

import android.content.Context;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ExpandableListView;

import com.prestoncinema.app.model.Lens;

import java.util.HashMap;
import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 */

public class MyListExpFragment extends Fragment {
    public static final String ARG_PAGE = "ARG_PAGE";

    private int mPage;
    private Context context;

    public List<String> myListDataHeader;
    public HashMap<String, List<Lens>> myListDataChild;

    private MyListExpListViewAdapter myListExpListViewAdapter;
    private ExpandableListView myListExpListView;
    private ExpandableListView expListView;

    public static MyListExpFragment newInstance(int page, List<String> myListDataHeader, HashMap<String, List<Lens>> myListDataChild, Context context) {
        Bundle args = new Bundle();
        args.putInt(ARG_PAGE, page);

        MyListExpFragment fragment = new MyListExpFragment();
        fragment.context = context;
        fragment.myListDataHeader = myListDataHeader;
        fragment.myListDataChild = myListDataChild;
        fragment.setArguments(args);

        Timber.d("header: " + fragment.myListDataHeader.toString());
        Timber.d("children: " + fragment.myListDataChild.toString());
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceStace) {
        super.onCreate(savedInstanceStace);
        mPage = getArguments().getInt(ARG_PAGE);

        /* Initialize the custom adapter class for the My List ExpandableListView */
//        myListDataHeader = Arrays.asList(getResources().getStringArray(R.array.my_list_array));                                                         // use the my list string array resource to populate the header of the my list list view
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_my_list, container, false);

//        myListExpListViewAdapter = new MyListExpListViewAdapter(this.context, this.myListDataHeader, this.myListDataChild, false, false, false);
//        myListExpListView = view.findViewById(R.tag.MyListFragmentExpListView);
//        myListExpListView.setAdapter(myListExpListViewAdapter);

//        myListExpListViewAdapter.setListener(new MyListExpListViewAdapter.MyListEnableListener() {
//            @Override
//            public void onChange(boolean myListA, boolean myListB, boolean myListC) {
//                Timber.d("data changed");
//                Timber.d(myListA + ", " + myListB + ", " + myListC);

//                getActivity();
//            }
//        });
//        myListExpListViewAdapter.registerDataSetObserver(new DataSetObserver() {
//            @Override
//            public void onChanged() {
//                super.onChanged();
//                Timber.d("adapter data changed");
//            }
//        });

        return view;
    }

    public interface OnMyListEnabledListener {
        public void onMyListEnabled(String listName);
    }
}

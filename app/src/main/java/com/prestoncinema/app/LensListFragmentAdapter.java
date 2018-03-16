package com.prestoncinema.app;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.support.v4.app.FragmentStatePagerAdapter;

import com.prestoncinema.app.db.LensClickCallback;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.model.Lens;
import com.prestoncinema.app.ui.MyListFragment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 * This is the main adapter that handles the Fragments for My List A, B, C, and All Lenses.
 */

public class LensListFragmentAdapter extends FragmentStatePagerAdapter {
    final int PAGE_COUNT = 4;
    private String tabTitles[] = new String[] { "All", "My List A", "My List B", "My List C" };

    /* Initialize the variables used in the 'All Lenses' fragment */
    private List<String> lensListManufHeader;
    private Map<Integer, Integer> lensListDataHeaderCount;
    private HashMap<String, List<String>> lensListTypeHeader;
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap;
    private ArrayList<LensEntity> allLensesList;

    /* Initialize the variables used in the 'My Lists' fragments */
    private List<String> myListDataHeader;
    private HashMap<String, List<LensEntity>> myListDataChild;

//    private HashMap<String, List<? extends Lens>> myListDataChild;

    /* Context used to know what activity we came from */
    private Context context;

    private AllLensesFragment allLensesFragment;

    private LensClickCallback lensClickCallback;

    public LensListFragmentAdapter(FragmentManager fm, List<String> myListDataHeader, HashMap<String, List<LensEntity>> myListDataChild,
                                   List<String> lensListManufHeader, HashMap<String, List<String>> lensListTypeHeader,
                                   Map<Integer, Integer> lensListDataHeaderCount, HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap,
                                   ArrayList<LensEntity> lensObjectArrayList, Context context) {
        super(fm);

        this.myListDataHeader = myListDataHeader;
        this.myListDataChild = myListDataChild;
        this.lensListManufHeader = lensListManufHeader;
        this.lensListTypeHeader = lensListTypeHeader;
        this.lensListDataHeaderCount = lensListDataHeaderCount;
        this.lensPositionMap = lensPositionMap;
        this.allLensesList = lensObjectArrayList;
        this.context = context;
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
        if (position == 0) {
            allLensesFragment = AllLensesFragment.newInstance(position + 1, this.lensListManufHeader, this.lensListTypeHeader, this.lensListDataHeaderCount, this.lensPositionMap, this.allLensesList, this.context);
            return allLensesFragment;
        }
        else {
            return MyListFragment.newInstance(position + 1, getPageTitle(position), this.myListDataChild, this.context);
        }
    }

    @Override
    public int getItemPosition(Object object) {
        Timber.d("getItemPosition");

        if (allLensesFragment != null) {
            allLensesFragment.updateAdapter();
        }

        return POSITION_NONE;
    }

    @Override
    public String getPageTitle(int position) {
        // Generate title based on item position
        return tabTitles[position];
    }

    @Override
    public void notifyDataSetChanged() {
        Timber.d("fragment adapter notify data set changed");
        super.notifyDataSetChanged();
    }

    public void updateAdapter(int currentTab) {
        Timber.d("notify data set changed but keep tab at position " + currentTab);


    }
}

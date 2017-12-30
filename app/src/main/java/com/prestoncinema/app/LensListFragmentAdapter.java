package com.prestoncinema.app;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.content.Context;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.view.View;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 */

public class LensListFragmentAdapter extends FragmentStatePagerAdapter {
    final int PAGE_COUNT = 4;
    private String tabTitles[] = new String[] { "My List A", "My List B", "My List C", "All" };

    /* Initialize the variables used in the 'All Lenses' fragment */
    private List<String> lensListManufHeader;
    private Map<Integer, Integer> lensListDataHeaderCount;
    private HashMap<String, List<String>> lensListTypeHeader;
    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap;
    private ArrayList<Lens> lensObjectArrayList;

    /* Initialize the variables used in the 'My Lists' fragments */
    private List<String> myListDataHeader;
    private HashMap<String, List<Lens>> myListDataChild;

    /* Context used to know what activity we came from */
    private Context context;

    private LensListFragment lensListFragment;

    public LensListFragmentAdapter(FragmentManager fm, List<String> myListDataHeader, HashMap<String, List<Lens>> myListDataChild,
                                   List<String> lensListManufHeader, HashMap<String, List<String>> lensListTypeHeader,
                                   Map<Integer, Integer> lensListDataHeaderCount, HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap,
                                   ArrayList<Lens> lensObjectArrayList, Context context) {
        super(fm);

        this.myListDataHeader = myListDataHeader;
        this.myListDataChild = myListDataChild;
        this.lensListManufHeader = lensListManufHeader;
        this.lensListTypeHeader = lensListTypeHeader;
        this.lensListDataHeaderCount = lensListDataHeaderCount;
        this.lensPositionMap = lensPositionMap;
        this.lensObjectArrayList = lensObjectArrayList;
        this.context = context;
    }

    @Override
    public int getCount() {
        return PAGE_COUNT;
    }

    @Override
    public Fragment getItem(int position) {
        if (position == 3) {
            lensListFragment = LensListFragment.newInstance(position + 1, this.lensListManufHeader, this.lensListTypeHeader, this.lensListDataHeaderCount, this.lensPositionMap, this.lensObjectArrayList, this.context);
            return lensListFragment;
        }
        else {
            return MyListFragment.newInstance(position + 1, getPageTitle(position), this.myListDataChild, this.context);
        }
    }

    @Override
    public int getItemPosition(Object object) {
        Timber.d("getItemPosition");

        if (lensListFragment != null) {
            lensListFragment.updateAdapter();
        }

        return POSITION_NONE;
    }

    @Override
    public String getPageTitle(int position) {
        // Generate title based on item position
        return tabTitles[position];
    }
}

package com.prestoncinema.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 */

/////////////////////////////////////////////////////////////////////////////////////////////////
// parent-level adapter class to generate custom multi-level ExpandableListView for displaying //
// lenses currently in a file. This level is the Header, which should just be the lens manuf.  //
// names (lensDataHeader var)                                                                  //
/////////////////////////////////////////////////////////////////////////////////////////////////
public class LensListParentExpListViewAdapter extends BaseExpandableListAdapter {
    private Context context;
    private List<String> listDataHeader;                                                           // header titles. Lens Manuf Names in this case
    private HashMap<String, List<String>> listDataChild;

    private HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap;
    private ArrayList<LensEntity> lensObjectArrayList;
    private Map<Integer, Integer> lensListDataHeaderCount;

    private LensListChildExpListView lensSecondLevel;
    private LensListChildExpListView[] childListViewsArray;
    private LensListChildExpListViewAdapter[] childAdaptersArray;

    private LensListChildExpListViewAdapter childAdapter;
    private LensAddedListener parentListener;
    private LensChangedListener childListener;
    private LensSelectedListener selectedListener;

    private ManufacturerSelectedListener manufacturerSelectedListener;
    private SeriesSelectedListener seriesSelectedListener;

    private ArrayList<Boolean> manufCheckedStatus = new ArrayList<>(8);
//    private ArrayList<Integer> seriesExpandedStatus = new ArrayList<>(8);
//    private SparseIntArray seriesExpandedStatus = new SparseIntArray();
    private HashMap<Integer, Integer[]> seriesExpandedStatus = new HashMap<>();

    private LensListEntity lensList;

    public LensListParentExpListViewAdapter(Context context, LensListEntity lensList, List<String> listDataHeader, HashMap<String, List<String>> listChildData,
                                            HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap,
                                            ArrayList<LensEntity> lensObjectArray, Map<Integer, Integer> lensListDataHeaderCount) {
        this.context = context;
        this.lensList = lensList;
        this.listDataHeader = listDataHeader;
        this.listDataChild = listChildData;
        this.lensPositionMap = lensPositionMap;
        this.lensObjectArrayList = lensObjectArray;
        this.lensListDataHeaderCount = lensListDataHeaderCount;

        initializeCheckedList();
        initializeExpandedStatus();
    }

    /* Interface and setter method for the listener for changes to "Parent" level of ExpandableListView */
    public interface LensAddedListener {
        void onAdd(String manuf, String series, int focal1, int focal2, String serial, String note);
    }

    public void setParentListener(LensAddedListener listener) {
        this.parentListener = listener;
    }

    /* Interface and setter method for the listener for changes to "Parent" level of ExpandableListView */
    public interface LensChangedListener {
        void onChange(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onDelete(LensListEntity lensList, LensEntity lens);
    }

    public void setChildListener(LensChangedListener listener) {
        this.childListener = listener;
    }

    /* Interface and setter method for the listener that handles sending/receiving only selected lenses */
    public interface LensSelectedListener {
        void onSelected(LensEntity lens);
    }

    /* Interface for the listener that handles checking/unchecking all lenses for a given manufacturer */
    public interface ManufacturerSelectedListener {
        void onSelected(String manufacturer, boolean checked);
    }

    /* Interface for the listener that handles checking/unchecking all lenses within a given series and manufacturer */
    public interface SeriesSelectedListener {
        void onSelected(String manufacturer, String series, boolean seriesChecked, boolean checkParent);
    }

    public void setLenses(ArrayList<LensEntity> newLenses) {
        this.lensObjectArrayList = newLenses;
        notifyDataSetChanged();
    }

    public void setSelectedListener(LensSelectedListener listener) { this.selectedListener = listener; }

    public void setManufacturerSelectedListener(ManufacturerSelectedListener listener) {
        this.manufacturerSelectedListener = listener;
    }

    public void setSeriesSelectedListener(SeriesSelectedListener listener) {
        this.seriesSelectedListener = listener;
    }

    public void initializeCheckedList() {
        if (this.listDataHeader != null) {
            for (int i = 0; i < this.listDataHeader.size(); i++) {
                this.manufCheckedStatus.add(i, checkManufacturerSelectedStatus(i));
            }
        }
    }

    public void initializeExpandedStatus() {
        if (this.listDataHeader != null) {
            for (int i = 0; i < this.listDataHeader.size(); i++) {
                int count = this.listDataChild.get(this.listDataHeader.get(i)).size();
                Integer[] expanded = new Integer[count];
                for (int j = 0; j < count; j++) {
                    expanded[j] = 0;
                }
                this.seriesExpandedStatus.put(i, expanded);
            }

            childListViewsArray = new LensListChildExpListView[this.listDataHeader.size()];
            childAdaptersArray = new LensListChildExpListViewAdapter[this.listDataHeader.size()];
        }
    }

    private boolean checkManufacturerSelectedStatus(int groupPosition) {
        Timber.d("checking manuf selected status for groupPos = " + groupPosition);

        String manuf = (String) getGroup(groupPosition);
        int numInManuf = 0;

        // TODO: make this handle when there are 0 lenses for a manuf
        boolean allChecked = true;
        if (this.lensObjectArrayList.size() > 0) {
            for (LensEntity lens : this.lensObjectArrayList) {
                if (lens.getManufacturer().equals(manuf)) {
                    numInManuf += 1;
                    if (!lens.getChecked()) {
                        allChecked = false;
                    }
                }
            }
        }

        else {
            allChecked = false;
        }

        if (numInManuf == 0) {
            allChecked = false;
        }

        return allChecked;
    }

//    @Override
//    public void notifyDataSetChanged() {
//        super.notifyDataSetChanged();
//        Timber.d("notify data set changed");
//    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        return this.listDataChild.get(this.listDataHeader.get(groupPosition)).get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        return childPosition;
    }

    @Override
    public View getChildView(final int groupPosition, final int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent)
    {
        List<String> lensTypeList = this.listDataChild.get(listDataHeader.get(groupPosition));
        final String parentNode = (String) getGroup(groupPosition);
        HashMap<Integer, ArrayList<Integer>> lensPositionIndicesMap = this.lensPositionMap.get(groupPosition);

        HashMap<String, ArrayList<LensEntity>> lensChildHash = new HashMap<>();

        final Integer[] childExpandedStatus = seriesExpandedStatus.get(groupPosition);

        for (String series : lensTypeList) {
            ArrayList<LensEntity> tempLensList = new ArrayList<>();
            lensChildHash.put(series, tempLensList);
            for (LensEntity lens : this.lensObjectArrayList) {
                if (lens.getManufacturer().equals(parentNode) && lens.getSeries().equals(series)) {
                    tempLensList.add(lens);
                }
            }
            lensChildHash.put(series, tempLensList);
        }

        /* Initialize the 2nd level of the ExpandableListView */
        lensSecondLevel = new LensListChildExpListView(this.context);

        /* Initialize the adapter for the 2nd level of the ExpandableListView */
        childAdapter = new LensListChildExpListViewAdapter(
                this.context,
                this.lensList,
                this.listDataChild.get(parentNode),
                lensChildHash,
                lensPositionIndicesMap,
                this.lensObjectArrayList,
                parentNode
        );
        lensSecondLevel.setAdapter(childAdapter);
        lensSecondLevel.setGroupIndicator(null);

//        lensSecondLevel.expandGroup(0);

        /* Set the listener for changes made to the "Parent" level of the ExpandableListView */
        childAdapter.setParentListener(new LensListChildExpListViewAdapter.ParentLensAddedListener() {
            @Override
            public void onAdd(String manuf, String series, int focal1, int focal2, String serial, String note) {
                Timber.d("add the new lens");
                parentListener.onAdd(manuf, series, focal1, focal2, serial, note);
            }
        });

        /* Set the listener for changes made to the "Child" level of the ExpandableListView (editing an existing lens) */
        childAdapter.setChildListener(new LensListChildExpListViewAdapter.ChildLensChangedListener() {
            @Override
            public void onChange(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
                Timber.d("edit the existing lens from All Lenses tab");
                childListener.onChange(lensList, lens, serial, note, myListA, myListB, myListC);
            }

            @Override
            public void onDelete(LensListEntity lensList, LensEntity lens) {
                childListener.onDelete(lensList, lens);
            }
        });

        /* Set the listener for checking individual lenses */
        childAdapter.setSelectedListener(new LensListChildExpListViewAdapter.LensSelectedListener() {
            @Override
            public void onSelected(LensEntity lens) {
                Timber.d("selected lenses listener");
                selectedListener.onSelected(lens);
            }
        });

        /* Set the listener for checking lenses from the Series level */
        childAdapter.setSeriesSelectedListener(new LensListChildExpListViewAdapter.SeriesSelectedListener() {
            @Override
            public void onSelected(String manuf, String series, boolean seriesChecked, boolean checkParent) {
                Timber.d("series selected listener");
                seriesSelectedListener.onSelected(manuf, series, seriesChecked, checkParent);
                if (checkParent) {
                    manufCheckedStatus.set(groupPosition, checkParent);
                }
            }
        });

        lensSecondLevel.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView expandableListView, View view, int groupPosition, long id) {
                Timber.d("child clicked. gp = " + groupPosition);
                Integer[] currentExpandedStatus = childExpandedStatus;
                Timber.d("expanded status for groupPos " + groupPosition + ": " + childExpandedStatus.toString());

                if (childExpandedStatus[groupPosition] == 0) {
                    currentExpandedStatus[groupPosition] = 1;
                }

                else {
                    currentExpandedStatus[groupPosition] = 0;
                }

                seriesExpandedStatus.put(groupPosition, currentExpandedStatus);

//                seriesExpandedStatus.put(groupPosition, 1)
//                Timber.d("series expanded status for gp = " + groupPosition + ": " + seriesExpandedStatus.get(parentNode)[groupPosition]);
                return false;
            }
        });

        childListViewsArray[groupPosition] = lensSecondLevel;
        childAdaptersArray[groupPosition] = childAdapter;

        return lensSecondLevel;
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        // TODO: try to figure out why this has to be hard-coded 1 as opposed to actually using the children's array size
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition)
    {
        return this.listDataHeader.get(groupPosition);
    }

    public boolean getGroupChecked(int groupPosition) {
        return this.manufCheckedStatus.get(groupPosition);
    }

    @Override
    public int getGroupCount()
    {
        if (this.listDataHeader != null) {
            return this.listDataHeader.size();
        }
        else {
            return 0;
        }
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    @Override
    public View getGroupView(final int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent)
    {
        final String headerTitle = (String) getGroup(groupPosition);
        String headerCount = String.valueOf(this.lensListDataHeaderCount.get(groupPosition));

        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.lens_list_group, null);
        }

        final ImageView headerCheckImageView = (ImageView) convertView.findViewById(R.id.checkLensManufacturerImageView);
        ImageView headerImageView = (ImageView) convertView.findViewById(R.id.lensHeaderImageView);
        TextView headerTextView = (TextView) convertView.findViewById(R.id.lensListHeader);
        TextView headerCountTextView = (TextView) convertView.findViewById(R.id.lensHeaderCountTextView);

        headerImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);
        headerTextView.setText(headerTitle);
        headerCountTextView.setText(headerCount);

        // TODO: make this use newRed and darkBlue color resources
        headerCheckImageView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF2E4147);
        headerImageView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF2E4147);
        headerTextView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF2E4147);
        headerCountTextView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF2E4147);

        headerCheckImageView.setImageResource(getGroupChecked(groupPosition) ? R.drawable.ic_check_box_green_checked_24dp : R.drawable.ic_check_box_gray_unchecked_24dp);

        headerCheckImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* If the lens was previously checked, uncheck the box and set the checked attributes to false */
                if (getGroupChecked(groupPosition)) {
                    headerCheckImageView.setImageResource(R.drawable.ic_check_box_gray_unchecked_24dp);
                    manufCheckedStatus.set(groupPosition, false);
                }
                /* If the lens was not previously checked, check the box and set the checked attribute to true */
                else {
                    headerCheckImageView.setImageResource(R.drawable.ic_check_box_green_checked_24dp);
                    manufCheckedStatus.set(groupPosition, true);
                }

                /* Call the interface callback to notify LensListDetailsActivity of the change in "checked" status */
                manufacturerSelectedListener.onSelected(headerTitle, getGroupChecked(groupPosition));

                updateChildCheckboxes(getGroupChecked(groupPosition));
            }
        });

        return convertView;
    }

    @Override
    public boolean hasStableIds()
    {
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition)
    {
        return true;
    }

    public void updateChildAdapter() {
        if (childAdapter != null) {
            Timber.d("updateChildAdapter");
            childAdapter.notifyDataSetChanged();
        }
    }

    public void enableCheckboxes() {
        if (childAdapter != null) {
            Timber.d("enable checkboxes from parent level");
            childAdapter.enableCheckboxes();
        }
    }

    public void updateChildCheckboxes(boolean checked) {
        if (childAdapter != null) {
            Timber.d("update the child textboxes from parent level");
            childAdapter.updateCheckboxes(checked);
        }
    }

    public void updateSelected(boolean selected) {
        for (int i = 0; i < getGroupCount(); i++) {

            int numChildren = this.listDataChild.get(listDataHeader.get(i)).size();
            int lensCount = this.lensListDataHeaderCount.get(i);
            boolean shouldBeSelected = selected && (lensCount > 0);
            this.manufCheckedStatus.set(i, shouldBeSelected);
        }


        Timber.d(seriesExpandedStatus.toString());


        notifyDataSetChanged();

//        for (int i = 0; i < getGroupCount(); i++) {
//            Integer[] childrenExpanded = seriesExpandedStatus.get(i);
//
//            for (int j = 0; j < childrenExpanded.length; j++) {
//                if (childrenExpanded[j] == 1) {
//                    lensSecondLevel.expandGroup(j);
//                }
//            }
//        }
    }

    public void expandGroups() {
        for (int i = 0; i < getGroupCount(); i++) {
            Integer[] childrenExpanded = seriesExpandedStatus.get(i);

            for (int j = 0; j < childrenExpanded.length; j++) {
                if (childrenExpanded[j] == 1) {
                    Timber.d("EXPAND GROUP: Group " + String.valueOf(i) + ", Child: " + String.valueOf(j));

                    childListViewsArray[i].expandGroup(j);
//                    childAdaptersArray[i].expandGroup(j);
                }
            }
        }
    }
}
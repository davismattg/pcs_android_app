package com.prestoncinema.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ExpandableListView;
import android.widget.ImageView;
import android.widget.TextView;

import com.prestoncinema.app.model.Lens;

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
    private ArrayList<Lens> lensObjectArrayList;
    private Map<Integer, Integer> lensListDataHeaderCount;

    private LensListChildExpListViewAdapter childAdapter;
    private LensAddedListener parentListener;
    private LensChangedListener childListener;
    private LensSelectedListener selectedListener;

    public LensListParentExpListViewAdapter(Context context, List<String> listDataHeader, HashMap<String, List<String>> listChildData,
                                            HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> lensPositionMap,
                                            ArrayList<Lens> lensObjectArray, Map<Integer, Integer> lensListDataHeaderCount) {
        this.context = context;
        this.listDataHeader = listDataHeader;
        this.listDataChild = listChildData;
        this.lensPositionMap = lensPositionMap;
        this.lensObjectArrayList = lensObjectArray;
        this.lensListDataHeaderCount = lensListDataHeaderCount;
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
        void onChange(Lens lens, String focal, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onDelete(Lens lens);
    }

    public void setChildListener(LensChangedListener listener) {
        this.childListener = listener;
    }

    /* Interface and setter method for the listener that handles sending/receiving only selected lenses */
    public interface LensSelectedListener {
        void onSelected(Lens lens);
    }

    public void setSelectedListener(LensSelectedListener listener) { this.selectedListener = listener; }


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
    public View getChildView(int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent)
    {
        List<String> lensTypeList = this.listDataChild.get(listDataHeader.get(groupPosition));
        String parentNode = (String) getGroup(groupPosition);
        HashMap<Integer, ArrayList<Integer>> lensPositionIndicesMap = this.lensPositionMap.get(groupPosition);

        HashMap<String, ArrayList<Lens>> lensChildHash = new HashMap<>();

        for (String series : lensTypeList) {
            ArrayList<Lens> tempLensList = new ArrayList<>();
            lensChildHash.put(series, tempLensList);
            for (Lens lens : this.lensObjectArrayList) {
                if (lens.getManufacturer().equals(parentNode) && lens.getSeries().equals(series)) {
                    tempLensList.add(lens);
                }
            }
            lensChildHash.put(series, tempLensList);
        }

        /* Initialize the 2nd level of the ExpandableListView */
        LensListChildExpListView lensSecondLevel = new LensListChildExpListView(this.context);

        /* Initialize the adapter for the 2nd level of the ExpandableListView */
        childAdapter = new LensListChildExpListViewAdapter(
                this.context,
                this.listDataChild.get(parentNode),
                lensChildHash,
                lensPositionIndicesMap,
                this.lensObjectArrayList,
                parentNode
        );
        lensSecondLevel.setAdapter(childAdapter);
        lensSecondLevel.setGroupIndicator(null);

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
            public void onChange(Lens lens, String focal, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
                Timber.d("edit the existing lens from All Lenses tab");
                childListener.onChange(lens, focal, serial, note, myListA, myListB, myListC);
            }

            @Override
            public void onDelete(Lens lens) {
                childListener.onDelete(lens);
            }
        });

        /* Set the listener for sending/receiving only selected lenses */
        childAdapter.setSelectedListener(new LensListChildExpListViewAdapter.LensSelectedListener() {
            @Override
            public void onSelected(Lens lens) {
                Timber.d("selected lenses listener");
                selectedListener.onSelected(lens);
            }
        });

        lensSecondLevel.setOnGroupClickListener(new ExpandableListView.OnGroupClickListener() {
            @Override
            public boolean onGroupClick(ExpandableListView expandableListView, View view, int groupPosition, long id) {
                Timber.d("child clicked. gp = " + groupPosition);
                return false;
            }
        });

        // TODO: Get onGroupCollapseListener working to prevent group collapse when focus on EditText in Dialog
//            lensSecondLevel.setOnGroupCollapseListener(new ExpandableListView.OnGroupCollapseListener() {
//                @Override
//                public void onGroupCollapse(int groupIndex) {
//                    Timber.d("collapse called for position %d", groupIndex);
//                    lensSecondLevel.expandGroup(groupIndex);
//                }
//            });

//            registerForContextMenu(lensSecondLevel);
        return lensSecondLevel;
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        // TODO: try to figure out why this has to be hard-coded 1 as opposed to actually using the children's array size
//            int count = this.listDataChild.get(this.listDataHeader.get(groupPosition)).size();
//            return count;
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition)
    {
        return this.listDataHeader.get(groupPosition);
    }

    @Override
    public int getGroupCount()
    {
        return this.listDataHeader.size();
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, ViewGroup parent)
    {
        String headerTitle = (String) getGroup(groupPosition);
        String headerCount = String.valueOf(this.lensListDataHeaderCount.get(groupPosition));

        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.lens_list_group, null);
        }

        ImageView headerImageView = (ImageView) convertView.findViewById(R.id.lensHeaderImageView);
        TextView headerTextView = (TextView) convertView.findViewById(R.id.lensListHeader);
        TextView headerCountTextView = (TextView) convertView.findViewById(R.id.lensHeaderCountTextView);

        headerImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);
        headerTextView.setText(headerTitle);
        headerCountTextView.setText(headerCount);

        // TODO: make this use newRed color resource
        headerImageView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF333333);
        headerTextView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF333333);
        headerCountTextView.setBackgroundColor(isExpanded ? 0xFFFF533D : 0xFF333333);

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
}
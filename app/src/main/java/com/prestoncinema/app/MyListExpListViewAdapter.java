package com.prestoncinema.app;

import android.content.Context;
import android.database.DataSetObserver;
import android.support.v4.content.ContextCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.prestoncinema.app.db.entity.LensEntity;

import java.util.HashMap;
import java.util.List;

import timber.log.Timber;

/**
 * Created by matt davis on 11/3/2017.
    //////////////////////////////////////////////////////////////////////////////////////////////////
    // Custom adapter class to generate multi-level ExpandableListView for displaying lenses        //
    // currently in My List A/B/C. The parent level is the Header, which should be the list         //
    // names (My List A, My List B, My List C). The Child level is a list of LensEntity objects.    //
    //////////////////////////////////////////////////////////////////////////////////////////////////
 */
public class MyListExpListViewAdapter extends BaseExpandableListAdapter {

    private Context context;
    private List<String> listNames;                                                             // header titles, list names in this case
    private HashMap<String, List<LensEntity>> lensesInList;                                           // list of lenses belonging to each list

    private boolean myListEnabled = false;

    public boolean addToMyListA = false;
    public boolean addToMyListB = false;
    public boolean addToMyListC = false;

    private MyListEnableListener listener;

    private HashMap<String, ImageView> groupViewList;

    public MyListExpListViewAdapter(Context context, List<String> listNames, HashMap<String, List<LensEntity>> lensesInList, boolean addToMyListA, boolean addToMyListB, boolean addToMyListC) {
        this.context = context;
        this.listNames = listNames;
        this.lensesInList = lensesInList;
        this.addToMyListA = addToMyListA;
        this.addToMyListB = addToMyListB;
        this.addToMyListC = addToMyListC;

        groupViewList = new HashMap<String, ImageView>(3);

        groupViewList.put("My List A", new ImageView(this.context));
        groupViewList.put("My List B", new ImageView(this.context));
        groupViewList.put("My List C", new ImageView(this.context));

    }

    public interface MyListEnableListener {
        void onChange(boolean myListA, boolean myListB, boolean myListC);
    }

    public void setListener(MyListEnableListener listener) {
        this.listener = listener;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
//        Timber.d("observer registered");
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {

    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        return this.lensesInList.get(this.listNames.get(groupPosition)).get(childPosition);
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
        LensEntity lensObject = (LensEntity) getChild(groupPosition, childPosition);

        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.my_list_lens, null);
        }

        TextView lensManufAndSeriesTextView = (TextView) convertView.findViewById(R.id.myListLensManufAndSeriesTextView);
        TextView lensFocalTextView = (TextView) convertView.findViewById(R.id.myListLensFocalTextView);
        TextView lensSerialAndNoteTextVIew = (TextView) convertView.findViewById(R.id.myListLensSerialTextView);

        ImageView myListFCalImageView = (ImageView) convertView.findViewById(R.id.myListLensCalFImageView);
        ImageView myListICalImageView = (ImageView) convertView.findViewById(R.id.myListLensCalIImageView);
        ImageView myListZCalImageView = (ImageView) convertView.findViewById(R.id.myListLensCalZImageView);

//        ImageView myListEditLensImageView = (ImageView) convertView.findViewById(R.id.myListEditLensImageView);

        String lensManufAndSeries = lensObject.getManufacturer() + " - " + lensObject.getSeries();
        String lensFocalString = constructFocalLengthString(lensObject.getFocalLength1(), lensObject.getFocalLength2());
        lensManufAndSeriesTextView.setText(lensManufAndSeries);
        lensFocalTextView.setText(lensFocalString);

        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        return this.lensesInList.get(this.listNames.get(groupPosition)).size();
    }

    @Override
    public Object getGroup(int groupPosition)
    {
//        this.groupViewReferences.put(this.listNames.get(groupPosition), getGroupView(groupPosition, false, null, this.get))
        return this.listNames.get(groupPosition);
    }

    @Override
    public int getGroupCount()
    {
        return this.listNames.size();
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded,
                             View convertView, final ViewGroup parent)
    {
        Timber.d("getGroupView: " + groupPosition);
        final String headerTitle = (String) getGroup(groupPosition);
        String headerCount = String.valueOf(getChildrenCount(groupPosition));

        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.my_list_group, null);

//            GroupViewHolder holder = new GroupViewHolder();
//            holder.addView(headerTitle, convertView.findViewById(R.tag.myListAddLensImageView));
//            convertView.setTag(holder);
        }

//        final GroupViewHolder holder = (GroupViewHolder) convertView.getTag();

        RelativeLayout groupLayout = (RelativeLayout) convertView.findViewById(R.id.MyListGroupLayout);
        ImageView headerImageView = (ImageView) convertView.findViewById(R.id.myListHeaderImageView);
        TextView headerTextView = (TextView) convertView.findViewById(R.id.myListHeader);
        TextView headerCountTextView = (TextView) convertView.findViewById(R.id.myListHeaderCountTextView);
        LinearLayout addLensLayout = (LinearLayout) convertView.findViewById(R.id.myListAddLensImageViewLayout);
        final ImageView addLensImageView = (ImageView) convertView.findViewById(R.id.myListAddLensImageView);

        headerImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_alt_white_24dp : R.drawable.ic_expand_more_alt_white_24dp);
        headerTextView.setText(headerTitle);
        headerCountTextView.setText(String.valueOf(headerCount));

        int blueBackgroundColor = ContextCompat.getColor(this.context, R.color.newBlue);
        int grayBackgroundColor = ContextCompat.getColor(this.context, R.color.darkGray);

        groupLayout.setBackgroundColor(isExpanded ? blueBackgroundColor : grayBackgroundColor);

        addLensImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                switch(headerTitle) {
                    case "My List A":
                        if (addToMyListA) {
                            addToMyListA = false;
//                            addLensImageView.setImageResource(R.drawable.ic_edit_24dp);
                        }
                        else {
                            Timber.d("addToMyListA set to true");
                            addToMyListA = true;
                            addToMyListB = false;
                            addToMyListC = false;

//                            updateMyListImageView(headerTitle, addLensImageView);

//                            updateMyListImageView(headerTitle);
//                            ImageView myListBImageView = (ImageView) groupViewReferences.get("My List B").findViewById(R.tag.myListAddLensImageView);
//                            ImageView myListCImageView = (ImageView) groupViewReferences.get("My List C").findViewById(R.tag.myListAddLensImageView);
//
//                            addLensImageView.setImageResource(R.drawable.ic_done_green_24dp);
//                            myListBImageView.setImageResource(R.drawable.ic_edit_24dp);
//                            myListCImageView.setImageResource(R.drawable.ic_edit_24dp);
                        }

                        break;

                    case "My List B":
                        if (addToMyListB) {
                            addToMyListB = false;
//                            addLensImageView.setImageResource(R.drawable.ic_edit_24dp);
                        }
                        else {
                            Timber.d("addToMyListB set to true");
                            addToMyListA = false;
                            addToMyListB = true;
                            addToMyListC = false;

//                            addLensImageView.setImageResource(R.drawable.ic_done_green_24dp);

//                            updateMyListImageView(headerTitle, addLensImageView);
                        }
                        break;

                    case "My List C":
                        if (addToMyListC) {
                            addToMyListC = false;
//                            addLensImageView.setImageResource(R.drawable.ic_edit_24dp);
                        }
                        else {
                            Timber.d("addToMyListC set to true");
                            addToMyListA = false;
                            addToMyListB = false;
                            addToMyListC = true;

//                            addLensImageView.setImageResource(R.drawable.ic_done_green_24dp);

//                            updateMyListImageView(headerTitle, addLensImageView);
                        }
                        break;
                }

                updateEditViews(addToMyListA, addToMyListB, addToMyListC);
                listener.onChange(addToMyListA, addToMyListB, addToMyListC);

            }
        });

        groupViewList.put(headerTitle, addLensImageView);

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

    /* Method to build the correctly formatted focal length(s) String depending on if the lens is a zoom or prime (focalLength2 == 0) */
    private String constructFocalLengthString(int fL1, int fL2) {
        if (fL2 > 0) {                                                                     // fL2 > 0 implies zoom lens
            return String.valueOf(fL1) + "-" + String.valueOf(fL2) + "mm";
        }
        return String.valueOf(fL1) + "mm";                                                                          // prime lens, so just return the first FL
    }

    private void updateEditViews(boolean listA, boolean listB, boolean listC) {
        Timber.d("update the views: " + listA + ", " + listB + ", " + listC);
        Timber.d("groupViewList: " + groupViewList.toString());
        ImageView addLensImageView;
        if (listA) {
            Timber.d("listA");
//            addLensImageView = groupViewList.get(0).findViewById(R.tag.myListAddLensImageView);
//            Timber.d("ImageView: " + addLensImageView.toString());
//            addLensImageView.setImageResource(R.drawable.ic_done_green_24dp);
            groupViewList.get("My List A").setImageResource(R.drawable.ic_done_green_24dp);
        }

        if (!listA) {
            Timber.d("!listA");
//            addLensImageView = groupViewList.get(0).findViewById(R.tag.myListAddLensImageView);
//            Timber.d("ImageView: " + addLensImageView.toString());
//            addLensImageView.setImageResource(R.drawable.ic_edit_24dp);
            groupViewList.get("My List A").setImageResource(R.drawable.ic_edit_24dp);
        }

        if (listB) {
            Timber.d("listB");
//            addLensImageView = groupViewList.get(1).findViewById(R.tag.myListAddLensImageView);
//            Timber.d("ImageView: " + addLensImageView.toString());
//            addLensImageView.setImageResource(R.drawable.ic_done_green_24dp);
            groupViewList.get("My List B").setImageResource(R.drawable.ic_done_green_24dp);
        }

        if (!listB) {
            Timber.d("!listB");
//            addLensImageView = groupViewList.get(1).findViewById(R.tag.myListAddLensImageView);
//            Timber.d("ImageView: " + addLensImageView.toString());
//            addLensImageView.setImageResource(R.drawable.ic_edit_24dp);
            groupViewList.get("My List B").setImageResource(R.drawable.ic_edit_24dp);
        }

        if (listC) {
            Timber.d("listC");
//            addLensImageView = groupViewList.get(2).findViewById(R.tag.myListAddLensImageView);
//            Timber.d("ImageView: " + addLensImageView.toString());
//            addLensImageView.setImageResource(R.drawable.ic_done_green_24dp);
            groupViewList.get("My List C").setImageResource(R.drawable.ic_done_green_24dp);
        }

        if (!listC) {
            Timber.d("!listC");
//            addLensImageView = groupViewList.get(2).findViewById(R.tag.myListAddLensImageView);
//            Timber.d("ImageView: " + addLensImageView.toString());
//            addLensImageView.setImageResource(R.drawable.ic_edit_24dp);
            groupViewList.get("My List C").setImageResource(R.drawable.ic_edit_24dp);
        }
    }
//    /* Method to change the icon back to pencil when another list is selected for editing */
//    private void updateMyListImageView(String currentList, ImageView editView) {
//        Timber.d("updateImageView: " + currentList);
////        Timber.d(groupViewReferences.toString());
//
//        for (String list : listNames) {
//            Timber.d("list: " + list);
//            Timber.d("equal? " + list.equals(currentList));
//
////            ImageView iv = (ImageView) groupViewReferences.get(list).findViewById(R.tag.myListAddLensImageView);
////            ImageView iv = (ImageView) holder.getView(list);
//
////            Timber.d("ImageView: " + iv.getTag());
//            Timber.d("current ImageView: " + editView.getTag());
//
//            if (list.equals(currentList)) {
//                Timber.d(list + " - set to green");
////                iv.setImageResource(R.drawable.ic_done_green_24dp);
//            } else {
//                Timber.d(list + " - set to edit");
////                iv.setImageResource(R.drawable.ic_edit_24dp);
//            }
//        }
//    }
//
//    static class GroupViewHolder {
//        private HashMap<String, View> groupViews = new HashMap<String, View>();
//
//        public GroupViewHolder addView(String list, View view) {
//            Timber.d("Adding view tag " + view.getTag() + " for list: " + list);
//            groupViews.put(list, view);
//            return this;
//        }
//
//        public View getView(String list) {
//            return groupViews.get(list);
//        }
//    }
}


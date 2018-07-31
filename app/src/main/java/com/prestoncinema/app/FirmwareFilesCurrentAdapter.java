package com.prestoncinema.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

/**
 * Created by MATT on 4/27/2018.
 */

public class FirmwareFilesCurrentAdapter extends BaseExpandableListAdapter {

//    private final LayoutInflater inf;
//    private String[] groups;
//    private String[] children;
    private List<String> headers = new ArrayList<>();
    private Context context;

    private HashMap<String, ArrayList<String>> firmwareMap;
    private HashMap<String, String> firmwareVersions;
//    private ArrayList<String> firmwares = new ArrayList<String>();

    public FirmwareFilesCurrentAdapter(Context context, HashMap<String, String> firmwareVersions, HashMap<String, ArrayList<String>> firmwareChanges) {
//        this.headers = new ArrayList<>(firmwareFiles.keySet());
//        this.firmwares = new ArrayList<>(firmwareFiles.values());
        this.firmwareVersions = firmwareVersions;
        this.firmwareMap = firmwareChanges;
        this.headers = new ArrayList<>(Arrays.asList(context.getResources().getStringArray(R.array.product_names_friendly)));
        this.context = context;
    }

    @Override
    public int getGroupCount() {
        return this.headers.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
//        return instructionsMap.get(groupPosition).size();
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return headers.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        ArrayList<String> children = this.firmwareMap.get(getGroup(groupPosition));

        return children;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    public Object getVersion(int groupPosition) {
        return this.firmwareVersions.get(this.headers.get(groupPosition));
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public View getChildView(int groupPosition, final int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {

//        ViewHolder holder;
//        if (convertView == null) {
//            convertView = inf.inflate(R.layout.list_item, parent, false);
//            holder = new ViewHolder();
//
//            holder.text = (TextView) convertView.findViewById(R.id.lblListItem);
//            convertView.setTag(holder);
//        } else {
//            holder = (ViewHolder) convertView.getTag();
//        }
//
//        holder.text.setText(getChild(groupPosition, childPosition).toString());

        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.firmware_versions_child, null);
        }

        ArrayList<String> changesList = (ArrayList<String>) getChild(groupPosition, childPosition);
        String changesText = "";

        for (int i=0; i < changesList.size(); i++) {
            String change = changesList.get(i);

            changesText += change;

            if (i != changesList.size() - 1) {
                changesText += "\n\n";
            }
        }

        TextView instructionsTextView = (TextView) convertView.findViewById(R.id.firmwareChangesTextView);
        instructionsTextView.setText(changesText);

        return convertView;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
//        ViewHolder holder;
//
//        if (convertView == null) {
//            convertView = inf.inflate(R.layout.list_group, parent, false);
//
//            holder = new ViewHolder();
//            holder.text = (TextView) convertView.findViewById(R.id.lblListHeader);
//            convertView.setTag(holder);
//        } else {
//            holder = (ViewHolder) convertView.getTag();
//        }
//
//        holder.text.setText(getGroup(groupPosition).toString());
        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.firmware_versions_group, null);
        }

        TextView productNameTextView = convertView.findViewById(R.id.firmwareVersionsCurrentNameTextView);
        TextView productVersionTextView = convertView.findViewById(R.id.firmwareVersionsCurrentVersionTextView);
        ImageView chevronImageView = convertView.findViewById(R.id.firmwareVersionsCurrentHeaderImageView);
        chevronImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);

        productNameTextView.setText((String) getGroup(groupPosition));
        productVersionTextView.setText((String) getVersion(groupPosition));

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}

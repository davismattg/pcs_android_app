package com.prestoncinema.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import org.w3c.dom.Text;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 4/27/2018.
 */

public class FirmwareUpdateInstructionsAdapter extends BaseExpandableListAdapter {

//    private final LayoutInflater inf;
//    private String[] groups;
//    private String[] children;
    private List<String> headers = new ArrayList<>();
    private Context context;

    private ArrayList<String> instructions = new ArrayList<String>();

    public FirmwareUpdateInstructionsAdapter(Context context, HashMap<String, ArrayList<String>> instructions) {
        this.headers = instructions.get("headers");
        this.instructions = instructions.get("instructions");
        this.context = context;
    }

    @Override
    public int getGroupCount() {
        return headers.size();
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
        return instructions.get(groupPosition);
    }

    @Override
    public long getGroupId(int groupPosition) {
        return groupPosition;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
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
            convertView = headerInflater.inflate(R.layout.firmware_update_instructions_child, null);
        }

        TextView instructionsTextView = (TextView) convertView.findViewById(R.id.firmwareUpdateInstructionsTextView);
        instructionsTextView.setText((String) getChild(groupPosition, childPosition));

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
            convertView = headerInflater.inflate(R.layout.firmware_update_instructions_group, null);
        }

        TextView productNameTextView = convertView.findViewById(R.id.firmwareUpdateInstructionsHeader);
        ImageView chevronImageView = convertView.findViewById(R.id.firmwareUpdateInstructionsHeaderImageView);
        chevronImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_white_24dp : R.drawable.ic_expand_more_white_24dp);

        productNameTextView.setText((String) getGroup(groupPosition));

        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return true;
    }
}

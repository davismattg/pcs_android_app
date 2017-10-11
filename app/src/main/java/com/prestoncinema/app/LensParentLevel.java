//package com.prestoncinema.app;
//
//import android.graphics.Color;
//import android.view.View;
//import android.view.ViewGroup;
//import android.widget.BaseExpandableListAdapter;
//import android.widget.TextView;
//
///**
// * Created by MATT on 9/18/2017.
// */
//
//public class LensParentLevel extends BaseExpandableListAdapter {
//    @Override
//    public Object getChild(int arg0, int arg1)
//    {
//        return arg1;
//    }
//
//    @Override
//    public long getChildId(int groupPosition, int childPosition)
//    {
//        return childPosition;
//    }
//
//    @Override
//    public View getChildView(int groupPosition, int childPosition,
//                             boolean isLastChild, View convertView, ViewGroup parent)
//    {
//        CustExpListview SecondLevelexplv = new CustExpListview(Home.this);
//        SecondLevelexplv.setAdapter(new SecondLevelAdapter());
//        SecondLevelexplv.setGroupIndicator(null);
//        return SecondLevelexplv;
//    }
//
//    @Override
//    public int getChildrenCount(int groupPosition)
//    {
//        return 3;
//    }
//
//    @Override
//    public Object getGroup(int groupPosition)
//    {
//        return groupPosition;
//    }
//
//    @Override
//    public int getGroupCount()
//    {
//        return 5;
//    }
//
//    @Override
//    public long getGroupId(int groupPosition)
//    {
//        return groupPosition;
//    }
//
//    @Override
//    public View getGroupView(int groupPosition, boolean isExpanded,
//                             View convertView, ViewGroup parent)
//    {
//        TextView tv = new TextView(Home.this);
//        tv.setText("->FirstLevel");
//        tv.setBackgroundColor(Color.BLUE);
//        tv.setPadding(10, 7, 7, 7);
//
//        return tv;
//    }
//
//    @Override
//    public boolean hasStableIds()
//    {
//        return true;
//    }
//
//    @Override
//    public boolean isChildSelectable(int groupPosition, int childPosition)
//    {
//        return true;
//    }
//}

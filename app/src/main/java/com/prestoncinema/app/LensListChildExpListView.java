package com.prestoncinema.app;

import android.content.Context;
import android.widget.ExpandableListView;

/**
 * Created by MATT on 11/14/2017.
 */

/////////////////////////////////////////////////////////////////////////////////////////////////
// second level of multi-level ExpandableListView for displaying lenses within a given file    //
// this is typically the lens type "Optimo, Prime, Cinema Zoom," etc, and is dependent on the  //
// lens manufacturer that is the parent to this second level                                   //
/////////////////////////////////////////////////////////////////////////////////////////////////
public class LensListChildExpListView extends ExpandableListView
{
    int groupPosition, childPosition, groupid;

    public LensListChildExpListView(Context context)
    {
        super(context);
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        widthMeasureSpec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
        heightMeasureSpec = MeasureSpec.makeMeasureSpec(100000000, MeasureSpec.AT_MOST);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}

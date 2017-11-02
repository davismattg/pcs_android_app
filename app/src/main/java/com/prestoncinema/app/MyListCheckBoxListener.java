package com.prestoncinema.app;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;


/**
 * Created by MATT on 10/30/2017.
 */

public class MyListCheckBoxListener implements CheckBox.OnCheckedChangeListener {

    public Context mContext;
//    public CheckBox checkBox;
    public boolean isFCal;

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        if (isChecked) {
            if (!isFCal) {
                showMyListToast();
                buttonView.setChecked(false);
            }
        }
    }

    public void showMyListToast() {
        Toast.makeText(mContext, "Focus must be calibrated to add to My List", Toast.LENGTH_SHORT).show();
    }
}

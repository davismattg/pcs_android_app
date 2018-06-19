package com.prestoncinema.app;

import android.content.Context;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Toast;

import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.ArrayList;


/**
 * Created by MATT on 10/30/2017.
 * This class is a custom listener for the checkboxes used to assign a lens to My List A, B, or C.
 * It verifies a couple things before actually letting the user assign the lens to the list.
 */

public class MyListCheckBoxListener implements CheckBox.OnCheckedChangeListener {

    public Context mContext;
    public boolean isFCal;
    public LensListEntity lensList;

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        // if the user checked to box to add the lens to My List
        if (isChecked) {
            // lens may only be added if at least the Focus is calibrated
            if (!isFCal) {
                showMyListToast("F Cal");
                buttonView.setChecked(false);
            }

            // Focus is calibrated, so check whether My List already contains 15 lenses (the max)
            else {
                switch (buttonView.getId()) {
                    case R.id.MyListACheckBox:
                        if (lensList != null) {
                            if (lensList.getMyListALongIds().size() == 15) {
                                showMyListToast("My List A");
                                buttonView.setChecked(false);
                            }
                        }
                        break;
                    case R.id.MyListBCheckBox:
                        if (lensList != null) {
                            if (lensList.getMyListBLongIds().size() == 15) {
                                showMyListToast("My List B");
                                buttonView.setChecked(false);
                            }
                        }
                        break;
                    case R.id.MyListCCheckBox:
                        if (lensList != null) {
                            if (lensList.getMyListCLongIds().size() == 15) {
                                showMyListToast("My List C");
                                buttonView.setChecked(false);
                            }
                        }
                        break;
                }
            }
        }
    }

    public void showMyListToast(String reason) {
        CharSequence toastText;
        if (reason.equals("F Cal")) {
            toastText = "Focus must be calibrated to add to My List";
        }
        else {
            toastText = reason + " can only contain 15 lenses";
        }

        SharedHelper.makeToast(mContext, toastText, Toast.LENGTH_SHORT);
    }
}

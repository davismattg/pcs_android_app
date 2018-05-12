package com.prestoncinema.app;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.TextView;

import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.ArrayList;

import timber.log.Timber;

/**
 * Created by MATT on 4/2/2018.
 */

public class AddToExistingLensListArrayAdapter extends ArrayAdapter<LensListEntity> {
    private ListSelectedListener listener;

    public AddToExistingLensListArrayAdapter(Context context, ArrayList<LensListEntity> lists) {
        super(context, 0, lists);
    }

    public interface ListSelectedListener {
        void onListSelected(LensListEntity list, boolean selected);
    }

    public void setListener(ListSelectedListener listListener) {
        this.listener = listListener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        // Get the lens list for this position
        final LensListEntity list = getItem(position);

        if (convertView == null) {
            convertView = LayoutInflater.from(getContext()).inflate(R.layout.existing_lens_list_item, parent, false);
        }

        final TextView listNameTextView = convertView.findViewById(R.id.existingLensListNameTextView);
        final CheckBox listSelectedCheckBox = convertView.findViewById(R.id.existingLensListCheckBox);

        if (list != null) {
            listNameTextView.setText(list.getName());
            listSelectedCheckBox.setTag(list.getId());

//            listNameTextView.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View view) {
//                    listSelectedCheckBox.setChecked();
//                }
//            });

            listSelectedCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
                @Override
                public void onCheckedChanged(CompoundButton compoundButton, boolean b) {
                    long listId = (long) compoundButton.getTag();
                    Timber.d("List ID: " + listId + ", checked: " + b);
                    if (listener != null) {
                        listener.onListSelected(list, b);
                    }
                }
            });
        }

        return convertView;
    }

}

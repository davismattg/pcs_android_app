package com.prestoncinema.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.prestoncinema.app.databinding.MyListLensBinding;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 11/15/2017.
 */

public class MyListViewAdapter extends ArrayAdapter<LensEntity> {
    private final Context context;
    private List<LensEntity> listChildren;

    private LensChangedListener listener;

    private LensListEntity lensList;

    private int numChecked;

    public MyListViewAdapter(Context context, List<LensEntity> children, LensListEntity lensList) {
        super(context, -1, children);
        this.context = context;
        this.listChildren = children;
        this.lensList = lensList;
    }

    public interface LensChangedListener {
        void onChange(LensListEntity lensList, LensEntity lens, String focalString, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onDelete(LensListEntity lensList, LensEntity lens);
        void onSelected(LensEntity lens);
    }

    public void setListener(LensChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final LensEntity lensObject = this.listChildren.get(position);
        final LensChangedListener lensListener = this.listener;

        MyListLensBinding binding;

        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            binding = MyListLensBinding.inflate(inflater);
            convertView = binding.getRoot();
        }

        else {
            binding = (MyListLensBinding) convertView.getTag();
        }

        binding.setLens(lensObject);
        convertView.setTag(binding);

        final ImageView checkLensImageView = convertView.findViewById(R.id.myListCheckLensImageView);

        /* OnClickListener for the checkboxes */
        checkLensImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* If the lens was previously checked, uncheck the box and set the checked attributes to false */
                if (lensObject.getChecked()) {
                    checkLensImageView.setImageResource(R.drawable.ic_check_box_gray_unchecked_24dp);
                    lensObject.setChecked(false);
                    numChecked -= 1;
                }
                /* If the lens was not previously checked, check the box and set the checked attribute to true */
                else {
                    checkLensImageView.setImageResource(R.drawable.ic_check_box_green_checked_24dp);
                    lensObject.setChecked(true);
                    numChecked += 1;
                }

                if (numChecked < 0) numChecked = 0;

                /* Call the interface callback to notify LensListDetailsActivity of the change in "checked" status */
                listener.onSelected(lensObject);
            }
        });

        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LayoutInflater dialogInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                final View editLensView = dialogInflater.inflate(R.layout.dialog_edit_lens, null);
                final LinearLayout editLensMainLayout = editLensView.findViewById(R.id.editLensMainLayout);
                final LinearLayout confirmLensDeleteLayout = editLensView.findViewById(R.id.confirmLensDeleteLayout);

                // initialize the UI components so we can access their contents when the user presses "Save"
                TextView lensManufAndSeriesTextView = editLensView.findViewById(R.id.lensManufAndSeriesTextView);                       // textView to display the lens manufacturer name
                final TextView lensFocalLengthTextView = editLensView.findViewById(R.id.lensFocalTextView);
                final EditText lensSerialEditText = editLensView.findViewById(R.id.LensSerialEditText);
                final EditText lensNoteEditText = editLensView.findViewById(R.id.LensNoteEditText);

                final CheckBox myListACheckBox = editLensView.findViewById(R.id.MyListACheckBox);
                final CheckBox myListBCheckBox = editLensView.findViewById(R.id.MyListBCheckBox);
                final CheckBox myListCCheckBox = editLensView.findViewById(R.id.MyListCCheckBox);

                ImageView CalFImageView = editLensView.findViewById(R.id.lensCalFImageView);
                ImageView CalIImageView = editLensView.findViewById(R.id.lensCalIImageView);
                ImageView CalZImageView = editLensView.findViewById(R.id.lensCalZImageView);

                // the hidden textView where we store the lens tag (in the form of the view's tag)
                final TextView lensIndexTextView = editLensView.findViewById(R.id.lensIndexTextView);

                // check the status string to see if the lens is part of a list
                final boolean myListA = lensObject.isLensMemberOfMyList(lensList, "My List A");
                final boolean myListB = lensObject.isLensMemberOfMyList(lensList, "My List B");
                final boolean myListC = lensObject.isLensMemberOfMyList(lensList, "My List C");

                boolean calF = lensObject.getCalibratedF();
                boolean calI = lensObject.getCalibratedI();
                boolean calZ = lensObject.getCalibratedZ();

                if (calF) {
                    CalFImageView.setVisibility(View.VISIBLE);
                }

                if (calI) {
                    CalIImageView.setVisibility(View.VISIBLE);
                }

                if (calZ) {
                    CalZImageView.setVisibility(View.VISIBLE);
                }

                // set up listeners for when the user checks the MyList boxes. If F of Lens isn't calibrated, don't let them add to list
                MyListCheckBoxListener listener = new MyListCheckBoxListener();
                listener.mContext = context;
                listener.isFCal = lensObject.getCalibratedF();

                myListACheckBox.setOnCheckedChangeListener(listener);
                myListBCheckBox.setOnCheckedChangeListener(listener);
                myListCCheckBox.setOnCheckedChangeListener(listener);


                // populate the text fields with existing values from the lens
                String lensManufAndSerial = lensObject.getManufacturer() + " - " + lensObject.getSeries();
                lensManufAndSeriesTextView.setText(lensManufAndSerial);
                final String lensFocalString = SharedHelper.constructFocalLengthString(lensObject.getFocalLength1(), lensObject.getFocalLength2());
                lensFocalLengthTextView.setText(lensFocalString);

                lensSerialEditText.setText(lensObject.getSerial());
                lensSerialEditText.setSelection(lensObject.getSerial().length());             // position the cursor at the end of the serial string

                lensNoteEditText.setText(lensObject.getNote());

                // check the myList checkboxes according to whether it's a member of the appropriate list
                myListACheckBox.setChecked(myListA);
                myListBCheckBox.setChecked(myListB);
                myListCCheckBox.setChecked(myListC);

                // add the tag from the lens item in the listView to the hidden textView so we can retrieve it later
                int lensTag = (int) lensObject.getTag();
                lensIndexTextView.setText(String.valueOf(lensTag));

                final AlertDialog dialog = new AlertDialog.Builder(context)
                        //                                    .setTitle("Edit Lens")
                        .setView(editLensView)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Remove", null)
                        .setCancelable(true)
                        .create();

                // custom onShowListener so we can do some checks before saving the lens. prevents "Save" button from automatically closing the dialog
                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(final DialogInterface dialog) {
                        Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                        Button negButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                        Button neutralButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);

                        final View.OnClickListener posButtonListener = new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                String newSerial = lensSerialEditText.getText().toString().trim();                                         // serial of the lens
                                String newNote = lensNoteEditText.getText().toString().trim();

                                // get the (potentially new) my list assignments for the lens
                                boolean myListA = myListACheckBox.isChecked();
                                boolean myListB = myListBCheckBox.isChecked();
                                boolean myListC = myListCCheckBox.isChecked();

                                // returns true if the lens serial + note is 14 chars or less
                                boolean readyToSave = SharedHelper.checkSerialLength(lensFocalString, newSerial, newNote);

                                if (readyToSave) {
                                    // TODO: pass the new parameters back to the main activity to edit the lens
                                    lensListener.onChange(lensList, lensObject, lensFocalString, newSerial, newNote, myListA, myListB, myListC);
                                    dialog.dismiss();

//                                    if (editSuccessful) {
//                                        Timber.d("edit the lens");
////                                        lensListener.onChange(myListA, myListB, myListC);
//                                        dialog.dismiss();
////                                        updateAdapter();
//                                    } else {
//                                        CharSequence toastText = "Error updating lens. Please try again.";
//                                        int duration = Toast.LENGTH_LONG;
//
//                                        Toast toast = Toast.makeText(context, toastText, duration);
//                                        toast.show();
//                                    }
                                } else {
                                    CharSequence toastText = "Error: Lens name too long.";
                                    int duration = Toast.LENGTH_LONG;

                                    Toast toast = Toast.makeText(context, toastText, duration);
                                    toast.show();
                                }
                            }
                        };

                        // perform some housekeeping before closing the dialog. Specifically, make sure the lens serial/note is not more than 14 chars long
                        posButton.setOnClickListener(posButtonListener);

                        // handle clicks on the "Delete" button
                        neutralButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Timber.d("delete lens " + lensObject.getTag());
                                lensListener.onDelete(lensList, lensObject);
                                dialog.dismiss();
                            }
                        });

                        // handle clicks on the "Delete" button
                        neutralButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                editLensMainLayout.setVisibility(View.GONE);
                                confirmLensDeleteLayout.setVisibility(View.VISIBLE);

                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setText("Delete");
                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.INVISIBLE);

                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        lensListener.onDelete(lensList, lensObject);
                                        dialog.dismiss();
                                    }
                                });

                                ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEGATIVE).setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        editLensMainLayout.setVisibility(View.VISIBLE);
                                        confirmLensDeleteLayout.setVisibility(View.GONE);
                                        ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_NEUTRAL).setVisibility(View.VISIBLE);
                                        ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setText("Save");

                                        ((AlertDialog) dialog).getButton(DialogInterface.BUTTON_POSITIVE).setOnClickListener(posButtonListener);
                                    }
                                });

                            }
                        });
                    }
                });

                dialog.show();
            }
        });

        return convertView;
    }
}

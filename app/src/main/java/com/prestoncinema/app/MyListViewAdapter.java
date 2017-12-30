package com.prestoncinema.app;

import android.app.Activity;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 11/15/2017.
 */

public class MyListViewAdapter extends ArrayAdapter<Lens> {
    private final Context context;
    private List<Lens> listChildren;

    private LensChangedListener listener;

    public MyListViewAdapter(Context context, List<Lens> children) {
        super(context, -1, children);
        this.context = context;
        this.listChildren = children;
    }

    public interface LensChangedListener {
        void onChange(Lens lens, String focalString, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onDelete(Lens lens);
    }

    public void setListener(LensChangedListener listener) {
        this.listener = listener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = inflater.inflate(R.layout.my_list_lens, parent, false);
        }

        final Lens lensObject = this.listChildren.get(position);
        final LensChangedListener lensListener = this.listener;

        TextView lensManufAndSeriesTextView = convertView.findViewById(R.id.myListLensManufAndSeriesTextView);
        TextView lensFocalTextView = (TextView) convertView.findViewById(R.id.myListLensFocalTextView);
        TextView lensSerialAndNoteTextVIew = (TextView) convertView.findViewById(R.id.myListLensSerialTextView);

        ImageView myListFCalImageView = (ImageView) convertView.findViewById(R.id.myListLensCalFImageView);
        ImageView myListICalImageView = (ImageView) convertView.findViewById(R.id.myListLensCalIImageView);
        ImageView myListZCalImageView = (ImageView) convertView.findViewById(R.id.myListLensCalZImageView);

        final ImageView myListEditLensImageView = (ImageView) convertView.findViewById(R.id.myListEditLensImageView);
        myListEditLensImageView.setTag(lensObject.getId());

        String lensManufAndSeries = lensObject.getManufacturer() + " - " + lensObject.getSeries();
        final String lensFocalString = SharedHelper.constructFocalLengthString(lensObject.getFocalLength1(), lensObject.getFocalLength2());
        String lensSerialAndNote = lensObject.getSerial() + " " + lensObject.getNote();

        lensManufAndSeriesTextView.setText(lensManufAndSeries);
        lensFocalTextView.setText(lensFocalString);
        lensSerialAndNoteTextVIew.setText(lensSerialAndNote);

        if (!lensObject.getCalibratedF()) {
            myListFCalImageView.setVisibility(View.GONE);
        }

        if (!lensObject.getCalibratedI()) {
            myListICalImageView.setVisibility(View.GONE);
        }

        if (!lensObject.getCalibratedZ()) {
            myListZCalImageView.setVisibility(View.GONE);
        }

        myListEditLensImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                LayoutInflater dialogInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                final View editLensView = dialogInflater.inflate(R.layout.dialog_edit_lens, null);

                // initialize the UI components so we can access their contents when the user presses "Save"
                TextView lensManufAndSeriesTextView = (TextView) editLensView.findViewById(R.id.lensManufAndSeriesTextView);                       // textView to display the lens manufacturer name
                final TextView lensFocalLengthTextView = (TextView) editLensView.findViewById(R.id.lensFocalTextView);
                final EditText lensSerialEditText = (EditText) editLensView.findViewById(R.id.LensSerialEditText);
                final EditText lensNoteEditText = (EditText) editLensView.findViewById(R.id.LensNoteEditText);

                final CheckBox myListACheckBox = (CheckBox) editLensView.findViewById(R.id.MyListACheckBox);
                final CheckBox myListBCheckBox = (CheckBox) editLensView.findViewById(R.id.MyListBCheckBox);
                final CheckBox myListCCheckBox = (CheckBox) editLensView.findViewById(R.id.MyListCCheckBox);

                ImageView CalFImageView = (ImageView) editLensView.findViewById(R.id.lensCalFImageView);
                ImageView CalIImageView = (ImageView) editLensView.findViewById(R.id.lensCalIImageView);
                ImageView CalZImageView = (ImageView) editLensView.findViewById(R.id.lensCalZImageView);

                // the hidden textView where we store the lens index (in the form of the view's tag)
                final TextView lensIndexTextView = (TextView) editLensView.findViewById(R.id.lensIndexTextView);

                // check the status string to see if the lens is part of a list
                final boolean myListA = lensObject.getMyListA();
                final boolean myListB = lensObject.getMyListB();
                final boolean myListC = lensObject.getMyListC();

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
                int lensTag = (int) myListEditLensImageView.getTag();
                lensIndexTextView.setText(String.valueOf(lensTag));

//                            final Lens thisLens = this.lensObjectList.get(lensTag);
//                            Timber.d(thisLens.getManufacturer());
//                            Timber.d(thisLens.getSeries());
//                            Timber.d(String.valueOf(thisLens.getFocalLength1()));
//                            Timber.d(String.valueOf(thisLens.getFocalLength2()));

                final AlertDialog dialog = new AlertDialog.Builder(context)
                        //                                    .setTitle("Edit Lens")
                        .setView(editLensView)
                        .setPositiveButton("Save", null)
                        .setNegativeButton("Cancel", null)
                        .setNeutralButton("Delete", null)
                        .setCancelable(true)
                        .create();

                // custom onShowListener so we can do some checks before saving the lens. prevents "Save" button from automatically closing the dialog
                dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                    @Override
                    public void onShow(final DialogInterface dialog) {
                        Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                        Button negButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEGATIVE);
                        Button neutralButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);

                        // perform some housekeeping before closing the dialog. Specifically, make sure the lens serial/note is not more than 14 chars long
                        posButton.setOnClickListener(new View.OnClickListener() {
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
                                    boolean editSuccessful = true; //editLens(lensObject, lensFocalString, newSerial, newNote, myListA, myListB, myListC);
                                    lensListener.onChange(lensObject, lensFocalString, newSerial, newNote, myListA, myListB, myListC);

                                    if (editSuccessful) {
                                        Timber.d("edit the lens");
//                                        lensListener.onChange(myListA, myListB, myListC);
                                        dialog.dismiss();
//                                        updateAdapter();
                                    } else {
                                        CharSequence toastText = "Error updating lens. Please try again.";
                                        int duration = Toast.LENGTH_LONG;

                                        Toast toast = Toast.makeText(context, toastText, duration);
                                        toast.show();
                                    }
                                } else {
                                    CharSequence toastText = "Error: Lens name too long.";
                                    int duration = Toast.LENGTH_LONG;

                                    Toast toast = Toast.makeText(context, toastText, duration);
                                    toast.show();
                                }
                            }
                        });

                        // handle clicks on the "Delete" button
                        neutralButton.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                Timber.d("delete lens " + lensObject.getId());
                                lensListener.onDelete(lensObject);
                                dialog.dismiss();
                            }
                        });
                    }
                });

                dialog.show();
            }
        });

        return convertView;
    }

//    private void updateAdapter() {
//        Timber.d("updateAdapter");
//        notifyDataSetChanged();
//    }

}

package com.prestoncinema.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.prestoncinema.app.databinding.LensListLensBinding;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 */

public class LensListChildExpListViewAdapter extends BaseExpandableListAdapter
{
    private Context context;
    private List<String> listDataHeader;                                                             // header titles (lens types, per manufacturer)
    private HashMap<String, ArrayList<LensEntity>> listDataChild;
    private ArrayList<LensEntity> lensObjectList;
    private HashMap<Integer, ArrayList<Integer>> listDataChildIndices;
    private String manufName;                                                                      // manufacturer name
    private boolean isPrime;
    private int numChecked = 0;

    private boolean allLenses = false;

    private ParentLensAddedListener parentListener;
    private ChildLensChangedListener childListener;
    private LensSelectedListener selectedListener;
    private SeriesSelectedListener seriesSelectedListener;
    private ExpandedStateListener expandedStateListener;

    private ViewGroup parentListView;

    private ArrayList<Boolean> seriesCheckedStatus = new ArrayList<>();

    private LensListEntity lensList;

    public LensListChildExpListViewAdapter(Context context,
                                           LensListEntity lensList, List<String> listDataHeader,
                                           HashMap<String, ArrayList<LensEntity>> listDataChild,
                                           HashMap<Integer, ArrayList<Integer>> listDataChildIndices,
                                           ArrayList<LensEntity> lensObjectList,
                                           String manufName) {
        this.context = context;
        this.lensList = lensList;
        this.listDataHeader = listDataHeader;
        this.listDataChild = listDataChild;
        this.lensObjectList = lensObjectList;
        this.listDataChildIndices = listDataChildIndices;
        this.manufName = manufName;

        this.allLenses = lensList.getName().equals("All Lenses");
        initializeCheckedList();
    }

    public interface ChildLensChangedListener {
        void onChange(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC);
        void onDelete(LensListEntity lensList, LensEntity lens);
    }

    public void setChildListener(ChildLensChangedListener listener) {
        this.childListener = listener;
    }

    public interface ParentLensAddedListener {
        void onAdd(String manuf, String series, int focal1, int focal2, String serial, String note);
    }

    public void setParentListener(ParentLensAddedListener listener) {
        this.parentListener = listener;
    }

    public interface LensSelectedListener {
        void onSelected(LensEntity lens);
    }

    public void setSelectedListener(LensSelectedListener listener) {
        this.selectedListener = listener;
    }

    public interface SeriesSelectedListener {
        void onSelected(String manuf, String series, boolean seriesChecked, boolean checkParent);
    }

    public void setSeriesSelectedListener(SeriesSelectedListener listener) {
        this.seriesSelectedListener = listener;
    }

    public interface ExpandedStateListener {
        void onExpanded(int position);
    }

    public void setExpandedStateListener(ExpandedStateListener listener) {
        this.expandedStateListener = listener;
    }

    public void initializeCheckedList() {
        for (int i = 0; i < this.listDataHeader.size(); i++) {
            this.seriesCheckedStatus.add(i, checkLensSelectedStatus(i));
        }
    }

    /**
     * Helper method to determine whether all lenses in a series are selected, and thus whether
     * the series should show a "checked" or "unchecked" image
     * @param groupPosition
     * @return
     */
    private boolean checkLensSelectedStatus(int groupPosition) {
        ArrayList<LensEntity> lensesInSeries = this.listDataChild.get((String) getGroup(groupPosition));

        boolean allChecked = true;
        if (lensesInSeries.size() > 0) {
            for (LensEntity lens : lensesInSeries) {
                if (!lens.getChecked()) {
                    allChecked = false;
                }
            }
        }
        else {
            allChecked = false;
        }

        return allChecked;
    }

    @Override
    public Object getChild(int groupPosition, int childPosition)
    {
        return this.listDataChild.get(this.listDataHeader.get(groupPosition)).get(childPosition);
    }

    @Override
    public long getChildId(int groupPosition, int childPosition)
    {
        return childPosition;
    }

    public Object getChildTag(int groupPosition, int childPosition) {
        return this.listDataChildIndices.get(groupPosition).get(childPosition);
    }

    @Override
    public View getChildView(final int groupPosition, int childPosition,
                             boolean isLastChild, View convertView, ViewGroup parent)
    {
        /* The Lens object whose information will populate the view */
        final LensEntity childObject = (LensEntity) getChild(groupPosition, childPosition);

        /* Get the strings that will be used to populate the lens row */
        final String childText = SharedHelper.constructFocalLengthString(childObject.getFocalLength1(), childObject.getFocalLength2());

        LensListLensBinding binding;

        parentListView = parent;

        /* If this is the first time we're inflating this view */
        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            binding = LensListLensBinding.inflate(headerInflater);
            convertView = binding.getRoot();
        }

        else {
            binding = (LensListLensBinding) convertView.getTag();
        }

        binding.setLens(childObject);
        convertView.setTag(binding);

        final ImageView checkLensImageView = convertView.findViewById(R.id.checkLensImageView);                               // the imageView used to contain the edit icon (pencil)

        // initialize the ImageViews used to show whether the lens is calibrated
        ImageView calFImageView = convertView.findViewById(R.id.lensCalFImageView);
        ImageView calIImageView = convertView.findViewById(R.id.lensCalIImageView);
        ImageView calZImageView = convertView.findViewById(R.id.lensCalZImageView);

        // show the icon if necessary
        if (childObject.getCalibratedF()) {
            calFImageView.setVisibility(View.VISIBLE);
        }

        if (childObject.getCalibratedI()) {
            calIImageView.setVisibility(View.VISIBLE);
        }

        if (childObject.getCalibratedZ()) {
            calZImageView.setVisibility(View.VISIBLE);
        }

        // initialize the ImageViews used to show whether the lens is a member of My List A/B/C
        ImageView myListAImageView = convertView.findViewById(R.id.myListAImageView);
        ImageView myListBImageView = convertView.findViewById(R.id.myListBImageView);
        ImageView myListCImageView = convertView.findViewById(R.id.myListCImageView);

        // show the My List A/B/C icon if necessary
        if (childObject.isLensMemberOfMyList(lensList, "My List A")) {
            myListAImageView.setVisibility(View.VISIBLE);
        }

        if (childObject.isLensMemberOfMyList(lensList, "My List B")) {
            myListBImageView.setVisibility(View.VISIBLE);
        }

        if (childObject.isLensMemberOfMyList(lensList, "My List C")) {
            myListCImageView.setVisibility(View.VISIBLE);
        }

        /* Set the tag and tag for this view */
        checkLensImageView.setTag(childObject.getId());

        convertView.setId((int) childObject.getId());
        convertView.setLongClickable(true);                                                                                             // enable longClick on the lens view

        checkLensImageView.setImageResource(childObject.getChecked() ? R.drawable.ic_check_box_green_checked_24dp : R.drawable.ic_check_box_gray_unchecked_24dp);

        /* OnClickListener for the checkboxes */
        checkLensImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                /* If the lens was previously checked, uncheck the box and set the checked attributes to false */
                if (childObject.getChecked()) {
                    checkLensImageView.setImageResource(R.drawable.ic_check_box_gray_unchecked_24dp);
                    childObject.setChecked(false);
                    numChecked -= 1;
                }
                /* If the lens was not previously checked, check the box and set the checked attribute to true */
                else {
                    checkLensImageView.setImageResource(R.drawable.ic_check_box_green_checked_24dp);
                    childObject.setChecked(true);
                    numChecked += 1;
                }

                if (numChecked < 0) numChecked = 0;

                /* Call the interface callback to notify LensListDetailsActivity of the change in "checked" status */
                selectedListener.onSelected(childObject);
            }
        });

        /* OnClickListener for when the user taps on the lens item itself. This is used to inflate the dialog where the user can actually edit the lens */
        convertView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                LayoutInflater dialogInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);

                final View editLensView = dialogInflater.inflate(allLenses ? R.layout.dialog_edit_lens_all_lenses : R.layout.dialog_edit_lens, null);                               // inflate the view to use as the edit dialog
                final LinearLayout editLensMainLayout = editLensView.findViewById(R.id.editLensMainLayout);
                final LinearLayout confirmLensDeleteLayout = editLensView.findViewById(R.id.confirmLensDeleteLayout);
//                final TextView deleteConfirmationTextView = editLensView.findViewById(R.id.confirmLensDeleteTextView);

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

                // check to see if the lens is part of a list
                final boolean myListA = childObject.isLensMemberOfMyList(lensList, "My List A") || childObject.getMyListA();
                final boolean myListB = childObject.isLensMemberOfMyList(lensList, "My List B") || childObject.getMyListB();
                final boolean myListC = childObject.isLensMemberOfMyList(lensList, "My List C") || childObject.getMyListC();

                if (childObject.getCalibratedF()) {
                    CalFImageView.setVisibility(View.VISIBLE);
                }

                if (childObject.getCalibratedI()) {
                    CalIImageView.setVisibility(View.VISIBLE);
                }

                if (childObject.getCalibratedZ()) {
                    CalZImageView.setVisibility(View.VISIBLE);
                }

                // set up listeners for when the user checks the MyList boxes.
                // If focus isn't calibrated, don't let them add to MyList.
                // Also check that each list doesn't have more than 15 lenses
                if (!allLenses) {
                    MyListCheckBoxListener listener = new MyListCheckBoxListener();
                    listener.mContext = context;
                    listener.isFCal = childObject.getCalibratedF();
                    listener.lensList = lensList;

                    myListACheckBox.setOnCheckedChangeListener(listener);
                    myListBCheckBox.setOnCheckedChangeListener(listener);
                    myListCCheckBox.setOnCheckedChangeListener(listener);

                    // check the myList checkboxes according to whether it's a member of the appropriate list
                    myListACheckBox.setChecked(myListA);
                    myListBCheckBox.setChecked(myListB);
                    myListCCheckBox.setChecked(myListC);
                }

                // populate the text fields with existing values from the lens
                String lensManufAndSerial = childObject.getManufacturer() + " - " + childObject.getSeries();
                lensManufAndSeriesTextView.setText(lensManufAndSerial);
                lensFocalLengthTextView.setText(SharedHelper.constructFocalLengthString(childObject.getFocalLength1(), childObject.getFocalLength2()));

                lensSerialEditText.setText(childObject.getSerial());
                if (childObject.getSerial() != null) {
                    lensSerialEditText.setSelection(childObject.getSerial().length());             // position the cursor at the end of the serial string
                }

                lensNoteEditText.setText(childObject.getNote());

                // add the tag from the lens item in the listView to the hidden textView so we can retrieve it later
                long lensTag = (long) checkLensImageView.getTag();
                lensIndexTextView.setText(String.valueOf(lensTag));

                final AlertDialog dialog = new AlertDialog.Builder(context)
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

                        final View.OnClickListener posButtonListener = new View.OnClickListener() {
                            @Override
                            public void onClick(View view) {
                                String newSerial = lensSerialEditText.getText().toString().trim();                                         // serial of the lens
                                String newNote = lensNoteEditText.getText().toString().trim();

                                boolean myListA = false;
                                boolean myListB = false;
                                boolean myListC = false;

                                if (!allLenses) {
                                    // get the (potentially new) my list assignments for the lens
                                    myListA = myListACheckBox.isChecked();
                                    myListB = myListBCheckBox.isChecked();
                                    myListC = myListCCheckBox.isChecked();
                                }

                                boolean serialLengthOK = SharedHelper.checkSerialLength(childText, newSerial, newNote);
                                boolean lensExists = false;
                                boolean readyToSave = false;

                                // TODO: finish lens checking logic
                                if (serialLengthOK) {
//                                    lensExists = SharedHelper.checkIfLensExists(listDataChild.get(childObject.getSeries()), childObject.getFocalLength1(), childObject.getFocalLength2(), newSerial, newNote);
//                                    readyToSave = (serialLengthOK && !lensExists);
                                    readyToSave = serialLengthOK;
                                }

                                if (readyToSave) {
                                    childListener.onChange(lensList, childObject, newSerial, newNote, myListA, myListB, myListC);
                                    dialog.dismiss();
                                } else {
                                    CharSequence toastText;

                                    if (lensExists) {
                                        toastText = "Error: Lens already exists in this list.";
                                    }
                                    else {
                                        toastText = "Error: Lens name too long.";
                                    }

                                    SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                                }
                            }
                        };

                        // perform some housekeeping before closing the dialog. Specifically, make sure the lens serial/note is not more than 14 chars long
                        posButton.setOnClickListener(posButtonListener);

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
                                        childListener.onDelete(lensList, childObject);
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

        // hide the cal icons if necessary TODO: figure out this bug cuz this shouldn't be needed. see SO post: https://stackoverflow.com/questions/50418604/imageview-setvisibility-not-working-only-for-first-item-in-expandablelistview
        if (!childObject.getCalibratedF()) {
            calFImageView.setVisibility(View.INVISIBLE);
        }

        if (!childObject.getCalibratedI()) {
            calIImageView.setVisibility(View.INVISIBLE);
        }

        if (!childObject.getCalibratedZ()) {
            calZImageView.setVisibility(View.INVISIBLE);
        }

        // hide the My List A/B/C icon if necessary TODO: figure out this bug cuz this shouldn't be needed. see SO post: https://stackoverflow.com/questions/50418604/imageview-setvisibility-not-working-only-for-first-item-in-expandablelistview
        if (!childObject.isLensMemberOfMyList(lensList, "My List A")) {
            myListAImageView.setVisibility(View.INVISIBLE);
        }

        if (!childObject.isLensMemberOfMyList(lensList, "My List B")) {
            myListBImageView.setVisibility(View.INVISIBLE);
        }

        if (!childObject.isLensMemberOfMyList(lensList, "My List C")) {
            myListCImageView.setVisibility(View.INVISIBLE);
        }

        return convertView;
    }

    @Override
    public int getChildrenCount(int groupPosition)
    {
        try {
            return this.listDataChild.get(this.listDataHeader.get(groupPosition)).size();
//            return this.listDataChildIndices.get(groupPosition).size();
        }
        catch (Exception e) {
            return 0;
        }
    }

    @Override
    public Object getGroup(int groupPosition)
    {
        return this.listDataHeader.get(groupPosition);
    }

    public boolean getGroupChecked(int groupPosition) {
        return this.seriesCheckedStatus.get(groupPosition);
    }

    @Override
    public int getGroupCount()
    {
        return this.listDataHeader.size();
    }

    @Override
    public long getGroupId(int groupPosition)
    {
        return groupPosition;
    }

    // this method is called when creating the views for the lens types (Optimo, Cinema Prime, etc).
    // We attach a "+" button (ImageView really) to each header so the user can add a lens in that series.
    // The alert dialog builder in the setOnClickListener function takes care of this.
    @Override
    public View getGroupView(final int groupPosition, final boolean isExpanded, View convertView, ViewGroup parent) {
        final String manufTitle = this.manufName;                                                                                      // the name of the lens manufacturer
        final String typeTitle = (String) getGroup(groupPosition);                                                                      // the series of the lens (Optimo, Cinema Prime, etc)

        // inflate the view to be shown
        if (convertView == null) {
            LayoutInflater headerInflater = (LayoutInflater) this.context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            convertView = headerInflater.inflate(R.layout.lens_list_type, null);
        }

//        Timber.d("groupPos: " + groupPosition + ", isExpanded: " + isExpanded);

        // initialize the view components
        final ImageView checkImageView = (ImageView) convertView.findViewById(R.id.checkLensTypeImageView);
        final FrameLayout checkBoxCheckedLayout = convertView.findViewById(R.id.seriesCheckBoxCheckedLayout);
        final FrameLayout addLensLayout = convertView.findViewById(R.id.lensTypeAddImageLayout);
        ImageView typeImageView = (ImageView) convertView.findViewById(R.id.lensTypeImageView);
        TextView typeTextView = (TextView) convertView.findViewById(R.id.lensListType);
        ImageView addImageView = (ImageView) convertView.findViewById(R.id.lensTypeAddImageView);

        // set the lens type in the textView
        typeTextView.setText(typeTitle);

        int childCount = getChildrenCount(groupPosition);

        // show the green check box if needed
        if (getGroupChecked(groupPosition)) {
            checkImageView.setVisibility(View.INVISIBLE);
            checkBoxCheckedLayout.setVisibility(View.VISIBLE);
        }

        // otherwise, show the empty gray one
        else {
            checkImageView.setVisibility(View.VISIBLE);
            checkBoxCheckedLayout.setVisibility(View.INVISIBLE);
        }
//        checkImageView.setImageResource(getGroupChecked(groupPosition) ? R.drawable.ic_check_box_green_checked_24dp : R.drawable.ic_check_box_gray_unchecked_24dp);

        if (childCount == 0) {
            int textColor = context.getResources().getColor(R.color.disabledGray);
            typeTextView.setTextColor(textColor); //setTextColor(0xFFBBBBBB);
            checkImageView.setVisibility(View.INVISIBLE);
            checkBoxCheckedLayout.setVisibility(View.INVISIBLE);
            typeImageView.setVisibility(View.INVISIBLE);
        }
        else {
            int textColor = context.getResources().getColor(R.color.darkBlue);
            typeTextView.setTextColor(textColor);
            checkImageView.setVisibility(View.VISIBLE);
            typeImageView.setVisibility(View.VISIBLE);

            // depending on the isExpanded state of the group (and if there are > 0 children in the group), display the appropriate up/down chevron icon
            typeImageView.setImageResource(isExpanded ? R.drawable.ic_expand_less_blue_24dp : R.drawable.ic_expand_more_blue_24dp);
        }

        // OnClickListener for when the box is unchecked and is being checked
        checkImageView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
//                /* If the lens was previously checked, uncheck the box and set the checked attributes to false */
//                if (getGroupChecked(groupPosition)) {
//                    checkImageView.setImageResource(R.drawable.ic_check_box_gray_unchecked_24dp);
//                    seriesCheckedStatus.set(groupPosition, false);
//                }
                /* If the lens was not previously checked, check the box and set the checked attribute to true */
//                else {
//                    checkImageView.setImageResource(R.drawable.ic_check_box_green_checked_24dp);
                checkImageView.setVisibility(View.INVISIBLE);
                checkBoxCheckedLayout.setVisibility(View.VISIBLE);
                    seriesCheckedStatus.set(groupPosition, true);
//                }

                /* Call the interface callback to notify LensListDetailsActivity of the change in "checked" status */
                seriesSelectedListener.onSelected(manufTitle, typeTitle, getGroupChecked(groupPosition), getSeriesCheckedStatus());
                notifyDataSetChanged();
//                manufacturerSelectedListener.updateChildren(headerTitle, getGroupChecked(groupPosition));

//                updateChildCheckboxes(getGroupChecked(groupPosition));
            }
        });

        // OnClickListener for when the box is already checked and is being unchecked
        checkBoxCheckedLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                checkBoxCheckedLayout.setVisibility(View.INVISIBLE);
                checkImageView.setVisibility(View.VISIBLE);
                seriesCheckedStatus.set(groupPosition, false);

                /* Call the interface callback to notify LensListDetailsActivity of the change in "checked" status */
                seriesSelectedListener.onSelected(manufTitle, typeTitle, getGroupChecked(groupPosition), getSeriesCheckedStatus());
                notifyDataSetChanged();
            }
        });

        addLensLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                runOnUiThread(new Runnable() {
//                    @Override
//                    public void run() {
                        // inflate the layout from dialog_add_lens.xml
                        LayoutInflater dialogInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                        final View addLensView = dialogInflater.inflate(R.layout.dialog_add_lens, null);

                        // initialize the UI components
                        TextView lensManufTextView = (TextView) addLensView.findViewById(R.id.lensManufTextView);
                        TextView lensSeriesTextView = (TextView) addLensView.findViewById(R.id.lensSeriesTextView);
                        final TextView lensDashTextView = (TextView) addLensView.findViewById(R.id.LensFocalDashTextView);

                        // initialize the UI components so we can access their contents when the user presses "Save"
                        final EditText lensFLength1 = (EditText) addLensView.findViewById(R.id.LensFocal1EditText);
                        final EditText lensFLength2 = (EditText) addLensView.findViewById(R.id.LensFocal2EditText);
                        final EditText lensSerialEditText = (EditText) addLensView.findViewById(R.id.LensSerialEditText);
                        final EditText lensNoteEditText = (EditText) addLensView.findViewById(R.id.LensNoteEditText);

                        final CheckBox myListACheckBox = (CheckBox) addLensView.findViewById(R.id.MyListACheckBox);
                        final CheckBox myListBCheckBox = (CheckBox) addLensView.findViewById(R.id.MyListBCheckBox);
                        final CheckBox myListCCheckBox = (CheckBox) addLensView.findViewById(R.id.MyListCCheckBox);

                        lensManufTextView.setText(manufTitle);
                        lensSeriesTextView.setText(typeTitle);

                        // check if the lens type is a Prime or Zoom (or Other) and show/hide Zoom/Prime toggle button accordingly
                        isPrime = SharedHelper.isPrime(typeTitle);
                        togglePrimeOrZoom(isPrime, lensDashTextView, lensFLength2);

                        final AlertDialog dialog = new AlertDialog.Builder(context)
                                .setView(addLensView)
                                .setPositiveButton("Save", null)
                                .setNegativeButton("Cancel", null)
                                .setNeutralButton("Zoom", null)
                                .setCancelable(true)
                                .create();

                        dialog.setOnShowListener(new DialogInterface.OnShowListener() {
                            @Override
                            public void onShow(final DialogInterface dialog) {
                                Button posButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_POSITIVE);
                                final Button modeButton = ((AlertDialog) dialog).getButton(AlertDialog.BUTTON_NEUTRAL);

                                if (typeTitle.trim().equals("Other")) {
                                    modeButton.setVisibility(View.VISIBLE);
                                }
                                else {
                                    modeButton.setVisibility(View.GONE);
                                }

                                posButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {
                                        String fLength1Entered = lensFLength1.getText().toString().replaceAll("[^\\d.]", "");
                                        String fLength2Entered = lensFLength2.getText().toString().replaceAll("[^\\d.]", "");

                                        boolean fLengthOK = isPrime ? (fLength1Entered.length() > 0) : (fLength1Entered.length() > 0 && fLength2Entered.length() > 0);

                                        if (fLengthOK) {
                                            int fLength1 = Integer.parseInt(lensFLength1.getText().toString().trim());
                                            int fLength2 = Integer.parseInt((isPrime) ? "0" : lensFLength2.getText().toString().trim());
                                            String serial = lensSerialEditText.getText().toString().trim();
                                            String note = lensNoteEditText.getText().toString().trim();

                                            String completeFocalString = SharedHelper.constructFocalLengthString(fLength1, fLength2);

                                            boolean serialLengthOK = SharedHelper.checkSerialLength(completeFocalString, serial, note);
                                            boolean lensExists = false;
                                            boolean readyToSave = false;

                                            if (serialLengthOK) {
                                                lensExists = SharedHelper.checkIfLensExists(listDataChild.get(typeTitle), fLength1, fLength2, serial, note);
                                                readyToSave = (serialLengthOK && !lensExists);
                                            }

                                            if (readyToSave) {
                                                parentListener.onAdd(manufTitle, typeTitle, fLength1, fLength2, serial, note);
                                                dialog.dismiss();
                                            } else {
                                                CharSequence toastText;
                                                if (lensExists) {
                                                    toastText = "Error: Lens already exists in file.";
                                                } else {
                                                    toastText = "Error: Lens name too long.";
                                                }

                                                SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                                            }
                                        }

                                        else {
                                            CharSequence toastText = "Error: Invalid Focal Length";

                                            SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                                        }
                                    }
                                });

                                modeButton.setOnClickListener(new View.OnClickListener() {
                                    @Override
                                    public void onClick(View v) {
                                        if (isPrime) {
                                            isPrime = false;
                                            modeButton.setText("Prime");
                                        }
                                        else {
                                            isPrime = true;
                                            modeButton.setText("Zoom");
                                        }
                                        togglePrimeOrZoom(isPrime, lensDashTextView, lensFLength2);
                                    }
                                });
                            }
                        });

                        dialog.show();
                    }
//                });
//            }
        });

        String tagString = manufTitle + " - " + typeTitle;
        convertView.setTag(tagString);

        return convertView;
    }

    private boolean getSeriesCheckedStatus() {
        boolean checkParent = true;

        for (int i = 0; i < seriesCheckedStatus.size(); i++) {
            boolean checked = seriesCheckedStatus.get(i);
            if (!checked && getChildrenCount(i) > 0){
                checkParent = false;
                return checkParent;
            }
        }
        return checkParent;
    }

    @Override
    public boolean hasStableIds() {
        // TODO Auto-generated method stub
        return true;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        // TODO Auto-generated method stub
        return true;
    }

    private void togglePrimeOrZoom(boolean prime, TextView dash, EditText serial2) {
        if (prime) {
            dash.setVisibility(View.GONE);
            serial2.setVisibility(View.GONE);
        }
        else {
            dash.setVisibility(View.VISIBLE);
            serial2.setVisibility(View.VISIBLE);
        }
    }

    public void enableCheckboxes() {
//        checkBoxesEnabled = true;
    }

    public void updateCheckBoxes(int groupPosition, boolean checked) {
        this.seriesCheckedStatus.set(groupPosition, checked);

//        LensListChildExpListView listView = new LensListChildExpListView(context);

//        View groupView = getGroupView(groupPosition, true, null, parentListView);


//        if (checked) {
//            groupView.findViewById()
//        }

//        notifyDataSetChanged();
    }

    public void expandGroup(int gp) {
        this.expandGroup(gp);
//        expandGroup(gp);
    }

//    private void showOrHideFAB() {
//        if (numChecked > 0) {
//            Timber.d("Show the FAB to export");
//        }
//        else {
//            Timber.d("Hide the FAB to export");
//        }
//    }
}

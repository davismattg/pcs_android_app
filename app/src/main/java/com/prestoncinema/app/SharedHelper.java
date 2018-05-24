package com.prestoncinema.app;

import android.app.ProgressDialog;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.prestoncinema.app.databinding.LensListBinding;
import com.prestoncinema.app.db.AppDatabase;
import com.prestoncinema.app.db.AppExecutors;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;

import rx.Observable;
import rx.Observer;
import rx.Single;
import rx.SingleSubscriber;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 * This class contains helper methods that can be used by other classes/activities
 */

public class SharedHelper {
    static Subscription lensListsSubscription;
    static int numLensesInList = 0;
    static int lensStringLength = 112;
    LensListBinding binding;

    /* Method to show a Toast to the user */
    public static void makeToast(Context context,CharSequence text, int duration) {
        Toast toast = Toast.makeText(context, text, duration);
        toast.show();
    }

    public static void setMargins(View v, int left, int top, int right, int bottom) {
        if (v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            Timber.d("setting margin for " + v.toString() + ", bottom: " + bottom);
            ViewGroup.MarginLayoutParams p = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            p.setMargins(left, top, right, bottom);
            v.requestLayout();
        }
    }

    /**
     * Helper method to get an array of long lens IDs from an array of LensEntitys
     * @param lenses
     * @return lensIds, a long[] containing the ID of all the lenses
     */
    public static long[] getLensIds(LensEntity... lenses) {
        long[] lensIds = new long[lenses.length];

        for (int i = 0; i < lenses.length; i++) {
            lensIds[i] = lenses[i].getId();
        }

        return lensIds;
    }

    /**
     * This method is used to initialize the HashMap used to display firmware update instructions
     * to the user. It uses the string resources for a given product.
     * @param context
     * @return
     */
    public static HashMap<String, ArrayList<String>> populateFirmwareUpdateInstructions(Context context) {
        HashMap<String, ArrayList<String>> instructionsMap = new HashMap<String, ArrayList<String>>();

        String hu3 = context.getResources().getString(R.string.update_hu3);
        String mdr2 = context.getResources().getString(R.string.update_mdr2);
        String mdr3 = context.getResources().getString(R.string.update_mdr3);
        String mdr4 = context.getResources().getString(R.string.update_mdr4);
        String vi = context.getResources().getString(R.string.update_vi);

//        HashMap<String, String> instructionsMap = new HashMap<String, String>();
        ArrayList<String> instructions = new ArrayList<String>();
        ArrayList<String> headers = new ArrayList<String>();

        instructions.add(hu3);
        instructions.add(mdr4);
        instructions.add(mdr3);
        instructions.add(vi);
        instructions.add(mdr2);

        headers.add("HU3");
        headers.add("MDR-4");
        headers.add("MDR-3");
        headers.add("Video Interface");
        headers.add("MDR-2");

        instructionsMap.put("headers", headers);
        instructionsMap.put("instructions", instructions);

        return instructionsMap;
    }

    /* Method to show an indeterminate progress dialog during database operations */
    public static ProgressDialog createProgressDialog(Context context, CharSequence text) {
        ProgressDialog mProgressDialog = new ProgressDialog(context);
        mProgressDialog.setMessage(text);

//        if (direction.equals("RX")) {
//            mProgressDialog.setMessage("Importing lenses from HU3...");
//        } else {      // direction = "TX"
//            mProgressDialog.setMessage("Sending lenses to HU3...");
//        }
        mProgressDialog.setCancelable(true);
        mProgressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
        mProgressDialog.setIndeterminate(true);

        return mProgressDialog;
    }

    public static HashMap<String, Object> buildLensListAndLenses(String name, String note, int count, ArrayList<String> lensDataStrings) {
        HashMap<String, Object> outputMap = new HashMap<>();

        ArrayList<LensEntity> builtLenses = new ArrayList<>(buildLenses(lensDataStrings));

        LensListEntity builtList = buildLensList(name, note, count);

        outputMap.put("list", builtList);
        outputMap.put("lenses", builtLenses);

        return outputMap;
    }

    public static LensListEntity buildLensList(String name, String note, int count) {
        LensListEntity newList = new LensListEntity();
        newList.setName(name);
        newList.setNote(note);
        newList.setCount(count);
        newList.setMyListAIds(new ArrayList<Long>());
        newList.setMyListBIds(new ArrayList<Long>());
        newList.setMyListCIds(new ArrayList<Long>());

        return newList;
    }

    public static LensListEntity buildLensList(LensListEntity list, ArrayList<LensEntity> lenses) {
        return buildLensList(list.getId(), list.getName(), list.getNote(), lenses.size(), lenses);
    }

    public static LensListEntity buildLensList(Long listId, String name, String note, int count, ArrayList<LensEntity> lenses) {
        LensListEntity list = new LensListEntity();

        ArrayList<Long> myListAIds = new ArrayList<Long>();
        ArrayList<Long> myListBIds = new ArrayList<Long>();
        ArrayList<Long> myListCIds = new ArrayList<Long>();

        if (listId != null) {
            list.setId(listId);
        }

        list.setName(name);
        list.setNote(note);
        list.setCount(count);

        for (LensEntity lens : lenses) {
            if (lens.getMyListA()) {
                myListAIds.add(lens.getId());
//                lens.setMyListA(false);
            }

            if (lens.getMyListB()) {
                myListBIds.add(lens.getId());
//                lens.setMyListB(false);
            }

            if (lens.getMyListC()) {
                myListCIds.add(lens.getId());
//                lens.setMyListC(false);
            }
        }

        list.setMyListAIds(myListAIds);
        list.setMyListBIds(myListBIds);
        list.setMyListCIds(myListCIds);

        return list;
    }

    /* Method to populate a List<LensEntity> from an imported array of lens data strings */
    public static List<LensEntity> buildLenses(ArrayList<String> lensStrings) {
        List<LensEntity> builtLenses = new ArrayList<LensEntity>(lensStrings.size());
        for (String data : lensStrings) {
            LensEntity thisLens = buildLensFromDataString(data);
            builtLenses.add(thisLens);
        }

        return builtLenses;
    }

    /* Method to set the "checked" attribute of each lens to true/false */
    public static ArrayList<LensEntity> setChecked(ArrayList<LensEntity> lenses, String manufacturer, String series, boolean checked) {
        for (LensEntity lens : lenses) {
            if (manufacturer != null) {
                if (lens.getManufacturer().equals(manufacturer)) {
                    if (series != null) {
                        if (lens.getSeries().equals(series)) {
                            lens.setChecked(checked);
                        }
                    }
                    else {
                        lens.setChecked(checked);
                    }
                }
            }
            else {
                lens.setChecked(checked);
            }
        }

        return lenses;
    }

    /* Method to filter out the lenses which aren't checked */
    public static ArrayList<LensEntity> getCheckedLenses(ArrayList<LensEntity> lenses) {
        ArrayList<LensEntity> checkedLenses = new ArrayList<>();
        for (LensEntity lens : lenses) {
            if (lens.getChecked()) {
                lens.setChecked(false);
                checkedLenses.add(lens);
            }
        }

        return checkedLenses;
    }

    /* Method to check if all lenses in the list are checked */
    public static boolean areAllLensesChecked(ArrayList<LensEntity> lenses) {
        if (lenses != null) {
            for (LensEntity lens : lenses) {
                if (!lens.getChecked()) {
                    return false;
                }
            }
        }

        return true;
    }

    /** This method returns the image for whatever product is detected for firmware updates
     * @param product
     * @return
     */
    public static int getProductImage(String product) {
        Timber.d("get product image for " + product + "$$");
        int id;
        switch (product) {
            case "HU3":
                id = R.drawable.hu3_cropped;
                break;
            case "MDR-4":
                id = R.drawable.mdr4_cropped;
                break;
            case "MDR-3":
                id = R.drawable.mdr3_cropped;
                break;
            case "MDR-2":
                id = R.drawable.mdr2_cropped;
                break;
            case "VI":
                id = R.drawable.vi_cropped;
                break;
            default:
                id = R.drawable.unknown_cropped;
                break;

        }
        return id;
    }

    /* Method to build a LensEntity from the data string obtained from HU3 or a file */
    // TODO: use StripChars to eliminate the index nonsense in this method
    public static LensEntity buildLensFromDataString(String data) {
        /* Initialize the Lens object that will store all the info about this lens */
        LensEntity lensObject = new LensEntity();

        String line = checkLensChars(data);

        StringBuilder lineBuilder = new StringBuilder(line);

        byte[] bytes = line.getBytes();

        /* Lens status (calibrated, myList, etc) */
        byte[] status1 = Arrays.copyOfRange(bytes, 14, 16);                               // bytes 15 and 16 (ASCII bytes) are the first (hex) status byte
        HashMap<String, boolean[]> statusMap = convertLensStatus(status1);
        lensObject.setCalibratedF(statusMap.get("calibrated")[0]);
        lensObject.setCalibratedI(statusMap.get("calibrated")[1]);
        lensObject.setCalibratedZ(statusMap.get("calibrated")[2]);

        // set the My List A/B/C status on the LensEntity object for now. It gets cleared later on
        // since the MyList stuff is now a part of the LensListEntity
        lensObject.setMyListA(statusMap.get("myList")[0]);
        lensObject.setMyListB(statusMap.get("myList")[1]);
        lensObject.setMyListC(statusMap.get("myList")[2]);

        // since the MyList A/B/C is not a part of the Lens itself, we need to alter the data string.
        // if we didn't, the data string sent to the HU3 would always indicate the lens was a member
        // of My List (assuming it was a member upon initial import)
        byte[] newStatus1 = LensHelper.removeMyListFromDataBytes(status1);
        char newStatus1Beginning = (char) newStatus1[0];
        char newStatus1End = (char) newStatus1[1];

        lineBuilder.setCharAt(14, newStatus1Beginning);
        lineBuilder.setCharAt(15, newStatus1End);

        /* Lens Manufacturer and Type */
        byte[] status2 = Arrays.copyOfRange(bytes, 16, 18);                                         // bytes 17 and 18 (ASCII bytes) are the second (hex) status byte
        HashMap<String, Object> nameAndTypeMap = convertManufName(status2);
        lensObject.setManufacturer((String) nameAndTypeMap.get("manufacturer"));
        lensObject.setSeries((String) nameAndTypeMap.get("series"));

        // adding the lens' tag to the correct position to be retrieved later in the ListView
        int manufPos = (int) nameAndTypeMap.get("manufPosition");                                   // position of the manufacturer header in the ListView
        int seriesPos = (int) nameAndTypeMap.get("seriesPosition");                                 // position of the series header within the manufacturer header of the ListView
        lensObject.setManufacturerPosition(manufPos);
        lensObject.setSeriesPosition(seriesPos);

        /* Focal length(s) */
        String focal1 = line.substring(18, 22);                                                     // bytes 19-22 (ASCII bytes) are the first (hex) focal length byte
        String focal2 = line.substring(22, 26);                                                     // bytes 23-26 (ASCII bytes) are the second (hex) focal length byte
        lensObject.setFocalLength1(convertFocalLength(focal1));
        lensObject.setFocalLength2(convertFocalLength(focal2));

        /* Serial number */
        String serial = line.substring(26, 30);
        String convertedSerial = convertSerial(serial);
        lensObject.setSerial(convertedSerial);

        /* Note */
        String lensName = line.substring(0, 14);                                                    // get the substring that contains the note (& serial & focal lengths)

        int noteBegin;
        String lensNote;
        if (convertedSerial.length() > 0 && lensName.contains(convertedSerial)) {                                                         // serial string present, look for it in the lens name
            noteBegin = lensName.indexOf(convertedSerial) + convertedSerial.length();               // set the tag to separate the lens serial and note
        }
        else {
            noteBegin = lensName.indexOf("mm") + 2;                                                 // no serial present, so anything after "mm" is considered the note
        }

        lensNote = lensName.substring(noteBegin).trim();                                            // grab the note using the tag determined above
        lensObject.setNote(lensNote);                                                               // set the note property of the lens object

        /* Data String (raw String that gets sent to HU3 */
        lensObject.setDataString(lineBuilder.toString());

        /* isPrime */
        lensObject.setIsPrime(SharedHelper.isPrime(lensObject.getSeries()));

        /* Checked attribute TODO: make sure this pulls the value from DB correctly */
        lensObject.setChecked(false);

        return lensObject;
    }


    /* This method accepts a status byte as input and returns a map of the lens' manufacturer name and series as strings.
    It calls the methods bytesToLensManuf and bytesToLensType to determine each of those values   */
    private static HashMap<String, Object> convertManufName(byte[] status) {
        HashMap<String, Object> lensManufAndTypeMap = new HashMap<>();
        String manufName = (String) bytesToLensManuf(status).get("manufacturer");
        String manufSeries = (String) bytesToLensSeries(status).get("series");
        int manufPos = (int) bytesToLensManuf(status).get("groupPos");
        int seriesPos = (int) bytesToLensSeries(status).get("seriesPos");

        lensManufAndTypeMap.put("manufacturer", manufName);
        lensManufAndTypeMap.put("series", manufSeries);
        lensManufAndTypeMap.put("manufPosition", manufPos);
        lensManufAndTypeMap.put("seriesPosition", seriesPos);

        return lensManufAndTypeMap;
    }

    /* This method accepts a status byte as input and returns the lens manufacturer and group position within the ListView according to that status byte */
    private static HashMap<String, Object> bytesToLensManuf(byte[] status) {
        HashMap<String, Object> manufNameAndPosition = new HashMap<>();
        String name;
        int groupPos;
        switch (status[0]) {
            case 48:
                name = "Angenieux";
                groupPos = 0;
                break;
            case 49:
                name = "Canon";
                groupPos = 1;
                break;
            case 50:
                name = "Cooke";
                groupPos = 2;
                break;
            case 51:
                name = "Fujinon";
                groupPos = 3;
                break;
            case 52:
                name = "Leica";
                groupPos = 4;
                break;
            case 53:
                name = "Panavision";
                groupPos = 5;
                break;
            case 54:
                name = "Zeiss";
                groupPos = 6;
                break;
            default:
                name = "Other";
                groupPos = 7;
                break;
        }

        manufNameAndPosition.put("manufacturer", name);
        manufNameAndPosition.put("groupPos", groupPos);
        return manufNameAndPosition;
    }

    /* This method accepts a status byte as input and returns the lens series according to that status byte
        The type is dependent on the manufacturer name as well which is why there are two switch statements. */
    private static HashMap<String, Object> bytesToLensSeries(byte[] status) {
        HashMap<String, Object> seriesAndPosition = new HashMap<>();
        String manufType;
        int seriesPos;
        switch (status[0]) {
            case 48:
                switch (status[1]) {
                    case 48:
                        manufType = "Optimo";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Rouge";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "HR";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Other";
                        seriesPos = 3;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 3;
                        break;
                }
                break;
            case 49:
                switch (status[1]) {
                    case 48:
                        manufType = "Cinema Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Cinema Zoom";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Other";
                        seriesPos = 2;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 2;
                        break;
                }
                break;
            case 50:
                switch (status[1]) {
                    case 48:
                        manufType = "S4";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "S5";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Panchro";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Zoom";
                        seriesPos = 3;
                        break;
                    case 52:
                        manufType = "Other";
                        seriesPos = 4;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 4;
                        break;
                }
                break;
            case 51:
                switch (status[1]) {
                    case 48:
                        manufType = "Premier Zoom";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Alura Zoom";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Prime";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Other";
                        seriesPos = 3;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 3;
                        break;
                }
                break;
            case 52:
                switch (status[1]) {
                    case 48:
                        manufType = "Summilux Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Other";
                        seriesPos = 1;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 1;
                        break;
                }
                break;
            case 53:
                switch (status[1]) {
                    case 48:
                        manufType = "Primo Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Primo Zoom";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Anam. Prime";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Anam. Zoom";
                        seriesPos = 3;
                        break;
                    case 52:
                        manufType = "P70 Prime";
                        seriesPos = 4;
                        break;
                    case 53:
                        manufType = "Other";
                        seriesPos = 5;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 5;
                        break;
                }
                break;
            case 54:
                switch (status[1]) {
                    case 48:
                        manufType = "Master Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Ultra Prime";
                        seriesPos = 1;
                        break;
                    case 50:
                        manufType = "Compact Prime";
                        seriesPos = 2;
                        break;
                    case 51:
                        manufType = "Zoom";
                        seriesPos = 3;
                        break;
                    case 52:
                        manufType = "Other";
                        seriesPos = 4;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 4;
                        break;
                }
                break;
            default:
                switch (status[1]) {
                    case 48:
                        manufType = "Prime";
                        seriesPos = 0;
                        break;
                    case 49:
                        manufType = "Zoom";
                        seriesPos = 1;
                        break;
                    default:
                        manufType = "";
                        seriesPos = 0;
                        break;
                }
                break;
        }

        seriesAndPosition.put("series", manufType);
        seriesAndPosition.put("seriesPos", seriesPos);
        return seriesAndPosition;
    }

    /* Method that accepts a String of the lens serial number (in hex representation, 4 characters) and returns that value as a (decimal) integer */
    private static String convertSerial(String serial) {
        int serialInDecimal = Integer.parseInt(serial, 16);                                         // convert from hex to decimal
        if (serialInDecimal > 0) {                                                                  // if serial > 0, user entered a serial for this lens
            return Integer.toString(serialInDecimal);
        }
        return "";                                                                                  // no serial entered, return empty string
    }


    // use the hex characters to parse the lens calibration status and if it's a member of any lists
    // just follow mirko's lens data structure //
    private static HashMap<String, boolean[]> convertLensStatus(byte[] bytes) {
        /* Initialize variables. lensStatusMap is return value, containing a value for keys "calibrated" and "myList" */
        HashMap<String, boolean[]> lensStatusMap = new HashMap<String, boolean[]>();
        boolean FCal = false;
        boolean ICal = false;
        boolean ZCal = false;
        boolean myListA = false;
        boolean myListB = false;
        boolean myListC = false;
        boolean[] calArray = new boolean[3];
        boolean[] listArray = new boolean[3];

        // check the first byte to determine the status
        switch (bytes[0]) {
            case 70:    // F
                FCal = true;
                myListC = true;
                myListB = true;
                break;
            case 69:    // E
                FCal = true;
                myListC = true;
                break;
            case 68:    // D
                FCal = true;
                myListB = true;
                break;
            case 67:    // C
                FCal = true;
                break;
            case 66:    // B
                myListC = true;
                myListB = true;
                break;
            case 65:    // A
                myListC = true;
                break;
            case 57:    // 9
                myListB = true;
                break;
            default:        // 8 => no list, F not calibrated. Default case
                break;
        }

        // check the second byte to dermine the status
        switch (bytes[1]) {
            case 70: case 69:  // F & E (since we don't care about the Z bit)
                myListA = true;
                ICal = true;
                ZCal = true;
                break;
            case 68: case 67: // D & C
                myListA = true;
                ICal = true;
                break;
            case 66: case 65:   // B & A
                myListA = true;
                ZCal = true;
                break;
            case 57: case 56:   // 9 & 8
                myListA = true;
                break;
            case 55:case 54:    // 7 & 6
                ICal = true;
                ZCal = true;
                break;
            case 53:case 52:    // 5 & 4
                ICal = true;
                break;
            case 51:case 50:    // 3 & 2
                ZCal = true;
                break;
            default:
                break;
        }

        // build the boolean arrays
        calArray[0] = FCal;
        calArray[1] = ICal;
        calArray[2] = ZCal;

        listArray[0] = myListA;
        listArray[1] = myListB;
        listArray[2] = myListC;

        // add to the HashMap and return
        lensStatusMap.put("calibrated", calArray);
        lensStatusMap.put("myList", listArray);

        return lensStatusMap;
    }

    /* Method that accepts String of lens focal length (in hex representation, 4 characters) and returns that value as a (decimal) integer */
    private static int convertFocalLength(String focal) {
        return Integer.parseInt(focal, 16);
    }

    /* Method to build the correctly formatted focal length(s) String depending on if the lens is a zoom or prime (focalLength2 == 0) */
    public static String constructFocalLengthString(int fL1, int fL2) {
        if (fL2 > 0) {                                                                     // fL2 > 0 implies zoom lens
            return String.valueOf(fL1) + "-" + String.valueOf(fL2) + "mm";
        }
        return String.valueOf(fL1) + "mm";                                                                          // prime lens, so just return the first FL
    }

    /* Method to build the correctly formatted Manuf/series string for display in My List */
    public static String constructManufAndSeriesString(String manuf, String series) {
        return manuf + " - " + series;
    }

    /* Method to build the correctly formatted lens serial/note string for display in My List */
    public static String constructSerialAndNoteString(String serial, String note) {
        return serial + " " + note;
    }

//    /* Checks if any of the My List assignment states are toggled from the + button on the My List A/B/C header */
//    public static boolean myListEnabled(MyListExpListViewAdapter adapter) {
//        return (adapter.addToMyListA || adapter.addToMyListB || adapter.addToMyListC);
//    }

    public static boolean checkSerialLength(String focal, String serial, String note) {
        String completeString;
        int completeStringLength;

        completeString = focal + " " + serial + note;
        Timber.d("completeString: " + completeString);

        completeStringLength = completeString.length();
        Timber.d("length: " + completeStringLength);

        return completeStringLength <= 14;
    }

    public static boolean checkIfLensExists(ArrayList<LensEntity> lenses, int focal1, int focal2, String serial, String note) {
        Timber.d("check if lens exists");
        boolean exists = false;
            for (LensEntity lens : lenses) {
                if (lens.getFocalLength1() == focal1) {
                    if (lens.getFocalLength2() == focal2) {
                        if (lens.getSerial().equals(serial)) {
                            if (lens.getNote().equals(note)) {
                                return true;
                            }
                        }
                    }
                }
            }
        return exists;
    }

    // if Prime lens, just show ___ mm. If zoom lens, show ____-____ mm
    public static boolean isPrime(String type) {
        switch (type) {
            // all the lens types that are zoom
            case "Optimo":case "Rouge":case "HR":case "Cinema Zoom":case "Zoom":case "Premier Zoom":case "Alura Zoom":case "Primo Zoom":case "Anam. Zoom":
//                Timber.d("Zoom lens detected, switch to zoom lens FL mode");
                return false;
            default:            // a prime lens detected
//                Timber.d("Prime lens detected, switch to prime lens FL mode");
                return true;
        }
    }

    /** This method checks the lens data coming from either the HU3 or stored via text file and makes sure it's OK for import.
     * It checks the first character to see if it's STX (0x2), and removes it if so. It also looks at the final two characters to
     * see if they are LF (0xA) and CR (0xD). If those aren't present, it adds them and returns a new array. Otherwise, it returns
     * the existing byte[].
     * @param lensChars
     * @return
     */
    public static byte[] checkLensChars(byte[] lensChars) {
        int begin = 0;
        boolean addCR = false;
        boolean addLF = false;

        /* Create a new array in case we need to append \r or \n */
        byte[] newChars = Arrays.copyOf(lensChars, lensChars.length + 2);

        /* Look for STX */
        if (lensChars[0] == 0x2) {
            begin += 1;
        }

        /* Check the second-to-last character for LF */
        if (!(lensChars[lensChars.length - 2] == 0xA)) {
            addLF = true;
        }

        /* Check the last character for CR */
        if (!(lensChars[lensChars.length - 1] == 0xD)) {
            addCR = true;
        }

        /* Set the second-to-last character to LF in the new array */
        if (addLF) {
            newChars[newChars.length - 2] = 0xA;
        }

        /* Set the last character to CR in the new array */
        if (addCR) {
            newChars[newChars.length - 1] = 0xD;
        }

        /* Return the new array if we needed to change it */
        if (addLF || addCR) {
            return Arrays.copyOfRange(newChars, begin, newChars.length);
        }

        /* Otherwise, return the original array */
        return Arrays.copyOfRange(lensChars, begin, lensChars.length);
    }

    /** Same method as above, but for handling string inputs
     *
     * @param lensString
     * @return
     */
    public static String checkLensChars(String lensString) {
        byte[] bytesIn = lensString.getBytes();
        return new String(checkLensChars(bytesIn));
    }

    /**
     * This method checks a lens string to make sure it's not corrupted. There are a few different conditions
     * that must be satisfied during the checks:
     * 1) Lens string must be all ASCII characters
     * 2) Lens string must be 110 characters long
     * 3) Lens manufacturer number invalid -- TODO
     * 4) Lens series invalid for manufacturer -- TODO
     * 5) Lens focal length invalid (must be 1-9999mm) -- TODO
     * @param lensString
     * @return
     */
    public static boolean isLensOK(String lensString) {
        // Check that the string contains only ASCII characters
        if (!isOnlyAscii(lensString)) {
            return false;
        }

        // Check that the lens string is 112 characters (110 + \n\r)
        if (lensString.length() != 112) {
            return false;
        }

        return true;
    }

    /**
     * Uses regex to determine if the string contains only ASCII characters
     * @param str the string to check
     * @return boolean indicating whether the string is only ASCII (true) or has other chars (false)
     */
    private static boolean isOnlyAscii(String str) {
        return str.matches("\\A\\p{ASCII}*\\z");
    }

    // TODO: make this access the database and update the checked attribute there
    public static void updateLensChecked(LensEntity lens) {
        Timber.d("Update lens checked status for tag: " + lens.getTag());
//        lensObjectArray.set(lens.getTag(), lens);
    }

    public static int getLensListCount(final Context context, AppExecutors executors, final long listId) {
        final AppDatabase database = AppDatabase.getInstance(context, executors);

        Single.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() {
                return database.lensListLensJoinDao().getLensCountForList(listId);
            }
        })
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new SingleSubscriber<Integer>() {
                    @Override
                    public void onSuccess(Integer value) {
                        
                    }

                    @Override
                    public void onError(Throwable error) {
                        CharSequence toastText = "Error getting lens count";
                        SharedHelper.makeToast(context, toastText, Toast.LENGTH_LONG);
                    }
                });
        return 0;
    }

    /**
     * Gets the List<String> of manufacturers for use in the ExpandableListViews
     * @param context
     * @return
     */
    public static List<String> populateLensManufHeader(Context context) {
        return Arrays.asList(context.getResources().getStringArray(R.array.lens_manuf_array));
    }

    // function to populate the lens type HashMap with each lens type name, based on manufacturer name
    public static HashMap<String, List<String>> populateLensTypeHeader(Context context, List<String> listDataHeader) {
        HashMap<String, List<String>> lensTypes = new HashMap<>();              // the return value
        for (String manufName : listDataHeader) {                                                   // loop through array of manuf names
            final int arrayId;                                            // the ID of the string-array resource containing the lens names

            switch (manufName) {
                case "Angenieux":
                    arrayId = R.array.lens_type_Angenieux;
                    break;
                case "Canon":
                    arrayId = R.array.lens_type_Canon;
                    break;
                case "Cooke":
                    arrayId = R.array.lens_type_Cooke;
                    break;
                case "Fujinon":
                    arrayId = R.array.lens_type_Fujinon;
                    break;
                case "Leica":
                    arrayId = R.array.lens_type_Leica;
                    break;
                case "Panavision":
                    arrayId = R.array.lens_type_Panavision;
                    break;
                case "Zeiss":
                    arrayId = R.array.lens_type_Zeiss;
                    break;
                case "Other":
                    arrayId = R.array.lens_type_Other;
                    break;
                default:
                    arrayId = R.array.lens_type_Empty;
                    break;
            }

            lensTypes.put(manufName, Arrays.asList(context.getResources().getStringArray(arrayId)));
        }

        return lensTypes;
    }

    public static Map<Integer, Integer> initializeLensTypeHeaderCount(List<String> manufacturers) {
        Map<Integer, Integer> count = new HashMap<Integer, Integer>(manufacturers.size());
        for (int i = 0; i < manufacturers.size(); i++) {
            count.put(i, 0);
        }

        return count;
    }

    public static Map<Integer, Integer> populateLensTypeHeaderCount(Map<Integer, Integer> initialMap, List<String> manufacturers, ArrayList<LensEntity> lenses) {
        Map<Integer, Integer> count = initialMap;
        Map<String, Integer> manufMap = new HashMap<String, Integer>();

        manufMap.put("Angenieux", 0);
        manufMap.put("Canon", 1);
        manufMap.put("Cooke", 2);
        manufMap.put("Fujinon", 3);
        manufMap.put("Leica", 4);
        manufMap.put("Panavision", 5);
        manufMap.put("Zeiss", 6);
        manufMap.put("Other", 7);

        for (LensEntity lens : lenses) {
            String manuf = lens.getManufacturer();
            int key = manufMap.get(manuf);
            int currCount = count.get(key);

            count.put(key, currCount += 1);
        }

        return count;
    }

    public static HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> initializePositionMap(List<String> manufacturers, Map<Integer, Integer> typeCount) {
        HashMap<Integer, HashMap<Integer, ArrayList<Integer>>> position = new HashMap<Integer, HashMap<Integer, ArrayList<Integer>>>();
        for (int i = 0; i < manufacturers.size(); i++) {
            position.put(i, new HashMap<Integer, ArrayList<Integer>>());
        }

        return position;
    }

    /**
     * This method builds the lens data string from a given LensEntity. Since MyList assignments are
     * specific to a lens list, we can't rely on the MyList A/B/C attributes of the LensEntity object.
     * As a result, we have to pass a lens list to this method to accurately determine whether a lens
     * should have its data string altered to be a member of My List A/B/C.
     * @param list the lens list these lenses are associated with
     * @param lens the lens we need to build the data string
     * @return
     */
    public static String buildLensDataString(LensListEntity list, LensEntity lens) {
        boolean myListA = false, myListB = false, myListC = false;

        // if we're exporting lenses that aren't associated with a particular list, list == null
        if (list != null) {
            if (!list.getName().equals("All Lenses")) {
                myListA = list.getMyListALongIds().contains(lens.getId());
                myListB = list.getMyListBLongIds().contains(lens.getId());
                myListC = list.getMyListCLongIds().contains(lens.getId());
            }
        }

        return buildLensDataString(lens.getManufacturer(), lens.getSeries(), lens.getFocalLength1(),
                        lens.getFocalLength2(), lens.getSerial(), lens.getNote(), lens.getCalibratedF(), lens.getCalibratedI(), lens.getCalibratedZ(), myListA, myListB, myListC);
    }

    public static String buildLensDataString(LensEntity lens, boolean myListA, boolean myListB, boolean myListC) {
        return buildLensDataString(lens.getManufacturer(), lens.getSeries(), lens.getFocalLength1(), lens.getFocalLength2(),
                lens.getSerial(), lens.getNote(), lens.getCalibratedF(), lens.getCalibratedI(), lens.getCalibratedZ(), myListA, myListB, myListC);
    }

    // function to do the heavy lifting of creating the hex characters from the user's selections
    public static String buildLensDataString(String manuf, String series, int focal1, int focal2, String serial, String note, boolean calF, boolean calI, boolean calZ, boolean myListA, boolean myListB, boolean myListC) {
        int width = 110;
        char fill = '0';
        int manufByte = 0x0;
        int typeByte = 0x0;
        int statByte0 = 0x8;
        int statByte1 = 0x0;
        int ang_byte = 0x0;
        int can_byte = 0x1;
        int cooke_byte = 0x2;
        int fuj_byte = 0x3;
        int lei_byte = 0x4;
        int pan_byte = 0x5;
        int zei_byte = 0x6;
        int oth_byte = 0xF;
        byte[] STX = {02};
        byte[] ETX = {0x0A, 0x0D};
        String ETXStr = new String(ETX);

        String lensName;
        String lensStatus1;
        String lensStatus2;
        String lensFocal1Str;
        String lensFocal2Str;
        String lensSerialStr;

        // look @ the focal lengths to determine if prime or zoom lens, and format the string appropriately (should always be 14 characters long)
        if (focal1 == focal2) {
            lensName = String.format("%-14s", String.valueOf(focal1) + "mm " + serial + note);
        }
        else if (focal2 == 0) {
            lensName = String.format("%-14s", String.valueOf(focal1) + "mm " + serial + note);
        }
        else {              // zoom lens
            statByte1 += 1;
            lensName = String.format("%-14s", String.valueOf(focal1) + "-" + String.valueOf(focal2) + "mm " + serial + note);
        }

        switch (manuf) {
            case "Angenieux": //48
                manufByte = ang_byte;
                switch (series) {
                    case "Optimo":
                        typeByte = 0x0;
                        break;
                    case "Rouge":
                        typeByte = 0x1;
                        break;
                    case "HR":
                        typeByte = 0x2;
                        break;
                    case "Other":
                        typeByte = 0x3;
                        break;
                    default:
                        break;
                }
                break;
            case "Canon":
                manufByte = can_byte;
                switch (series) {
                    case "Cinema Prime":
                        typeByte = 0x0;
                        break;
                    case "Cinema Zoom":
                        typeByte = 0x1;
                        break;
                    case "Other":
                        typeByte = 0x2;
                        break;
                    default:
                        break;
                }
                break;
            case "Cooke":
                manufByte = cooke_byte;
                switch (series) {
                    case "S4":
                        typeByte = 0x0;
                        break;
                    case "S5":
                        typeByte = 0x1;
                        break;
                    case "Panchro":
                        typeByte = 0x2;
                        break;
                    case "Zoom":
                        typeByte = 0x3;
                        break;
                    case "Other":
                        typeByte = 0x4;
                        break;
                    default:
                        break;
                }
                break;
            case "Fujinon": //48
                manufByte = fuj_byte;
                switch (series) {
                    case "Premier Zoom":
                        typeByte = 0x0;
                        break;
                    case "Alura Zoom":
                        typeByte = 0x1;
                        break;
                    case "Prime":
                        typeByte = 0x2;
                        break;
                    case "Other":
                        typeByte = 0x3;
                        break;
                    default:
                        break;
                }
                break;
            case "Leica":
                manufByte = lei_byte;
                switch (series) {
                    case "Summilux Prime":
                        typeByte = 0x0;
                        break;
                    case "Other":
                        typeByte = 0x1;
                        break;
                    default:
                        break;
                }
                break;
            case "Panavision":
                manufByte = pan_byte;
                switch (series) {
                    case "Primo Prime":
                        typeByte = 0x0;
                        break;
                    case "Primo Zoom":
                        typeByte = 0x1;
                        break;
                    case "Anam. Prime":
                        typeByte = 0x2;
                        break;
                    case "Anam. Zoom":
                        typeByte = 0x3;
                        break;
                    case "P70 Prime":
                        typeByte = 0x4;
                        break;
                    case "Other":
                        typeByte = 0x5;
                        break;
                    default:
                        break;
                }
                break;
            case "Zeiss":
                manufByte = zei_byte;
                switch (series) {
                    case "Master Prime":
                        typeByte = 0x0;
                        break;
                    case "Ultra Prime":
                        typeByte = 0x1;
                        break;
                    case "Compact Prime":
                        typeByte = 0x2;
                        break;
                    case "Zoom":
                        typeByte = 0x3;
                        break;
                    case "Other":
                        typeByte = 0x4;
                        break;
                    default:
                        break;
                }
                break;
            case "Other":
                manufByte = oth_byte;
                switch (series) {
                    case "Prime":
                        typeByte = 0x0;
                        break;
                    case "Zoom":
                        typeByte = 0x1;
                        break;
                    default:
                        break;
                }
                break;

            default:
                break;
        }

        // check calibrated status
        if (calF) {
            statByte0 += 0x4;
        }

        if (calI) {
            statByte1 += 0x4;
        }

        if (calZ) {
            statByte1 += 0x2;
        }

        // check myList booleans
        if (myListA) {
            statByte1 += 0x8;
        }

        if (myListB) {
            statByte0 += 0x1;
        }

        if (myListC) {
            statByte0 += 0x2;
        }

        if (statByte0 == 10) {
            statByte0 = 0xA;
        }

        if (statByte0 == 11) {
            statByte0 = 0xB;
        }

        // convert to the hex characters that will be written in the file. these strings all need to
        // be constant length no matter how many characters are inside, so you have to pad with 0's if necessary
        lensStatus1 = Integer.toHexString(statByte0).toUpperCase() + Integer.toHexString(statByte1).toUpperCase();
        lensStatus2 = Integer.toHexString(manufByte).toUpperCase() + Integer.toHexString(typeByte).toUpperCase();
        lensFocal1Str = String.format("%4s", Integer.toHexString(focal1).toUpperCase()).replaceAll(" ", "0");
        lensFocal2Str = String.format("%4s", Integer.toHexString(focal2).toUpperCase()).replaceAll(" ", "0");

        if (serial.length() > 0) {
            lensSerialStr = String.format("%4s", Integer.toHexString(Integer.parseInt(serial)).toUpperCase()).replaceAll(" ", "0");
        }
        else {
            lensSerialStr = "0000"; //String.format("%4s", Integer.toHexString(0).toUpperCase()).replaceAll(" ", "0");
        }
        String toPad = lensName + lensStatus1 + lensStatus2 + lensFocal1Str + lensFocal2Str + lensSerialStr;
        String padded = toPad + new String(new char[width - toPad.length()]).replace('\0', fill) + ETXStr;

        Timber.d("lensString length: " + padded.length());
        Timber.d("lensString:" + padded + "$$");

        return padded;
    }

    public static void getNumLensesInList(Context context, final long listId) {
        Timber.d("getting number of lenses for list id = " + listId);

        AppExecutors appExecutors = new AppExecutors();
        final AppDatabase db = AppDatabase.getInstance(context, appExecutors);

        Observable<Integer> lensListsObservable = Observable.fromCallable(new Callable<Integer>() {
            @Override
            public Integer call() {
                return db.lensListLensJoinDao().getLensCountForList(listId);
//                return db.lensListLensJoinDao().getLensesForList(listId).size();
            }
        });

        lensListsSubscription = lensListsObservable
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Observer<Integer>() {
                    @Override
                    public void onCompleted() {
                        Timber.d("Observable onCompleted");
                    }

                    @Override
                    public void onError(Throwable e) {
                        Timber.d("Observable onError: " + e);
                    }

                    @Override
                    public void onNext(Integer numLenses) {
                        Timber.d("Observable onNext - numLenses: " + numLenses);
                        numLensesInList = numLenses;
                    }
                });
    }

    public static String getNumLensesInList(Context context, LensListEntity list) {
        getNumLensesInList(context, list.getId());
        return String.valueOf(numLensesInList);
    }
}

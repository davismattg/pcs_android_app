package com.prestoncinema.app;

import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

import timber.log.Timber;

/**
 * Created by MATT on 5/1/2018.
 */

public class LensHelper {
    static int MAX_LENS_COUNT = 255;

    /**
     * This method is just a quick check to make sure we don't send the HU3 more than 255 lenses.
     * This threshold might change for the HU4.
     *
     * @param numLenses
     * @return
     */
    public static boolean isLensCountOK(int numLenses) {
        return numLenses <= MAX_LENS_COUNT;
    }

    public static String removeMyListFromDataString(String oldData) {
        String newData;

        String line = SharedHelper.checkLensChars(oldData);
        byte[] bytes = line.getBytes();
        byte[] status1 = Arrays.copyOfRange(bytes, 14, 16);
        byte[] newStatus1 = removeMyListFromDataBytes(status1);

        bytes[14] = newStatus1[0];
        bytes[15] = newStatus1[1];

        newData = new String(bytes);

        return newData;
    }

    public static byte[] removeMyListFromDataBytes(byte[] bytes) {
        byte[] outBytes = new byte[2];

        // check the first byte to determine the status
        switch (bytes[0]) {
            case 70:    // F
                outBytes[0] = 67;
                break;
            case 69:    // E
                outBytes[0] = 67;
                break;
            case 68:    // D
                outBytes[0] = 67;
                break;
            case 67:    // C
                outBytes[0] = 67;
                break;
            case 66:    // B
                outBytes[0] = 56;
                break;
            case 65:    // A
                outBytes[0] = 56;
                break;
            case 57:    // 9
                outBytes[0] = 56;
                break;
            default:        // 8 => no list, F not calibrated. Default case
                outBytes[0] = 56;
                break;
        }

        // check the second byte to determine the status
        switch (bytes[1]) {
            case 70:                // F
                outBytes[1] = 55;
                break;
            case 69:                // E
                outBytes[1] = 54;
                break;
            case 68:                // D
                outBytes[1] = 53;
                break;
            case 67:                // C
                outBytes[1] = 52;
                break;
            case 66:                // B
                outBytes[1] = 51;
                break;
            case 65:                // A
                outBytes[1] = 50;
                break;
            case 57:                // 9
                outBytes[1] = 49;
                break;
            case 56:                // 8
                outBytes[1] = 48;
                break;
            case 55:
            case 54:
            case 53:
            case 52:
            case 51:
            case 50:
            case 49:
            case 48:    // 3, 2, 1, 0
                outBytes[1] = bytes[1];
                break;
            default:
                break;
        }

        return outBytes;
    }

    /**
     * This method selects the lenses from the ArrayList and returns the appropriate ones given the manufacturer/series
     * parameters. This makes it easy to use the updateAll method in the LensDao
     *
     * @param manufacturer
     * @param series
     * @param checked
     * @return
     */
    public static LensEntity[] getLenses(ArrayList<LensEntity> lensesToCheck, String manufacturer, String series, boolean checked) {
        ArrayList<LensEntity> lenses = new ArrayList<>();

        for (LensEntity lens : lensesToCheck) {
            if (manufacturer != null) {
                if (lens.getManufacturer().equals(manufacturer)) {
                    if (series != null) {
                        if (lens.getSeries().equals(series)) {
                            if (lens.getChecked() != checked) {
                                lens.setChecked(checked);
                                lenses.add(lens);
                                if (checked) {
//                                    numLensesChecked++;
                                } else {
//                                    numLensesChecked--;
                                }
                            }
                        }
                    } else {
                        if (lens.getChecked() != checked) {
                            lens.setChecked(checked);
                            lenses.add(lens);
                            if (checked) {
//                                numLensesChecked++;
                            } else {
//                                numLensesChecked--;
                            }
                        }
                    }
                }
            } else {
                if (lens.getChecked() != checked) {
                    lens.setChecked(checked);
                    lenses.add(lens);
                    if (checked) {
//                        numLensesChecked++;
                    } else {
//                        numLensesChecked--;
                    }
                }
            }
        }

        return lenses.toArray(new LensEntity[lenses.size()]);
    }

    /**
     * This is the main method for editing a lens. It takes in the lens and associated lens list
     * in case the user changed My List assignments. It returns a HashMap containing the edited
     * lens and lens list, which are saved to the database in the activity this method was called from.
     * @param lensList the list associated with this operation
     * @param lens the lens to edit
     * @param serial the new serial
     * @param note the new note
     * @param myListA whether lens is a member of My List A
     * @param myListB whether lens is a member of My List B
     * @param myListC whether lens is a member of My List C
     * @return HashMap containing the lens and list that were changed
     */
    public static HashMap<String, Object> editListAndLens(LensListEntity lensList, LensEntity lens, String serial, String note, boolean myListA, boolean myListB, boolean myListC) {
        HashMap<String, Object> lensAndListMap = new HashMap<>();

        if (!lensList.getName().equals("All Lenses")) {
            // get the My List assignments from the LensList
            ArrayList<Long> myListAIds = lensList.getMyListALongIds();
            ArrayList<Long> myListBIds = lensList.getMyListBLongIds();
            ArrayList<Long> myListCIds = lensList.getMyListCLongIds();

            // add the lens to My List A (if it's not already a member)
            if (myListA) {
                // only add the ID if it's not already in the list
                if (!myListAIds.contains(lens.getId())) {
                    myListAIds.add(lens.getId());
                    lensList.setMyListAIds(myListAIds);
                }
            }

            // remove the lens from My List A (if it is currently a member)
            else {
                // only remove the ID if it's currently in the list
                if (myListAIds.contains(lens.getId())) {
                    myListAIds.remove(lens.getId());
                    lensList.setMyListAIds(myListAIds);
                }
            }

            // add the lens to My List B (if it's not already a member)
            if (myListB) {
                // only add the ID if it's not already in the list
                if (!myListBIds.contains(lens.getId())) {
                    myListBIds.add(lens.getId());
                    lensList.setMyListBIds(myListBIds);
                }
            }

            // remove the lens from My List B (if it is currently a member)
            else {
                // only remove the ID if it's currently in the list
                if (myListBIds.contains(lens.getId())) {
                    myListBIds.remove(lens.getId());
                    lensList.setMyListBIds(myListBIds);
                }
            }

            // add the lens to My List C (if it's not already a member)
            if (myListC) {
                // only add the ID if it's not already in the list
                if (!myListCIds.contains(lens.getId())) {
                    myListCIds.add(lens.getId());
                    lensList.setMyListCIds(myListCIds);
                }
            }

            // remove the lens from My List C (if it is currently a member)
            else {
                // only remove the ID if it's currently in the list
                if (myListCIds.contains(lens.getId())) {
                    myListCIds.remove(lens.getId());
                    lensList.setMyListCIds(myListCIds);
                }
            }
        }

        // update the serial and note attributes
        lens.setSerial(serial);
        lens.setNote(note);

        // build the data string. this strips out any My List assignments from the actual data string
        lens.setDataString(SharedHelper.buildLensDataString(lensList, lens));

        lensAndListMap.put("lens", lens);
        lensAndListMap.put("list", lensList);

        return lensAndListMap;
    }
}

package com.prestoncinema.app;

import com.prestoncinema.app.model.Lens;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Timer;

import timber.log.Timber;

/**
 * Created by MATT on 11/14/2017.
 * This class contains helper methods that can be used by other classes/activities
 */

public class SharedHelper {

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

    public static boolean checkIfLensExists(ArrayList<Lens> lenses, int focal1, int focal2, String serial, String note) {
        Timber.d("check if lens exists");
        boolean exists = false;
            for (Lens lens : lenses) {
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
                Timber.d("Zoom lens detected, switch to zoom lens FL mode");
                return false;
            default:            // a prime lens detected
                Timber.d("Prime lens detected, switch to prime lens FL mode");
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

        if (lensChars[0] == 0x2) {
//            Timber.d("STX found. Removing");
            begin += 1;
        }

        /* Check the second-to-last character for LF */
        if (!(lensChars[lensChars.length - 2] == 0xA)) {
//            Timber.d("LF not found on end. Append LF.");
            addLF = true;
        }

        /* Check the last character for CR */
        if (!(lensChars[lensChars.length - 1] == 0xD)) {
//            Timber.d("CR not found on end. Append CR.");
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
}

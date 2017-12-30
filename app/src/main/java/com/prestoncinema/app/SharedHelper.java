package com.prestoncinema.app;

import java.util.Arrays;
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

    public static byte[] checkLensChars(byte[] lensChars) {
        int begin = 0;

        if (lensChars[0] == 0x2) {
            Timber.d("STX found. Removing");
            begin += 1;
        }
        return Arrays.copyOfRange(lensChars, begin, lensChars.length);
    }

    public static String checkLensChars(String lensString) {
        byte[] bytesIn = lensString.getBytes();

        return new String(checkLensChars(bytesIn));
    }
}

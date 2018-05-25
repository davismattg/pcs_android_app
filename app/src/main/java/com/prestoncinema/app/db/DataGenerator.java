package com.prestoncinema.app.db;

import com.prestoncinema.app.SharedHelper;
import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.Lens;
import com.prestoncinema.app.model.LensList;

import java.util.ArrayList;
import java.util.List;

import timber.log.Timber;

/**
 * Created by MATT on 1/24/2018.
 *
 * Generates data to pre-populate the database
 */

public class DataGenerator {
    /* Initialize the default Lens List */
    private static String[] MANUFACTURERS = {"Angenieux", "Canon", "Cooke", "Fujinon", "Leica", "Panavision", "Zeiss", "Other"};
    private static final String DEFAULT_NAME = "Default Lenses";
    private static final String DEFAULT_NOTE = "Basic list w/o lens mappings";
    private static final int DEFAULT_COUNT = 65;
    private static final int NUM_ANGENIUEX = 5;
    private static final int NUM_CANON = 2;
    private static final int NUM_COOKE = 8;
    private static final int NUM_FUJINON = 2;
    private static final int NUM_LEICA = 10;
    private static final int NUM_PANAVISION = 24;
    private static final int NUM_ZEISS = 14;
    private static final int NUM_OTHER = 0;

    private static final int[] MANUFACTURER_COUNT = {NUM_ANGENIUEX, NUM_CANON, NUM_COOKE, NUM_FUJINON, NUM_LEICA, NUM_PANAVISION, NUM_ZEISS, NUM_OTHER};
    private static final int NUM_LENSES = NUM_ANGENIUEX + NUM_CANON + NUM_COOKE + NUM_FUJINON + NUM_LEICA + NUM_PANAVISION + NUM_ZEISS + NUM_OTHER;

    public static LensListEntity generateDefaultLensList() {
        Timber.d("------------- Generating Default Lens List --------------");
        LensListEntity def = new LensListEntity();
        def.setName(DEFAULT_NAME);
        def.setNote(DEFAULT_NOTE);
        def.setCount(DEFAULT_COUNT);

        return def;
    }

    public static List<LensEntity> generateDefaultLenses(final LensListEntity defaultList) {
        Timber.d("------------- Generating Default Lenses --------------");
        List<LensEntity> defaultLenses = new ArrayList<>(NUM_LENSES);

        for (int i=0; i < MANUFACTURERS.length; i++) {
            for (int j=0; j < MANUFACTURER_COUNT[i]; j++) {
                defaultLenses.add(i+j, createLens(MANUFACTURERS[i], j));
            }
        }

        return defaultLenses;
    }

    private static LensEntity createLens(String manufacturer, int index) {
        LensEntity lens = new LensEntity();
        String series = "";
        int focal1 = 0;
        int focal2 = 0;

        switch(manufacturer) {
            case "Angenieux":
                series = "Optimo";
                switch(index) {
                    case 0:
                        focal1 = 15;
                        focal2 = 40;
                        break;
                    case 1:
                        focal1 = 17;
                        focal2 = 80;
                        break;
                    case 2:
                        focal1 = 24;
                        focal2 = 290;
                        break;
                    case 3:
                        focal1 = 28;
                        focal2 = 76;
                        break;
                    case 4:
                        focal1 = 30;
                        focal2 = 80;
                        break;
                }
                break;
            case "Canon":
                series = "Cinema Zoom";
                switch(index) {
                    case 0:
                        focal1 = 14;
                        focal2 = 60;
                        break;
                    case 1:
                        focal1 = 30;
                        focal2 = 300;
                        break;
                }
                break;
            case "Cooke":
                series = "S4";
                switch(index) {
                    case 0:
                        focal1 = 100;
                        focal2 = 0;
                        break;
                    case 1:
                        focal1 = 18;
                        focal2 = 0;
                        break;
                    case 2:
                        focal1 = 21;
                        focal2 = 0;
                        break;
                    case 3:
                        focal1 = 25;
                        focal2 = 0;
                        break;
                    case 4:
                        focal1 = 32;
                        focal2 = 0;
                        break;
                    case 5:
                        focal1 = 40;
                        focal2 = 0;
                        break;
                    case 6:
                        focal1 = 50;
                        focal2 = 0;
                        break;
                    case 7:
                        focal1 = 75;
                        focal2 = 0;
                        break;
                }
                break;
            case "Fujinon":
                series = "Alura Zoom";
                switch(index) {
                    case 0:
                        focal1 = 18;
                        focal2 = 80;
                        break;
                    case 1:
                        focal1 = 18;
                        focal2 = 86;
                        break;
                }
                break;
            case "Leica":
               series = "Summilux Prime";
               switch(index) {
                   case 0:
                       focal1 = 16;
                       focal2 = 0;
                       break;
                   case 1:
                       focal1 = 18;
                       focal2 = 0;
                       break;
                   case 2:
                       focal1 = 21;
                       focal2 = 0;
                       break;
                   case 3:
                       focal1 = 25;
                       focal2 = 0;
                       break;
                   case 4:
                       focal1 = 29;
                       focal2 = 0;
                       break;
                   case 5:
                       focal1 = 35;
                       focal2 = 0;
                       break;
                   case 6:
                       focal1 = 40;
                       focal2 = 0;
                       break;
                   case 7:
                       focal1 = 50;
                       focal2 = 0;
                       break;
                   case 8:
                       focal1 = 65;
                       focal2 = 0;
                       break;
                   case 9:
                       focal1 = 75;
                       focal2 = 0;
                       break;
               }
               break;
            case "Panavision":
                switch(index) {
                    case 0:
                        series = "Primo Prime";
                        focal1 = 100;
                        focal2 = 0;
                        break;
                    case 1:
                        series = "P70 Prime";
                        focal1 = 100;
                        focal2 = 0;
                        break;
                    case 2:
                        series = "P70 Prime";
                        focal1 = 125;
                        focal2 = 0;
                        break;
                    case 3:
                        series = "Primo Zoom";
                        focal1 = 135;
                        focal2 = 320;
                        break;
                    case 4:
                        series = "P70 Prime";
                        focal1 = 150;
                        focal2 = 0;
                        break;
                    case 5:
                        series = "Primo Zoom";
                        focal1 = 17;
                        focal2 = 75;
                        break;
                    case 6:
                        series = "P70 Prime";
                        focal1 = 200;
                        focal2 = 0;
                        break;
                    case 7:
                        series = "Primo Prime";
                        focal1 = 21;
                        focal2 = 0;
                        break;
                    case 8:
                        series = "Primo Zoom";
                        focal1 = 24;
                        focal2 = 290;
                        break;
                    case 9:
                        series = "Primo Prime";
                        focal1 = 24;
                        focal2 = 0;
                        break;
                    case 10:
                        series = "P70 Prime";
                        focal1 = 250;
                        focal2 = 0;
                        break;
                    case 11:
                        series = "Primo Prime";
                        focal1 = 27;
                        focal2 = 0;
                        break;
                    case 12:
                        series = "P70 Prime";
                        focal1 = 27;
                        focal2 = 0;
                        break;
                    case 13:
                        series = "Primo Prime";
                        focal1 = 35;
                        focal2 = 0;
                        break;
                    case 14:
                        series = "P70 Prime";
                        focal1 = 35;
                        focal2 = 0;
                        break;
                    case 15:
                        series = "Primo Prime";
                        focal1 = 40;
                        focal2 = 0;
                        break;
                    case 16:
                        series = "P70 Prime";
                        focal1 = 40;
                        focal2 = 0;
                        break;
                    case 17:
                        series = "Primo Prime";
                        focal1 = 50;
                        focal2 = 0;
                        break;
                    case 18:
                        series = "P70 Prime";
                        focal1 = 50;
                        focal2 = 0;
                        break;
                    case 19:
                        series = "Primo Prime";
                        focal1 = 65;
                        focal2 = 0;
                        break;
                    case 20:
                        series = "P70 Prime";
                        focal1 = 65;
                        focal2 = 0;
                        break;
                    case 21:
                        series = "Primo Prime";
                        focal1 = 75;
                        focal2 = 0;
                        break;
                    case 22:
                        series = "P70 Prime";
                        focal1 = 80;
                        focal2 = 0;
                        break;
                    case 23:
                        series = "Primo Prime";
                        focal1 = 85;
                        focal2 = 0;
                        break;
                }
                break;
            case "Zeiss":
                switch(index) {
                    case 0:
                        series = "Master Prime";
                        focal1 = 100;
                        focal2 = 0;
                        break;
                    case 1:
                        series = "Ultra Prime";
                        focal1 = 100;
                        focal2 = 0;
                        break;
                    case 2:
                        series = "Ultra Prime";
                        focal1 = 16;
                        focal2 = 0;
                        break;
                    case 3:
                        series = "Master Prime";
                        focal1 = 18;
                        focal2 = 0;
                        break;
                    case 4:
                        series = "Ultra Prime";
                        focal1 = 24;
                        focal2 = 0;
                        break;
                    case 5:
                        series = "Master Prime";
                        focal1 = 25;
                        focal2 = 0;
                        break;
                    case 6:
                        series = "Ultra Prime";
                        focal1 = 28;
                        focal2 = 0;
                        break;
                    case 7:
                        series = "Ultra Prime";
                        focal1 = 32;
                        focal2 = 0;
                        break;
                    case 8:
                        series = "Master Prime";
                        focal1 = 35;
                        focal2 = 0;
                        break;
                    case 9:
                        series = "Ultra Prime";
                        focal1 = 40;
                        focal2 = 0;
                        break;
                    case 10:
                        series = "Master Prime";
                        focal1 = 50;
                        focal2 = 0;
                        break;
                    case 11:
                        series = "Ultra Prime";
                        focal1 = 50;
                        focal2 = 0;
                        break;
                    case 12:
                        series = "Ultra Prime";
                        focal1 = 65;
                        focal2 = 0;
                        break;
                    case 13:
                        series = "Master Prime";
                        focal1 = 75;
                        focal2 = 0;
                        break;
                }
                break;
        }

        lens.setManufacturer(manufacturer);
        lens.setSeries(series);
        lens.setSerial("");
        lens.setNote("");
        lens.setFocalLength1(focal1);
        lens.setFocalLength2(focal2);
        lens.setIsPrime(SharedHelper.isPrime(series));
        lens.setTag(index);
        lens.setManufacturerPosition(0);
        lens.setSeriesPosition(0);
        lens.setDataString(SharedHelper.buildLensDataString(manufacturer, series, focal1, focal2, "", "", false, false, false, false, false, false, ""));
        lens.setCalibratedF(false);
        lens.setCalibratedI(false);
        lens.setCalibratedZ(false);
        lens.setMyListA(false);
        lens.setMyListB(false);
        lens.setMyListC(false);
        lens.setChecked(false);

        return lens;
    }
}

package com.prestoncinema.app;

import com.prestoncinema.app.db.entity.LensEntity;

import java.util.ArrayList;

/**
 * Created by MATT on 5/1/2018.
 */

public class LensHelper {
    /** This method selects the lenses from the ArrayList and returns the appropriate ones given the manufacturer/series
     * parameters. This makes it easy to use the updateAll method in the LensDao
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
            }
            else {
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
}

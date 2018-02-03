package com.prestoncinema.app.db;

import com.prestoncinema.app.model.LensList;

/**
 * Created by MATT on 1/22/2018.
 * Access point for accessing lens list data
 */

public interface LensListDataSource {
    /**
     * Gets the lens list corresponding to the given ID from the database
     */
    LensList getLensList(Long id);

    /**
     * Gets a lens list corresponding to the given name from the database
     */
    LensList getLensListByName(String name);

    /**
     * Inserts the lens list into the data source, or, if it's an existing lens list,
     * updates it.
     */
    void insertOrUpdateLensList(LensList list);

    /**
     * Deletes all lens lists from the data source
     */
    void deleteAllLensLists();
}

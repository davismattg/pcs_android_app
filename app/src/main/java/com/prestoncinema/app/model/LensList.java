package com.prestoncinema.app.model;

import com.prestoncinema.app.db.entity.LensEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MATT on 1/31/2018.
 */

public interface LensList {
    /* GETTERZZZ */
    long getId();
    String getName();

    String getMyListAIds();
    ArrayList<Long> getMyListALongIds();

    String getMyListBIds();
    ArrayList<Long> getMyListBLongIds();

    String getMyListCIds();
    ArrayList<Long> getMyListCLongIds();

    String getNote();

    int getCount();

    List<LensEntity> getLenses();

    /* S-S-S-SETTERZZZ */
    void setId(long id);
    void setName(String name);

    void setMyListAIds(String myListAIds);
    void setMyListAIds(ArrayList<Long> ids);

    void setMyListBIds(String myListBIds);
    void setMyListBIds(ArrayList<Long> ids);

    void setMyListCIds(String myListCIds);
    void setMyListCIds(ArrayList<Long> ids);

    void setNote(String note);
    void setCount(int count);
    void setLenses(List<LensEntity> lenses);

    void increaseCount();
    void decreaseCount();
}

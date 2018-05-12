package com.prestoncinema.app.model;

import com.prestoncinema.app.db.entity.LensEntity;

import java.util.List;

/**
 * Created by MATT on 1/31/2018.
 */

public interface LensList {
    long getId();
    String getName();
    String getLocation();
    String getNote();
    int getCount();
    List<LensEntity> getLenses();

    void setId(long id);
    void setName(String name);
    void setLocation(String location);
    void setNote(String note);
    void setCount(int count);
    void setLenses(List<LensEntity> lenses);

    void increaseCount();
    void decreaseCount();
}

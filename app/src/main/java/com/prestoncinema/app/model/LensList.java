package com.prestoncinema.app.model;

/**
 * Created by MATT on 1/31/2018.
 */

public interface LensList {
    long getId();
    String getName();
    String getLocation();
    String getNote();
    int getCount();

    void setId(long id);
    void setName(String name);
    void setLocation(String location);
    void setNote(String note);
    void setCount(int count);
}

package com.prestoncinema.app.model;

/**
 * Created by MATT on 1/31/2018.
 */

public interface LensList {
    int getId();
    String getName();
    String getLocation();

    void setId(int id);
    void setName(String name);
    void setLocation(String location);
}

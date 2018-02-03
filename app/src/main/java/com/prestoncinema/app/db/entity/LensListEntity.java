package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.PrimaryKey;

import com.prestoncinema.app.model.LensList;

/**
* Created by MATT on 1/31/2018.
* This is the custom Java entity class used for the LensListEntity database model
*/

@Entity(tableName = "lens_lists")
public class LensListEntity implements LensList {
    @PrimaryKey
    private int id;
    private String name;
    private String location = "";

    @Override
    public int getId() {
            return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LensListEntity() {

    }

    public LensListEntity(int id, String name, String location) {
        this.id = id;
        this.name = name;
        this.location = location;
    }

    public LensListEntity(LensList list) {
        this.id = list.getId();
        this.name = list.getName();
        this.location = list.getLocation();
    }
}

package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;

import com.prestoncinema.app.model.LensList;

/**
* Created by MATT on 1/31/2018.
* This is the custom Java entity class used for the LensListEntity database model
*/

@Entity(tableName = "lens_lists")
public class LensListEntity implements LensList {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private String location = "";
    private String note;
    private int count;

    @Override
    public long getId() {
            return id;
    }

    public void setId(long id) {
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

    @Override
    public String getNote() { return note; }

    public void setNote(String note) { this.note = note; }

    @Override
    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public LensListEntity() {

    }

    @Ignore
    public LensListEntity(int id, String name, String location, String note) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.note = note;
    }

    @Ignore
    public LensListEntity(LensList list) {
        this.id = list.getId();
        this.name = list.getName();
        this.location = list.getLocation();
        this.note = list.getNote();
    }
}

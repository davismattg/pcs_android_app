package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.os.Parcel;
import android.os.Parcelable;

import com.prestoncinema.app.model.LensList;

import java.util.List;

/**
* Created by MATT on 1/31/2018.
* This is the custom Java entity class used for the LensListEntity database model
*/

@Entity(tableName = "lens_lists")
public class LensListEntity implements LensList, Parcelable {
    @PrimaryKey(autoGenerate = true)
    private long id;
    private String name;
    private String location = "";
    private String note;
    private int count;

    public int describeContents() { return 0; };

    @Ignore
    private List<LensEntity> lenses;

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeString(name);
        out.writeString(location);
        out.writeString(note);
        out.writeInt(count);
    }

    public static final Parcelable.Creator<LensListEntity> CREATOR =
            new Parcelable.Creator<LensListEntity>() {
                public LensListEntity createFromParcel(Parcel in) {
                    return new LensListEntity(in);
                }

                public LensListEntity[] newArray(int size) {
                    return new LensListEntity[size];
                }
            };

    private LensListEntity(Parcel in) {
        id = in.readLong();
        name = in.readString();
        location = in.readString();
        note = in.readString();
        count = in.readInt();
    }

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

    public void increaseCount() {
        count++;
    }

    public void decreaseCount() {
        count--;
    }

    @Override
    public List<LensEntity> getLenses() {
        return lenses;
    }

    public void setLenses(List<LensEntity> lenses) {
        this.lenses = lenses;
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

package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.PrimaryKey;
import android.os.Parcel;
import android.os.Parcelable;

import com.prestoncinema.app.model.LensList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
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
    private String note;
    private int count;

    private String myListAIds;
    private String myListBIds;
    private String myListCIds;

    public int describeContents() { return 0; };

    @Ignore
    private List<LensEntity> lenses;

    @Ignore
    private ArrayList<Long> myListALongIds;
    @Ignore
    private ArrayList<Long> myListBLongIds;
    @Ignore
    private ArrayList<Long> myListCLongIds;

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeString(name);
        out.writeString(myListAIds);
        out.writeString(myListBIds);
        out.writeString(myListCIds);
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
        myListAIds = in.readString();
        myListBIds = in.readString();
        myListCIds = in.readString();
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

    // My List A
    @Override public String getMyListAIds() {
        return myListAIds;
    }

    @Override
    public ArrayList<Long> getMyListALongIds() {
        ArrayList<Long> longIds = new ArrayList<>();

        if (myListAIds != null) {
            try {
                JSONArray json = new JSONArray(myListAIds);

                for (int i = 0; i < json.length(); i++) {
                    longIds.add(json.getLong(i));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return longIds;
    }

    public void setMyListAIds(String myListAIds) {
        this.myListAIds = myListAIds;
    }

    public void setMyListAIds(ArrayList<Long> ids) {
        JSONArray json = new JSONArray(ids);
        this.myListAIds = json.toString();
    }

    @Override public String getMyListBIds() {
        return myListBIds;
    }

    @Override
    public ArrayList<Long> getMyListBLongIds() {
        ArrayList<Long> longIds = new ArrayList<>();

        if (myListBIds != null) {
            try {
                JSONArray json = new JSONArray(myListBIds);

                for (int i = 0; i < json.length(); i++) {
                    longIds.add(json.getLong(i));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return longIds;
    }

    public void setMyListBIds(String myListBIds) {
        this.myListBIds = myListBIds;
    }

    public void setMyListBIds(ArrayList<Long> ids) {
        JSONArray json = new JSONArray(ids);
        this.myListBIds = json.toString();
    }

    @Override public String getMyListCIds() {
        return myListCIds;
    }

    @Override
    public ArrayList<Long> getMyListCLongIds() {
        ArrayList<Long> longIds = new ArrayList<>();

        if (myListCIds != null) {
            try {
                JSONArray json = new JSONArray(myListCIds);

                for (int i = 0; i < json.length(); i++) {
                    longIds.add(json.getLong(i));
                }

            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        return longIds;
    }

    public void setMyListCIds(String myListCIds) {
        this.myListCIds = myListCIds;
    }

    public void setMyListCIds(ArrayList<Long> ids) {
        JSONArray json = new JSONArray(ids);
        this.myListCIds = json.toString();
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
    public LensListEntity(int id, String name, String myListAIds, String myListBIds, String myListCIds, String note) {
        this.id = id;
        this.name = name;
        this.myListAIds = myListAIds;
        this.myListBIds = myListBIds;
        this.myListCIds = myListCIds;
        this.note = note;
    }

    @Ignore
    public LensListEntity(LensList list) {
        this.id = list.getId();
        this.name = list.getName();
        this.myListAIds = list.getMyListAIds();
        this.myListBIds = list.getMyListBIds();
        this.myListCIds = list.getMyListCIds();
        this.note = list.getNote();
    }
}

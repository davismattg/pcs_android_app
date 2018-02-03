package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.support.annotation.Nullable;

import com.prestoncinema.app.model.Lens;

/**
 * Created by MATT on 1/31/2018.
 */

@Entity(tableName = "lenses",
        foreignKeys = {
            @ForeignKey(entity = LensListEntity.class,
                        parentColumns = "id",
                        childColumns = "lensListId",
                        onDelete = ForeignKey.SET_NULL)},
        indices = {@Index(value="lensListId")})
public class LensEntity implements Lens{
    @PrimaryKey(autoGenerate = true)
    private int id;

    private int lensListId = 0;
    private int tag;

    private int manufacturerPosition;
    private int seriesPosition;
    private int focalLength1;
    private int focalLength2;

    private String dataString;
    private String manufacturer;
    private String series;
    private String serial;
    private String note;

    private boolean isPrime;
    private boolean calibratedF;
    private boolean calibratedI;
    private boolean calibratedZ;
    private boolean myListA;
    private boolean myListB;
    private boolean myListC;
    private boolean checked;

    @Override
    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    @Override
    public int getLensListId() {
        return lensListId;
    }

    public void setLensListId(int lensListId) {
        this.lensListId = lensListId;
    }

    @Override
    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
    }

    @Override
    public String getDataString() {
        return dataString;
    }

    public void setDataString(String dataString) {
        this.dataString = dataString;
    }

    @Override
    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    @Override
    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
    }

    @Override
    public int getManufacturerPosition() {
        return manufacturerPosition;
    }

    public void setManufacturerPosition(int manufacturerPosition) {
        this.manufacturerPosition = manufacturerPosition;
    }

    @Override
    public int getSeriesPosition() {
        return seriesPosition;
    }

    public void setSeriesPosition(int seriesPosition) {
        this.seriesPosition = seriesPosition;
    }

    @Override
    public int getFocalLength1() {
        return focalLength1;
    }

    public void setFocalLength1(int focalLength1) {
        this.focalLength1 = focalLength1;
    }

    @Override
    public int getFocalLength2() {
        return focalLength2;
    }

    public void setFocalLength2(int focalLength2) {
        this.focalLength2 = focalLength2;
    }

    @Override
    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    @Override
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    @Override
    public boolean getIsPrime() {
        return isPrime;
    }

    public void setIsPrime(boolean isPrime) {
        this.isPrime = isPrime;
    }

    @Override
    public boolean getCalibratedF() {
        return calibratedF;
    }

    public void setCalibratedF(boolean calibratedF) {
        this.calibratedF = calibratedF;
    }

    @Override
    public boolean getCalibratedI() {
        return calibratedI;
    }

    public void setCalibratedI(boolean calibratedI) {
        this.calibratedI = calibratedI;
    }

    @Override
    public boolean getCalibratedZ() {
        return calibratedZ;
    }

    public void setCalibratedZ(boolean calibratedZ) {
        this.calibratedZ = calibratedZ;
    }

    @Override
    public boolean getMyListA() {
        return myListA;
    }

    public void setMyListA(boolean myListA) {
        this.myListA = myListA;
    }

    @Override
    public boolean getMyListB() {
        return myListB;
    }

    public void setMyListB(boolean myListB) {
        this.myListB = myListB;
    }

    @Override
    public boolean getMyListC() {
        return myListC;
    }

    public void setMyListC(boolean myListC) {
        this.myListC = myListC;
    }

    @Override
    public boolean getChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
    }

    public LensEntity() {

    }

    public LensEntity(int id, int lensListId, int tag, int manufPos, int seriesPos, int fl1, int fl2,
                      String data, String manuf, String series, String serial, String note,
                      boolean isPrime, boolean calF, boolean calI, boolean calZ, boolean myListA,
                      boolean myListB, boolean myListC, boolean checked) {
        this.id = id;
        this.lensListId = lensListId;
        this.tag = tag;
        this.manufacturerPosition = manufPos;
        this.seriesPosition = seriesPos;
        this.focalLength1 = fl1;
        this.focalLength2 = fl2;
        this.dataString = data;
        this.manufacturer = manuf;
        this.series = series;
        this.serial = serial;
        this.note = note;
        this.isPrime = isPrime;
        this.calibratedF = calF;
        this.calibratedI = calI;
        this.calibratedZ = calZ;
        this.myListA = myListA;
        this.myListB = myListB;
        this.myListC = myListC;
        this.checked = checked;
    }

    public LensEntity(Lens lens) {
        this.id = lens.getId();
        this.lensListId = lens.getLensListId();
        this.tag = lens.getTag();
        this.manufacturerPosition = lens.getManufacturerPosition();
        this.seriesPosition = lens.getSeriesPosition();
        this.focalLength1 = lens.getFocalLength1();
        this.focalLength2 = lens.getFocalLength2();
        this.dataString = lens.getDataString();
        this.manufacturer = lens.getManufacturer();
        this.series = lens.getSeries();
        this.serial = lens.getSerial();
        this.note = lens.getNote();
        this.isPrime = lens.getIsPrime();
        this.calibratedF = lens.getCalibratedF();
        this.calibratedI = lens.getCalibratedI();
        this.calibratedZ = lens.getCalibratedZ();
        this.myListA = lens.getMyListA();
        this.myListB = lens.getMyListB();
        this.myListC = lens.getMyListC();
        this.checked = lens.getChecked();
    }
}

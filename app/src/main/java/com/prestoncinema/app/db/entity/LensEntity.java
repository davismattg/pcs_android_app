package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;
import android.arch.persistence.room.Ignore;
import android.arch.persistence.room.Index;
import android.arch.persistence.room.PrimaryKey;
import android.databinding.BaseObservable;
import android.databinding.Bindable;
import android.databinding.ObservableField;
import android.databinding.ObservableParcelable;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.Nullable;

import com.prestoncinema.app.BR;
import com.prestoncinema.app.model.Lens;

import java.util.Comparator;

/**
 * Created by MATT on 1/31/2018.
 */

@Entity(tableName = "lenses")
public class LensEntity extends BaseObservable implements Lens, Parcelable, Comparable<LensEntity> {
    @PrimaryKey(autoGenerate = true)
    private long id;

//    private int lensListId;
    private int tag;

    private int manufacturerPosition;
    private int seriesPosition;
    private int focalLength1;
    private int focalLength2;

    private String dataString;
    private String manufacturer = "";
    private String series = "";
    private String serial = "";
    private String note = "";

    private boolean isPrime;
    private boolean calibratedF;
    private boolean calibratedI;
    private boolean calibratedZ;
    private boolean myListA;
    private boolean myListB;
    private boolean myListC;
    private boolean checked;

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel out, int flags) {
        out.writeLong(id);
        out.writeInt(tag);
        out.writeInt(manufacturerPosition);
        out.writeInt(seriesPosition);
        out.writeInt(focalLength1);
        out.writeInt(focalLength2);

        out.writeString(dataString);
        out.writeString(manufacturer);
        out.writeString(series);
        out.writeString(serial);
        out.writeString(note);

        boolean[] bools = {isPrime, calibratedF, calibratedI, calibratedZ, myListA, myListB, myListC, checked};
        out.writeBooleanArray(bools);
    }

    public static final Parcelable.Creator<LensEntity> CREATOR =
        new Parcelable.Creator<LensEntity>() {
            public LensEntity createFromParcel(Parcel in) {
                return new LensEntity(in);
            }

            public LensEntity[] newArray(int size) {
                return new LensEntity[size];
            }
    };

    private LensEntity(Parcel in) {
        id = in.readLong();
        tag = in.readInt();
        manufacturerPosition = in.readInt();
        seriesPosition = in.readInt();
        focalLength1 = in.readInt();
        focalLength2 = in.readInt();

        dataString = in.readString();
        manufacturer = in.readString();
        series = in.readString();
        serial = in.readString();
        note = in.readString();

        boolean[] bools = new boolean[8];
        in.readBooleanArray(bools);
        isPrime = bools[0];
        calibratedF = bools[1];
        calibratedI = bools[2];
        calibratedZ = bools[3];
        myListA = bools[4];
        myListB = bools[5];
        myListC = bools[6];
        checked = bools[7];
    }

    /**
     * compareTo method for sorting an arraylist of lenses by focal length 1
     * @param lens
     * @return
     */
    public int compareTo(LensEntity lens) {
        int compareFocal1 = ((LensEntity) lens).getFocalLength1();

        return this.focalLength1 - compareFocal1;
    }

//    public static Comparator<LensEntity>

    @Bindable
//    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
        notifyPropertyChanged(BR.id);
    }

//    @Override
//    public int getLensListId() {
//        return lensListId;
//    }
//
//    public void setLensListId(int lensListId) {
//        this.lensListId = lensListId;
//    }

    @Bindable
//    @Override
    public int getTag() {
        return tag;
    }

    public void setTag(int tag) {
        this.tag = tag;
        notifyPropertyChanged(BR.tag);
    }

    @Bindable
//    @Override
    public String getDataString() {
        return dataString;
    }

    public void setDataString(String dataString) {
        this.dataString = dataString;
        notifyPropertyChanged(BR.dataString);
    }

    @Bindable
//    @Override
    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
        notifyPropertyChanged(BR.manufacturer);
    }

    @Bindable
//    @Override
    public String getSeries() {
        return series;
    }

    public void setSeries(String series) {
        this.series = series;
        notifyPropertyChanged(BR.series);
    }

    @Bindable
//    @Override
    public int getManufacturerPosition() {
        return manufacturerPosition;
    }

    public void setManufacturerPosition(int manufacturerPosition) {
        this.manufacturerPosition = manufacturerPosition;
        notifyPropertyChanged(BR.manufacturerPosition);
    }

    @Bindable
//    @Override
    public int getSeriesPosition() {
        return seriesPosition;
    }

    public void setSeriesPosition(int seriesPosition) {
        this.seriesPosition = seriesPosition;
        notifyPropertyChanged(BR.seriesPosition);
    }

    @Bindable
//    @Override
    public int getFocalLength1() {
        return focalLength1;
    }

    public void setFocalLength1(int focalLength1) {
        this.focalLength1 = focalLength1;
        notifyPropertyChanged(BR.focalLength1);
    }

    @Bindable
//    @Override
    public int getFocalLength2() {
        return focalLength2;
    }

    public void setFocalLength2(int focalLength2) {
        this.focalLength2 = focalLength2;
        notifyPropertyChanged(BR.focalLength2);
    }

    @Bindable
//    @Override
    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
        notifyPropertyChanged(BR.serial);
    }

    @Bindable
//    @Override
    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
        notifyPropertyChanged(BR.note);
    }

    @Bindable
//    @Override
    public boolean getIsPrime() {
        return isPrime;
    }

    public void setIsPrime(boolean isPrime) {
        this.isPrime = isPrime;
        notifyPropertyChanged(BR.isPrime);
    }

    @Bindable
//    @Override
    public boolean getCalibratedF() {
        return calibratedF;
    }

    public void setCalibratedF(boolean calibratedF) {
        this.calibratedF = calibratedF;
        notifyPropertyChanged(BR.calibratedF);
    }

    @Bindable
//    @Override
    public boolean getCalibratedI() {
        return calibratedI;
    }

    public void setCalibratedI(boolean calibratedI) {
        this.calibratedI = calibratedI;
        notifyPropertyChanged(BR.calibratedI);
    }

    @Bindable
//    @Override
    public boolean getCalibratedZ() {
        return calibratedZ;
    }

    public void setCalibratedZ(boolean calibratedZ) {
        this.calibratedZ = calibratedZ;
        notifyPropertyChanged(BR.calibratedZ);
    }

    @Bindable
//    @Override
    public boolean getMyListA() {
        return myListA;
    }

    public void setMyListA(boolean myListA) {
        this.myListA = myListA;
        notifyPropertyChanged(BR.myListA);
    }

    @Bindable
//    @Override
    public boolean getMyListB() {
        return myListB;
    }

    public void setMyListB(boolean myListB) {
        this.myListB = myListB;
        notifyPropertyChanged(BR.myListB);
    }

    @Bindable
//    @Override
    public boolean getMyListC() {
        return myListC;
    }

    public void setMyListC(boolean myListC) {
        this.myListC = myListC;
        notifyPropertyChanged(BR.myListC);
    }

    @Bindable
//    @Override
    public boolean getChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        this.checked = checked;
        notifyPropertyChanged(BR.checked);
    }

    public LensEntity() {

    }

    @Ignore
    public LensEntity(int id, int tag, int manufPos, int seriesPos, int fl1, int fl2,
                      String data, String manuf, String series, String serial, String note,
                      boolean isPrime, boolean calF, boolean calI, boolean calZ, boolean myListA,
                      boolean myListB, boolean myListC, boolean checked) {
        this.id = id;
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

    @Ignore
    public LensEntity(Lens lens) {
        this.id = lens.getId();
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

package com.prestoncinema.app.model;

/**
 * Created by MATT on 1/31/2018.
 */

public interface Lens {
    long getId();
    int getTag();
    String getDataString();
    String getManufacturer();
    String getSeries();
    int getManufacturerPosition();
    int getSeriesPosition();
    int getFocalLength1();
    int getFocalLength2();
    boolean getIsPrime();
    String getSerial();
    String getNote();
    boolean getCalibratedF();
    boolean getCalibratedI();
    boolean getCalibratedZ();
    boolean getMyListA();
    boolean getMyListB();
    boolean getMyListC();
    boolean getChecked();
//    int getLensListId();

    void setId(long id);
    void setTag(int tag);
    void setDataString(String data);
    void setManufacturer(String manuf);
    void setSeries(String series);
    void setManufacturerPosition(int pos);
    void setSeriesPosition(int pos);
    void setFocalLength1(int fl1);
    void setFocalLength2(int fl2);
    void setIsPrime(boolean isPrime);
    void setSerial(String serial);
    void setNote(String note);
    void setCalibratedF(boolean calF);
    void setCalibratedI(boolean calI);
    void setCalibratedZ(boolean calZ);
    void setMyListA(boolean myListA);
    void setMyListB(boolean myListB);
    void setMyListC(boolean myListC);
    void setChecked(boolean checked);
//    void setLensListId(int id);
}

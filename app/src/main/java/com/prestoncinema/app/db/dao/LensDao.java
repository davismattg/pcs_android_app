package com.prestoncinema.app.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.prestoncinema.app.db.entity.LensEntity;

import java.util.List;

/**
 * Created by MATT on 1/31/2018.
 */

@Dao
public interface LensDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LensEntity> lenses);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LensEntity lens);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(LensEntity lens);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    int updateAll(LensEntity... lenses);

    @Delete
    void delete(LensEntity... lenses);

    /* SQL Queries for retrieving Lenses from the database. Room checks these SQL queries during compilation,
    so you can be confident the app will work if these SQL queries pass.
     */
    @Query("SELECT * FROM lenses")
    List<LensEntity> loadAll();

    @Query("SELECT * FROM lenses WHERE checked")
    List<LensEntity> loadSelected();

//    @Query("SELECT * FROM lenses")
//    List<LensEntity> loadAllLensesSync();

//    @Query("SELECT * FROM lenses WHERE lensListId = :lensListId")
//    LiveData<List<LensEntity>> loadLenses(int lensListId);
//
//    @Query("SELECT * FROM lenses WHERE lensListId = :lensListId")
//    List<LensEntity> loadLensesSync(int lensListId);

    @Query("SELECT COUNT(*) from lenses")
    int getCount();

    @Query("SELECT COUNT(*) FROM lenses WHERE checked")
    int getSelectedCount();

    @Query("SELECT * from lenses WHERE id = :id")
    LiveData<LensEntity> findLensById(int id);

    @Query("SELECT * from lenses WHERE id = :id")
    LensEntity findLensByIdSync(int id);

    @Query("SELECT * FROM lenses WHERE manufacturer = :manufacturer")
    LiveData<List<LensEntity>> loadLensesByManufacturer(String manufacturer);

    @Query("SELECT * FROM lenses WHERE manufacturer = :manufacturer")
    List<LensEntity> loadLensesByManufacturerSync(String manufacturer);

    @Query("SELECT * FROM lenses WHERE manufacturer = :manufacturer AND series = :series")
    LiveData<List<LensEntity>> loadLensesManufacturerAndSeries(String manufacturer, String series);

    @Query("SELECT * FROM lenses WHERE manufacturer = :manufacturer AND series = :series")
    List<LensEntity> loadLensesManufacturerAndSeriesSync(String manufacturer, String series);

    @Query("SELECT COUNT(*) FROM lenses WHERE manufacturer = :manufacturer AND series = :series AND " +
            "focalLength1 = :focal1 AND focalLength2 = :focal2 AND serial = :serial AND note = :note")
    int lensExists(String manufacturer, String series, int focal1, int focal2, String serial, String note);

    @Query("SELECT * FROM lenses WHERE manufacturer = :manufacturer AND series = :series AND " +
            "focalLength1 = :focal1 AND focalLength2 = :focal2 AND serial = :serial AND note = :note")
    LensEntity getLensByAttributes(String manufacturer, String series, int focal1, int focal2, String serial, String note);

    @Query("DELETE from lenses")
    void deleteAll();
}

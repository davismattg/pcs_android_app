package com.prestoncinema.app.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.model.Lens;

import java.util.List;

/**
 * Created by MATT on 1/31/2018.
 */

@Dao
public interface LensDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LensEntity> lenses);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LensEntity lens);

    @Update
    void updateLenses(LensEntity... lenses);

    @Delete
    void deleteLenses(LensEntity... lenses);

    /* SQL Queries for retrieving Lenses from the database. Room checks these SQL queries during compilation,
    so you can be confident the app will work if these SQL queries pass.
     */
    @Query("SELECT * FROM lenses")
    LiveData<List<LensEntity>> loadAllLenses();

    @Query("SELECT * FROM lenses")
    List<LensEntity> loadAllLensesSync();

    @Query("SELECT * FROM lenses WHERE lensListId = :lensListId")
    LiveData<List<LensEntity>> loadLenses(int lensListId);

    @Query("SELECT * FROM lenses WHERE lensListId = :lensListId")
    List<LensEntity> loadLensesSync(int lensListId);

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
}

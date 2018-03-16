package com.prestoncinema.app.db.dao;

import android.arch.lifecycle.LiveData;
import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Delete;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.arch.persistence.room.Update;

import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.model.LensList;

import java.util.List;

/**
 * Created by MATT on 1/17/2018.
 */

@Dao
public interface LensListDao {
//    @Insert(onConflict = OnConflictStrategy.REPLACE)
//    long insert(LensListEntity listEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(List<LensListEntity> lensLists);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(LensListEntity lensList);

    @Update(onConflict = OnConflictStrategy.REPLACE)
    void update(LensListEntity... lists);

    @Delete
    void delete(LensListEntity... lists);

    /* SQL Queries for retrieving Lens Lists from the database. Room checks these SQL queries during compilation,
    so you can be confident the app will work if these SQL queries pass.
     */
    @Query("SELECT * FROM lens_lists")
    List<LensListEntity> loadAllLensLists();

    @Query("SELECT * from lens_lists WHERE id = :id")
    LensListEntity loadLensList(long id);

    @Query("SELECT * from lens_lists WHERE id = :id")
    LensListEntity loadLensListSync(int id);

    @Query("SELECT * FROM lens_lists WHERE name = :name")
    LensListEntity loadLensListByName(String name);

    @Query("SELECT * FROM lens_lists WHERE name = :name")
    LensListEntity loadLensListByNameSync(String name);

    @Query("DELETE FROM lens_lists")
    void deleteAll();
}

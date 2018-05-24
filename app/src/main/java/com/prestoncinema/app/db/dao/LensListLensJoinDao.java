package com.prestoncinema.app.db.dao;

import android.arch.persistence.room.Dao;
import android.arch.persistence.room.Insert;
import android.arch.persistence.room.OnConflictStrategy;
import android.arch.persistence.room.Query;
import android.databinding.generated.callback.OnClickListener;

import com.prestoncinema.app.db.entity.LensEntity;
import com.prestoncinema.app.db.entity.LensListEntity;
import com.prestoncinema.app.db.entity.LensListLensJoinEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MATT on 2/13/2018.
 */
@Dao
public interface LensListLensJoinDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(LensListLensJoinEntity lensListLensJoinEntity);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertAll(ArrayList<LensListLensJoinEntity> entities);

    @Query("SELECT * from lens_list_lens_join")
    List<LensListLensJoinEntity> selectAll();

    @Query("SELECT * from lens_lists ls LEFT JOIN lens_list_lens_join lj ON lj.listId = ls.id WHERE lj.lensId = :lensId")
    List<LensListEntity> getListsForLens(final long lensId);

    @Query("SELECT * from lenses l LEFT JOIN lens_list_lens_join j ON j.lensId = l.id WHERE j.listId = :listId")
    List<LensEntity> getLensesForList(final long listId);

    @Query("SELECT COUNT(*) from lenses l LEFT JOIN lens_list_lens_join j ON j.lensId = l.id WHERE j.listId = :listId")
    int getLensCountForList(final long listId);

    @Query("SELECT * from lens_lists ls LEFT JOIN lens_list_lens_join lj ON lj.listId = ls.id WHERE lj.lensId IN (:lensIds) GROUP BY ls.id")
    List<LensListEntity> getListsForLenses(long[] lensIds);

    @Query("SELECT * from lens_list_lens_join WHERE listId = :listId AND lensId = :lensId")
    LensListLensJoinEntity getByListAndLensId(long listId, long lensId);

//    @Query("SELECT COUNT(*) from lens_list_lens_join WHERE")

    @Query("DELETE from lens_list_lens_join WHERE listId = :id")
    void deleteByListId(long id);

    @Query("DELETE from lens_list_lens_join WHERE lensId = :id")
    void deleteByLensId(long id);

    @Query("DELETE from lens_list_lens_join WHERE listId = :listId AND lensId = :lensId")
    void deleteByListAndLensId(long listId, long lensId);

    @Query("DELETE from lens_list_lens_join")
    void deleteAll();
}

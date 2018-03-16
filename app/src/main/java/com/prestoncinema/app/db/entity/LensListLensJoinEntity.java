package com.prestoncinema.app.db.entity;

import android.arch.persistence.room.Entity;
import android.arch.persistence.room.ForeignKey;

import com.prestoncinema.app.model.LensList;

/**
 * Created by MATT on 2/13/2018.
 */

@Entity(tableName = "lens_list_lens_join",
        primaryKeys = { "listId", "lensId" },
        foreignKeys = {
            @ForeignKey(entity = LensListEntity.class,
                        parentColumns = "id",
                        childColumns = "listId",
                        onDelete = ForeignKey.CASCADE,
                        onUpdate = ForeignKey.CASCADE),
            @ForeignKey(entity = LensEntity.class,
                        parentColumns = "id",
                        childColumns = "lensId",
                        onDelete = ForeignKey.CASCADE,
                        onUpdate = ForeignKey.CASCADE)
        })

public class LensListLensJoinEntity {
    public final long listId;
    public final long lensId;

    public LensListLensJoinEntity(final long listId, final long lensId) {
        this.listId = listId;
        this.lensId = lensId;
    }
}

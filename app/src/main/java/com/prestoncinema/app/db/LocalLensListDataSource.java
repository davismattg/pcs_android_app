//package com.prestoncinema.app.db;
//
//import android.arch.lifecycle.LiveData;
//import android.content.Context;
//import android.support.annotation.NonNull;
//import android.support.annotation.VisibleForTesting;
//import android.support.transition.Visibility;
//
//import com.prestoncinema.app.AppDatabase;
//import com.prestoncinema.app.LensList;
//import com.prestoncinema.app.LensListDao;
//
///**
// * Created by MATT on 1/22/2018.
// */
//
//public class LocalLensListDataSource implements LensListDataSource {
//    private static volatile LocalLensListDataSource INSTANCE;
//
//    private LensListDao lensListDao;
//
//    @VisibleForTesting
//    LocalLensListDataSource(LensListDao listDao) {
//        lensListDao = listDao;
//    }
//
//    public static LocalLensListDataSource getInstance(@NonNull Context context) {
//        if (INSTANCE == null) {
//            synchronized (LocalLensListDataSource.class) {
//                if (INSTANCE == null) {
//                    AppDatabase database = AppDatabase.getInstance(context);
//                    INSTANCE = new LocalLensListDataSource((database.lensListDao()));
//                }
//            }
//        }
//
//        return INSTANCE;
//    }
//
//    @Override
//    public LiveData<LensList> getLensList(Long id) {
//        return lensListDao.loadLensList(id);
//    }
//
//    @Override
//    public LensList getLensListByName(String name) {
//        return lensListDao.getLensListByName(name);
//    }
//
//    @Override
//    public void insertOrUpdateLensList(LensList list) {
//        lensListDao.insertLensList(list);
//    }
//
//    @Override
//    public void deleteAllLensLists() {
//        lensListDao.deleteAllLensLists();
//    }
//}

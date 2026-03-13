package com.sanlei.pos.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.sanlei.pos.data.db.dao.PendingSaleDao;
import com.sanlei.pos.data.db.dao.ProductDao;
import com.sanlei.pos.data.db.entity.CategoryEntity;
import com.sanlei.pos.data.db.entity.PendingSale;
import com.sanlei.pos.data.db.entity.ProductEntity;

@Database(entities = {ProductEntity.class, CategoryEntity.class, PendingSale.class}, version = 1, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {
    private static volatile AppDatabase INSTANCE;

    public abstract ProductDao productDao();
    public abstract PendingSaleDao pendingSaleDao();

    public static AppDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "sanlei_pos.db")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}

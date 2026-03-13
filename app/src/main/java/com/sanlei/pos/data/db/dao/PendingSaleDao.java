package com.sanlei.pos.data.db.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.sanlei.pos.data.db.entity.PendingSale;

import java.util.List;

@Dao
public interface PendingSaleDao {
    @Insert
    long insert(PendingSale sale);

    @Query("SELECT * FROM pending_sales WHERE synced = 0 ORDER BY createdAt")
    List<PendingSale> getUnsynced();

    @Query("SELECT COUNT(*) FROM pending_sales WHERE synced = 0")
    int getUnsyncedCount();

    @Query("UPDATE pending_sales SET synced = 1, serverInvoice = :invoice WHERE id = :id")
    void markSynced(int id, String invoice);

    @Query("SELECT * FROM pending_sales ORDER BY createdAt DESC LIMIT 50")
    List<PendingSale> getRecent();

    @Query("DELETE FROM pending_sales WHERE synced = 1 AND createdAt < :before")
    void cleanOldSynced(long before);
}

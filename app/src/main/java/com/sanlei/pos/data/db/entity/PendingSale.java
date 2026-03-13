package com.sanlei.pos.data.db.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "pending_sales")
public class PendingSale {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String localId;        // UUID for idempotency
    public String jsonPayload;    // Full sale JSON to POST to server
    public long createdAt;        // timestamp millis
    public boolean synced;
    public String serverInvoice;  // set after successful sync
}

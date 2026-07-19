package io.droidmcp.audit

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database backing [RoomAuditSink]. Single entity, version 1, no exported schema (the
 * audit log is a private on-device cache, not a versioned data contract).
 */
@Database(entities = [ToolCallAuditEntity::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
}

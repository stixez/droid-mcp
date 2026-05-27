package io.droidmcp.audit

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [ToolCallAuditEntity::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditDao(): AuditDao
}

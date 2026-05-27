package io.droidmcp.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface AuditDao {

    @Insert
    suspend fun insert(entry: ToolCallAuditEntity)

    /** Most recent calls first. */
    @Query("SELECT * FROM tool_call_audit ORDER BY timestamp DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<ToolCallAuditEntity>

    /** Reactive most-recent-first stream for a browse UI. */
    @Query("SELECT * FROM tool_call_audit ORDER BY timestamp DESC LIMIT :limit")
    fun observe(limit: Int): Flow<List<ToolCallAuditEntity>>

    /** All rows oldest-first — used by JSON export. */
    @Query("SELECT * FROM tool_call_audit ORDER BY timestamp ASC")
    suspend fun all(): List<ToolCallAuditEntity>

    @Query("SELECT COUNT(*) FROM tool_call_audit")
    suspend fun count(): Int

    /** Drop rows recorded before [cutoff] (epoch millis). @return rows deleted. */
    @Query("DELETE FROM tool_call_audit WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long): Int

    @Query("DELETE FROM tool_call_audit")
    suspend fun clear()
}

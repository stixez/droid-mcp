package io.droidmcp.audit

import android.content.Context
import androidx.room.Room
import io.droidmcp.core.AuditSink
import io.droidmcp.core.ToolCallAudit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong

/**
 * Room-backed [AuditSink] persisting every HTTP `tools/call` to a private
 * on-device database.
 *
 * Wire it into the server with `DroidMcp.Builder.withAuditSink(...)`. Writes are
 * fire-and-forget on a background scope, so [record] returns immediately and a
 * DB hiccup can never fail a tool call. Rows older than [retention] are pruned
 * after a write, at most once per minute.
 *
 * Writes run in a child job of [scope], so [close] cancels only this sink's own
 * pending writes — never a host-supplied scope such as `lifecycleScope`. Call
 * [flush] first to wait for in-flight writes.
 *
 * **Privacy note:** the persisted [ToolCallAudit.argumentsJson] contains
 * whatever the LLM passed — message text, contact names, file paths,
 * coordinates. The database lives in the host app's private storage; the host
 * owns its retention, export, and deletion. Set [retention] to
 * [Duration.ZERO] to keep rows indefinitely (pruning is then skipped).
 */
class RoomAuditSink(
    context: Context,
    private val retention: Duration = Duration.ofDays(7),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : AuditSink {

    private val db: AuditDatabase = Room.databaseBuilder(
        context.applicationContext,
        AuditDatabase::class.java,
        DB_NAME,
    ).build()

    private val dao: AuditDao = db.auditDao()

    /** Parent of every write; cancelling it leaves the caller's [scope] untouched. */
    private val writeJob = SupervisorJob(scope.coroutineContext[Job])
    private val writeScope = CoroutineScope(scope.coroutineContext + writeJob)

    private val lastPruneAt = AtomicLong(0L)

    override fun record(entry: ToolCallAudit) {
        writeScope.launch {
            try {
                dao.insert(entry.toEntity())
                val now = System.currentTimeMillis()
                val last = lastPruneAt.get()
                if (!retention.isZero && !retention.isNegative &&
                    now - last >= PRUNE_INTERVAL_MS && lastPruneAt.compareAndSet(last, now)
                ) {
                    dao.deleteOlderThan(now - retention.toMillis())
                }
            } catch (_: Exception) {
                // A DB failure must never propagate to the request path.
            }
        }
    }

    /** Most recent [limit] calls, newest first. */
    suspend fun recent(limit: Int = 100): List<ToolCallAudit> =
        dao.recent(limit).map { it.toAudit() }

    /** Reactive newest-first stream for a browse UI. */
    fun observe(limit: Int = 100): Flow<List<ToolCallAudit>> =
        dao.observe(limit).map { rows -> rows.map { it.toAudit() } }

    suspend fun count(): Int = dao.count()

    /** Force a retention sweep now. @return rows deleted. No-op if retention is zero. */
    suspend fun pruneNow(): Int =
        if (retention.isZero || retention.isNegative) 0
        else dao.deleteOlderThan(System.currentTimeMillis() - retention.toMillis())

    /** Delete the entire audit history. */
    suspend fun clear() = dao.clear()

    /** Serialize the full history (oldest-first) to a JSON array string. */
    suspend fun exportJson(): String {
        val array = JSONArray()
        dao.all().forEach { row ->
            array.put(
                JSONObject().apply {
                    put("timestamp", row.timestamp)
                    put("tool_name", row.toolName)
                    put("client_label", row.clientLabel ?: JSONObject.NULL)
                    put("arguments_json", row.argumentsJson ?: JSONObject.NULL)
                    put("success", row.success)
                    put("error_message", row.errorMessage ?: JSONObject.NULL)
                    put("duration_ms", row.durationMs)
                }
            )
        }
        return array.toString()
    }

    /** Suspend until every write queued so far has finished. */
    suspend fun flush() {
        writeJob.children.toList().forEach { it.join() }
    }

    /**
     * Cancel this sink's pending writes and close the database. Does not cancel the
     * [scope] passed to the constructor. Call [flush] first to avoid dropping rows.
     */
    fun close() {
        writeJob.cancel()
        db.close()
    }

    companion object {
        const val DB_NAME: String = "droid_mcp_audit.db"
        private const val PRUNE_INTERVAL_MS = 60_000L
    }
}

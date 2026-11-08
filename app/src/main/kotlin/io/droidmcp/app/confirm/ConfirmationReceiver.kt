package io.droidmcp.app.confirm

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.droidmcp.app.appGraph

/** Allow / Deny buttons on approval notifications. */
class ConfirmationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, -1)
        if (id < 0 || !intent.hasExtra(EXTRA_SESSION)) return
        val scope = intent.getStringExtra(EXTRA_SCOPE)
            ?.let { name -> ApprovalScope.entries.firstOrNull { it.name == name } }
            ?: ApprovalScope.Once
        context.appGraph.confirmations.answer(
            id = id,
            allow = intent.getBooleanExtra(EXTRA_ALLOW, false),
            session = intent.getLongExtra(EXTRA_SESSION, 0L),
            scope = scope,
        )
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_ALLOW = "allow"
        private const val EXTRA_SESSION = "session"
        private const val EXTRA_SCOPE = "scope"

        fun pendingIntent(
            context: Context,
            id: Int,
            allow: Boolean,
            session: Long,
            scope: ApprovalScope = ApprovalScope.Once,
        ): PendingIntent {
            val intent = Intent(context, ConfirmationReceiver::class.java)
                .setAction(if (allow) "allow-${scope.name}" else "deny")
                .putExtra(EXTRA_ID, id)
                .putExtra(EXTRA_ALLOW, allow)
                .putExtra(EXTRA_SESSION, session)
                .putExtra(EXTRA_SCOPE, scope.name)
            // A distinct request code per id and per button, so the actions don't overwrite each other.
            val button = if (!allow) 0 else 1 + scope.ordinal
            return PendingIntent.getBroadcast(
                context,
                id * 4 + button,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}

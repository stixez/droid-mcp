package io.droidmcp.core.transport

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Bearer-token authority for [HttpTransport].
 *
 * Holds one **primary** token (the value surfaced as `DroidMcp.serverToken` and
 * embedded in the pairing QR) plus any number of **paired-client** tokens, each
 * tied to an opaque host-supplied label so it can be revoked individually.
 *
 * - [verify] returns the label that owns a presented token, or `null` if none
 *   matches — callers use the label for audit attribution.
 * - [rotatePrimary] mints a fresh primary, invalidating the old one. Paired
 *   clients are unaffected.
 * - [pair] mints a new per-client token; [revoke] drops it.
 *
 * All comparisons are constant-time over each candidate token so a presented
 * value can't be recovered byte-by-byte via timing. The number of paired
 * clients is small and host-controlled, so iterating them is not a concern.
 *
 * Thread-safe: the primary is `@Volatile`, clients live in a [ConcurrentHashMap].
 */
class TokenStore(seedPrimary: String? = null) {

    data class PairedClient(val label: String, val token: String, val createdAt: Long)

    @Volatile
    private var primary: String = seedPrimary ?: generateToken()

    /** label -> client */
    private val clients = ConcurrentHashMap<String, PairedClient>()

    val primaryToken: String get() = primary

    /**
     * Resolve a presented token to the label that owns it.
     * @return [PRIMARY_LABEL] for the primary token, the client label for a
     *   paired token, or `null` if the token is missing or unrecognized.
     */
    fun verify(provided: String?): String? {
        val candidate = provided?.toByteArray(Charsets.UTF_8) ?: return null
        if (constantTimeEquals(primary, candidate)) return PRIMARY_LABEL
        for (client in clients.values) {
            if (constantTimeEquals(client.token, candidate)) return client.label
        }
        return null
    }

    /** Replace the primary token with a fresh value and return it. */
    fun rotatePrimary(): String {
        val next = generateToken()
        primary = next
        return next
    }

    /**
     * Mint a token for a named client. Re-pairing an existing label replaces its
     * token (and invalidates the previous one). Returns the new token.
     */
    fun pair(label: String): String {
        val token = generateToken()
        clients[label] = PairedClient(label, token, System.currentTimeMillis())
        return token
    }

    /** Revoke a client's token. @return true if a client with that label existed. */
    fun revoke(label: String): Boolean = clients.remove(label) != null

    /**
     * Snapshot of paired clients (does not include the primary), oldest first.
     * Ties on [PairedClient.createdAt] (two pairings within the same millisecond)
     * break deterministically on the unique label, so the order is stable rather
     * than dependent on map iteration order.
     */
    fun pairedClients(): List<PairedClient> =
        clients.values.sortedWith(compareBy({ it.createdAt }, { it.label }))

    private fun constantTimeEquals(expected: String, providedBytes: ByteArray): Boolean =
        MessageDigest.isEqual(expected.toByteArray(Charsets.UTF_8), providedBytes)

    companion object {
        /** Label reported by [verify] for the primary (non-paired) token. */
        const val PRIMARY_LABEL: String = "primary"

        internal fun generateToken(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}

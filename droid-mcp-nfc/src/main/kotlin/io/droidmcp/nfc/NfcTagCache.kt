package io.droidmcp.nfc

import android.nfc.NdefMessage
import android.nfc.Tag
import android.nfc.tech.Ndef

/**
 * Simple in-memory cache for the most recently scanned NFC tag.
 *
 * The host Activity should call [update] from its `onNewIntent()` when a tag is
 * discovered via NFC foreground dispatch. [ReadNfcTagTool] and [WriteNfcTagTool]
 * operate on whatever tag is cached here.
 *
 * Alongside the [Tag] handle, [update] snapshots the tag's NDEF contents as
 * delivered at discovery time (`Ndef.getCachedNdefMessage()`, which needs no
 * radio I/O) plus its `maxSize` / `isWritable`. Tags usually leave the field
 * long before an LLM asks to read them, so [ReadNfcTagTool] serves this
 * snapshot (with `cached = true`) when the live read fails with
 * `TagLostException`.
 *
 * @property lastTag the most recently scanned [Tag], or `null` if none seen yet.
 */
object NfcTagCache {
    @Volatile
    var lastTag: Tag? = null
        private set

    /**
     * NDEF snapshot of [lastTag] taken in [update], or null for non-NDEF tags /
     * no tag.
     */
    @Volatile
    internal var snapshot: NdefSnapshot? = null
        private set

    /** Records [tag] as the most recently scanned tag and snapshots its NDEF data. */
    fun update(tag: Tag) {
        update(tag, null)
    }

    /**
     * Records [tag] as the most recently scanned tag. [message] overrides the
     * snapshotted NDEF message — pass the first entry of
     * `NfcAdapter.EXTRA_NDEF_MESSAGES` if you already have it; otherwise the
     * tag's own cached message is used.
     */
    fun update(tag: Tag, message: NdefMessage?) {
        val ndef = runCatching { Ndef.get(tag) }.getOrNull()
        snapshot = ndef?.let {
            NdefSnapshot(
                message = message ?: runCatching { it.cachedNdefMessage }.getOrNull(),
                maxSize = runCatching { it.maxSize }.getOrDefault(0),
                isWritable = runCatching { it.isWritable }.getOrDefault(false),
            )
        }
        lastTag = tag
    }

    /** Replace the NDEF snapshot for [tag] after a successful live read/write. */
    internal fun refresh(tag: Tag, message: NdefMessage?, maxSize: Int, isWritable: Boolean) {
        if (lastTag === tag) snapshot = NdefSnapshot(message, maxSize, isWritable)
    }

    /** Clears the cached tag. */
    fun clear() {
        lastTag = null
        snapshot = null
    }

    /** NDEF contents and capabilities captured for the cached tag. */
    internal data class NdefSnapshot(
        val message: NdefMessage?,
        val maxSize: Int,
        val isWritable: Boolean,
    )
}

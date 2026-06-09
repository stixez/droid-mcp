package io.droidmcp.nfc

import android.nfc.Tag

/**
 * Simple in-memory cache for the most recently scanned NFC tag.
 *
 * The host Activity should call [update] from its `onNewIntent()` when a tag is
 * discovered via NFC foreground dispatch. [ReadNfcTagTool] and [WriteNfcTagTool]
 * operate on whatever tag is cached here.
 *
 * @property lastTag the most recently scanned [Tag], or `null` if none seen yet.
 */
object NfcTagCache {
    @Volatile
    var lastTag: Tag? = null
        private set

    /** Records [tag] as the most recently scanned tag. */
    fun update(tag: Tag) {
        lastTag = tag
    }

    /** Clears the cached tag. */
    fun clear() {
        lastTag = null
    }
}

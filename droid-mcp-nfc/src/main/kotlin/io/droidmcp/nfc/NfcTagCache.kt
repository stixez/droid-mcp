package io.droidmcp.nfc

import android.nfc.Tag

/**
 * Simple in-memory cache for the most recently scanned NFC tag.
 * The host Activity should call NfcTagCache.update(tag) from its
 * onNewIntent() when a tag is discovered via foreground dispatch.
 */
object NfcTagCache {
    @Volatile
    var lastTag: Tag? = null
        private set

    fun update(tag: Tag) {
        lastTag = tag
    }

    fun clear() {
        lastTag = null
    }
}

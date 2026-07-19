package io.droidmcp.core.transport

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class TokenStoreTest {

    @Test
    fun `seeded primary token is used verbatim`() {
        val store = TokenStore("my-seed-token")
        assertThat(store.primaryToken).isEqualTo("my-seed-token")
        assertThat(store.verify("my-seed-token")).isEqualTo(TokenStore.PRIMARY_LABEL)
    }

    @Test
    fun `unseeded store generates a primary token`() {
        val store = TokenStore()
        assertThat(store.primaryToken).isNotEmpty()
        assertThat(store.verify(store.primaryToken)).isEqualTo(TokenStore.PRIMARY_LABEL)
    }

    @Test
    fun `verify rejects unknown and null tokens`() {
        val store = TokenStore("seed")
        assertThat(store.verify("wrong")).isNull()
        assertThat(store.verify(null)).isNull()
    }

    @Test
    fun `rotatePrimary invalidates old token and returns new one`() {
        val store = TokenStore("old")
        val next = store.rotatePrimary()
        assertThat(next).isNotEqualTo("old")
        assertThat(store.verify("old")).isNull()
        assertThat(store.verify(next)).isEqualTo(TokenStore.PRIMARY_LABEL)
    }

    @Test
    fun `paired client token resolves to its label`() {
        val store = TokenStore("primary-tok")
        val clientToken = store.pair("laptop")
        assertThat(store.verify(clientToken)).isEqualTo("laptop")
        // primary still works independently
        assertThat(store.verify("primary-tok")).isEqualTo(TokenStore.PRIMARY_LABEL)
    }

    @Test
    fun `revoke drops a client token`() {
        val store = TokenStore()
        val clientToken = store.pair("phone")
        assertThat(store.revoke("phone")).isTrue()
        assertThat(store.verify(clientToken)).isNull()
        assertThat(store.revoke("phone")).isFalse()
    }

    @Test
    fun `re-pairing a label replaces the previous token`() {
        val store = TokenStore()
        val first = store.pair("desktop")
        val second = store.pair("desktop")
        assertThat(second).isNotEqualTo(first)
        assertThat(store.verify(first)).isNull()
        assertThat(store.verify(second)).isEqualTo("desktop")
        assertThat(store.pairedClients().map { it.label }).containsExactly("desktop")
    }

    @Test
    fun `rotating primary leaves paired clients intact`() {
        val store = TokenStore("p0")
        val clientToken = store.pair("watch")
        store.rotatePrimary()
        assertThat(store.verify(clientToken)).isEqualTo("watch")
    }

    @Test
    fun `pairedClients excludes the primary and is sorted by creation`() {
        val store = TokenStore("p")
        store.pair("a")
        store.pair("b")
        val labels = store.pairedClients().map { it.label }
        assertThat(labels).containsExactly("a", "b").inOrder()
    }
}

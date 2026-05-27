package io.droidmcp.core.transport

import java.security.KeyStore
import java.security.MessageDigest

/**
 * TLS material for [HttpTransport]. Holds a keystore containing the server's
 * private key + self-signed certificate under [keyAlias], plus the HTTPS port
 * to bind.
 *
 * Core only *consumes* a keystore — it never generates one, so it carries no
 * certificate-building dependency. The opt-in `droid-mcp-tls` module produces
 * a [TlsConfig] via `SelfSignedCert.loadOrCreate(...)`; a host can equally hand
 * in its own keystore from anywhere.
 *
 * Because the certificate is self-signed, clients can't validate it against a
 * CA chain. Instead they **pin [certFingerprintSha256]** — surface it in the
 * pairing QR and have the client verify the presented cert's SHA-256 matches.
 */
class TlsConfig(
    val keyStore: KeyStore,
    val keyAlias: String,
    val keyStorePassword: CharArray,
    val privateKeyPassword: CharArray,
    val httpsPort: Int = 8443,
) {
    /**
     * Colon-separated uppercase-hex SHA-256 of the server certificate's DER
     * encoding (e.g. `A1:B2:...`). Stable across restarts as long as the same
     * keystore is reused. Pin this on the client.
     */
    val certFingerprintSha256: String by lazy {
        val cert = keyStore.getCertificate(keyAlias)
            ?: error("No certificate under alias '$keyAlias' in keystore")
        MessageDigest.getInstance("SHA-256")
            .digest(cert.encoded)
            .joinToString(":") { "%02X".format(it) }
    }
}

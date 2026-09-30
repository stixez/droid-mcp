package io.droidmcp.tls

import android.util.Log
import io.droidmcp.core.transport.TlsConfig
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.security.UnrecoverableKeyException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

/**
 * Generates (and persists) a self-signed certificate for droid-mcp's HTTPS
 * transport, returning a [TlsConfig] ready to hand to
 * `DroidMcp.Builder.enableTls(...)`.
 *
 * Uses BouncyCastle only to *build* the X.509 certificate — Android's public
 * API has no certificate builder, and `AndroidKeyStore` won't export a private
 * key for the embedded server to use. Key generation and signing go through the
 * platform providers (Conscrypt), so no security provider is registered.
 *
 * The keystore is **PKCS12**, the one keystore type Android's runtime supports
 * (it has no JKS provider). It's persisted so the certificate — and therefore
 * the pinned [TlsConfig.certFingerprintSha256] — stays stable across restarts.
 *
 * The certificate is intentionally minimal: `CN=droid-mcp`, no SANs. Clients
 * pin the fingerprint rather than validating hostname/chain, so SANs are moot.
 */
object SelfSignedCert {

    private const val KEYSTORE_TYPE = "PKCS12"
    private const val SIGNATURE_ALGORITHM = "SHA256withRSA"
    private const val KEY_SIZE_BITS = 2048
    private const val DEFAULT_ALIAS = "droid-mcp"
    private const val DEFAULT_PASSWORD = "droid-mcp"
    private const val TAG = "SelfSignedCert"

    /**
     * Load the keystore at [file] if it exists, otherwise generate a fresh
     * self-signed cert and persist it there.
     *
     * @param file PKCS12 keystore location (e.g. `File(context.filesDir, "droid-mcp-tls.p12")`)
     * @param alias key entry alias
     * @param password keystore + private-key password (protects the on-disk
     *   file; the security boundary is the pinned fingerprint, not this value)
     * @param httpsPort port the TLS server should bind
     * @param validityDays certificate lifetime
     * @throws IOException if the keystore exists and the JDK reports [password] as wrong (on
     *   Android, where wrong password and corruption look the same, the keystore is regenerated).
     * @throws IllegalStateException if the keystore exists but holds no entry under [alias].
     */
    fun loadOrCreate(
        file: File,
        alias: String = DEFAULT_ALIAS,
        password: CharArray = DEFAULT_PASSWORD.toCharArray(),
        httpsPort: Int = 8443,
        validityDays: Long = 3650,
    ): TlsConfig {
        val keyStore = if (file.exists()) {
            loadExisting(file, alias, password, validityDays)
        } else {
            generateAndPersist(file, alias, password, validityDays)
        }
        return TlsConfig(
            keyStore = keyStore,
            keyAlias = alias,
            keyStorePassword = password,
            privateKeyPassword = password,
            httpsPort = httpsPort,
        )
    }

    /**
     * Loads [file]. Corruption (truncated/garbled file) or an expired certificate triggers
     * regeneration. A missing alias, or a password the JDK definitely identifies as wrong, is a
     * host configuration error and is thrown, since regenerating would change the fingerprint
     * every client pins.
     *
     * Android's PKCS12 provider can't tell the two apart ("mac invalid - wrong password or
     * corrupted file"), so there that case regenerates: a new fingerprint means clients re-pair,
     * which is recoverable, while throwing would keep TLS down until someone deletes the file.
     */
    /** Only the JDK's PKCS12 provider says so unambiguously, through an [UnrecoverableKeyException] cause. */
    internal fun isDefinitelyWrongPassword(e: IOException): Boolean = e.cause is UnrecoverableKeyException

    private fun loadExisting(file: File, alias: String, password: CharArray, validityDays: Long): KeyStore {
        val keyStore = try {
            KeyStore.getInstance(KEYSTORE_TYPE).apply {
                file.inputStream().use { load(it, password) }
            }
        } catch (e: IOException) {
            if (isDefinitelyWrongPassword(e)) throw e
            Log.w(TAG, "TLS keystore at ${file.path} is unreadable (corrupt, or wrong password); regenerating (fingerprint will change)", e)
            file.delete()
            return generateAndPersist(file, alias, password, validityDays)
        }
        val cert = keyStore.getCertificate(alias) as? X509Certificate
            ?: throw IllegalStateException("TLS keystore ${file.path} has no certificate under alias '$alias'")
        return try {
            cert.checkValidity()
            keyStore
        } catch (_: CertificateException) {
            Log.w(TAG, "TLS certificate in ${file.path} expired; regenerating (fingerprint will change)")
            file.delete()
            generateAndPersist(file, alias, password, validityDays)
        }
    }

    /** Generates a fresh keystore and persists it to [file] via a temp-file-then-rename
     * so a process kill mid-write leaves either the old file or nothing — never a
     * truncated one. */
    private fun generateAndPersist(file: File, alias: String, password: CharArray, validityDays: Long): KeyStore {
        val keyStore = generate(alias, password, validityDays)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "${file.name}.tmp")
        tmp.outputStream().use { keyStore.store(it, password) }
        if (!tmp.renameTo(file)) {
            tmp.delete()
            throw IOException("Failed to persist TLS keystore to ${file.path}")
        }
        return keyStore
    }

    private fun generate(alias: String, password: CharArray, validityDays: Long): KeyStore {
        val random = SecureRandom()
        val keyPair = KeyPairGenerator.getInstance("RSA").apply {
            initialize(KEY_SIZE_BITS, random)
        }.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - ONE_MINUTE_MS)
        val notAfter = Date(now + validityDays * ONE_DAY_MS)
        val subject = X500Principal("CN=droid-mcp")
        val serial = BigInteger(64, random)

        val builder = JcaX509v3CertificateBuilder(
            subject, serial, notBefore, notAfter, subject, keyPair.public,
        )
        val signer = JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(keyPair.private)
        val certificate: X509Certificate = JcaX509CertificateConverter()
            .getCertificate(builder.build(signer))

        return KeyStore.getInstance(KEYSTORE_TYPE).apply {
            load(null, null)
            setKeyEntry(alias, keyPair.private, password, arrayOf(certificate))
        }
    }

    private const val ONE_MINUTE_MS = 60_000L
    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L
}

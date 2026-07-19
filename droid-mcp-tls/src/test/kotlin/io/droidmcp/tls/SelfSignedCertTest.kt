package io.droidmcp.tls

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.cert.X509Certificate
import java.nio.file.Path

class SelfSignedCertTest {

    @Test
    fun `creates a loadable keystore with a self-signed cert`(@TempDir dir: Path) {
        val file = File(dir.toFile(), "tls.p12")
        val config = SelfSignedCert.loadOrCreate(file, httpsPort = 9443)

        assertThat(file.exists()).isTrue()
        assertThat(config.httpsPort).isEqualTo(9443)
        val cert = config.keyStore.getCertificate(config.keyAlias) as X509Certificate
        assertThat(cert.subjectX500Principal.name).contains("droid-mcp")
        // self-signed: issuer == subject
        assertThat(cert.issuerX500Principal).isEqualTo(cert.subjectX500Principal)
        // private key is present and recoverable with the configured password
        assertThat(config.keyStore.getKey(config.keyAlias, config.keyStorePassword)).isNotNull()
    }

    @Test
    fun `fingerprint is colon-hex SHA-256 and stable across reload`(@TempDir dir: Path) {
        val file = File(dir.toFile(), "tls.p12")
        val first = SelfSignedCert.loadOrCreate(file)
        val fingerprint = first.certFingerprintSha256

        // 32 bytes -> 32 hex pairs joined by 31 colons
        assertThat(fingerprint.split(":")).hasSize(32)
        assertThat(fingerprint).matches("([0-9A-F]{2}:){31}[0-9A-F]{2}")

        // reloading the persisted keystore yields the same cert + fingerprint
        val reloaded = SelfSignedCert.loadOrCreate(file)
        assertThat(reloaded.certFingerprintSha256).isEqualTo(fingerprint)
    }

    @Test
    fun `regenerating into a fresh file yields a different cert`(@TempDir dir: Path) {
        val a = SelfSignedCert.loadOrCreate(File(dir.toFile(), "a.p12"))
        val b = SelfSignedCert.loadOrCreate(File(dir.toFile(), "b.p12"))
        assertThat(a.certFingerprintSha256).isNotEqualTo(b.certFingerprintSha256)
    }

    @Test
    fun `corrupted keystore file is regenerated instead of throwing`(@TempDir dir: Path) {
        val file = File(dir.toFile(), "corrupt.p12")
        file.writeBytes(byteArrayOf(1, 2, 3, 4)) // not a valid PKCS12 file

        val config = SelfSignedCert.loadOrCreate(file)

        assertThat(file.exists()).isTrue()
        val cert = config.keyStore.getCertificate(config.keyAlias) as X509Certificate
        assertThat(cert.subjectX500Principal.name).contains("droid-mcp")
    }
}

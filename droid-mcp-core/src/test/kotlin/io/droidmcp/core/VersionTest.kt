package io.droidmcp.core

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import java.io.File
import java.util.Properties

/** [DROID_MCP_VERSION] is a compile-time constant, so it can't read Gradle — pin it to it instead. */
class VersionTest {

    @Test
    fun `DROID_MCP_VERSION matches VERSION_NAME in gradle properties`() {
        // Android unit tests run with the module directory as the working directory.
        val props = Properties().apply { File("../gradle.properties").inputStream().use { load(it) } }
        assertThat(DROID_MCP_VERSION).isEqualTo(props.getProperty("VERSION_NAME"))
    }
}

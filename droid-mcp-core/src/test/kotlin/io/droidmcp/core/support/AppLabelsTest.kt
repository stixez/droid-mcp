package io.droidmcp.core.support

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class AppLabelsTest {

    private val appInfo = mockk<ApplicationInfo>()
    private val pm = mockk<PackageManager> {
        every { getApplicationInfo("hidden.app", 0) } throws PackageManager.NameNotFoundException()
        every { getApplicationLabel(appInfo) } returns "Hidden App"
    }

    @Test
    fun `a miss is retried once app info arrives, then cached`() {
        val labels = AppLabels(pm)
        assertThat(labels.labelFor("hidden.app")).isNull()
        assertThat(labels.labelFor("hidden.app")).isNull()
        assertThat(labels.labelFor("hidden.app", appInfo)).isEqualTo("Hidden App")
        assertThat(labels.labelFor("hidden.app")).isEqualTo("Hidden App")
        verify(exactly = 1) { pm.getApplicationInfo("hidden.app", 0) }
        verify(exactly = 1) { pm.getApplicationLabel(appInfo) }
    }
}

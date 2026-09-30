package io.droidmcp.calllog

import android.provider.CallLog
import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class CallLogUtilsTest {

    @Test
    fun `callTypeName maps every known type and falls back to unknown`() {
        assertThat(callTypeName(CallLog.Calls.INCOMING_TYPE)).isEqualTo("incoming")
        assertThat(callTypeName(CallLog.Calls.OUTGOING_TYPE)).isEqualTo("outgoing")
        assertThat(callTypeName(CallLog.Calls.MISSED_TYPE)).isEqualTo("missed")
        assertThat(callTypeName(CallLog.Calls.REJECTED_TYPE)).isEqualTo("rejected")
        assertThat(callTypeName(CallLog.Calls.BLOCKED_TYPE)).isEqualTo("blocked")
        assertThat(callTypeName(CallLog.Calls.VOICEMAIL_TYPE)).isEqualTo("unknown")
        assertThat(callTypeName(-1)).isEqualTo("unknown")
    }

}

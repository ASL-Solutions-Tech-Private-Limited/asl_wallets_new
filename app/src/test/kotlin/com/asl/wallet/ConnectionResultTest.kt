package com.asl.wallet

import com.asl.wallet.ui.ConnectionResult
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.IOException
import javax.net.ssl.SSLPeerUnverifiedException

class ConnectionResultTest {
    @Test fun `401 from protected endpoint means server reachable and pin matched`() {
        assertThat(ConnectionResult.from(401, null, 120)).isEqualTo(ConnectionResult.Verified(120))
    }

    @Test fun `pin mismatch is reported as insecure, never as success`() {
        assertThat(ConnectionResult.from(null, SSLPeerUnverifiedException("Certificate pinning failure!"), 5)).isEqualTo(ConnectionResult.InsecureBlocked)
    }

    @Test fun `io errors are offline, other codes unexpected`() {
        assertThat(ConnectionResult.from(null, IOException(), 5)).isEqualTo(ConnectionResult.NoInternet)
        assertThat(ConnectionResult.from(500, null, 5)).isEqualTo(ConnectionResult.Unexpected("HTTP 500"))
    }
}

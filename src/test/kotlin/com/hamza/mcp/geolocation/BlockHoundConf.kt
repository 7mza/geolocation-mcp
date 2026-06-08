package com.hamza.mcp.geolocation

import reactor.blockhound.BlockHound
import reactor.blockhound.integration.BlockHoundIntegration

class BlockHoundConf : BlockHoundIntegration {
    override fun applyTo(builder: BlockHound.Builder) {
        // Random.UUID sub need access to /dev/urandom in GeoLocationServiceMcpTest
        builder.allowBlockingCallsInside($$"sun.security.provider.NativePRNG$RandomIO", "ensureBufferValid")
    }
}

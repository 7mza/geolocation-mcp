package com.hamza.mcp.geolocation

import tools.jackson.databind.ObjectMapper
import java.security.MessageDigest
import kotlin.random.Random

@Suppress("ArrayInDataClass")
data class BinaryFixture(
    val bytes: ByteArray,
    val digest: String,
)

class TestUtils {
    companion object {
        fun generateRandomBinary(sizeBytes: Int = 1024 * 1024): BinaryFixture {
            val bytes = Random.nextBytes(sizeBytes)
            val digest = "sha256:${MessageDigest.getInstance("SHA-256").digest(bytes).toHexString()}"
            return BinaryFixture(bytes, digest)
        }

        inline fun <reified T> parseJson(
            json: String,
            objectMapper: ObjectMapper,
        ): T = objectMapper.readValue(json.trimIndent(), T::class.java)

        inline fun <reified T> writeJson(
            t: T,
            objectMapper: ObjectMapper,
        ): String = objectMapper.writeValueAsString(t)
    }
}

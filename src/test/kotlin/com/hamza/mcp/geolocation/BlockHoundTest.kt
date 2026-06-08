package com.hamza.mcp.geolocation

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import reactor.blockhound.BlockingOperationError
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import java.time.Duration
import java.util.function.Consumer

// BlockHound regression tests
class BlockHoundTest {
    @Test
    fun `BlockHound should watch junit and trigger if any blocking code is detected inside reactive chain`() {
        val ex =
            assertThrows<RuntimeException> {
                Mono
                    .delay(Duration.ofMillis(1))
                    .doOnNext(Consumer { Thread.sleep(1) })
                    .block()
            }
        assertThat(ex.cause).isInstanceOf(BlockingOperationError::class.java)
    }

    @Test
    fun `blocking code running on scheduler should not trigger BlockHound`() {
        Mono
            .fromCallable { Thread.sleep(1) }
            .subscribeOn(Schedulers.boundedElastic())
            .block()
    }
}

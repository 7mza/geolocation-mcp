package com.hamza.mcp.geolocation

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.Awaitility.await
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.mockito.kotlin.atLeastOnce
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.test.StepVerifier
import java.util.concurrent.TimeUnit

class TorExitNodeRepositoryTest {
    private val client = mock<ITorExitNodeClient>()

    private val repository = TorExitNodeRepository(client)

    @Test
    fun `populate exitNodes on init`() {
        whenever(client.pullExitNodes()).thenReturn(hashSetOf("hope", "lion").toMono())
        repository.init()
        assertThat(repository.exitNodes.get()).containsExactlyInAnyOrder("hope", "lion")
    }

    @Test
    fun `empty exitNodes on init if client error`() {
        whenever(client.pullExitNodes()).thenReturn(Mono.error(RuntimeException("toto")))
        repository.init()
        assertThat(repository.exitNodes.get()).isEmpty()
    }

    @Test
    fun `refresh exitNodes`() {
        whenever(client.pullExitNodes()).thenReturn(hashSetOf("hope", "lion").toMono())
        repository.init()
        assertThat(repository.exitNodes.get()).containsExactlyInAnyOrder("hope", "lion")
        whenever(client.pullExitNodes()).thenReturn(hashSetOf("a", "h", "l").toMono())
        StepVerifier.create(repository.refresh()).verifyComplete()
        assertThat(repository.exitNodes.get()).containsExactlyInAnyOrder("a", "h", "l")
    }

    @Test
    fun `refresh exitNodes should keep old data if client error`() {
        whenever(client.pullExitNodes()).thenReturn(hashSetOf("hope", "lion").toMono())
        repository.init()
        assertThat(repository.exitNodes.get()).containsExactlyInAnyOrder("hope", "lion")
        whenever(client.pullExitNodes()).thenReturn(Mono.error(RuntimeException("toto")))
        StepVerifier.create(repository.refresh()).verifyComplete()
        assertThat(repository.exitNodes.get()).containsExactlyInAnyOrder("hope", "lion")
    }

    @Test
    fun isTorExit() {
        whenever(client.pullExitNodes()).thenReturn(hashSetOf("hope", "lion").toMono())
        repository.init()
        assertThat(repository.isTorExitNode("hope")).isTrue
        assertThat(repository.isTorExitNode("espoir")).isFalse
    }
}

// @EnableWireMock does not work with processAOT
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["geolocation.torRefreshCron=*/3 * * * * *"],
)
class TorExitNodeRepositoryScheduledTest {
    companion object {
        @RegisterExtension
        private val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(wireMockConfig().dynamicPort())
                .build()

        @DynamicPropertySource
        @JvmStatic
        fun properties(registry: DynamicPropertyRegistry) {
            registry.add("geolocation.torBaseUrl") { wireMock.baseUrl() }
        }
    }

    @MockitoBean
    private lateinit var initializer: IDatabaseInitializer

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @MockitoSpyBean
    private lateinit var torRepository: TorExitNodeRepository

    @Test
    fun `Scheduled should trigger`() {
        wireMock.stubFor(
            WireMock
                .get("/torbulkexitlist")
                .withHeader(HttpHeaders.ACCEPT, WireMock.equalTo(MediaType.TEXT_PLAIN_VALUE))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withBodyFile("torbulkexitlist.txt")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE),
                ),
        )

        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted { verify(torRepository, atLeastOnce()).scheduledRefresh() }
    }
}

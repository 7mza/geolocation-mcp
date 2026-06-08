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

class X4BNetRepositoryTest {
    private val client = mock<IX4BNetClient>()

    private val repository = X4BNetRepository(client)

    @Test
    fun `populate vpnRanges and datacenterRanges on init`() {
        whenever(client.pullVpnRanges()).thenReturn(listOf(1L to 2L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(10L to 20L).toMono())
        repository.init()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(1L to 2L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(10L to 20L)
    }

    @Test
    fun `vpnRanges and datacenterRanges on init should keep old data if client error`() {
        whenever(client.pullVpnRanges()).thenReturn(listOf(1L to 2L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(10L to 20L).toMono())
        repository.init()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(1L to 2L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(10L to 20L)
        //
        whenever(client.pullVpnRanges()).thenReturn(Mono.error(RuntimeException("toto")))
        whenever(client.pullDatacenterRanges()).thenReturn(Mono.error(RuntimeException("toto")))
        repository.init()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(1L to 2L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(10L to 20L)
    }

    @Test
    fun `refresh vpnRanges and datacenterRanges`() {
        whenever(client.pullVpnRanges()).thenReturn(listOf(1L to 2L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(10L to 20L).toMono())
        repository.init()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(1L to 2L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(10L to 20L)
        //
        whenever(client.pullVpnRanges()).thenReturn(listOf(3L to 4L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(30L to 40L).toMono())
        //
        StepVerifier.create(repository.refresh()).verifyComplete()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(3L to 4L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(30L to 40L)
    }

    @Test
    fun `refresh vpnRanges and datacenterRanges should keep old data if client error`() {
        whenever(client.pullVpnRanges()).thenReturn(listOf(1L to 2L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(10L to 20L).toMono())
        repository.init()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(1L to 2L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(10L to 20L)
        //
        whenever(client.pullVpnRanges()).thenReturn(Mono.error(RuntimeException("toto")))
        whenever(client.pullDatacenterRanges()).thenReturn(Mono.error(RuntimeException("toto")))
        //
        StepVerifier.create(repository.refresh()).verifyComplete()
        assertThat(repository.vpnRanges.get().first()).isEqualTo(1L to 2L)
        assertThat(repository.datacenterRanges.get().first()).isEqualTo(10L to 20L)
    }

    @Test
    fun isVpn() {
        whenever(client.pullVpnRanges()).thenReturn(listOf(35298560L to 35298815L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(1L to 2L).toMono())
        repository.init()
        assertThat(repository.isVpn("2.26.157.1")).isTrue
        assertThat(repository.isVpn("99.26.157.1")).isFalse
        assertThat(repository.isVpn("toto")).isFalse
    }

    @Test
    fun isDatacenter() {
        whenever(client.pullVpnRanges()).thenReturn(listOf(1L to 2L).toMono())
        whenever(client.pullDatacenterRanges()).thenReturn(listOf(35298560L to 35298815L).toMono())
        repository.init()
        assertThat(repository.isDatacenter("2.26.157.1")).isTrue
        assertThat(repository.isDatacenter("99.26.157.1")).isFalse
        assertThat(repository.isDatacenter("toto")).isFalse
    }
}

// @EnableWireMock does not work with processAOT
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["geolocation.x4bNetRefreshCron=*/3 * * * * *"],
)
class X4BNetRepositoryScheduledTest {
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
            registry.add("geolocation.x4bNetBaseUrl") { wireMock.baseUrl() }
        }
    }

    @MockitoBean
    private lateinit var initializer: IDatabaseInitializer

    @MockitoBean
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoSpyBean
    private lateinit var x4bNetRepository: X4BNetRepository

    @Test
    fun `Scheduled should trigger`() {
        wireMock.stubFor(
            WireMock
                .get("/X4BNet/lists_vpn/main/output/vpn/ipv4.txt")
                .withHeader(HttpHeaders.ACCEPT, WireMock.equalTo(MediaType.TEXT_PLAIN_VALUE))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withBodyFile("vpn_ipv4.txt")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE),
                ),
        )
        wireMock.stubFor(
            WireMock
                .get("/X4BNet/lists_vpn/main/output/datacenter/ipv4.txt")
                .withHeader(HttpHeaders.ACCEPT, WireMock.equalTo(MediaType.TEXT_PLAIN_VALUE))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withBodyFile("dc_ipv4.txt")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_PLAIN_VALUE),
                ),
        )

        await()
            .atMost(10, TimeUnit.SECONDS)
            .untilAsserted { verify(x4bNetRepository, atLeastOnce()).scheduledRefresh() }
    }
}

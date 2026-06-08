package com.hamza.mcp.geolocation

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.test.StepVerifier

// @EnableWireMock does not work with processAOT
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class X4BNetClientTest {
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

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @Autowired
    private lateinit var client: IX4BNetClient

    @Test
    fun pullVpnRanges() {
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
        StepVerifier
            .create(client.pullVpnRanges())
            .assertNext { results ->
                assertThat(results).hasSize(3)
                assertThat(results).isEqualTo(
                    listOf(35298560L to 35298815L, 37307392L to 37308415L, 37417284L to 37417287L)
                        .sortedBy { it.first },
                )
            }.verifyComplete()
    }

    @Test
    fun pullDatacenterRanges() {
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
        StepVerifier
            .create(client.pullDatacenterRanges())
            .assertNext { results ->
                assertThat(results).hasSize(3)
                assertThat(results).hasSize(3)
                assertThat(results).isEqualTo(
                    listOf(24379392L to 24510463L, 34619392L to 34620415L, 2565516544L to 2565516799L)
                        .sortedBy { it.first },
                )
            }.verifyComplete()
    }
}

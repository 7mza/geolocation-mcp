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
class TorExitNodeClientTest {
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
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @Autowired
    private lateinit var client: ITorExitNodeClient

    @Test
    fun pullExitNodes() {
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

        StepVerifier
            .create(client.pullExitNodes())
            .assertNext {
                assertThat(it).hasSize(5)
                assertThat(it).containsExactlyInAnyOrder(
                    "171.25.193.25",
                    "80.67.167.81",
                    "198.98.51.189",
                    "89.58.26.216",
                    "109.70.100.4",
                )
            }.verifyComplete()
    }
}

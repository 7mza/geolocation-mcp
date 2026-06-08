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
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import kotlin.io.path.Path

// @EnableWireMock does not work with processAOT
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GithubReleaseClientTest {
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
            registry.add("geolocation.githubApiBaseUrl") { wireMock.baseUrl() }
        }
    }

    @MockitoBean
    private lateinit var initializer: IDatabaseInitializer

    @MockitoBean
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @Autowired
    private lateinit var properties: GeoLocationProperties

    @Autowired
    private lateinit var client: IGithubReleaseClient

    @Test
    fun pullRelease() {
        wireMock.stubFor(
            WireMock
                .get("/repos/${properties.githubRepo}/releases/latest")
                .withHeader(HttpHeaders.ACCEPT, WireMock.equalTo(MediaType.APPLICATION_JSON_VALUE))
                .willReturn(
                    WireMock
                        .aResponse()
                        .withBodyFile("release.json")
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE),
                ),
        )

        StepVerifier
            .create(client.pullRelease())
            .assertNext { release ->
                assertThat(release.tagName).isEqualTo("2026.06.04")
                assertThat(release.assets).hasSize(3)

                val city = release.assets.first { it.name == "GeoLite2-City.mmdb" }
                assertThat(city.size).isEqualTo(65655525L)
                assertThat(
                    city.digest,
                ).isEqualTo("sha256:2cd96caa302ec416f69f3c298497d52108b382f418522d66ad97ed0b9dde0f80")
                assertThat(
                    city.browserDownloadUrl,
                ).isEqualTo("https://github.com/P3TERX/GeoLite.mmdb/releases/download/2026.06.04/GeoLite2-City.mmdb")
                assertThat(city.updatedAt).isEqualTo("2026-06-04T05:56:15Z")

                val asn = release.assets.first { it.name == "GeoLite2-ASN.mmdb" }
                assertThat(asn.size).isEqualTo(12290045L)
                assertThat(
                    asn.digest,
                ).isEqualTo("sha256:815d961f2424a09792fddf3b9bfadc000ad416b26de5123b8ce515a89c22923a")
                assertThat(
                    asn.browserDownloadUrl,
                ).isEqualTo("https://github.com/P3TERX/GeoLite.mmdb/releases/download/2026.06.04/GeoLite2-ASN.mmdb")
                assertThat(asn.updatedAt).isEqualTo("2026-06-04T05:56:13Z")
            }.verifyComplete()
    }

    @Test // JaCoCo branch
    fun `init with token`() {
        val p =
            GeoLocationProperties().apply {
                directory = Path(System.getProperty("java.io.tmpdir"))
                githubApiBaseUrl = "http://localhost:1"
                githubRepo = "noop/noop"
                githubToken = "noop"
                torBaseUrl = "noop"
                torRefreshCron = "-"
                x4bNetBaseUrl = "noop"
                x4bNetRefreshCron = "-"
            }
        p.torRefreshCron
        p.x4bNetRefreshCron
        GithubReleaseClient(WebClient.builder(), p)
    }
}

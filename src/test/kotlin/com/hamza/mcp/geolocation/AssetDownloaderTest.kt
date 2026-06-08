package com.hamza.mcp.geolocation

import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig
import com.github.tomakehurst.wiremock.junit5.WireMockExtension
import com.hamza.mcp.geolocation.models.GitHubAsset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.FileSystemUtils
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import java.time.Instant
import kotlin.io.path.Path

// @EnableWireMock does not work with processAOT
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [$$"geolocation.directory=${java.io.tmpdir}/geolocation-mcp_1"],
)
class AssetDownloaderTest {
    companion object {
        private val dir = Path(System.getProperty("java.io.tmpdir"), "geolocation-mcp_1")

        private lateinit var fixture: BinaryFixture

        private lateinit var asset: GitHubAsset

        @RegisterExtension
        private val wireMock: WireMockExtension =
            WireMockExtension
                .newInstance()
                .options(wireMockConfig().dynamicPort())
                .build()

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            fixture = TestUtils.generateRandomBinary()
            asset =
                GitHubAsset(
                    name = "toto.bin",
                    digest = fixture.digest,
                    browserDownloadUrl = "${wireMock.baseUrl()}/toto.bin",
                    size = fixture.bytes.size.toLong(),
                    updatedAt = Instant.now(),
                )
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            FileSystemUtils.deleteRecursively(dir)
        }
    }

    @MockitoBean
    private lateinit var initializer: IDatabaseInitializer

    @MockitoBean
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @Autowired
    private lateinit var downloader: IAssetDownloader

    @Test
    fun downloadAsset() {
        wireMock.stubFor(
            WireMock
                .get("/toto.bin")
                .willReturn(
                    WireMock
                        .aResponse()
                        .withBody(fixture.bytes)
                        .withHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE),
                ),
        )

        StepVerifier
            .create(downloader.downloadAsset(asset))
            .assertNext {
                assertThat(it.digest).isEqualTo(fixture.digest)
                assertThat(it.path.fileName.toString()).isEqualTo("toto.bin")
            }.verifyComplete()
    }

    @Test // JaCoCo branch
    fun `init with token`() {
        AssetDownloader(
            WebClient.builder(),
            GeoLocationProperties().apply {
                directory = Path(System.getProperty("java.io.tmpdir"))
                githubApiBaseUrl = "http://localhost:1"
                githubRepo = "noop/noop"
                githubToken = "noop"
            },
        )
    }
}

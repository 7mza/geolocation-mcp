package com.hamza.mcp.geolocation

import com.maxmind.geoip2.DatabaseReader
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toMono
import reactor.test.StepVerifier
import java.io.IOException
import java.nio.file.Path
import java.time.Instant

class DatabaseInitializerTest {
    @TempDir
    private lateinit var tempDir: Path

    private val client = mock<IGithubReleaseClient>()
    private val downloader = mock<IAssetDownloader>()
    private val repository = mock<IManifestRepository>()
    private val properties = mock<GeoLocationProperties>()
    private val initializer =
        DatabaseInitializer(
            githubReleaseClient = client,
            assetDownloader = downloader,
            manifestRepository = repository,
            properties = properties,
        )

    private val cityAsset =
        GitHubAsset(
            name = "GeoLite2-City.mmdb",
            size = 65655525L,
            digest = "sha256:2cd96caa302ec416f69f3c298497d52108b382f418522d66ad97ed0b9dde0f80",
            browserDownloadUrl = "https://example.com/GeoLite2-City.mmdb",
            updatedAt = Instant.parse("2026-06-04T05:56:15Z"),
        )

    private val asnAsset =
        GitHubAsset(
            name = "GeoLite2-ASN.mmdb",
            size = 12290045L,
            digest = "sha256:815d961f2424a09792fddf3b9bfadc000ad416b26de5123b8ce515a89c22923a",
            browserDownloadUrl = "https://example.com/GeoLite2-ASN.mmdb",
            updatedAt = Instant.parse("2026-06-04T05:56:13Z"),
        )

    private val release = GitHubRelease(tagName = "2026.06.04", assets = listOf(cityAsset, asnAsset))

    private val manifest =
        DbManifest(
            tagName = "2026.06.04",
            downloadedAt = Instant.parse("2026-06-04T11:31:15.054460551Z"),
            assets = listOf(cityAsset, asnAsset),
        )

    @Test
    fun `skip download when manifest is present`() {
        whenever(repository.read()).thenReturn(manifest.toMono())
        whenever(properties.directory).thenReturn(tempDir)

        // verifyError because no real DBs in these unit tests, real integration test elsewhere
        StepVerifier.create(initializer.initialize()).verifyError(IOException::class.java)

        // client never pull release from GitHub
        verify(client, never()).pullRelease()
        // downloader never download asset from GitHub
        verify(downloader, never()).downloadAsset(any())
        // repository never writes manifest
        verify(repository, never()).write(any())
    }

    @Test
    fun `downloads when manifest is not present`() {
        whenever(repository.read()).thenReturn(Mono.empty())
        whenever(properties.directory).thenReturn(tempDir)
        whenever(client.pullRelease()).thenReturn(release.toMono())
        whenever(downloader.downloadAsset(cityAsset))
            .thenReturn(DownloadResult(tempDir.resolve(cityAsset.name), cityAsset.digest).toMono())
        whenever(downloader.downloadAsset(asnAsset))
            .thenReturn(DownloadResult(tempDir.resolve(asnAsset.name), asnAsset.digest).toMono())
        whenever(repository.write(any())).thenReturn(Mono.empty())

        // verifyError because no real DBs in these unit tests, real integration test elsewhere
        StepVerifier.create(initializer.initialize()).verifyError(IOException::class.java)

        // client pulled release from GitHub
        verify(client).pullRelease()
        // downloader pulled assets from GitHub
        verify(downloader).downloadAsset(cityAsset)
        verify(downloader).downloadAsset(asnAsset)
        // repository wrote manifest
        verify(repository).write(any<DbManifest>())
    }

    @Test
    fun `IllegalStateException on digest mismatch`() {
        whenever(repository.read()).thenReturn(Mono.empty())
        whenever(properties.directory).thenReturn(tempDir)
        whenever(client.pullRelease()).thenReturn(release.toMono())
        whenever(downloader.downloadAsset(any()))
            .thenReturn(DownloadResult(tempDir.resolve("corrupt.mmdb"), "sha256:wrongdigest").toMono())

        StepVerifier
            .create(initializer.initialize())
            .verifyErrorMatches { it is IllegalStateException && it.message!!.contains("digest mismatch") }
    }

    @Test
    fun `close readers`() {
        val city = mock<DatabaseReader>()
        val asn = mock<DatabaseReader>()

        initializer.cityReader.set(city)
        initializer.asnReader.set(asn)

        initializer.close()

        verify(city).close()
        verify(asn).close()
    }

    @Test
    fun `close when readers are not initialized`() {
        initializer.close()
    }
}

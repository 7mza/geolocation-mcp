package com.hamza.mcp.geolocation

import com.hamza.mcp.geolocation.models.DbManifest
import com.hamza.mcp.geolocation.models.GitHubAsset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.util.FileSystemUtils
import reactor.test.StepVerifier
import java.nio.file.Files
import java.time.Instant
import kotlin.io.path.Path
import kotlin.io.path.deleteIfExists

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [$$"geolocation.directory=${java.io.tmpdir}/geolocation-mcp_2"],
)
class ManifestRepositoryTest {
    companion object {
        private val dir = Path(System.getProperty("java.io.tmpdir"), "geolocation-mcp_2")

        @BeforeAll
        @JvmStatic
        fun beforeAll() {
            Files.createDirectories(dir)
            listOf("titi", "tata").forEach {
                dir.resolve(it).let { path -> Files.write(path, TestUtils.generateRandomBinary().bytes) }
            }
        }

        @AfterAll
        @JvmStatic
        fun afterAll() {
            FileSystemUtils.deleteRecursively(dir)
        }
    }

    @AfterEach
    fun afterEach() {
        dir.resolve("manifest.json").deleteIfExists()
    }

    @MockitoBean
    private lateinit var initializer: IDatabaseInitializer

    @MockitoBean
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @Autowired
    private lateinit var repository: IManifestRepository

    private val manifest =
        DbManifest(
            tagName = "toto",
            downloadedAt = Instant.now(),
            assets =
                listOf(
                    GitHubAsset(
                        name = "titi",
                        size = 1024,
                        digest = "titititi",
                        browserDownloadUrl = "google.fr",
                        updatedAt = Instant.now().plusSeconds(60),
                    ),
                    GitHubAsset(
                        name = "tata",
                        size = 2048,
                        digest = "tatatata",
                        browserDownloadUrl = "yahoo.fr",
                        updatedAt = Instant.now().plusSeconds(120),
                    ),
                ),
        )

    @Test
    fun read() {
        StepVerifier
            .create(repository.write(manifest).then(repository.read()))
            .assertNext { assertThat(it).isEqualTo(manifest) }
            .verifyComplete()
    }

    @Test
    fun `read with non existing json should not throw exception`() {
        StepVerifier
            .create(repository.read())
            .verifyComplete()
    }
}

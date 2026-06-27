package com.hamza.mcp.geolocation

import com.maxmind.db.CHMCache
import com.maxmind.geoip2.DatabaseReader
import jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy
import org.slf4j.LoggerFactory
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DataBufferUtils
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToFlux
import org.springframework.web.reactive.function.client.bodyToMono
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.onErrorResume
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import java.nio.file.Files
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

interface IGithubReleaseClient {
    fun pullRelease(): Mono<GitHubRelease>
}

@Component
class GithubReleaseClient(
    builder: WebClient.Builder,
    private val properties: GeoLocationProperties,
) : IGithubReleaseClient {
    private val client =
        builder
            .baseUrl(properties.githubApiBaseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .let {
                if (properties.githubToken.isNotBlank()) {
                    it.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.githubToken}")
                } else {
                    it
                }
            }.build()

    override fun pullRelease() =
        client
            .get()
            .uri("repos/${properties.githubRepo}/releases/latest")
            .retrieve()
            .bodyToMono<GitHubRelease>()
}

interface IAssetDownloader {
    fun downloadAsset(asset: GitHubAsset): Mono<DownloadResult>
}

@Component //  FIXME: combine with IGithubReleaseClient
class AssetDownloader(
    builder: WebClient.Builder,
    private val properties: GeoLocationProperties,
) : IAssetDownloader {
    private val client =
        builder
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_OCTET_STREAM_VALUE)
            .let {
                if (properties.githubToken.isNotBlank()) {
                    it.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer ${properties.githubToken}")
                } else {
                    it
                }
            }.build()

    override fun downloadAsset(asset: GitHubAsset): Mono<DownloadResult> {
        val target = properties.directory.resolve(asset.name)
        val md = MessageDigest.getInstance("SHA-256")
        return Mono
            .fromCallable { Files.createDirectories(target.parent) }
            .subscribeOn(Schedulers.boundedElastic())
            .then(
                client
                    .get()
                    .uri(asset.browserDownloadUrl)
                    .retrieve()
                    .bodyToFlux<DataBuffer>()
                    .doOnNext { it.readableByteBuffers().forEach { buf -> md.update(buf) } }
                    .let { DataBufferUtils.write(it, target) }
                    .then(Mono.fromCallable { DownloadResult(target, "sha256:${md.digest().toHexString()}") }),
            )
    }
}

interface IManifestRepository {
    fun read(): Mono<DbManifest>

    fun write(manifest: DbManifest): Mono<Void>
}

@Component
class ManifestRepository(
    properties: GeoLocationProperties,
    private val objectMapper: ObjectMapper,
) : IManifestRepository {
    private val manifestPath = properties.directory.resolve("manifest.json")

    override fun read() =
        Mono
            .fromCallable { Files.readAllBytes(manifestPath) }
            .subscribeOn(Schedulers.boundedElastic())
            .onErrorResume(NoSuchFileException::class) { Mono.empty() }
            .map { objectMapper.readValue<DbManifest>(it) }
            // verify that manifest + files it references exists = successful download
            .filter { manifest -> manifest.assets.all { Files.exists(manifestPath.parent.resolve(it.name)) } }

    override fun write(manifest: DbManifest) =
        Mono
            .fromCallable {
                Files.createDirectories(manifestPath.parent)
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(manifestPath.toFile(), manifest)
            }.subscribeOn(Schedulers.boundedElastic())
            .then()
}

interface IDatabaseInitializer {
    val cityReader: AtomicReference<DatabaseReader>
    val asnReader: AtomicReference<DatabaseReader>

    fun initialize(): Mono<Void>
}

@Component
class DatabaseInitializer(
    private val githubReleaseClient: IGithubReleaseClient,
    private val assetDownloader: IAssetDownloader,
    private val manifestRepository: IManifestRepository,
    private val properties: GeoLocationProperties,
) : IDatabaseInitializer {
    override val cityReader = AtomicReference<DatabaseReader>()
    override val asnReader = AtomicReference<DatabaseReader>()

    @PostConstruct
    fun init() {
        initialize().block()
    }

    override fun initialize() =
        manifestRepository
            .read()
            .switchIfEmpty {
                githubReleaseClient
                    .pullRelease()
                    .flatMap { release ->
                        val city = release.assets.first { it.name == "GeoLite2-City.mmdb" }
                        val asn = release.assets.first { it.name == "GeoLite2-ASN.mmdb" }
                        Mono
                            .zip(
                                assetDownloader.downloadAsset(city).flatMap { verify(it, city) },
                                assetDownloader.downloadAsset(asn).flatMap { verify(it, asn) },
                            ).flatMap {
                                val manifest = DbManifest(release.tagName, Instant.now(), listOf(city, asn))
                                manifestRepository.write(manifest).thenReturn(manifest)
                            }
                    }
            }.flatMap {
                Mono
                    .zip(
                        loadReader(properties.directory.resolve("GeoLite2-City.mmdb")),
                        loadReader(properties.directory.resolve("GeoLite2-ASN.mmdb")),
                    ).doOnNext { (city, asn) ->
                        cityReader.set(city)
                        asnReader.set(asn)
                    }
            }.then()

    private fun loadReader(path: Path): Mono<DatabaseReader> =
        Mono
            .fromCallable { DatabaseReader.Builder(path.toFile()).withCache(CHMCache()).build() }
            .subscribeOn(Schedulers.boundedElastic())

    private fun verify(
        result: DownloadResult,
        asset: GitHubAsset,
    ): Mono<Path> =
        if (result.digest == asset.digest) {
            result.path.toMono()
        } else {
            Mono
                .fromCallable { Files.deleteIfExists(result.path) }
                .subscribeOn(Schedulers.boundedElastic())
                .then(IllegalStateException("digest mismatch for ${asset.name}").toMono())
        }

    @PreDestroy
    fun close() {
        cityReader.get()?.close()
        asnReader.get()?.close()
    }
}

interface ITorExitNodeClient {
    fun pullExitNodes(): Mono<HashSet<String>>
}

@Component
class TorExitNodeClient(
    builder: WebClient.Builder,
    properties: GeoLocationProperties,
) : ITorExitNodeClient {
    private val client =
        builder
            .baseUrl(properties.torBaseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
            .build()

    override fun pullExitNodes(): Mono<HashSet<String>> =
        client
            .get()
            .uri("torbulkexitlist")
            .retrieve()
            .bodyToMono<String>()
            .map { body ->
                body
                    .lines()
                    .filterNot { it.startsWith("#") || it.isBlank() }
                    .toHashSet()
            }
}

interface ITorExitNodeRepository {
    val exitNodes: AtomicReference<HashSet<String>>

    fun isTorExitNode(ip: String): Boolean

    fun refresh(): Mono<Void>
}

@Component
class TorExitNodeRepository(
    private val client: ITorExitNodeClient,
) : ITorExitNodeRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override val exitNodes = AtomicReference(HashSet<String>())

    @PostConstruct
    fun init() {
        refresh().block()
    }

    @Scheduled(cron = $$"${geolocation.torRefreshCron}")
    fun scheduledRefresh() {
        refresh().block()
    }

    override fun isTorExitNode(ip: String) = exitNodes.get().contains(ip)

    override fun refresh(): Mono<Void> =
        client
            .pullExitNodes()
            .doOnNext {
                logger.info("tor exit node list updated: {} nodes", it.size)
                exitNodes.set(it)
            }.doOnError { logger.warn("failed to load tor exit nodes: {}", it.message) }
            .onErrorComplete()
            .then()
}

interface IX4BNetClient {
    fun pullVpnRanges(): Mono<List<Pair<Long, Long>>>

    fun pullDatacenterRanges(): Mono<List<Pair<Long, Long>>>
}

@Component
class X4BNetClient(
    builder: WebClient.Builder,
    properties: GeoLocationProperties,
) : IX4BNetClient {
    private val client =
        builder
            .baseUrl(properties.x4bNetBaseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.TEXT_PLAIN_VALUE)
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) } // large txt files inc buf to 2MB
            .build()

    override fun pullVpnRanges() = fetchRanges("/X4BNet/lists_vpn/main/output/vpn/ipv4.txt")

    override fun pullDatacenterRanges() = fetchRanges("/X4BNet/lists_vpn/main/output/datacenter/ipv4.txt")

    private fun fetchRanges(path: String) =
        client
            .get()
            .uri(path)
            .retrieve()
            .bodyToMono<String>()
            .map { body ->
                body
                    .lines()
                    .filterNot { it.startsWith("#") || it.isBlank() }
                    .map { it.cidrToRange() }
                    .sortedBy { it.first }
            }
}

interface IX4BNetRepository {
    val vpnRanges: AtomicReference<List<Pair<Long, Long>>>
    val datacenterRanges: AtomicReference<List<Pair<Long, Long>>>

    fun isVpn(ip: String): Boolean

    fun isDatacenter(ip: String): Boolean

    fun refresh(): Mono<Void>
}

@Component
class X4BNetRepository(
    private val client: IX4BNetClient,
) : IX4BNetRepository {
    private val logger = LoggerFactory.getLogger(javaClass)

    override val vpnRanges = AtomicReference(emptyList<Pair<Long, Long>>())
    override val datacenterRanges = AtomicReference(emptyList<Pair<Long, Long>>())

    @PostConstruct
    fun init() {
        refresh().block()
    }

    @Scheduled(cron = $$"${geolocation.x4bNetRefreshCron}")
    fun scheduledRefresh() {
        refresh().block()
    }

    override fun isVpn(ip: String) = ip.toIpv4LongOrNull()?.let { vpnRanges.get().containsIp(it) } ?: false

    override fun isDatacenter(ip: String) = ip.toIpv4LongOrNull()?.let { datacenterRanges.get().containsIp(it) } ?: false

    override fun refresh() =
        Mono
            .`when`(
                client
                    .pullVpnRanges()
                    .doOnNext {
                        vpnRanges.set(it)
                        logger.info("vpn ranges updated: {} entries", it.size)
                    }.doOnError { logger.warn("failed to load vpn ranges: {}", it.message) }
                    .onErrorComplete(),
                client
                    .pullDatacenterRanges()
                    .doOnNext {
                        datacenterRanges.set(it)
                        logger.info("datacenter ranges updated: {} entries", it.size)
                    }.doOnError { logger.warn("failed to load datacenter ranges: {}", it.message) }
                    .onErrorComplete(),
            )
}

internal fun String.toIpv4LongOrNull(): Long? {
    val parts = split(".")
    if (parts.size != 4) return null
    return parts.fold(0L) { acc, part -> acc shl 8 or (part.toLongOrNull() ?: return null) }
}

internal fun String.cidrToRange(): Pair<Long, Long> {
    val (ip, prefix) = split("/")
    val start = ip.toIpv4LongOrNull()!!
    val size = 1L shl (32 - prefix.toInt())
    return start to start + size - 1
}

internal fun List<Pair<Long, Long>>.containsIp(ipLong: Long): Boolean {
    if (isEmpty()) return false
    var lo = 0
    var hi = size - 1
    var result = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (this[mid].first <= ipLong) {
            result = mid
            lo = mid + 1
        } else {
            hi = mid - 1
        }
    }
    return result >= 0 && ipLong <= this[result].second
}

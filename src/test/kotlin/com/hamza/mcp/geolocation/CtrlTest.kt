package com.hamza.mcp.geolocation

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers.anyList
import org.mockito.ArgumentMatchers.anyString
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.webflux.test.autoconfigure.WebFluxTest
import org.springframework.http.MediaType
import org.springframework.http.server.RequestPath
import org.springframework.http.server.reactive.ServerHttpRequest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.test.web.reactive.server.expectBody
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.server.ServerWebExchange
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.reflect.full.memberProperties

@WebFluxTest(controllers = [Ctrl::class])
class CtrlTest {
    init {
        // reflection warm up, for BlockHound not to trigger on 1st request
        emptyList<DbManifest>()::class.memberProperties
    }

    @MockitoBean
    private lateinit var service: IGeoLocationService

    @MockitoBean
    private lateinit var repository: IManifestRepository

    @Autowired
    private lateinit var client: WebTestClient

    private val data =
        GeoLocationData(
            city =
                CityData(
                    name = "Minneapolis",
                    isoCode = "MN",
                    latitude = 44.9733,
                    longitude = -93.2323,
                    postalCode = "55455",
                ),
            country =
                CountryData(
                    name = "United States",
                    isoCode = "US",
                ),
            asn =
                AsnData(
                    autonomousSystemNumber = 217,
                    autonomousSystemOrganization = "University of Minnesota",
                    ipAddress = "128.101.101.101",
                    hostAddress = "128.101.0.0",
                    prefixLength = 16,
                ),
        )

    private val asset =
        GitHubAsset(
            name = "GeoLite2-ASN.mmdb",
            size = 12290045L,
            digest = "sha256:815d961f2424a09792fddf3b9bfadc000ad416b26de5123b8ce515a89c22923a",
            browserDownloadUrl = "https://example.com/GeoLite2-ASN.mmdb",
            updatedAt = Instant.parse("2026-06-04T05:56:13Z"),
        )

    private val manifest =
        DbManifest(
            tagName = "2026.06.04",
            downloadedAt = Instant.parse("2026-06-04T11:31:15.054460551Z"),
            assets = listOf(asset),
        )

    @Test
    fun geoLocation() {
        whenever(service.geoLocation(anyString())).thenReturn(data.toMono())

        val response: GeoLocationData? =
            client
                .get()
                .uri("/api/128.101.101.101")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<GeoLocationData>()
                .returnResult()
                .responseBody
        assertThat(response).isEqualTo(data)
    }

    @ParameterizedTest
    @ValueSource(strings = ["pp.rr.11.0", "blabla"])
    fun `geoLocation returns 400 for invalid ips`(ip: String) {
        val body =
            client
                .get()
                .uri("/api/$ip")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isBadRequest()
                .expectBody<Map<String, Any>>()
                .returnResult()
                .responseBody!!

        assertThat(body["message"]).isEqualTo("must be valid IPv4 or IPv6 address")

        verify(service, never()).geoLocation(anyString())
    }

    @Test
    fun `geoLocationBulk all success`() {
        val results =
            listOf(
                GeoLocationBulk(ip = "128.101.101.101", data = data),
                GeoLocationBulk(ip = "8.8.8.8", data = data.copy(asn = data.asn?.copy(ipAddress = "8.8.8.8"))),
            )
        whenever(service.geoLocations(anyList())).thenReturn(results.toFlux())

        val response =
            client
                .post()
                .uri("/api/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(listOf("128.101.101.101", "8.8.8.8"))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<List<GeoLocationBulk>>()
                .returnResult()
                .responseBody!!

        assertThat(response).hasSize(2)
        assertThat(response).allMatch { it.error == null && it.data != null }
        assertThat(response).anyMatch { it.ip == "128.101.101.101" }
        assertThat(response).anyMatch { it.ip == "8.8.8.8" }
    }

    @Test
    fun `geoLocationBulk some success some fail`() {
        val results =
            listOf(
                GeoLocationBulk(ip = "128.101.101.101", data = data),
                GeoLocationBulk(ip = "0.0.0.0", error = "The address 0.0.0.0 is not in the database."),
            )
        whenever(service.geoLocations(anyList())).thenReturn(results.toFlux())

        val response =
            client
                .post()
                .uri("/api/bulk")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(listOf("128.101.101.101", "0.0.0.0"))
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<List<GeoLocationBulk>>()
                .returnResult()
                .responseBody!!

        assertThat(response).hasSize(2)
        assertThat(response).anyMatch { it.ip == "128.101.101.101" && it.data != null && it.error == null }
        assertThat(response).anyMatch { it.ip == "0.0.0.0" && it.error != null && it.data == null }
    }

    @Test
    fun `geoLocation returns 500 when service throw exception`() {
        whenever(service.geoLocation(anyString()))
            .thenReturn(Mono.error(RuntimeException("error")))

        client
            .get()
            .uri("/api/128.101.101.101")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus()
            .is5xxServerError
    }

    @Test
    fun manifest() {
        whenever(repository.read()).thenReturn(manifest.toMono())

        val response: DbManifest? =
            client
                .get()
                .uri("/api/manifest")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<DbManifest>()
                .returnResult()
                .responseBody
        assertThat(response).isEqualTo(manifest)
    }

    @Test
    fun `manifest when repository return empty`() {
        whenever(repository.read()).thenReturn(Mono.empty())

        val response: DbManifest? =
            client
                .get()
                .uri("/api/manifest")
                .accept(MediaType.APPLICATION_JSON)
                .exchange()
                .expectStatus()
                .isOk
                .expectBody<DbManifest>()
                .returnResult()
                .responseBody
        assertThat(response!!.tagName).isEqualTo("none")
        assertThat(response.downloadedAt).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES))
        assertThat(response.assets).isEmpty()
    }

    @Test // JaCoCo branch
    fun `handleValidation falls back when no errors`() {
        val ex = mock<HandlerMethodValidationException>()
        val exchange = mock<ServerWebExchange>()
        val request = mock<ServerHttpRequest>()
        val path = mock<RequestPath>()
        whenever(ex.allErrors).thenReturn(emptyList())
        whenever(exchange.request).thenReturn(request)
        whenever(request.path).thenReturn(path)
        whenever(path.value()).thenReturn("/test")
        whenever(request.id).thenReturn("req")
        val result = ControllerAdvice().handleValidation(ex, exchange)
        assertThat(result["message"]).isEqualTo("Validation failed")
    }
}

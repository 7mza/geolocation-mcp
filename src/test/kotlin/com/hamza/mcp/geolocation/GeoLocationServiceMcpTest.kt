package com.hamza.mcp.geolocation

import com.maxmind.db.Network
import com.maxmind.geoip2.DatabaseReader
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.AsnResponse
import com.maxmind.geoip2.model.CityResponse
import com.maxmind.geoip2.record.City
import com.maxmind.geoip2.record.Continent
import com.maxmind.geoip2.record.Country
import com.maxmind.geoip2.record.Location
import com.maxmind.geoip2.record.MaxMind
import com.maxmind.geoip2.record.Postal
import com.maxmind.geoip2.record.RepresentedCountry
import com.maxmind.geoip2.record.Subdivision
import com.maxmind.geoip2.record.Traits
import io.modelcontextprotocol.client.McpClient
import io.modelcontextprotocol.spec.McpSchema
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.ai.mcp.client.webflux.transport.WebClientStreamableHttpTransport
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.springframework.web.reactive.function.client.WebClient
import reactor.test.StepVerifier
import tools.jackson.databind.ObjectMapper
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicReference

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeoLocationServiceMcpTest {
    @LocalServerPort
    private var port: Int = 0

    @MockitoBean
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @MockitoBean
    private lateinit var initializer: IDatabaseInitializer

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private val mockCityReader = mock<DatabaseReader>()
    private val mockAsnReader = mock<DatabaseReader>()

    private val mcpClient by lazy {
        McpClient
            .async(
                WebClientStreamableHttpTransport
                    .builder(WebClient.builder().baseUrl("http://localhost:$port"))
                    .endpoint("/mcp")
                    .build(),
            ).build()
    }

    @BeforeEach
    fun setupMocks() {
        whenever(initializer.cityReader).thenReturn(AtomicReference(mockCityReader))
        whenever(initializer.asnReader).thenReturn(AtomicReference(mockAsnReader))

        val locales = listOf("en")

        val minneapolisCityResponse =
            CityResponse(
                City(locales, null, null, mapOf("en" to "Minneapolis")),
                Continent(locales, "NA", null, mapOf("en" to "North America")),
                Country(locales, null, null, false, "US", mapOf("en" to "United States")),
                Location(20, null, 44.9733, -93.2323, null, "America/Chicago"),
                MaxMind(),
                Postal("55455", null),
                Country(),
                RepresentedCountry(),
                listOf(Subdivision(locales, null, null, "MN", emptyMap())),
                Traits(),
            )
        val minneapolisAsnResponse =
            AsnResponse(
                217L,
                "University of Minnesota",
                InetAddress.getByName("128.101.101.101"),
                Network(InetAddress.getByName("128.101.101.101"), 16),
            )
        whenever(mockCityReader.city(InetAddress.getByName("128.101.101.101"))).thenReturn(minneapolisCityResponse)
        whenever(mockAsnReader.asn(InetAddress.getByName("128.101.101.101"))).thenReturn(minneapolisAsnResponse)

        val googleCityResponse =
            CityResponse(
                City(),
                Continent(),
                Country(locales, null, null, false, "US", emptyMap()),
                Location(),
                MaxMind(),
                Postal(),
                Country(),
                RepresentedCountry(),
                emptyList(),
                Traits(),
            )
        val googleAsnResponse =
            AsnResponse(
                null,
                null,
                InetAddress.getByName("8.8.8.8"),
                Network(InetAddress.getByName("8.8.8.8"), 15),
            )
        whenever(mockCityReader.city(InetAddress.getByName("8.8.8.8"))).thenReturn(googleCityResponse)
        whenever(mockAsnReader.asn(InetAddress.getByName("8.8.8.8"))).thenReturn(googleAsnResponse)

        whenever(mockCityReader.city(InetAddress.getByName("0.0.0.0")))
            .thenThrow(AddressNotFoundException("no data for 0.0.0.0"))
    }

    @AfterEach
    fun afterEach() {
        mcpClient.closeGracefully().block()
    }

    @ParameterizedTest
    @ValueSource(strings = ["getGeoLocationData", "getGeoLocationDataBulk"])
    fun `mcp tools should expose tool`(toolName: String) {
        StepVerifier
            .create(mcpClient.initialize().then(mcpClient.listTools()))
            .assertNext { result ->
                assertThat(result.tools()).anyMatch { it.name() == toolName }
            }.verifyComplete()
    }

    @Test
    fun `getGeoLocationData tool should return correct GeoLocationData`() {
        StepVerifier
            .create(
                mcpClient
                    .initialize()
                    .then(
                        mcpClient
                            .callTool(
                                McpSchema.CallToolRequest(
                                    "getGeoLocationData",
                                    mapOf("ip" to "128.101.101.101"),
                                    null,
                                ),
                            ),
                    ),
            ).assertNext {
                assertThat(it.isError).isFalse
                assertThat(it.content()).isNotEmpty
                val text = (it.content().first() as McpSchema.TextContent).text()
                val data = TestUtils.parseJson<GeoLocationData>(text, objectMapper)
                assertThat(data.isDatacenter).isFalse
                assertThat(data.isVpn).isFalse
                assertThat(data.isTorExitNode).isFalse
                assertThat(data.city?.name).isEqualTo("Minneapolis")
                assertThat(data.city?.isoCode).isEqualTo("MN")
                assertThat(data.city?.latitude).isCloseTo(44.9733, within(0.1))
                assertThat(data.city?.longitude).isCloseTo(-93.2323, within(0.1))
                assertThat(data.city?.accuracyRadius).isEqualTo("20 km")
                assertThat(data.city?.postalCode).isEqualTo("55455")
                assertThat(data.city?.timeZone).isEqualTo("America/Chicago")
                assertThat(data.country?.name).isEqualTo("United States")
                assertThat(data.country?.isoCode).isEqualTo("US")
                assertThat(data.country?.isInEuropeanUnion).isFalse
                assertThat(data.continent?.name).isEqualTo("North America")
                assertThat(data.continent?.code).isEqualTo("NA")
                assertThat(data.asn?.autonomousSystemNumber).isEqualTo(217)
                assertThat(data.asn?.autonomousSystemOrganization).isEqualTo("University of Minnesota")
                assertThat(data.asn?.ipAddress).isEqualTo("128.101.101.101")
                assertThat(data.asn?.hostAddress).isEqualTo("128.101.0.0")
                assertThat(data.asn?.prefixLength).isEqualTo(16)
            }.verifyComplete()
    }

    @Test
    fun `getGeoLocationDataBulk tool all success`() {
        StepVerifier
            .create(
                mcpClient
                    .initialize()
                    .then(
                        mcpClient.callTool(
                            McpSchema.CallToolRequest(
                                "getGeoLocationDataBulk",
                                mapOf("ips" to listOf("128.101.101.101", "8.8.8.8")),
                                null,
                            ),
                        ),
                    ),
            ).assertNext { result ->
                assertThat(result.isError).isFalse
                val text = (result.content().first() as McpSchema.TextContent).text()
                val data = TestUtils.parseJson<GeoLocationBulkResult>(text, objectMapper).results
                assertThat(data).hasSize(2)
                assertThat(data).allMatch { it.error == null && it.data != null }
                assertThat(data).anyMatch { it.ip == "128.101.101.101" && it.data?.city?.name == "Minneapolis" }
                assertThat(data).anyMatch { it.ip == "8.8.8.8" && it.data?.country?.isoCode == "US" }
            }.verifyComplete()
    }

    @Test
    fun `getGeoLocationDataBulk tool some success some fail`() {
        StepVerifier
            .create(
                mcpClient
                    .initialize()
                    .then(
                        mcpClient.callTool(
                            McpSchema.CallToolRequest(
                                "getGeoLocationDataBulk",
                                mapOf("ips" to listOf("128.101.101.101", "0.0.0.0")),
                                null,
                            ),
                        ),
                    ),
            ).assertNext { result ->
                assertThat(result.isError).isFalse
                val text = (result.content().first() as McpSchema.TextContent).text()
                val data = TestUtils.parseJson<GeoLocationBulkResult>(text, objectMapper).results
                assertThat(data).hasSize(2)
                assertThat(data).anyMatch {
                    it.ip == "128.101.101.101" && it.data?.city?.name == "Minneapolis" && it.error == null
                }
                assertThat(data).anyMatch { it.ip == "0.0.0.0" && it.error != null && it.data == null }
            }.verifyComplete()
    }
}

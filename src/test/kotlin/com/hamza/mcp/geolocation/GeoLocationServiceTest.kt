package com.hamza.mcp.geolocation

import com.hamza.mcp.geolocation.models.AsnData
import com.hamza.mcp.geolocation.models.CityData
import com.hamza.mcp.geolocation.models.ContinentData
import com.hamza.mcp.geolocation.models.CountryData
import com.hamza.mcp.geolocation.models.GeoLocationBulk
import com.hamza.mcp.geolocation.models.GeoLocationBulkResult
import com.hamza.mcp.geolocation.models.GeoLocationData
import com.hamza.mcp.geolocation.models.toCityData
import com.maxmind.geoip2.exception.AddressNotFoundException
import com.maxmind.geoip2.model.CityResponse
import com.maxmind.geoip2.record.Location
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.bean.override.mockito.MockitoBean
import reactor.test.StepVerifier
import java.net.UnknownHostException

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GeoLocationServiceTest {
    @MockitoBean
    private lateinit var torRepository: ITorExitNodeRepository

    @MockitoBean
    private lateinit var x4bNetRepository: IX4BNetRepository

    @Autowired
    private lateinit var service: IGeoLocationService

    @Test
    fun city() {
        StepVerifier
            .create(service.city("128.101.101.101"))
            .assertNext {
                assertThat(it.name).isEqualTo("Minneapolis")
                assertThat(it.isoCode).isEqualTo("MN")
                assertThat(it.latitude).isCloseTo(44.9733, within(0.1))
                assertThat(it.longitude).isCloseTo(-93.2323, within(0.1))
                assertThat(it.accuracyRadius).isEqualTo("20 km")
                assertThat(it.postalCode).isEqualTo("55455")
                assertThat(it.timeZone).isEqualTo("America/Chicago")
            }.verifyComplete()
    }

    @Test
    fun country() {
        StepVerifier
            .create(service.country("128.101.101.101"))
            .assertNext {
                assertThat(it.name).isEqualTo("United States")
                assertThat(it.isoCode).isEqualTo("US")
                assertThat(it.isInEuropeanUnion).isFalse
            }.verifyComplete()
    }

    @Test
    fun asn() {
        StepVerifier
            .create(service.asn("128.101.101.101"))
            .assertNext {
                assertThat(it.autonomousSystemNumber).isEqualTo(217)
                assertThat(it.autonomousSystemOrganization).isEqualTo("University of Minnesota")
                assertThat(it.ipAddress).isEqualTo("128.101.101.101")
                assertThat(it.hostAddress).isEqualTo("128.101.0.0")
                assertThat(it.prefixLength).isEqualTo(16)
            }.verifyComplete()
    }

    @Test
    fun geoLocation() {
        StepVerifier
            .create(service.geoLocation("128.101.101.101"))
            .assertNext {
                assertThat(it.isDatacenter).isFalse
                assertThat(it.isTorExitNode).isFalse
                assertThat(it.isVpn).isFalse
                assertThat(it.city?.name).isEqualTo("Minneapolis")
                assertThat(it.city?.isoCode).isEqualTo("MN")
                assertThat(it.city?.latitude).isCloseTo(44.9733, within(0.1))
                assertThat(it.city?.longitude).isCloseTo(-93.2323, within(0.1))
                assertThat(it.city?.accuracyRadius).isEqualTo("20 km")
                assertThat(it.city?.postalCode).isEqualTo("55455")
                assertThat(it.city?.timeZone).isEqualTo("America/Chicago")
                assertThat(it.country?.name).isEqualTo("United States")
                assertThat(it.country?.isoCode).isEqualTo("US")
                assertThat(it.country?.isInEuropeanUnion).isFalse
                assertThat(it.continent?.name).isEqualTo("North America")
                assertThat(it.continent?.code).isEqualTo("NA")
                assertThat(it.asn?.autonomousSystemNumber).isEqualTo(217)
                assertThat(it.asn?.autonomousSystemOrganization).isEqualTo("University of Minnesota")
                assertThat(it.asn?.ipAddress).isEqualTo("128.101.101.101")
                assertThat(it.asn?.hostAddress).isEqualTo("128.101.0.0")
                assertThat(it.asn?.prefixLength).isEqualTo(16)
            }.verifyComplete()
    }

    @Test
    fun `geoLocation with valid ip but not in database`() {
        StepVerifier
            .create(service.geoLocation("0.0.0.0"))
            .verifyError(AddressNotFoundException::class.java)
    }

    @Test
    fun `geoLocation with non valid ip`() {
        StepVerifier
            .create(service.geoLocation("blabla"))
            .verifyError(UnknownHostException::class.java)
    }

    @Test
    fun `geoLocations all success`() {
        StepVerifier
            .create(service.geoLocations(listOf("128.101.101.101", "8.8.8.8")).collectList())
            .assertNext { results ->
                assertThat(results).hasSize(2)
                assertThat(results).allMatch { it.error == null && it.data != null }
                assertThat(results).anyMatch { it.ip == "128.101.101.101" && it.data?.city?.name == "Minneapolis" }
                assertThat(results).anyMatch { it.ip == "8.8.8.8" && it.data?.country?.isoCode == "US" }
            }.verifyComplete()
    }

    @Test
    fun `geoLocations some success some fail`() {
        StepVerifier
            .create(service.geoLocations(listOf("128.101.101.101", "0.0.0.0", "blabla")).collectList())
            .assertNext { results ->
                assertThat(results).hasSize(3)
                assertThat(results).anyMatch {
                    it.ip == "128.101.101.101" && it.data?.city?.name == "Minneapolis" && it.error == null
                }
                assertThat(results).anyMatch { it.ip == "0.0.0.0" && it.error != null && it.data == null }
                assertThat(results).anyMatch { it.ip == "blabla" && it.error != null && it.data == null }
            }.verifyComplete()
    }

    @Test // JaCoCo branch
    fun `init data classes`() {
        GeoLocationData()
        GeoLocationBulk("")
        GeoLocationBulkResult(emptyList())
        CityData()
        CountryData()
        ContinentData()
        AsnData()
    }

    @Test // JaCoCo branch
    fun `toCityData with null accuracyRadius`() {
        val response = mock<CityResponse>()
        val location = mock<Location>()
        whenever(response.city()).thenReturn(mock())
        whenever(response.mostSpecificSubdivision()).thenReturn(mock())
        whenever(response.location()).thenReturn(location)
        whenever(response.postal()).thenReturn(mock())
        whenever(location.accuracyRadius()).thenReturn(null)
        val result = response.toCityData()
        assertThat(result.accuracyRadius).isNull()
    }
}

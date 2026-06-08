package com.hamza.mcp.geolocation.models

import com.maxmind.geoip2.model.AsnResponse
import com.maxmind.geoip2.model.CityResponse
import tools.jackson.databind.PropertyNamingStrategies
import tools.jackson.databind.annotation.JsonNaming
import java.nio.file.Path
import java.time.Instant

data class GeoLocationData(
    val isDatacenter: Boolean = false,
    val isTorExitNode: Boolean = false,
    val isVpn: Boolean = false,
    val city: CityData? = null,
    val country: CountryData? = null,
    val continent: ContinentData? = null,
    val asn: AsnData? = null,
)

data class GeoLocationBulk(
    val ip: String,
    val data: GeoLocationData? = null,
    val error: String? = null,
)

data class GeoLocationBulkResult(
    val results: List<GeoLocationBulk>,
)

data class CityData(
    val name: String? = null,
    val isoCode: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val accuracyRadius: String? = null,
    val postalCode: String? = null,
    val timeZone: String? = null,
)

data class CountryData(
    val name: String? = null,
    val isoCode: String? = null,
    val isInEuropeanUnion: Boolean? = null,
)

data class ContinentData(
    val name: String? = null,
    val code: String? = null,
)

data class AsnData(
    val autonomousSystemNumber: Long? = null,
    val autonomousSystemOrganization: String? = null,
    val ipAddress: String? = null,
    val hostAddress: String? = null,
    val prefixLength: Int? = null,
)

fun CityResponse.toCityData() =
    CityData(
        name = this.city().name(),
        isoCode = this.mostSpecificSubdivision().isoCode(),
        latitude = this.location().latitude(),
        longitude = this.location().longitude(),
        accuracyRadius = this.location().accuracyRadius()?.let { "$it km" },
        postalCode = this.postal().code(),
        timeZone = this.location().timeZone(),
    )

fun CityResponse.toCountryData() =
    CountryData(
        name = this.country().name(),
        isoCode = this.country().isoCode(),
        isInEuropeanUnion = this.country().isInEuropeanUnion,
    )

fun CityResponse.toContinentData() =
    ContinentData(
        name = this.continent().name(),
        code = this.continent().code(),
    )

fun AsnResponse.toAsnData() =
    AsnData(
        autonomousSystemOrganization = this.autonomousSystemOrganization(),
        autonomousSystemNumber = this.autonomousSystemNumber(),
        ipAddress = this.ipAddress().hostAddress,
        hostAddress = this.network().networkAddress().hostAddress,
        prefixLength = this.network().prefixLength,
    )

fun CityResponse.toGeoLocationData() =
    GeoLocationData(
        city = this.toCityData(),
        country = this.toCountryData(),
        continent = this.toContinentData(),
    )

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GitHubRelease(
    val tagName: String,
    val assets: List<GitHubAsset>,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class GitHubAsset(
    val name: String,
    val size: Long,
    val digest: String,
    val browserDownloadUrl: String,
    val updatedAt: Instant,
)

data class DownloadResult(
    val path: Path,
    val digest: String,
)

data class DbManifest(
    val tagName: String,
    val downloadedAt: Instant,
    val assets: List<GitHubAsset>,
)

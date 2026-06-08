package com.hamza.mcp.geolocation

import com.hamza.mcp.geolocation.models.AsnData
import com.hamza.mcp.geolocation.models.CityData
import com.hamza.mcp.geolocation.models.CountryData
import com.hamza.mcp.geolocation.models.GeoLocationBulk
import com.hamza.mcp.geolocation.models.GeoLocationBulkResult
import com.hamza.mcp.geolocation.models.GeoLocationData
import com.hamza.mcp.geolocation.models.toAsnData
import com.hamza.mcp.geolocation.models.toCityData
import com.hamza.mcp.geolocation.models.toCountryData
import com.hamza.mcp.geolocation.models.toGeoLocationData
import org.springframework.ai.mcp.annotation.McpTool
import org.springframework.ai.mcp.annotation.McpToolParam
import org.springframework.stereotype.Service
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import reactor.kotlin.core.publisher.toFlux
import reactor.kotlin.core.publisher.toMono
import reactor.kotlin.core.util.function.component1
import reactor.kotlin.core.util.function.component2
import java.net.InetAddress

interface IGeoLocationService {
    fun city(ip: String): Mono<CityData>

    fun country(ip: String): Mono<CountryData>

    fun asn(ip: String): Mono<AsnData>

    fun geoLocation(ip: String): Mono<GeoLocationData>

    fun geoLocations(ips: List<String>): Flux<GeoLocationBulk>
}

@Service
class GeoLocationService(
    private val initializer: IDatabaseInitializer,
    private val torRepository: ITorExitNodeRepository,
    private val x4bNetRepository: IX4BNetRepository,
) : IGeoLocationService {
    override fun city(ip: String) =
        Mono
            .fromCallable {
                initializer
                    .cityReader
                    .get()
                    .city(InetAddress.getByName(ip))
                    .toCityData()
            }.subscribeOn(Schedulers.boundedElastic())

    override fun country(ip: String) =
        Mono
            .fromCallable {
                initializer
                    .cityReader
                    .get()
                    .city(InetAddress.getByName(ip))
                    .toCountryData()
            }.subscribeOn(Schedulers.boundedElastic())

    override fun asn(ip: String) =
        Mono
            .fromCallable {
                initializer
                    .asnReader
                    .get()
                    .asn(InetAddress.getByName(ip))
                    .toAsnData()
            }.subscribeOn(Schedulers.boundedElastic())

    @McpTool(
        name = "getGeoLocationData",
        title = "IP Geolocation Lookup",
        description = """
Returns geolocation data for a public IPv4 or IPv6 address, including:
- Flags: isDatacenter, isTorExitNode, isVPN
- City name, region ISO code, latitude/longitude, accuracy radius, postal code, timezone
- Country name, ISO code, EU membership
- Continent name and code
- ASN number, ISP/organization name, network CIDR

Fails for private, loopback, or reserved IP ranges (e.g. 192.168.x.x, 127.0.0.1) as they are not present in the databases.
""",
        generateOutputSchema = true,
        annotations =
            McpTool.McpAnnotations(
                readOnlyHint = true,
                destructiveHint = false,
                openWorldHint = false,
            ),
    )
    override fun geoLocation(
        @McpToolParam(description = "Public IPv4 (e.g. 8.8.8.8) or IPv6 (e.g. 2001:4860:4860::8888) address") ip: String,
    ) = Mono
        .zip(
            Mono
                .fromCallable {
                    initializer
                        .cityReader
                        .get()
                        .city(InetAddress.getByName(ip))
                        .toGeoLocationData()
                }.subscribeOn(Schedulers.boundedElastic()),
            this.asn(ip),
        ).map { (geoLocationData, asn) ->
            geoLocationData
                .copy(
                    asn = asn,
                    isDatacenter = x4bNetRepository.isDatacenter(ip),
                    isTorExitNode = torRepository.isTorExitNode(ip),
                    isVpn = x4bNetRepository.isVpn(ip),
                )
        }

    override fun geoLocations(ips: List<String>) =
        ips
            .toFlux()
            .flatMap { ip ->
                this
                    .geoLocation(ip)
                    .map { GeoLocationBulk(ip = ip, data = it) }
                    .onErrorResume { GeoLocationBulk(ip = ip, error = it.message).toMono() }
            }

    @McpTool(
        name = "getGeoLocationDataBulk",
        title = "Bulk IP Geolocation Lookup",
        description = """
Returns geolocation data for a list of public IPv4 or IPv6 addresses.
Each result contains the queried IP and either full geolocation data or an error message.
Failed lookups (e.g. private/reserved IPs) do not affect the rest of the results, they appear with a null `data` and a populated `error` field.
""",
        generateOutputSchema = true,
        annotations =
            McpTool.McpAnnotations(
                readOnlyHint = true,
                destructiveHint = false,
                openWorldHint = false,
            ),
    )
    fun geoLocationsMcp(
        @McpToolParam(description = "List of public IPv4 or IPv6 addresses") ips: List<String>,
    ): Mono<GeoLocationBulkResult> = geoLocations(ips).collectList().map { GeoLocationBulkResult(it) }
}

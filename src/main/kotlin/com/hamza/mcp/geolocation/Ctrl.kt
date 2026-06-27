package com.hamza.mcp.geolocation

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.constraints.Pattern
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.kotlin.core.publisher.switchIfEmpty
import reactor.kotlin.core.publisher.toMono
import java.time.Instant
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody

@Tag(name = "geo", description = "Geolocation data")
@RequestMapping(value = ["/api"], produces = [MediaType.APPLICATION_JSON_VALUE])
interface Api {
    @GetMapping("/{ip}")
    @Operation(
        summary = "IP Geolocation Lookup",
        description = """
Mirrors MCP tool `getGeoLocationData`.

Returns geolocation data for a public IPv4 or IPv6 address, including:
- Flags: isDatacenter, isTorExitNode, isVPN
- City name, region ISO code, latitude/longitude, accuracy radius, postal code, timezone
- Country name, ISO code, EU membership
- Continent name and code
- ASN number, ISP/organization name, network CIDR

Fails for private, loopback, or reserved IP ranges (e.g. 192.168.x.x, 127.0.0.1) as they are not present in the databases.
""",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(GeoLocationData::class),
                        examples = [
                            ExampleObject(
                                name = "OK",
                                value = """
{
  "isDatacenter": false,
  "isTorExitNode": false,
  "isVpn": false,
  "city": {
    "name": "Minneapolis",
    "isoCode": "MN",
    "latitude": 44.9696,
    "longitude": -93.2348,
    "accuracyRadius": "20 km",
    "postalCode": "55455",
    "timeZone": "America/Chicago"
  },
  "country": {
    "name": "United States",
    "isoCode": "US",
    "isInEuropeanUnion": false
  },
  "continent": {
    "name": "North America",
    "code": "NA"
  },
  "asn": {
    "autonomousSystemNumber": 217,
    "autonomousSystemOrganization": "University of Minnesota",
    "ipAddress": "128.101.101.101",
    "hostAddress": "128.101.0.0",
    "prefixLength": 16
  }
}
""",
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "invalid IP address format",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "400",
                                value = """
{
  "timestamp": "2026-06-05T05:09:20.652Z",
  "path": "/api/0.0.0.pp",
  "status": 400,
  "error": "Bad Request",
  "requestId": "033c836a-8",
  "message": "Validation failed"
}
""",
                            ),
                        ],
                    ),
                ],
            ),
            ApiResponse(
                responseCode = "500",
                description = "address not found in database / internal error",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = [
                            ExampleObject(
                                name = "500",
                                value = """
{
  "timestamp": "2026-06-05T05:07:46.161Z",
  "path": "/api/0.0.0.0",
  "status": 500,
  "error": "Internal Server Error",
  "requestId": "69062ecb-6",
  "message": "The address 0.0.0.0 is not in the database."
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun geoLocation(
        @PathVariable
        @Pattern(
            regexp = "^((\\d{1,3}\\.){3}\\d{1,3}|[0-9a-fA-F:]{2,39})$",
            message = "must be valid IPv4 or IPv6 address",
        )
        @Parameter(
            description = "Public IPv4 (e.g. 8.8.8.8) or IPv6 (e.g. 2001:4860:4860::8888) address",
            example = "128.101.101.101",
        )
        ip: String,
    ): Mono<GeoLocationData>

    @PostMapping("/bulk")
    @Operation(
        summary = "Bulk IP Geolocation Lookup",
        description = """
Mirrors MCP tool `getGeoLocationDataBulk`.

Returns geolocation data for a list of public IPv4 or IPv6 addresses.

Each result contains the queried IP and either full geolocation data or an error message.

Failed lookups (e.g. private/reserved IPs) do not affect the rest of the results, they appear with a null `data` and a populated `error` field.
""",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        array = ArraySchema(schema = Schema(GeoLocationBulk::class)),
                        examples = [
                            ExampleObject(
                                name = "OK",
                                value = """
[
  {
    "ip": "0.0.0.0",
    "data": null,
    "error": "The address 0.0.0.0 is not in the database."
  },
  {
    "ip": "128.101.101.101",
    "data": {
      "isDatacenter": false,
      "isTorExitNode": false,
      "isVpn": false,
      "city": {
        "name": "Minneapolis",
        "isoCode": "MN",
        "latitude": 44.9696,
        "longitude": -93.2348,
        "accuracyRadius": "20 km",
        "postalCode": "55455",
        "timeZone": "America/Chicago"
      },
      "country": {
        "name": "United States",
        "isoCode": "US",
        "isInEuropeanUnion": false
      },
      "continent": {
        "name": "North America",
        "code": "NA"
      },
      "asn": {
        "autonomousSystemNumber": 217,
        "autonomousSystemOrganization": "University of Minnesota",
        "ipAddress": "128.101.101.101",
        "hostAddress": "128.101.0.0",
        "prefixLength": 16
      }
    },
    "error": null
  }
]
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun geoLocationBulk(
        @SwaggerRequestBody(
            content = [
                Content(
                    mediaType = MediaType.APPLICATION_JSON_VALUE,
                    examples = [
                        ExampleObject(
                            name = "example request",
                            description = "List of public IPv4 or IPv6 address",
                            value = """["128.101.101.101", "8.8.8.8", "0.0.0.0", "blabla"]""",
                        ),
                    ],
                ),
            ],
        )
        @RequestBody ips: List<String>,
    ): Flux<GeoLocationBulk>

    @GetMapping("/manifest")
    @Operation(summary = "get GeoLite2 databases manifest")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OK",
                content = [
                    Content(
                        mediaType = MediaType.APPLICATION_JSON_VALUE,
                        schema = Schema(DbManifest::class),
                        examples = [
                            ExampleObject(
                                name = "OK",
                                value = """
{
  "tagName": "2026.06.04",
  "downloadedAt": "2026-06-04T14:57:03.833088668Z",
  "assets": [
    {
      "name": "GeoLite2-City.mmdb",
      "size": 65655525,
      "digest": "sha256:2cd96caa302ec416f69f3c298497d52108b382f418522d66ad97ed0b9dde0f80",
      "browser_download_url": "https://github.com/P3TERX/GeoLite.mmdb/releases/download/2026.06.04/GeoLite2-City.mmdb",
      "updated_at": "2026-06-04T05:56:15Z"
    },
    {
      "name": "GeoLite2-ASN.mmdb",
      "size": 12290045,
      "digest": "sha256:815d961f2424a09792fddf3b9bfadc000ad416b26de5123b8ce515a89c22923a",
      "browser_download_url": "https://github.com/P3TERX/GeoLite.mmdb/releases/download/2026.06.04/GeoLite2-ASN.mmdb",
      "updated_at": "2026-06-04T05:56:13Z"
    }
  ]
}
""",
                            ),
                        ],
                    ),
                ],
            ),
        ],
    )
    fun manifest(): Mono<DbManifest>
}

@RestController
class Ctrl(
    private val service: IGeoLocationService,
    private val repository: IManifestRepository,
) : Api {
    override fun geoLocation(ip: String) = service.geoLocation(ip)

    override fun geoLocationBulk(ips: List<String>) = service.geoLocations(ips)

    override fun manifest() =
        repository
            .read()
            .switchIfEmpty { DbManifest(tagName = "none", downloadedAt = Instant.now(), assets = emptyList()).toMono() }
}

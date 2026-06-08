# CLAUDE.md

**NEVER COMMIT OR PUBLISH TO DOCKERHUB WITHOUT EXPLICIT USER PROMPT**

## stack

- Kotlin 2.4, Spring Boot 4.0.6, Java 25
- Spring AI MCP server Webflux
- Reactor / coroutines reactor bridge
- GraalVM native image support (musl, static, -Os, UPX, distroless)

## commands

```shell
# setup graalvm
sdk env install

# run on jvm
./gradlew bootRun

# test all
./gradlew test

# test single class
./gradlew test --tests "com.hamza.mcp.geolocation.DatabaseInitializerTest"

# kotlin lint check
./gradlew ktlintCheck
# kotlin lint fix
./gradlew ktlintFormat

# prettier format for non kotlin
npm run format

# graalvm native compilation
## -PgenerateMetadata = tracing-agent will instrument tests to generate reachability metadata
./gradlew clean ktlintFormat ktlintCheck build -PgenerateMetadata
## build native docker image
./gradlew buildImage

# publish image to docker hub
./gradlew publishImage

# run native docker image
docker run -p 8891:8080 -v geolocation_data:/home/nonroot/.geolocation-mcp 7mza/geolocation-mcp:latest

# mcp inspector
npm run mcp

# dependency vulnerability scan
./gradlew dependencyCheckAnalyze
```

## architecture

`com.hamza.mcp.geolocation.models` for any data class that needs reflection registration

flat package `com.hamza.mcp.geolocation` for else

### layers

- `Components.kt`
  - `GithubReleaseClient` : P3TERX/GeoLite.mmdb repo release API
  - `AssetDownloader` : streaming binary download with digest
  - `ManifestRepository` : JSON manifest r/w
  - `DatabaseInitializer` : orchestrates previous + loads DBs readers
  - `TorExitNodeClient` : `https://check.torproject.org/torbulkexitlist` client
  - `TorExitNodeRepository` : loads Tor exits as HashMap + refresh + ip search
  - `X4BNetClient` : X4BNet/lists_vpn repo client + CIDRs to Long ranges directly
  - `X4BNetRepository` : loads VPN/Datacenter ranges + ip binary search + refresh
- `Services.kt`
  - `GeoLocationService`
    - reactor wrapper over `com.maxmind.geoip2:geoip2`
    - main service exposing `@McpTool` (getGeoLocationData, getGeoLocationDataBulk)
- `Ctrl.kt` REST / OpenAPI (for debug)
- `*.models.Models.kt` DTOs / `com.maxmind.geoip2:geoip2` domain mapping
- `Configurations.kt` beans / configs / properties
- `ControllerAdvice.kt` error response overriding when needed

### startup

`DatabaseInitializer.init()` run blocking at startup since everything depends on databases being loaded

it will read JSON manifest at `~/.geolocation-mcp/manifest.json`

**if absent or referencing any missing DB**

it will fetch latest GitHub release from `https://api.github.com/repos/P3TERX/GeoLite.mmdb/releases/latest`

downloads `GeoLite2-City.mmdb` and `GeoLite2-ASN.mmdb`

calculate downloaded SHA-256 digests and verify them against GitHub asset digests

reject with error if mismatch and delete downloads

writes JSON manifest

```json
{
  "tagName": "2026.06.04",
  "downloadedAt": "2026-06-05T07:01:19.210402666Z",
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
```

**if manifest and both databases are present it will read them directly**

construct 2 `DatabaseReader` instances (City + ASN) as `AtomicReference` for thread safe hotswap (when DBs update is
implemented)

exact same principle for `TorExitNodeRepository` and `X4BNetRepository`

## rules

- any blocking code must run on `Schedulers.boundedElastic()` BlockHound is active in JUnit to enforce this
- always prefer Kotlin sugar (expression bodies, `let`, `also`, `apply`, destructuring, `when`, extension functions,
  trailing lambdas, ...etc.) over Java verbosity
- use `reactor-kotlin-extensions` to simplify webflux verbosity when possible

## native compilation

GraalVM tracing-agent is configured in `build.gradle.kts` to instrument test tasks

it uses [access filter](src/main/resources/META-INF/native-image/native-access-filter.json) to limit collected hints (
only necessary)

if there's an error during an `*Aot` task do a `./gradlew clean && ./gradlew --stop` then delete
`src/main/resources/META-INF/native-image/.lock`

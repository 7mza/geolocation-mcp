# GeoLocation MCP Server

![Coverage](.github/badges/jacoco.svg)

Fast and up-to-date IP geolocation data for LLMs

<details>
<summary>Demo</summary>

![demo](docs/demo.gif)

</details>

For public IPv4 / IPv6 address:

- Flags: isDatacenter, isTorExitNode, isVPN
- City name, region ISO code, latitude/longitude, accuracy radius, postal code, timezone
- Country name, ISO code, EU membership/GDPR
- ASN number, ISP/organization name, network CIDR

## MCP tools

| Tool                     | Description                                   |
| ------------------------ | --------------------------------------------- |
| `getGeoLocationData`     | single IP lookup                              |
| `getGeoLocationDataBulk` | batch lookup, failed IPs don't block the rest |

## Data sources

| Data                    | Source                                                                                                                                     | Refresh              |
| ----------------------- | ------------------------------------------------------------------------------------------------------------------------------------------ | -------------------- |
| City / ASN              | [GeoLite2](https://dev.maxmind.com/geoip/geolite2-free-geolocation-data) via [P3TERX/GeoLite.mmdb](https://github.com/P3TERX/GeoLite.mmdb) | on startup if absent |
| Tor exit nodes          | [Tor Project](https://check.torproject.org/torbulkexitlist)                                                                                | daily                |
| VPN / Datacenter ranges | [X4BNet/lists_vpn](https://github.com/X4BNet/lists_vpn)                                                                                    | daily                |

## Usage

```shell
# named volume so DBs persist across restarts
docker run -p 8891:8080 -v geolocation_data:/home/nonroot/.geolocation-mcp 7mza/geolocation-mcp:latest
```

or with compose

```yaml
services:
  geolocation-mcp:
    image: 7mza/geolocation-mcp:latest
    ports:
      - '8891:8080'
    restart: unless-stopped
    volumes:
      - geolocation_data:/home/nonroot/.geolocation-mcp
volumes:
  geolocation_data:
    name: geolocation_data
```

Connect your LLM:

```shell
# example for claude
claude mcp add --transport http geolocation http://localhost:8891/mcp
```

Test with MCP inspector:

```shell
npm i && npm run mcp
# transport: streamable
# url: http://localhost:8891/mcp
```

## Build from source

Requires [SDKMAN](https://sdkman.io) and [nvm](https://github.com/nvm-sh/nvm).

```shell
nvm use && npm i && sdk env install
```

JVM:

```shell
./gradlew clean ktlintFormat ktlintCheck build
./gradlew bootRun
```

Native:

```shell
./gradlew clean ktlintFormat ktlintCheck build -PgenerateMetadata
./gradlew buildImage
docker run -p 8891:8080 -v geolocation_data:/home/nonroot/.geolocation-mcp 7mza/geolocation-mcp:latest
```

## License

GeoLite2 DBs are subject to the [GeoLite2 EULA](https://www.maxmind.com/en/geolite/eula).

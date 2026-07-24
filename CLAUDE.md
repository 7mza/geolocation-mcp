# CLAUDE.md

**NEVER COMMIT OR PUBLISH TO DOCKERHUB WITHOUT EXPLICIT USER PROMPT**

## stack

- Kotlin 2.4.10, Spring Boot 4.1.0, Java 25
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

## rules

- any blocking code must run on `Schedulers.boundedElastic()` BlockHound is active in JUnit to enforce this
- always prefer Kotlin sugar (expression bodies, `let`, `also`, `apply`, destructuring, `when`, extension functions,
  trailing lambdas, ...etc.) over Java verbosity
- use `reactor-kotlin-extensions` to simplify webflux verbosity when possible

## native compilation

GraalVM tracing-agent is configured in `build.gradle.kts` to instrument test tasks

if there's an error during an `*Aot` task do a `./gradlew clean && ./gradlew --stop` then delete
`src/main/resources/META-INF/native-image/.lock`

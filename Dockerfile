FROM ghcr.io/graalvm/native-image-community:25-muslib AS builder
RUN microdnf install -y --nodocs --setopt=install_weak_deps=0 xz && microdnf clean all
RUN curl -fsSL https://github.com/upx/upx/releases/download/v5.2.0/upx-5.2.0-amd64_linux.tar.xz \
    | tar -xJ --strip-components=1 -C /usr/local/bin/ upx-5.2.0-amd64_linux/upx

WORKDIR /app
COPY gradle/ gradle/
COPY build.gradle.kts gradle.properties gradlew settings.gradle.kts ./
RUN ./gradlew dependencies -x npm_run_format --no-daemon
COPY src/ src/
RUN ./gradlew nativeCompile -x test -x npm_run_format --no-daemon

RUN upx --lzma --best build/native/nativeCompile/geolocation-mcp

RUN mkdir -p /home/nonroot/.geolocation-mcp

FROM gcr.io/distroless/static-debian13:nonroot
LABEL io.modelcontextprotocol.server.name="io.github.7mza/geolocation-mcp"
COPY --from=builder --chown=nonroot:nonroot /home/nonroot/.geolocation-mcp /home/nonroot/.geolocation-mcp
COPY --from=builder /app/build/native/nativeCompile/geolocation-mcp /app
ENTRYPOINT ["/app"]

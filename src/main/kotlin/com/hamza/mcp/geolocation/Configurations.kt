package com.hamza.mcp.geolocation

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.info.BuildProperties
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.annotation.EnableScheduling
import java.nio.file.Path

@Configuration
@EnableScheduling
class Configurations {
    @Bean
    fun jacksonCustomizer() = JsonMapperBuilderCustomizer { it.findAndAddModules() }

    @Bean
    @Profile("!native")
    fun openAPI(buildProperties: BuildProperties): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title(buildProperties.name)
                    .version(buildProperties.version)
                    .description("GeoLocation MCP API")
                    .contact(Contact().name("team").email("alias.ducky891@passinbox.com"))
                    .license(License().name("MIT").url("https://opensource.org/licenses/MIT")),
            )
}

@Configuration
@ConfigurationProperties(prefix = "geolocation")
class GeoLocationProperties {
    lateinit var directory: Path
    lateinit var githubApiBaseUrl: String
    lateinit var githubRepo: String
    lateinit var githubToken: String
    lateinit var torBaseUrl: String
    lateinit var torRefreshCron: String
    lateinit var x4bNetBaseUrl: String
    lateinit var x4bNetRefreshCron: String
}

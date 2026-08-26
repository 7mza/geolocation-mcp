import com.bmuschko.gradle.docker.tasks.image.DockerPushImage
import com.github.gradle.node.npm.task.NpmTask
import org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
import org.gradle.api.tasks.testing.logging.TestLogEvent.FAILED
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.owasp.dependencycheck.reporting.ReportGenerator.Format
import org.springframework.boot.gradle.tasks.aot.ProcessAot
import org.springframework.boot.gradle.tasks.aot.ProcessTestAot

plugins {
    kotlin("jvm") version "2.4.10"
    kotlin("plugin.spring") version "2.4.10"
    id("org.springframework.boot") version "4.1.1"
    id("io.spring.dependency-management") version "1.1.7"
    id("com.autonomousapps.dependency-analysis") version "3.19.1"
    id("com.bmuschko.docker-remote-api") version "10.0.0"
    id("com.github.node-gradle.node") version "7.1.0"
    id("io.github.ben-manes.versions") version "0.61.0"
    id("org.graalvm.buildtools.native") version "1.1.10"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
    id("org.owasp.dependencycheck") version "13.0.0"
    jacoco
}

group = "com.hamza.mcp"
version = "0.0.1"

java { toolchain { languageVersion = JavaLanguageVersion.of(25) } }

kotlin { compilerOptions { freeCompilerArgs.addAll("-Xjsr305=strict") } }

repositories { mavenCentral() }

private val blockhoundVersion = "1.0.17.RELEASE"
private val geoip2Version = "5.2.0"
private val mockitoCoreVersion = "5.23.0"
private val mockitoKotlinVersion = "6.3.0"
private val openapiVersion = "3.1.0"
private val wiremockSpringBootVersion = "4.2.2"

private val mockitoAgent: Configuration = configurations.create("mockitoAgent")

dependencies {
    implementation("com.maxmind.geoip2:geoip2:$geoip2Version")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.springdoc:springdoc-openapi-starter-webflux-ui:$openapiVersion")
    implementation("org.springframework.ai:spring-ai-starter-mcp-server-webflux")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webclient")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("tools.jackson.module:jackson-module-kotlin")

    mockitoAgent("org.mockito:mockito-core:$mockitoCoreVersion") { isTransitive = false }

    testImplementation("io.projectreactor.tools:blockhound-junit-platform:$blockhoundVersion")
    testImplementation("org.mockito.kotlin:mockito-kotlin:$mockitoKotlinVersion")
    testImplementation("org.springframework.boot:spring-boot-starter-webflux-test")
    testImplementation("org.wiremock.integrations:wiremock-spring-boot:$wiremockSpringBootVersion")

    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

private val springAiVersion = "2.0.1"

dependencyManagement { imports { mavenBom("org.springframework.ai:spring-ai-bom:$springAiVersion") } }

private val dockerRegistry = property("dockerRegistry") as String
private val dockerUsername = property("dockerUsername") as String
private val dockerImage = "$dockerRegistry/$dockerUsername/${project.name}"

tasks {
    withType<JavaCompile>().configureEach {
        options.encoding = Charsets.UTF_8.name()
        options.isFork = true
    }

    withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgumentProviders += CommandLineArgumentProvider { listOf("-javaagent:${mockitoAgent.asPath}") }
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        forkEvery = 100
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_13)) {
            jvmArgs("-XX:+AllowRedefinitionToAddDeleteMethods")
        }
        // https://graalvm.github.io/native-build-tools/latest/gradle-plugin.html
        if (project.hasProperty("generateMetadata")) {
            val metadataDir = "$projectDir/src/main/resources/META-INF/native-image/"
            doFirst { delete(metadataDir) }
            jvmArgs("-agentlib:native-image-agent=config-merge-dir=$metadataDir")
            maxParallelForks = 1
            forkEvery = 0
        }
        reports {
            html.required = false
            junitXml.required = false
        }
        testLogging {
            events = setOf(FAILED)
            exceptionFormat = FULL
            showCauses = true
            showExceptions = true
            showStackTraces = true
            showStandardStreams = false
        }
        finalizedBy(jacocoTestReport)
        extensions.configure<JacocoTaskExtension> {
            excludes = listOf("jdk.internal.*")
            isIncludeNoLocationClasses = true
        }
    }

    withType<ProcessAot>().configureEach { args("--spring.profiles.active=default,native") }

    withType<ProcessTestAot>().configureEach {
        jvmArgs("-XX:+EnableDynamicAgentLoading")
        if (JavaVersion.current().isCompatibleWith(JavaVersion.VERSION_13)) {
            jvmArgs("-XX:+AllowRedefinitionToAddDeleteMethods")
        }
    }

    jacocoTestReport {
        dependsOn(test)
        classDirectories.setFrom(
            classDirectories.files.map { fileTree(it) { exclude("**/ApplicationKt.class") } },
        )
        reports {
            csv.required = true
            html.outputLocation = layout.buildDirectory.dir("jacocoHtml")
            xml.required = false
        }
    }

    val npmRunFormat =
        register("npm_run_format", NpmTask::class) {
            description = "npm run format hook"
            args = listOf("run", "format")
        }

    processResources { dependsOn(npmRunFormat) }

    register<Exec>("buildImage") {
        description = "build image using buildx"
        group = "publishing"
        commandLine(
            "docker",
            "buildx",
            "build",
            "--load",
            "-t",
            "$dockerImage:${project.version}",
            "-t",
            "$dockerImage:latest",
            ".",
        )
    }

    register<DockerPushImage>("publishImage") {
        description = "publish image to DockerHub"
        group = "publishing"
        images.set(setOf("$dockerImage:${project.version}", "$dockerImage:latest"))
        registryCredentials {
            username.set(dockerUsername)
            password.set(System.getenv("DOCKERHUB_TOKEN") ?: "")
        }
    }
}

configure<KtlintExtension> {
    android.set(false)
    coloredOutput.set(true)
    debug.set(true)
    verbose.set(true)
    version.set("1.8.0")
}

springBoot { buildInfo() }

graalvmNative { binaries { named("main") { buildArgs.addAll("--static", "--libc=musl", "-Os") } } }

node {
    download = true
    version = "24.20.0"
}

// https://nvd.nist.gov/developers/request-an-api-key
// mkdir -p ~/owasp-data && chmod 777 ~/owasp-data && docker run --rm -v ~/owasp-data:/usr/share/dependency-check/data owasp/dependency-check:latest --updateonly --nvdApiKey "$NVD_APIKEY"
dependencyCheck {
    data.directory = "${System.getProperty("user.home")}/owasp-data"
    format = Format.HTML.toString()
    nvd.apiKey = System.getenv("NVD_APIKEY") ?: ""
}

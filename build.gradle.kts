import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "4.0.0"
    id("io.spring.dependency-management") version "1.1.7"
    kotlin("jvm") version "2.2.20"
    id("io.ktor.plugin") version "3.2.3"
    kotlin("plugin.spring") version "2.2.20"
    kotlin("plugin.serialization") version "2.2.20"
    id("org.openapi.generator") version "7.17.0"
}

group = "no.vegvesen.nvdb.kafka"
version = "0.0.1-SNAPSHOT"

kotlin {
    jvmToolchain(21)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.apache.kafka:kafka-streams")
    implementation("org.springframework.kafka:spring-kafka")
    implementation("io.projectreactor.kotlin:reactor-kotlin-extensions")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.0.0")

    implementation("io.github.nomisrev:kotlin-kafka:0.4.1")
    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-coroutines-reactor
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.10.2")
    runtimeOnly("org.xerial:sqlite-jdbc")

    // https://mvnrepository.com/artifact/org.jetbrains.kotlinx/kotlinx-datetime
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")


    // Ktor Client (for NVDB API calls)
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-client-logging")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.kafka:spring-kafka-test")
    testImplementation("org.apache.kafka:kafka-streams-test-utils")
    testImplementation("io.mockk:mockk:1.14.6")
}

tasks.withType<KotlinCompile> {
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
        optIn.addAll(
            "kotlin.time.ExperimentalTime",
            "kotlinx.coroutines.ExperimentalCoroutinesApi",
            "kotlinx.serialization.ExperimentalSerializationApi",
        )
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

val downloadUberiketSpec by tasks.registering {
    val specUrl = "https://nvdbapiles.atlas.vegvesen.no/api-docs/uberiket"
    val specFile = layout.buildDirectory.file("openapi-specs/uberiket.json").get().asFile

    outputs.file(specFile)

    doLast {
        specFile.parentFile.mkdirs()
        uri(specUrl).toURL().openStream().use { input ->
            specFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
    }
}

val generateUberiketApi by tasks.registering(org.openapitools.generator.gradle.plugin.tasks.GenerateTask::class) {
    dependsOn(downloadUberiketSpec)

    inputSpec.set(layout.buildDirectory.file("openapi-specs/uberiket.json").get().asFile.absolutePath)
    outputDir.set(file("src/main/kotlin/generated/openapi/uberiket").absolutePath)
    generatorName.set("kotlin")
    packageName.set("no.vegvesen.nvdb.api.uberiket")
    modelPackage.set("no.vegvesen.nvdb.api.uberiket.model")

    globalProperties.set(
        mapOf(
            "models" to "",
            "apis" to "false",
            "supportingFiles" to "false",
            "modelTests" to "false",
            "modelDocs" to "false"
        )
    )

    typeMappings.set(
        mapOf(
            "AnyType" to "JsonElement",
            "object" to "JsonElement"
        )
    )

    importMappings.set(
        mapOf(
            "JsonElement" to "kotlinx.serialization.json.JsonElement"
        )
    )

    configOptions.set(
        mapOf(
            "dateLibrary" to "kotlinx-datetime",
            "serializationLibrary" to "kotlinx_serialization"
        )
    )

    cleanupOutput.set(true)
}

sourceSets {
    main {
        java {
            srcDir("src/main/kotlin/generated/openapi/uberiket/src/main/kotlin")
        }
    }
}

# Technology Stack

## Core Framework
- **Spring Boot 4.0.0** - Application framework with dependency injection, configuration, and runtime management
- **Kotlin 2.2.20** - Primary programming language with JVM toolchain 21
- **Gradle (Kotlin DSL)** - Build tool and dependency management

## Kafka & Stream Processing
- **Apache Kafka Streams** - Stream processing framework
- **Spring Kafka** - Spring integration for Apache Kafka
- **kotlin-kafka (0.4.1)** - Kotlin-specific Kafka utilities

## HTTP Client & API
- **Ktor Client** - For making HTTP calls to NVDB API
  - ktor-client-cio (Coroutine-based I/O engine)
  - ktor-client-content-negotiation
  - ktor-serialization-kotlinx-json
  - ktor-client-logging
- **Spring WebFlux** - For reactive web capabilities

## Serialization
- **kotlinx-serialization-json** - JSON serialization/deserialization
- **Custom KotlinxJsonSerializer** - Kafka-specific serializer using kotlinx.serialization

## Database
- **SQLite JDBC** - Lightweight embedded database for tracking producer progress
- **Spring JDBC** - Database access abstraction

## API Documentation
- **SpringDoc OpenAPI** (3.0.0) - Automatic API documentation and Swagger UI

## Additional Libraries
- **kotlinx-datetime (0.7.1)** - Date/time handling
- **kotlinx-coroutines-reactor** - Coroutine support for reactive programming
- **Project Reactor Kotlin Extensions** - Kotlin extensions for reactive streams

## Testing
- **Spring Boot Test** - Testing framework
- **Spring Kafka Test** - Embedded Kafka for integration testing
- **Kafka Streams Test Utils** - Testing utilities for stream topologies
- **MockK (1.14.6)** - Mocking framework for Kotlin
- **JUnit Platform** - Test runner

## Code Generation
- **OpenAPI Generator** (7.17.0) - Generates Kotlin model classes from NVDB Uberiket OpenAPI spec

# Project Overview

## Purpose
kafka-at-home is a Spring Boot 4 application that consumes and transforms road data from the Norwegian Road Database (NVDB) Uberiket API using Kafka Streams.

## Core Functionality
- **Consumes** road data from NVDB Uberiket API (https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/)
- **Transforms** the data using Kafka Streams for processing
- **Produces** enriched data to output Kafka topics for downstream consumption

## Supported NVDB Data Types
The application handles various road object types from NVDB:
- **Fartsgrense (105)**: Speed limits
- **Vegbredde (583)**: Road width
- **Kjørefelt (616)**: Driving lanes
- **Funksjonsklasse (821)**: Functional road class

## Key Components
- REST API endpoints for triggering data fetching
- Kafka producer for scheduled/on-demand data ingestion with backfill and update modes
- Kafka Streams topology for data transformation
- SQLite database for tracking producer progress
- Health checks and monitoring via Spring Actuator

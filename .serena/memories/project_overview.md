# Project Overview

## Purpose

nvdb-kafka-streams is a Spring Boot 4 application that consumes and transforms road data from the Norwegian Road Database (
NVDB) Uberiket API using Kafka Streams.

## Core Functionality

- **Consumes** road data from NVDB Uberiket API (https://nvdbapiles.atlas.vegvesen.no/uberiket/api/v1/)
- **Transforms** the data using Kafka Streams for processing
- **Produces** enriched data to output Kafka topics for downstream consumption

## Key Components

- REST API endpoints for triggering data fetching
- Kafka producer for scheduled/on-demand data ingestion with backfill and update modes
- Kafka Streams topology for data transformation
- SQLite database for tracking producer progress

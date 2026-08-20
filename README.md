# TradeX — Trading Order Management System

TradeX is a portfolio-grade **Trading Order Management System** built with **Java 21** and **Spring Boot 3.5.16**. It demonstrates real engineering discipline, featuring an in-memory price-time-priority matching engine, JWT-secured REST APIs, event-driven domain event side-effects via Kafka, Redis-backed stock price caching, and PostgreSQL persistence.

## Key Features

- **Price-Time Priority Matching Engine**: Pure Java algorithm (`TreeMap` + `ArrayDeque`) with $O(\log p)$ operations and deterministic sequence-number FIFO tiebreaking.
- **Synchronous Match & Execution**: Orders match and execute trade fills synchronously within the HTTP request transaction.
- **Asynchronous Domain Events**: Kafka handles post-commit notifications and audit logging without affecting matching engine correctness.
- **Idempotency**: Client-supplied `clientOrderId` prevents duplicate order creation; deterministic `executionId` prevents duplicate trade persistence.
- **Financial Precision**: All prices and monetary amounts use `BigDecimal` / `NUMERIC(19,4)`; share quantities use integer `BIGINT` / `Long`.

## Tech Stack

- **Core**: Java 21, Spring Boot 3.5.16, Spring Security 6.4, Spring Data JPA
- **Storage**: PostgreSQL 16 (source of truth), Redis 7 (cache-aside)
- **Messaging**: Apache Kafka 3.7 (KRaft mode)
- **Frontend**: React 18 + TypeScript (Vite)
- **DevOps**: Docker & Docker Compose, Flyway DB migrations

## Quickstart

```bash
# 1. Start infrastructure
docker compose up -d

# 2. Run backend
mvn spring-boot:run

# 3. Access health check
curl http://localhost:8080/api/health
```

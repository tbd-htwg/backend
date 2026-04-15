# Trip Planning Backend

Spring Boot backend for a simple trip planning application.

## Prerequisites

- Java 21
- Maven 3.9+

## Run Locally

1. Start the application:

```bash
mvn spring-boot:run
```

2. The API is available at:
   - `http://localhost:8080/api/v2`

3. H2 database setup:
   - Database is stored in `./db/tripplanning` (file-based H2).
   - H2 console is enabled at `http://localhost:8080/h2-console`.
   - JDBC URL: `jdbc:h2:file:./db/tripplanning;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`
   - User: `sa`
   - Password: (empty)

## Seed Example Data

Seed realistic demo data (users, trips, locations, accommodations, transports, likes, comments):

```bash
python3 scripts/seed_example_data.py
```

With custom API target:

```bash
ROOT_URL=http://localhost:8080 BASE_PATH=/api/v2 python3 scripts/seed_example_data.py
```

If your deployment exposes the API under a prefix, set `BASE_PATH` accordingly (for example `/api/v2`).

## API Reference (OpenAPI / Swagger)

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

These endpoints provide a frontend-friendly reference for request/response schemas and available routes.

## Current API Endpoints

This project currently uses Spring Data REST repositories, not handwritten controllers.
Endpoints are inferred from repository resources and are available under the base path `/api/v2`.

Main collection resources currently exposed:

- `GET/POST /api/v2/users`
- `GET/PUT/PATCH/DELETE /api/v2/users/{id}`
- `GET/POST /api/v2/trips`
- `GET/PUT/PATCH/DELETE /api/v2/trips/{id}`

Depending on repository methods, Spring Data REST may additionally expose search endpoints under:

- `/api/v2/users/search`
- `/api/v2/trips/search`

## Project Structure (Overview)

- `com.tripplanning`
  - Spring Boot application entrypoint (`Application`).
- `com.tripplanning.user`
  - `UserEntity` and `UserRepository` (Spring Data REST resource `/api/v2/users`).
- `com.tripplanning.trip`
  - `TripEntity` and `TripRepository` (Spring Data REST resource `/api/v2/trips`).
- `com.tripplanning.accommodation`
  - `AccomEntity` and `AccomRepository`.
- `com.tripplanning.transport`
  - `TransportEntity` and `TransportRepository`.
- `com.tripplanning.comment`
  - `CommentEntity` and `CommentRepository`.
- `com.tripplanning.location`
  - `LocationEntity` and `LocationRepository`.
- `com.tripplanning.tripLocation`
  - `TripLocationEntity` and `TripLocationRepository`.
- `com.tripplanning.api.config`
  - Security and OpenAPI configuration.

## Run Tests

```bash
mvn test
```

## Docker

Build image:

```bash
docker build -t trip-backend:local .
```

Run container:

```bash
docker run --rm -p 8080:8080 trip-backend:local
```

API base URL inside local Docker run:
- `http://localhost:8080/api/v2`

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
   - `http://localhost:8080/v1`

3. H2 database setup:
   - Database is stored in `./tripplanning` (file-based H2).
   - H2 console is enabled at `http://localhost:8080/h2-console`.
   - JDBC URL: `jdbc:h2:file:./tripplanning;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE`
   - User: `sa`
   - Password: (empty)

## API Reference (OpenAPI / Swagger)

- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- Swagger UI: `http://localhost:8080/swagger-ui/index.html`

These endpoints provide a frontend-friendly reference for request/response schemas and available routes.

## Current API Endpoints

- `POST /v1/users` - register a user
- `POST /v1/trips` - create a trip
- `GET /v1/trips` - list trips
- `GET /v1/trips/{id}` - get trip details by id

## Project Structure (Overview)

- `com.tripplanning.api.controller`
  - REST controllers (`UserController`, `TripController`) with `/v1` endpoints.
- `com.tripplanning.api.dto`
  - Immutable API payloads (Java records) for request/response models.
- `com.tripplanning.api.exception`
  - API-level exceptions and centralized exception handler.
- `com.tripplanning.user`
  - `UserEntity`, `UserRepository`, and `UserService` for user domain logic and persistence.
- `com.tripplanning.trip`
  - `TripEntity`, `TripRepository`, and `TripService` for trip domain logic and persistence.
- `com.tripplanning.api.config`
  - OpenAPI/Swagger configuration.

## Run Tests

```bash
mvn test
```

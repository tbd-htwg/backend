# Backend microservices (GKE)

**Full platform steps (Terraform, kubectl, deploy):** [infrastructure/ms2/docs/gettingstarted/README.md](../infrastructure/ms2/docs/gettingstarted/README.md) — includes **Identity / Firebase** for `POST /api/v2/auth/google`.

Maven multi-module layout:

| Module | Artifact | Port (local) |
|--------|----------|----------------|
| `tripplanning-common` | shared clients, auth properties | — |
| `tripplanning-trip-service` | Cloud SQL, Elasticsearch, GCS, auth | 8080 |
| `tripplanning-social-service` | Firestore likes/comments | 8081 |

## Build

```bash
mvn -pl tripplanning-trip-service -am package
mvn -pl tripplanning-social-service -am package
```

## Docker

```bash
docker build --build-arg SERVICE=trip -t tripplanning-trip-service ./backend
docker build --build-arg SERVICE=social -t tripplanning-social-service ./backend
```

## Local run

1. Postgres/H2 + trip-service: `SPRING_PROFILES_ACTIVE=local mvn -pl tripplanning-trip-service spring-boot:run`
2. Firestore emulator + social-service: `SPRING_PROFILES_ACTIVE=local mvn -pl tripplanning-social-service spring-boot:run`

## Internal APIs

- Trip: `HEAD /internal/trips/{id}`, `GET /internal/users?ids=`, `POST /internal/cache/trips/liked-by/evict`
- Social: `GET /internal/users/{userId}/liked-trip-ids`

Optional header `X-Internal-Secret` when `TRIPPLANNING_INTERNAL_SECRET` is set.

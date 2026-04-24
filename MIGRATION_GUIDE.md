# Step-by-Step Guide: MongoDB Social Data Layer Migration

Ziel: Comments und Likes aus PostgreSQL (JPA) in ein Document Store migrieren.

- **Local/Dev**: MongoDB (Spring Data MongoDB)
- **Prod (Cloud Run/GCP)**: Google Firestore

---

## Überblick der Änderungen

| Was ändert sich                                      | Von                     | Nach                                  |
| ---------------------------------------------------- | ----------------------- | ------------------------------------- |
| `comment` Tabelle                                    | PostgreSQL / JPA        | MongoDB Collection                    |
| `user_likes_trips` Join-Tabelle                      | PostgreSQL M:N          | MongoDB Collection                    |
| `CommentEntity` / `CommentRepository`                | JPA                     | wird ersetzt durch Document + Service |
| `UserEntity.likedTrips`                              | `@ManyToMany` JPA       | wird entfernt                         |
| `TripEntity.likedByUsers`                            | `@ManyToMany` JPA       | wird entfernt                         |
| `TripRepository.countLikes` / `findByLikedByUsersId` | JPQL                    | wird in Service/Controller verlagert  |
| REST API Pfade                                       | Spring Data REST (auto) | explizite `@RestController`           |

---

## Phase 1 — Maven Dependencies & Konfiguration

### Schritt 1: MongoDB Dependency in `pom.xml` hinzufügen

Datei: `pom.xml`

Füge nach der bestehenden `spring-boot-starter-data-jpa` Dependency ein:

```xml
<!-- MongoDB für local/dev -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-mongodb</artifactId>
</dependency>
```

Für **Production (Firestore)** zusätzlich:

```xml
<!-- Google Firestore für prod -->
<dependency>
    <groupId>com.google.cloud</groupId>
    <artifactId>spring-cloud-gcp-starter-data-firestore</artifactId>
    <version>5.10.0</version>
</dependency>
```

---

### Schritt 2: MongoDB URI in `application-local.yml` eintragen

Datei: `src/main/resources/application-local.yml`

Füge am Ende des Profil-Blocks hinzu:

```yaml
data:
  mongodb:
    uri: mongodb://localhost:27017/tripplanning
```

---

### Schritt 3: Firestore Config in `application.yml` (Prod) eintragen

Datei: `src/main/resources/application.yml`

Füge einen neuen Block hinzu (wird über Env-Vars befüllt):

```yaml
spring:
  cloud:
    gcp:
      project-id: ${GCP_PROJECT_ID}
      firestore:
        enabled: true
        database-id: ${FIRESTORE_DATABASE_ID:(default)}
```

---

### Schritt 4: `@EnableMongoRepositories` in `Application.java` konfigurieren

Datei: `src/main/java/com/tripplanning/Application.java`

Spring Boot erkennt Mongo- und JPA-Repositories normalerweise automatisch. Da beide gleichzeitig aktiv sind, müssen sie explizit auf unterschiedliche Packages zeigen:

```java
@SpringBootApplication
@EnableJpaRepositories(basePackages = {
    "com.tripplanning.trip",
    "com.tripplanning.user",
    "com.tripplanning.accommodation",
    "com.tripplanning.transport",
    "com.tripplanning.location",
    "com.tripplanning.tripLocation"
})
@EnableMongoRepositories(basePackages = "com.tripplanning.social")
public class Application { ... }
```

> **Hinweis**: Erst ausführen wenn die neuen Mongo-Repositories in `com.tripplanning.social` existieren (Schritt 6).

---

## Phase 2 — Document-Klassen & Mongo-Repositories

### Schritt 5: `CommentDocument` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/CommentDocument.java`

```java
package com.tripplanning.social;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "comments")
@CompoundIndex(name = "trip_createdAt", def = "{'tripId': 1, 'createdAt': -1}")
@Getter
@NoArgsConstructor
public class CommentDocument {

    public CommentDocument(Long tripId, Long userId, String content) {
        this.tripId = tripId;
        this.userId = userId;
        this.content = content;
        this.createdAt = Instant.now();
    }

    @Id
    private String id;

    private Long tripId;
    private Long userId;
    private String content;
    private Instant createdAt;

    public void setContent(String content) {
        this.content = content;
    }
}
```

---

### Schritt 6: `TripLikeDocument` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/TripLikeDocument.java`

```java
package com.tripplanning.social;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "likes")
@CompoundIndex(name = "userId_tripId_unique", def = "{'userId': 1, 'tripId': 1}", unique = true)
@Getter
@NoArgsConstructor
public class TripLikeDocument {

    public TripLikeDocument(Long userId, Long tripId) {
        this.userId = userId;
        this.tripId = tripId;
    }

    @Id
    private String id;

    private Long userId;
    private Long tripId;
}
```

---

### Schritt 7: `CommentMongoRepository` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/CommentMongoRepository.java`

```java
package com.tripplanning.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface CommentMongoRepository extends MongoRepository<CommentDocument, String> {
    Page<CommentDocument> findByTripIdOrderByCreatedAtDesc(Long tripId, Pageable pageable);
    void deleteByUserId(Long userId);
}
```

---

### Schritt 8: `TripLikeMongoRepository` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/TripLikeMongoRepository.java`

```java
package com.tripplanning.social;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface TripLikeMongoRepository extends MongoRepository<TripLikeDocument, String> {
    long countByTripId(Long tripId);
    boolean existsByUserIdAndTripId(Long userId, Long tripId);
    void deleteByUserIdAndTripId(Long userId, Long tripId);
    Page<TripLikeDocument> findByUserId(Long userId, Pageable pageable);
}
```

---

## Phase 3 — Service-Schicht

### Schritt 9: `CommentService` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/CommentService.java`

```java
package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentMongoRepository commentRepository;

    public Page<CommentDocument> getCommentsByTrip(Long tripId, Pageable pageable) {
        return commentRepository.findByTripIdOrderByCreatedAtDesc(tripId, pageable);
    }

    public CommentDocument createComment(Long tripId, Long userId, String content) {
        return commentRepository.save(new CommentDocument(tripId, userId, content));
    }

    public void deleteByUserId(Long userId) {
        commentRepository.deleteByUserId(userId);
    }

    public void deleteById(String id) {
        commentRepository.deleteById(id);
    }
}
```

---

### Schritt 10: `LikeService` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/LikeService.java`

```java
package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final TripLikeMongoRepository likeRepository;

    public boolean existsLike(Long userId, Long tripId) {
        return likeRepository.existsByUserIdAndTripId(userId, tripId);
    }

    public long countLikes(Long tripId) {
        return likeRepository.countByTripId(tripId);
    }

    public void addLike(Long userId, Long tripId) {
        if (!existsLike(userId, tripId)) {
            likeRepository.save(new TripLikeDocument(userId, tripId));
        }
    }

    public void removeLike(Long userId, Long tripId) {
        likeRepository.deleteByUserIdAndTripId(userId, tripId);
    }

    public Page<TripLikeDocument> getLikedTripsByUser(Long userId, Pageable pageable) {
        return likeRepository.findByUserId(userId, pageable);
    }
}
```

---

## Phase 4 — REST Controller

### Schritt 11: `CommentController` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/CommentController.java`

Dieser Controller ersetzt das bisherige Spring Data REST Export von `CommentRepository`.  
Er bildet die gleichen Pfade ab, die das Frontend erwartet (`/api/v2/comments`).

```java
package com.tripplanning.social;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v2/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService commentService;

    // GET /api/v2/comments/search/findByTripIdOrderByCreatedAtDesc?tripId=1
    @GetMapping("/search/findByTripIdOrderByCreatedAtDesc")
    public Page<CommentDocument> getByTrip(
            @RequestParam Long tripId,
            Pageable pageable) {
        return commentService.getCommentsByTrip(tripId, pageable);
    }

    // POST /api/v2/comments
    // Body: { "tripId": 1, "userId": 2, "content": "..." }
    @PostMapping
    public ResponseEntity<CommentDocument> create(@RequestBody Map<String, Object> body) {
        Long tripId = parseLong(body, "tripId");
        Long userId = parseLong(body, "userId");
        String content = (String) body.get("content");
        CommentDocument saved = commentService.createComment(tripId, userId, content);
        return ResponseEntity.ok(saved);
    }

    // DELETE /api/v2/comments/{id}
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        commentService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Long parseLong(Map<String, Object> body, String key) {
        Object val = body.get(key);
        if (val instanceof Number n) return n.longValue();
        // Unterstütze auch URI-Format: "/api/v2/trips/1"
        if (val instanceof String s) {
            String[] parts = s.split("/");
            return Long.parseLong(parts[parts.length - 1]);
        }
        throw new IllegalArgumentException("Missing field: " + key);
    }
}
```

---

### Schritt 12: `LikeController` anlegen

Erstelle neue Datei: `src/main/java/com/tripplanning/social/LikeController.java`

Dieser Controller bildet die Association-Routen nach, die das Frontend für Likes verwendet  
(`/api/v2/users/{id}/likedTrips`, `/api/v2/trips/search/countLikes`, etc.).

```java
package com.tripplanning.social;

import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class LikeController {

    private final LikeService likeService;
    private final TripRepository tripRepository;

    // GET /api/v2/trips/search/countLikes?tripId=1
    @GetMapping("/api/v2/trips/search/countLikes")
    public long countLikes(@RequestParam Long tripId) {
        return likeService.countLikes(tripId);
    }

    // GET /api/v2/trips/search/findByLikedByUsersId?userId=1
    @GetMapping("/api/v2/trips/search/findByLikedByUsersId")
    public Page<TripEntity> findByLikedByUsersId(@RequestParam Long userId, Pageable pageable) {
        Page<TripLikeDocument> likePage = likeService.getLikedTripsByUser(userId, pageable);
        List<Long> tripIds = likePage.getContent().stream()
                .map(TripLikeDocument::getTripId)
                .collect(Collectors.toList());
        List<TripEntity> trips = tripRepository.findAllById(tripIds);
        return new PageImpl<>(trips, pageable, likePage.getTotalElements());
    }

    // GET /api/v2/users/{userId}/likedTrips
    @GetMapping("/api/v2/users/{userId}/likedTrips")
    public Page<TripEntity> getLikedTrips(@PathVariable Long userId, Pageable pageable) {
        return findByLikedByUsersId(userId, pageable);
    }

    // POST /api/v2/users/{userId}/likedTrips
    // Body: text/uri-list oder JSON { "tripId": 1 }
    @PostMapping(value = "/api/v2/users/{userId}/likedTrips",
                 consumes = {"text/uri-list", "application/json"})
    public ResponseEntity<Void> likeTrip(
            @PathVariable Long userId,
            @RequestBody String body) {
        Long tripId = parseIdFromUriOrNumber(body.trim());
        likeService.addLike(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/v2/users/{userId}/likedTrips/{tripId}
    @DeleteMapping("/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<Void> unlikeTrip(
            @PathVariable Long userId,
            @PathVariable Long tripId) {
        likeService.removeLike(userId, tripId);
        return ResponseEntity.noContent().build();
    }

    // HEAD /api/v2/users/{userId}/likedTrips/{tripId}  (exists-Check)
    @RequestMapping(method = RequestMethod.HEAD,
                    value = "/api/v2/users/{userId}/likedTrips/{tripId}")
    public ResponseEntity<Void> likeExists(
            @PathVariable Long userId,
            @PathVariable Long tripId) {
        return likeService.existsLike(userId, tripId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    private Long parseIdFromUriOrNumber(String raw) {
        // "/api/v2/trips/42" oder "42"
        String[] parts = raw.split("/");
        return Long.parseLong(parts[parts.length - 1]);
    }
}
```

---

## Phase 5 — JPA bereinigen

### Schritt 13: `likedByUsers` aus `TripEntity` entfernen

Datei: `src/main/java/com/tripplanning/trip/TripEntity.java`

**Entfernen** (die gesamten Felder + Imports):

```java
// Diese Imports entfernen, falls nicht anderweitig verwendet:
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;

// Dieses Feld entfernen:
@ManyToMany(mappedBy = "likedTrips")
private List<UserEntity> likedByUsers = new ArrayList<>();
```

---

### Schritt 14: `likedTrips` aus `UserEntity` entfernen

Datei: `src/main/java/com/tripplanning/user/UserEntity.java`

**Entfernen**:

```java
// Dieses Feld + Annotations entfernen:
@Builder.Default
@ManyToMany
@JoinTable(
    name = "userLikesTrips",
    joinColumns = @JoinColumn(name = "userId"),
    inverseJoinColumns = @JoinColumn(name = "tripId")
)
private List<TripEntity> likedTrips = new ArrayList<>();
```

Auch den Import entfernen wenn er nicht mehr gebraucht wird:

```java
import com.tripplanning.trip.TripEntity;  // nur entfernen wenn der @OneToMany trips-Import ihn auch nicht braucht
```

---

### Schritt 15: `CommentEntity` und `CommentRepository` löschen

Lösche folgende Dateien (oder leere sie — Spring darf die Klassen nicht mehr laden):

- `src/main/java/com/tripplanning/comment/CommentEntity.java`
- `src/main/java/com/tripplanning/comment/CommentRepository.java`

> **Achtung**: Das `comment` Package kann leer bleiben oder ganz entfernt werden.

---

### Schritt 16: `TripRepository` bereinigen

Datei: `src/main/java/com/tripplanning/trip/TripRepository.java`

**Entfernen** (werden jetzt vom `LikeController` übernommen):

```java
Page<TripEntity> findByLikedByUsersId(Long userId, Pageable pageable);

@Query("SELECT size(t.likedByUsers) FROM TripEntity t WHERE t.id = :tripId")
int countLikes(@Param("tripId") Long tripId);
```

Auch nicht mehr benötigte Imports entfernen:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
```

---

## Phase 6 — Flyway Migration

### Schritt 17: Flyway `V3__drop_social_tables.sql` erstellen

Erstelle neue Datei: `src/main/resources/db/migration/V3__drop_social_tables.sql`

```sql
-- Drop social tables migrated to MongoDB / Firestore.
-- Safe to run: constraints reference these tables, so drop in the right order.

ALTER TABLE comment DROP CONSTRAINT IF EXISTS fk_comment_user;
ALTER TABLE comment DROP CONSTRAINT IF EXISTS fk_comment_trip;

DROP TABLE IF EXISTS user_likes_trips;
DROP TABLE IF EXISTS comment;
```

> **Hinweis**: Die genauen Constraint-Namen ggf. via `\d comment` in psql prüfen und anpassen.  
> Auf dem `local`-Profil (H2 + `ddl-auto: create-drop`) läuft Flyway ohnehin nicht — diese Migration greift nur in Prod.

---

## Phase 7 — Lokales Testen

### Schritt 18: MongoDB lokal starten

Option A — Docker:

```bash
docker run -d -p 27017:27017 --name mongo-tripplanning mongo:7
```

Option B — direkt installieren: [mongodb.com/try/download/community](https://www.mongodb.com/try/download/community)

---

### Schritt 19: App mit `local`-Profil starten und testen

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=local
```

Testpunkte:

- `GET /api/v2/comments/search/findByTripIdOrderByCreatedAtDesc?tripId=1` → leere Page
- `POST /api/v2/comments` mit `{ "tripId": 1, "userId": 1, "content": "Test" }` → 200 + Dokument
- `POST /api/v2/users/1/likedTrips` mit URI-Body `/api/v2/trips/1`
- `GET /api/v2/trips/search/countLikes?tripId=1` → 1
- `DELETE /api/v2/users/1/likedTrips/1`
- `GET /api/v2/trips/search/countLikes?tripId=1` → 0

---

### Schritt 20: Compile-Fehler prüfen und beheben

```bash
./mvnw compile
```

Typische Fehler nach den JPA-Änderungen:

- Hibernate Search `@IndexedEmbedded` auf Felder die jetzt fehlen → in `TripEntity` / `UserEntity` entfernen
- Imports auf gelöschte Klassen (`CommentEntity`, etc.) → bereinigen
- `Application.java` `@EnableMongoRepositories`-Package stimmt nicht → Package-Pfad korrigieren

---

## Phase 8 — Production (Firestore) — Optional

### Schritt 21: Firestore-Implementierungen anlegen (Prod-Profil)

Erstelle eine Firestore-basierte Implementierung der Service-Schicht **nur wenn** du Firestore in Prod nutzt (ansonsten reicht MongoDB Atlas als gemeinsame Lösung für dev + prod).

Option A — **MongoDB Atlas in Prod** (einfachste Lösung):

- Flyway V3 deployen (Drop-Skript).
- Cloud Run Env-Var `SPRING_DATA_MONGODB_URI` auf Atlas-Cluster-URI setzen.
- Kein separater Firestore-Code nötig.

Option B — **Firestore in Prod**:

- `spring-cloud-gcp-starter-data-firestore` Dependency aus Schritt 1 aktivieren.
- Profile `prod` mit `@Profile("prod")` in Services anlegen, die `FirestoreTemplate` statt `MongoRepository` nutzen.
- Cloud Run Service Account: IAM-Rolle `roles/datastore.user` zuweisen.
- Env-Vars: `GCP_PROJECT_ID`, optional `FIRESTORE_DATABASE_ID`.

---

### Schritt 22: GitHub Actions / Cloud Run Deployment anpassen

Datei: `.github/workflows/deploy-gcp-cloudrun.yml`

Env-Vars für Prod:

**Bei MongoDB Atlas:**

```yaml
env:
  SPRING_DATA_MONGODB_URI: ${{ secrets.MONGODB_ATLAS_URI }}
```

**Bei Firestore:**

```yaml
env:
  GCP_PROJECT_ID: ${{ vars.GCP_PROJECT_ID }}
  SPRING_PROFILES_ACTIVE: prod
```

Außerdem: Workload Identity oder Service Account JSON für Firestore-Zugriff konfigurieren.

---

## Reihenfolge auf einen Blick

```
Schritt 1-4   → pom.xml + Config + @Enable-Annotations
Schritt 5-8   → Neue Document-Klassen + Mongo-Repositories anlegen
Schritt 9-10  → Service-Schicht (CommentService, LikeService)
Schritt 11-12 → REST-Controller (ersetzen Spring Data REST)
Schritt 13-16 → JPA bereinigen (Felder, Klassen, Repository-Methoden entfernen)
Schritt 17    → Flyway V3 Migration anlegen
Schritt 18-20 → Lokal testen + Kompilierungsfehler beheben
Schritt 21-22 → Prod-Deployment (Firestore oder MongoDB Atlas)
```

---

## Risiken & Hinweise

- **Spring Data REST** exportiert Mongo-Repositories standardmäßig. Stelle sicher, dass `CommentMongoRepository` und `TripLikeMongoRepository` **nicht** automatisch exportiert werden. Entweder via `@RepositoryRestResource(exported = false)` oder weil die expliziten Controller den gleichen Pfad übernehmen und Konflikte erzwingen.
- **Hibernate Search** re-indexiert `TripEntity` über `UserEntity`-Felder (`@IndexedEmbedded`). Nach dem Entfernen von `likedTrips` / `likedByUsers` sicherstellen, dass `TripSearchMappingConfigurer` keine Felder referenziert die nicht mehr existieren.
- **Atomarität**: PostgreSQL ↔ MongoDB Operationen sind nicht transaktional. Für `deleteByUserId` (User löschen) müssen Likes und Comments in MongoDB separat gelöscht werden — das muss ggf. in einer User-Delete-Logik koordiniert werden.
- **Lokales Profil (H2)**: Flyway ist im `local`-Profil deaktiviert, Hibernate macht `create-drop`. Die V3-Migration greift also nur in Prod-ähnlichen Umgebungen.

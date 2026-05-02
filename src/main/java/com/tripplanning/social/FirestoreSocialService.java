package com.tripplanning.social;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import com.google.api.gax.rpc.ApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.firestore.FieldPath;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Firestore aggregation counts and cursor-paginated comment reads. Uses the sync {@link Firestore}
 * client for {@link Query#count()} and ordered queries (Spring Data Firestore reactive repos do not
 * cover cursors + aggregation cleanly).
 */
@Service
@RequiredArgsConstructor
public class FirestoreSocialService {

    private static final String LIKES = "likes";
    private static final String COMMENTS = "comments";
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 10;

    private final Firestore firestore;
    private final ObjectMapper objectMapper;

    public long countLikesForTrip(long tripId) {
        Query q = firestore.collection(LIKES).whereEqualTo("tripId", tripId);
        return runCount(q);
    }

    public long countCommentsForTrip(long tripId) {
        Query q = firestore.collection(COMMENTS).whereEqualTo("tripId", tripId);
        return runCount(q);
    }

    private long runCount(Query query) {
        try {
            return query.count().get().get().getCount();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Firestore count interrupted");
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Firestore count failed: " + c.getMessage());
        }
    }

    /**
     * Newest-first page of comments for a trip. Uses {@code tripId + createdAt desc + __name__ desc}
     * with an optional cursor for "load more".
     */
    public CommentPage fetchCommentPage(long tripId, Integer requestedSize, String cursor) {
        int size = requestedSize == null ? DEFAULT_PAGE_SIZE : requestedSize;
        if (size < 1) {
            size = DEFAULT_PAGE_SIZE;
        }
        size = Math.min(size, MAX_PAGE_SIZE);

        Query q =
                firestore
                        .collection(COMMENTS)
                        .whereEqualTo("tripId", tripId)
                        .orderBy("createdAt", Query.Direction.DESCENDING)
                        .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                        .limit(size + 1);

        if (cursor != null && !cursor.isBlank()) {
            CursorPayload decoded = decodeCursor(cursor);
            q =
                    q.startAfter(
                            decoded.at(),
                            decoded.id());
        }

        QuerySnapshot snapshot;
        try {
            snapshot = q.get().get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Firestore query interrupted");
        } catch (ExecutionException e) {
            Throwable c = e.getCause() != null ? e.getCause() : e;
            if (isMissingOrInvalidFirestoreIndex(c)) {
                throw new ResponseStatusException(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        "Firestore comment query is not ready (missing composite index or rules)."
                                + " Deploy indexes from backend/firestore.indexes.json"
                                + " (e.g. firebase deploy --only firestore:indexes). Detail: "
                                + c.getMessage());
            }
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR, "Firestore comment query failed: " + c.getMessage());
        }

        List<QueryDocumentSnapshot> docs = snapshot.getDocuments();
        boolean hasMore = docs.size() > size;
        List<QueryDocumentSnapshot> pageDocs =
                hasMore ? docs.subList(0, size) : new ArrayList<>(docs);

        String nextCursor = null;
        if (hasMore && !pageDocs.isEmpty()) {
            QueryDocumentSnapshot last = pageDocs.get(pageDocs.size() - 1);
            nextCursor = encodeCursor(last.getLong("createdAt"), last.getId());
        }

        List<CommentRow> rows = new ArrayList<>(pageDocs.size());
        for (QueryDocumentSnapshot doc : pageDocs) {
            rows.add(fromSnapshot(doc));
        }
        return new CommentPage(rows, nextCursor, hasMore);
    }

    private static CommentRow fromSnapshot(QueryDocumentSnapshot doc) {
        Long tripId = doc.getLong("tripId");
        Long userId = doc.getLong("userId");
        String content = doc.getString("content");
        Long createdAt = doc.getLong("createdAt");
        return new CommentRow(
                doc.getId(),
                tripId != null ? tripId : 0L,
                userId != null ? userId : 0L,
                content != null ? content : "",
                createdAt != null ? createdAt : 0L);
    }

    private String encodeCursor(Long createdAtMillis, String documentId) {
        if (createdAtMillis == null || documentId == null) {
            return null;
        }
        try {
            String json =
                    "{\"at\":"
                            + createdAtMillis
                            + ",\"id\":"
                            + objectMapper.writeValueAsString(documentId)
                            + "}";
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(json.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "cursor encode failed");
        }
    }

    private CursorPayload decodeCursor(String cursor) {
        try {
            byte[] raw = Base64.getUrlDecoder().decode(cursor);
            JsonNode n = objectMapper.readTree(raw);
            if (!n.hasNonNull("at") || !n.hasNonNull("id")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
            }
            return new CursorPayload(n.get("at").asLong(), n.get("id").asText());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }

    public record CommentPage(List<CommentRow> items, String nextCursor, boolean hasMore) {}

    public record CommentRow(
            String id, long tripId, long userId, String content, long createdAtMillis) {}

    private record CursorPayload(long at, String id) {}

    /**
     * Firestore returns FAILED_PRECONDITION when a required composite index is missing (or similar
     * query setup issues). The error message usually mentions "index" and often includes a console
     * URL — we must not treat that as HTTP 400 (bad client input).
     */
    private static boolean isMissingOrInvalidFirestoreIndex(Throwable err) {
        for (Throwable t = err; t != null; t = t.getCause()) {
            if (t instanceof ApiException api
                    && api.getStatusCode() != null
                    && api.getStatusCode().getCode()
                            == com.google.api.gax.rpc.StatusCode.Code.FAILED_PRECONDITION) {
                return true;
            }
            String msg = t.getMessage();
            if (msg != null) {
                String m = msg.toLowerCase(Locale.ROOT);
                if (m.contains("requires an index")
                        || m.contains("the query requires a composite index")
                        || m.contains("failed_precondition")) {
                    return true;
                }
            }
        }
        return false;
    }
}

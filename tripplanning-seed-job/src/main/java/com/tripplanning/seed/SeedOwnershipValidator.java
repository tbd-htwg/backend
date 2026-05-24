package com.tripplanning.seed;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.tripplanning.seed.assets.DatasetSpec;
import com.tripplanning.seed.assets.SeedAssetLoader;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SeedOwnershipValidator {

    private final JdbcTemplate jdbc;
    private final Firestore firestore;
    private final SeedAssetLoader assetLoader;

    public SeedOwnershipValidator(
            JdbcTemplate jdbc,
            org.springframework.beans.factory.ObjectProvider<Firestore> firestoreProvider,
            SeedAssetLoader assetLoader) {
        this.jdbc = jdbc;
        this.firestore = firestoreProvider.getIfAvailable();
        this.assetLoader = assetLoader;
    }

    public void validate(SeedContext ctx) throws Exception {
        DatasetSpec spec = assetLoader.loadDatasetSpec();
        validateSqlTrips(spec.totalUsers());
        if (firestore != null) {
            validateFirestoreComments(spec.totalUsers(), spec.minCommentsPerUser());
            validateFirestoreLikes(spec.totalUsers(), spec.minLikesPerUser());
        } else {
            log.warn("Firestore not available; skipping social ownership validation.");
        }
        if (ctx.allTripIds().size() != spec.totalTrips()) {
            throw new IllegalStateException(
                    "Expected "
                            + spec.totalTrips()
                            + " trips but found "
                            + ctx.allTripIds().size());
        }
    }

    private void validateSqlTrips(int totalUsers) {
        List<Map<String, Object>> rows =
                jdbc.queryForList(
                        """
                        SELECT user_id, COUNT(*) AS cnt
                        FROM trips
                        WHERE user_id BETWEEN 1 AND ?
                        GROUP BY user_id
                        """,
                        totalUsers);
        if (rows.size() != totalUsers) {
            throw new IllegalStateException(
                    "Trip ownership check failed: expected "
                            + totalUsers
                            + " users with trips, got "
                            + rows.size());
        }
        for (Map<String, Object> row : rows) {
            long cnt = ((Number) row.get("cnt")).longValue();
            if (cnt < 1) {
                throw new IllegalStateException("User " + row.get("user_id") + " has no trips");
            }
        }
    }

    private void validateFirestoreComments(int totalUsers, int minPerUser) throws Exception {
        Set<Long> usersWithComments = new HashSet<>();
        for (QueryDocumentSnapshot doc :
                firestore.collection("comments").get().get().getDocuments()) {
            Long userId = doc.getLong("userId");
            if (userId != null && userId >= 1 && userId <= totalUsers) {
                usersWithComments.add(userId);
            }
        }
        if (usersWithComments.size() < totalUsers) {
            throw new IllegalStateException(
                    "Comment ownership check failed: only "
                            + usersWithComments.size()
                            + "/"
                            + totalUsers
                            + " users have comments (min "
                            + minPerUser
                            + " each required)");
        }
    }

    private void validateFirestoreLikes(int totalUsers, int minPerUser) throws Exception {
        Set<Long> usersWithLikes = new HashSet<>();
        for (QueryDocumentSnapshot doc : firestore.collection("likes").get().get().getDocuments()) {
            Long userId = doc.getLong("userId");
            if (userId != null && userId >= 1 && userId <= totalUsers) {
                usersWithLikes.add(userId);
            }
        }
        if (usersWithLikes.size() < totalUsers) {
            throw new IllegalStateException(
                    "Like ownership check failed: only "
                            + usersWithLikes.size()
                            + "/"
                            + totalUsers
                            + " users have likes (min "
                            + minPerUser
                            + " each required)");
        }
    }
}

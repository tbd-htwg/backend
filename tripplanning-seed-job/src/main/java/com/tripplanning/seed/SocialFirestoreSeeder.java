package com.tripplanning.seed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.google.cloud.firestore.Firestore;
import com.tripplanning.seed.assets.DatasetSpec;
import com.tripplanning.seed.assets.SeedAssetLoader;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class SocialFirestoreSeeder {

    private final Firestore firestore;
    private final SeedAssetLoader assetLoader;

    public SocialFirestoreSeeder(
            org.springframework.beans.factory.ObjectProvider<Firestore> firestoreProvider,
            SeedAssetLoader assetLoader) {
        this.firestore = firestoreProvider.getIfAvailable();
        this.assetLoader = assetLoader;
    }

    public Map<Long, List<String>> seed(SeedContext ctx) throws Exception {
        if (firestore == null) {
            log.warn("Firestore not available; skipping social seed.");
            return Map.of();
        }
        DatasetSpec spec = assetLoader.loadDatasetSpec();
        Random rng = new Random(43);
        Map<Long, List<String>> commentIdsByUser = new HashMap<>();
        Set<String> likeDocIds = new HashSet<>();

        for (long userId = 1; userId <= spec.totalUsers(); userId++) {
            List<Long> ownTrips = ctx.tripIdsForUser(userId);
            if (ownTrips.isEmpty()) {
                throw new IllegalStateException("User " + userId + " has no trips");
            }
            long ownTrip = ownTrips.get(0);
            String commentId =
                    writeComment(userId, ownTrip, PerfSeedText.comment(rng, ownTrip, userId));
            commentIdsByUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(commentId);

            long otherTrip = pickOtherTrip(ctx, userId, rng);
            String likeId = writeLike(userId, otherTrip);
            likeDocIds.add(likeId);
        }

        List<Long> socialTrips = selectSocialTrips(ctx.allTripIds(), spec.socialTripFraction(), rng);
        for (Long tripId : socialTrips) {
            long commenter = 1 + rng.nextInt(spec.totalUsers());
            String commentId =
                    writeComment(commenter, tripId, PerfSeedText.comment(rng, tripId, commenter));
            commentIdsByUser.computeIfAbsent(commenter, k -> new ArrayList<>()).add(commentId);

            long liker = 1 + rng.nextInt(spec.totalUsers());
            String likeId = writeLike(liker, tripId);
            likeDocIds.add(likeId);
        }

        log.info(
                "Firestore seed complete: comments~={}, likes~={}",
                commentIdsByUser.values().stream().mapToInt(List::size).sum(),
                likeDocIds.size());
        return commentIdsByUser;
    }

    private static List<Long> selectSocialTrips(List<Long> allTrips, double fraction, Random rng) {
        List<Long> shuffled = new ArrayList<>(allTrips);
        java.util.Collections.shuffle(shuffled, rng);
        int count = Math.max(1, (int) (allTrips.size() * fraction));
        return shuffled.subList(0, Math.min(count, shuffled.size()));
    }

    private static long pickOtherTrip(SeedContext ctx, long userId, Random rng) {
        List<Long> all = ctx.allTripIds();
        for (int attempt = 0; attempt < 20; attempt++) {
            long candidate = all.get(rng.nextInt(all.size()));
            if (!ctx.tripIdsForUser(userId).contains(candidate)) {
                return candidate;
            }
        }
        return all.get((int) ((userId + 7) % all.size()));
    }

    private String writeComment(long userId, long tripId, String content) throws Exception {
        Map<String, Object> doc = new HashMap<>();
        doc.put("tripId", tripId);
        doc.put("userId", userId);
        doc.put("content", content);
        doc.put("createdAt", System.currentTimeMillis());
        var ref = firestore.collection("comments").document();
        ref.set(doc).get();
        return ref.getId();
    }

    private String writeLike(long userId, long tripId) throws Exception {
        String docId = userId + "_" + tripId;
        Map<String, Object> doc = new HashMap<>();
        doc.put("userId", userId);
        doc.put("tripId", tripId);
        firestore.collection("likes").document(docId).set(doc).get();
        return docId;
    }
}

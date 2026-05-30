package com.tripplanning.seed;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanning.seed.assets.DatasetSpec;
import com.tripplanning.seed.assets.SeedAssetLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class ManifestWriter {

    private final ObjectMapper objectMapper;
    private final SeedAssetLoader assetLoader;
    private final SeedProperties seedProperties;

    public void write(SeedContext ctx, Map<Long, List<String>> commentIdsByUser) throws Exception {
        DatasetSpec spec = assetLoader.loadDatasetSpec();
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("user_id_min", 1);
        root.put("user_id_max", spec.totalUsers());
        root.put("user_count", spec.totalUsers());
        root.put("trip_id_min", ctx.tripIdMin());
        root.put("trip_id_max", ctx.tripIdMax());
        root.put("trip_count", ctx.allTripIds().size());
        root.put("perf_requirements", true);

        List<Long> viralTripIds = ctx.viralTripIds();
        if (viralTripIds.isEmpty()) {
            viralTripIds = ViralTripSupport.viralTripIds(ctx.allTripIds(), spec.viralTripIntervalOrDefault());
        }
        if (!viralTripIds.isEmpty()) {
            root.put("viral_trip_ids", viralTripIds);
            root.put("viral_trip_id", ViralTripSupport.canonicalViralTripId(viralTripIds));
        }

        Map<String, Object> users = new HashMap<>();
        for (long userId = 1; userId <= spec.totalUsers(); userId++) {
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("tripIds", ctx.tripIdsForUser(userId));
            entry.put("commentIds", commentIdsByUser.getOrDefault(userId, List.of()));
            users.put(Long.toString(userId), entry);
        }
        root.put("users", users);

        Path out = Path.of(seedProperties.manifestOutputPath());
        Files.createDirectories(out.getParent() != null ? out.getParent() : Path.of("."));
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), root);
        log.info("Wrote manifest to {}", out);

        String copyTo = seedProperties.copyManifestTo();
        if (copyTo != null && !copyTo.isBlank()) {
            Path copy = Path.of(copyTo);
            Files.createDirectories(copy.getParent() != null ? copy.getParent() : Path.of("."));
            Files.writeString(copy, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(root));
            log.info("Copied manifest to {}", copy);
        }
    }
}

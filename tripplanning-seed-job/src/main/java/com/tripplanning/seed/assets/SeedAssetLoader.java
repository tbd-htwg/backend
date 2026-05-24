package com.tripplanning.seed.assets;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Component
public class SeedAssetLoader {

    private final ObjectMapper objectMapper;

    public SeedAssetLoader(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DatasetSpec loadDatasetSpec() throws Exception {
        return objectMapper.readValue(
                new ClassPathResource("seed/dataset-spec.json").getInputStream(), DatasetSpec.class);
    }

    public List<PrefetchedPlace> loadPlaces() throws Exception {
        return objectMapper.readValue(
                new ClassPathResource("seed/google-places.json").getInputStream(),
                new TypeReference<>() {});
    }

    public List<SampleImageRow> loadSampleImages() throws Exception {
        List<SampleImageRow> rows = new ArrayList<>();
        try (var reader =
                new BufferedReader(
                        new InputStreamReader(
                                new ClassPathResource("seed/sample-images.csv").getInputStream(),
                                StandardCharsets.UTF_8))) {
            String header = reader.readLine();
            if (header == null) {
                return rows;
            }
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] parts = parseCsvLine(line);
                if (parts.length < 6) {
                    continue;
                }
                rows.add(
                        new SampleImageRow(
                                parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]));
            }
        }
        return rows;
    }

    /** Minimal CSV parser (fields are simple; author names rarely contain commas in our export). */
    private static String[] parseCsvLine(String line) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(String[]::new);
    }
}

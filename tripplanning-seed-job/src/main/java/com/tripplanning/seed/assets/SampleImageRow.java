package com.tripplanning.seed.assets;

public record SampleImageRow(
        String category,
        String contentId,
        String author,
        String filePath,
        String imagePath,
        String contentType,
        String regionTag) {

    public SampleImageRow(
            String category,
            String contentId,
            String author,
            String filePath,
            String imagePath,
            String contentType) {
        this(category, contentId, author, filePath, imagePath, contentType, PlaceSeedSupport.REGION_GENERIC);
    }
}

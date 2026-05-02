package com.tripplanning.images;

import java.text.Normalizer;

public final class ImageUploadDtos {
    private ImageUploadDtos() {}

    public record CreateUploadRequest(String fileName, String contentType) {}

    public record CreateUploadResponse(
            Long imageId,
            String uploadUrl, //signed upload URL (time limited)
            String signedReadUrl, //signed read URL (time limited)
            String objectName, //image path in database
            String contentType) {}

    /**
     * Link a bucket object to a trip stop. URL uses {@code /trip-location-images/preuploaded} so Spring
     * Data REST does not treat extra path segments under {@code /trip-locations/{id}/…} as repository
     * properties (which returned HTTP 404 before the controller ran).
     */
    public record RegisterPreuploadedBody(Long tripLocationId, String imagePath) {}

    public record RegisterPreuploadedResponse(Long imageId, String signedReadUrl, String objectName) {}

    /** Validates GCS object name for pre-uploaded registration. */
    public static String normalizedRegisteredObjectName(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("imagePath is required.");
        }
        String objectName = Normalizer.normalize(raw.trim(), Normalizer.Form.NFC);
        if (objectName.startsWith("/")) {
            throw new IllegalArgumentException("imagePath must be a relative object name.");
        }
        rejectPathTraversalSegments(objectName);
        if (objectName.length() > 500) {
            throw new IllegalArgumentException("imagePath exceeds maximum length.");
        }
        return objectName;
    }

    /**
     * Blocks {@code ..} / {@code .} as path segments only. Filenames such as {@code alexey_k..jpg} are
     * allowed (unlike a naive {@code contains("..")} check, which wrongly rejected those).
     */
    private static void rejectPathTraversalSegments(String objectName) {
        String normalized = objectName.replace('\\', '/');
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (segment.equals("..") || segment.equals(".")) {
                throw new IllegalArgumentException(
                        "imagePath must not contain '.' or '..' as a path segment.");
            }
        }
    }
}

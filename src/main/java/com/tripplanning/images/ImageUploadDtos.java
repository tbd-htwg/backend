package com.tripplanning.images;

public final class ImageUploadDtos {
    private ImageUploadDtos() {}

    public record CreateUploadRequest(String fileName, String contentType) {}

    public record CreateUploadResponse(
            Long imageId,
            String uploadUrl,
            String objectUrl,
            String objectName,
            String contentType) {}
}
